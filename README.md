# cold-monitor-backend

[English](#english) · [Português (Brasil)](#português-brasil)

---

## English

Backend for **cold-monitor**, a cold-chain temperature monitoring system. Sensors in fridges, freezers and cold rooms
send temperature readings to this API; each reading is stored and checked against the minimum and maximum thresholds
registered for its equipment. Readings outside that range are flagged, every reading is pushed live to the
[dashboard](https://github.com/carloslpv/cold-monitor-dashboard) over Server-Sent Events, and a Telegram alert goes out
when an equipment leaves its range, when its sensor stops sending readings, and again when either situation is over.
The API is protected: people log in with a user name and password, and sensors send a device API key.

The architecture diagram, screenshots and an overview of the whole system are in
[cold-monitor-infra](https://github.com/carloslpv/cold-monitor-infra).

### Stack

- Java 25
- Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator, Security, Session JDBC)
- PostgreSQL 18 with Flyway migrations
- Maven (wrapper included)
- Testcontainers for integration tests

### Running locally

Requirements: JDK 25 and Docker.

Set `ADMIN_PASSWORD` and `DEVICE_API_KEY` first (in the environment or in `.env`, see [Configuration](#configuration));
the app does not start without an administrator password.

```bash
docker compose up -d
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. PostgreSQL is exposed on host port **5433** so it does not clash with
another local instance on 5432.

To run the app with a throwaway database managed by Testcontainers instead of Docker Compose:

```bash
./mvnw spring-boot:test-run
```

`test-run` uses the test credentials from `src/test/resources/config/application.yaml`: user `admin`, password
`test-admin-password` and device key `test-device-api-key-0123456789`.

### Docker

The `Dockerfile` builds the app with the Maven wrapper and runs it on a JRE as a non-root user, with a health check on
`/actuator/health`:

```bash
docker build -t cold-monitor-backend .
docker run -p 8080:8080 -e DB_HOST=host.docker.internal -e ADMIN_PASSWORD=... -e DEVICE_API_KEY=... cold-monitor-backend
```

To run the whole system (database, backend, dashboard) around the clock, use the compose file in
[cold-monitor-infra](https://github.com/carloslpv/cold-monitor-infra).

### Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5433` | PostgreSQL port (also used by `compose.yaml`) |
| `DB_NAME` | `coldmonitor` | Database name |
| `DB_USER` | `coldmonitor` | Database user |
| `DB_PASSWORD` | `coldmonitor` | Database password |
| `SERVER_PORT` | `8080` | HTTP port |
| `ADMIN_USERNAME` | `admin` | User name of the administrator who logs in to the dashboard |
| `ADMIN_PASSWORD` | (required) | Administrator password, at least 8 characters. The app does not start without it |
| `VIEWER_USERNAME` | `viewer` | User name of the optional read-only account |
| `VIEWER_PASSWORD` | (empty) | Its password, at least 8 characters. The account exists only while this is set |
| `DEVICE_API_KEY` | (empty) | Key sensors send in `X-API-Key`, at least 16 characters (e.g. `openssl rand -hex 32`). Sensors cannot send readings while it is empty |
| `SESSION_TIMEOUT` | `12h` | How long a login lasts without requests (an open dashboard keeps it alive) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins allowed to call `/api/**` with the session cookie (the dashboard dev server by default) |
| `TELEGRAM_BOT_TOKEN` | (empty) | Bot token from @BotFather. Alerts are only logged while this or the chat id is empty |
| `TELEGRAM_CHAT_ID` | (empty) | Chat that receives the alerts |
| `ALERT_RECOVERY_READINGS` | `2` | Consecutive in-range readings needed before an excursion counts as over |
| `ALERT_OFFLINE_AFTER` | `5m` | Time without readings before an equipment counts as offline (`30s`, `5m`, `1h`, …) |
| `ALERT_OFFLINE_CHECK_INTERVAL` | `15s` | How often equipment is checked for missing readings |
| `ALERT_TIME_ZONE` | `America/Sao_Paulo` | Time zone of the times written in alert messages |

Variables can also go in a `.env` file at the project root (one `KEY=value` per line), which the app reads on startup.
The file is git-ignored; keep secrets such as the bot token there or in the environment, never in `application.yaml`.

### Authentication

Everything under `/api/v1` needs authentication except the login itself; `/actuator/health` stays public for health
checks.

- **People (dashboard)**: there is one administrator, `ADMIN_USERNAME` / `ADMIN_PASSWORD`; changing the password in the
  environment and restarting changes it. `POST /api/v1/auth/login` with the form fields `username` and `password` starts
  a session and sets an HTTP-only cookie, which also authorizes the live stream. `GET /api/v1/auth/me` returns the
  logged-in user (or 401) and `POST /api/v1/auth/logout` ends the session. Sessions are stored in PostgreSQL (Spring
  Session JDBC), so restarting the backend does not log anyone out.
- **Read-only account**: with `VIEWER_PASSWORD` set, `VIEWER_USERNAME` logs in the same way and may call every `GET`
  endpoint, live stream included, but nothing that changes data (403). The public demo hands this login to visitors.
- **CSRF**: requests that change data with the session cookie must echo the value of the `XSRF-TOKEN` cookie in the
  `X-XSRF-TOKEN` header. The dashboard does this on its own.
- **Sensors**: send `X-API-Key: <DEVICE_API_KEY>`. A device may list and fetch equipment, register equipment and post
  readings (`GET /equipment`, `GET /equipment/{id}`, `POST /equipment`, `POST /equipment/{id}/readings`); anything
  else answers 403. Devices get no session and no CSRF check.
- Failures are problem details: 401 with `Authentication required`, `Invalid username or password` or
  `Invalid API key`, and 403 with `Access denied`.

```bash
# a sensor sending a reading
curl -H "X-API-Key: $DEVICE_API_KEY" -H 'Content-Type: application/json' \
  -d '{"temperature": 4.2}' http://localhost:8080/api/v1/equipment/<id>/readings

# logging in from the command line: the first request hands out the XSRF-TOKEN cookie
curl -s -c cookies.txt http://localhost:8080/api/v1/auth/me > /dev/null
curl -b cookies.txt -c cookies.txt -H "X-XSRF-TOKEN: $(awk '$6 == "XSRF-TOKEN" {print $7}' cookies.txt)" \
  -d username=admin --data-urlencode "password=$ADMIN_PASSWORD" http://localhost:8080/api/v1/auth/login
```

### API

All endpoints are under `/api/v1`. Errors follow RFC 9457 (`application/problem+json`); validation errors include an
`errors` object mapping each field to its message.

#### Equipment

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/equipment` | Register equipment |
| `GET` | `/equipment?active=true` | List equipment, optionally filtered by `active` |
| `GET` | `/equipment/{id}` | Get one equipment |
| `PUT` | `/equipment/{id}` | Update equipment, thresholds or `active` flag |

```json
{
  "code": "FRIDGE-01",
  "name": "Vaccine fridge",
  "location": "Pharmacy",
  "type": "REFRIGERATOR",
  "minTemperature": 2.0,
  "maxTemperature": 8.0
}
```

`type` is one of `REFRIGERATOR`, `FREEZER`, `COLD_ROOM`, `OTHER`. `code` must be unique, temperatures are in °C with up
to two decimals between -100 and 100, and `minTemperature` must be lower than `maxTemperature`.

#### Readings

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/equipment/{id}/readings` | Record a reading |
| `GET` | `/equipment/{id}/readings?from=&to=&page=&size=` | Reading history, newest first |
| `GET` | `/equipment/{id}/readings/latest` | Most recent reading |
| `GET` | `/readings/out-of-range?equipmentId=&from=&to=&page=&size=` | Out-of-range readings, newest first |
| `GET` | `/readings/stream` | Live stream of new readings (Server-Sent Events) |

```json
{ "temperature": 9.3, "recordedAt": "2026-10-17T03:00:00Z" }
```

`recordedAt` is the sensor timestamp and is optional (server time is used when omitted). Timestamps more than five
minutes ahead of the server clock are rejected, and inactive equipment does not accept readings. `from`/`to` are ISO-8601
instants and default to the last 24 hours.

Each reading is returned with a `status`:

- `IN_RANGE`: `minTemperature <= temperature <= maxTemperature` (both limits inclusive)
- `BELOW_MIN`: below the minimum
- `ABOVE_MAX`: above the maximum

The thresholds in force when the reading arrived are stored with it, so changing an equipment's limits later does not
rewrite the history.

#### Sensor outages

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/sensor-outages?open=true` | Equipment currently offline (no readings for `ALERT_OFFLINE_AFTER`) |
| `GET` | `/sensor-outages?from=&to=` | Outages detected in the window (last 24 hours by default), newest first |

```json
{
  "id": "1426e142-…",
  "equipmentId": "f4dbf3b8-…",
  "equipmentCode": "SIM-FRIDGE-01",
  "lastReadingAt": "2026-10-17T05:18:21Z",
  "lastTemperature": 5.03,
  "detectedAt": "2026-10-17T05:18:52Z",
  "endedAt": null,
  "endTemperature": null
}
```

`lastReadingAt` and `endedAt` are arrival times on the server clock; `endedAt` stays `null` while the sensor is silent.

### Live stream

`GET /api/v1/readings/stream` is a `text/event-stream` that sends a `reading` event for every reading stored after the
client connects. The event `id` is the reading id and `data` has the same JSON shape as the other reading endpoints:

```
event:reading
id:06fc79e3-e00f-44f1-a9b9-1d91d76d1781
data:{"id":"06fc79e3-…","equipmentId":"313de0d3-…","equipmentCode":"SIM-FRIDGE-02","temperature":5.20,"status":"IN_RANGE",…}
```

Events are sent after the reading's transaction commits. A `keep-alive` comment goes out every 15 seconds, and the
server closes each stream after 30 minutes; browsers' `EventSource` reconnects on its own (the stream asks for a
3-second retry). Readings stored while a client is disconnected are not replayed, so clients reload history from the
REST endpoints after reconnecting.

The same stream sends a `sensor-outage` event when an equipment goes offline and again when its readings resume. Its
`data` has the shape of the `/sensor-outages` endpoint (`endedAt` is `null` in the first one). An `equipment` event,
with the shape of the `/equipment` endpoints, goes out whenever equipment is registered or updated (thresholds,
`active` flag), so clients can refresh their equipment list.

```bash
curl -N -b cookies.txt http://localhost:8080/api/v1/readings/stream   # after logging in as shown above
```

### Out-of-range alerts

Alerts work per **excursion**, a continuous stretch of out-of-range readings on one equipment, not per reading:

- The first out-of-range reading opens an excursion and sends an "above the maximum" (or "below the minimum") alert
  with the reading, the allowed range and the start time.
- Further out-of-range readings send nothing; the excursion keeps track of the lowest and highest temperature.
- Once `ALERT_RECOVERY_READINGS` consecutive readings are back in range (2 by default, so a value hovering at the limit
  does not flap), the excursion closes and a "back in range" alert goes out with the current reading, the peak
  temperature and how long it lasted. The end time is the first of those in-range readings.
- Readings older than the latest one stored for that equipment (a sensor flushing its buffer) are stored but do not
  open or close excursions.

Excursions are stored in the `excursion` table, so an excursion that is open when the app restarts is still closed and
announced correctly afterwards. Alerts are sent in order on a background thread after the reading's transaction
commits; Telegram failures are logged and retried up to 3 times, honouring Telegram's `retry_after` on HTTP 429. Every
alert is also logged at `WARN`, and each out-of-range reading still publishes an `OutOfRangeReadingEvent` (logged at
`INFO`). Messages are written in Brazilian Portuguese:

```
🔴 Temperatura acima do máximo
Vaccine fridge (SIM-FRIDGE-01) · Pharmacy
Leitura: 9,3 °C · faixa: 2,0 a 8,0 °C
Início: 17/10/2026 02:00:00
```

### Offline sensor alerts

A sensor that stops sending readings (power cut, network down, dead battery) would otherwise go unnoticed, so the
backend also watches for silence:

- Every `ALERT_OFFLINE_CHECK_INTERVAL` (15 s), each active equipment whose last reading arrived more than
  `ALERT_OFFLINE_AFTER` ago (5 min) gets an open **sensor outage** and a "sensor without readings" alert with the last
  reading and how long it has been silent. If the equipment was in an excursion when it went silent, the alert says so,
  since nobody can tell whether the temperature recovered.
- The next reading from that equipment closes the outage and sends a "sensor is back" alert with the new reading and
  how long the gap lasted. It goes out before any excursion alert about that same reading.
- Equipment that never sent a reading and inactive equipment are not checked. Silence is measured with the server's
  arrival times, so a sensor with a wrong clock is still judged correctly.
- Right after the backend starts, silence counts from the startup time, so a backend restart (when no reading could
  arrive) does not flag every sensor.

Outages are stored in the `sensor_outage` table, so one still open across a restart is not announced twice. Messages:

```
⚠️ Sensor sem leituras
Vaccine fridge (SIM-FRIDGE-01) · Pharmacy
Última leitura: 5,03 °C em 17/10/2026 02:18:21
Sem leituras há 31 s
Atenção: estava acima do máximo desde 17/10/2026 02:15:00 · máxima: 9,8 °C
```

```
🟢 Sensor voltou a enviar leituras
Vaccine fridge (SIM-FRIDGE-01) · Pharmacy
Leitura: 4,34 °C · faixa: 2,0 a 8,0 °C
Sem leituras por 46 s
Retorno: 17/10/2026 02:19:07
```

#### Setting up the Telegram bot

1. In Telegram, talk to [@BotFather](https://t.me/BotFather), send `/newbot` and pick a name and a username ending in
   `bot`. BotFather replies with the bot token.
2. Open a chat with the new bot and send `/start` (a bot cannot message you first). To alert a group instead, add the
   bot to the group and send a message there.
3. Open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser and copy `message.chat.id` (group ids are
   negative).
4. Set `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID`, in the environment or in `.env`, and restart the app. The log says
   `Telegram alerts on` at startup.

The Telegram Bot API is free. Tests and `spring-boot:test-run` never send real alerts: they blank both settings in
`src/test/resources/config/application.yaml`, and the alert tests use a fake Telegram server.

### Technical decisions

- **Server-Sent Events for the live stream.** Updates only flow from the server to the dashboard, and every change
  goes through REST, so a bidirectional WebSocket would add a protocol without adding anything. SSE is plain HTTP: the
  session cookie authorizes it (`EventSource` cannot send custom headers), proxies pass it with buffering off, and the
  browser reconnects on its own. `ReadingStream` sends after the transaction commits (`@TransactionalEventListener`),
  from a single virtual thread, so a slow client never delays ingestion and every client sees events in commit order.
  Streams are closed as soon as shutdown starts, so graceful shutdown does not wait for them.
- **Alerts per excursion, with hysteresis.** `ExcursionTracker` opens an excursion on the first out-of-range reading
  and closes it after `ALERT_RECOVERY_READINGS` consecutive in-range readings, so one incident produces two messages
  instead of one per reading, and a value hovering at the limit does not flap. It runs inside the reading's transaction,
  so the reading and the excursion state commit or roll back together.
- **A row lock per equipment.** Two readings of the same equipment arriving at once could both open an excursion.
  Both the tracker and the offline watchdog take a `PESSIMISTIC_WRITE` lock on the equipment row, which serializes them
  per equipment without a global lock.
- **Late readings are stored but do not change state.** A sensor flushing its buffer after a network outage sends old
  readings; they go into the history but cannot open or close an excursion that newer readings already decided.
- **Offline detection by arrival time.** `SensorWatchdog` compares `received_at` (server clock), not the sensor's
  `recorded_at`, so a sensor with a wrong clock is still judged correctly, and it counts silence from startup at the
  earliest, so restarting the backend does not raise an alert for every sensor.
- **History that explains itself.** Each reading stores the thresholds it was checked against, so editing an
  equipment's range never changes the status of past readings. A partial index on out-of-range readings keeps that
  query fast as the table grows.
- **Sessions in PostgreSQL, API key for devices.** A form login with an HTTP-only cookie keeps credentials out of
  JavaScript, Spring Session JDBC keeps logins through restarts, and `csrf.spa()` protects writes. Sensors have no
  browser and no cookie, so they use `X-API-Key`, compared in constant time, with no session and no CSRF check.
- **Telegram off the request path.** `AlertNotifier` sends after the commit on a single background thread, in order,
  retrying network errors and honouring `retry_after` on HTTP 429, and gives up on 4xx errors that retrying cannot
  fix. The token is redacted from logged errors.
- **Schema owned by Flyway, tests on real PostgreSQL.** Hibernate only validates the schema (`ddl-auto: validate`) and
  integration tests use Testcontainers, so partial indexes, `TIMESTAMPTZ` and locks behave as in production.

### Tests

```bash
./mvnw test
```

Integration tests start PostgreSQL through Testcontainers, so Docker must be running. GitHub Actions
(`.github/workflows/ci.yml`) runs them and builds the Docker image on every push and pull request.

### Project layout

```
src/main/java/dev/carloslpv/coldmonitor
├── common      error handling, paging, clock, CORS
├── equipment   equipment registration and thresholds
├── reading     reading ingestion, history, out-of-range detection and live stream
├── alert       excursion and sensor outage tracking, Telegram alerts
└── security    login, sessions, CSRF and the device API key
src/main/resources/db/migration   Flyway migrations
```

### License

[PolyForm Noncommercial 1.0.0](LICENSE.md): you may read, run and modify this code for personal, study, research and
other noncommercial purposes. Commercial use needs written permission from the author; get in touch through
[GitHub](https://github.com/carloslpv).

---

## Português (Brasil)

Backend do **cold-monitor**, um sistema de monitoramento de temperatura da cadeia fria. Sensores em geladeiras, freezers
e câmaras frias enviam leituras de temperatura para esta API; cada leitura é gravada e comparada com os limites mínimo
e máximo cadastrados para o equipamento. Leituras fora da faixa são sinalizadas, toda leitura é enviada ao vivo para o
[dashboard](https://github.com/carloslpv/cold-monitor-dashboard) por Server-Sent Events, e um alerta vai para o
Telegram quando um equipamento sai da faixa, quando o sensor dele para de enviar leituras, e de novo quando cada
situação termina. A API é protegida: pessoas entram com usuário e senha, e os sensores enviam uma chave de API de
dispositivo.

O diagrama de arquitetura, os screenshots e a visão geral do sistema inteiro estão no
[cold-monitor-infra](https://github.com/carloslpv/cold-monitor-infra).

### Tecnologias

- Java 25
- Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator, Security, Session JDBC)
- PostgreSQL 18 com migrações Flyway
- Maven (wrapper incluído)
- Testcontainers nos testes de integração

### Como rodar localmente

Requisitos: JDK 25 e Docker.

Antes, defina `ADMIN_PASSWORD` e `DEVICE_API_KEY` (no ambiente ou no `.env`, veja [Configuração](#configuração)); a
aplicação não sobe sem a senha do administrador.

```bash
docker compose up -d
./mvnw spring-boot:run
```

A API responde em `http://localhost:8080`. O PostgreSQL fica exposto na porta **5433** do host para não conflitar com
outra instância local na 5432.

Para rodar a aplicação com um banco descartável gerenciado pelo Testcontainers, sem o Docker Compose:

```bash
./mvnw spring-boot:test-run
```

O `test-run` usa as credenciais de teste de `src/test/resources/config/application.yaml`: usuário `admin`, senha
`test-admin-password` e chave de dispositivo `test-device-api-key-0123456789`.

### Docker

O `Dockerfile` compila a aplicação com o Maven wrapper e roda num JRE com usuário sem privilégios, com health check em
`/actuator/health`:

```bash
docker build -t cold-monitor-backend .
docker run -p 8080:8080 -e DB_HOST=host.docker.internal -e ADMIN_PASSWORD=... -e DEVICE_API_KEY=... cold-monitor-backend
```

Para rodar o sistema inteiro (banco, backend e dashboard) 24 horas por dia, use o compose do
[cold-monitor-infra](https://github.com/carloslpv/cold-monitor-infra).

### Configuração

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5433` | Porta do PostgreSQL (também usada pelo `compose.yaml`) |
| `DB_NAME` | `coldmonitor` | Nome do banco |
| `DB_USER` | `coldmonitor` | Usuário do banco |
| `DB_PASSWORD` | `coldmonitor` | Senha do banco |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `ADMIN_USERNAME` | `admin` | Usuário do administrador que entra no dashboard |
| `ADMIN_PASSWORD` | (obrigatória) | Senha do administrador, com pelo menos 8 caracteres. A aplicação não sobe sem ela |
| `VIEWER_USERNAME` | `viewer` | Usuário da conta opcional só de leitura |
| `VIEWER_PASSWORD` | (vazio) | A senha dela, com pelo menos 8 caracteres. A conta só existe enquanto esta variável estiver definida |
| `DEVICE_API_KEY` | (vazio) | Chave que os sensores enviam em `X-API-Key`, com pelo menos 16 caracteres (por exemplo, `openssl rand -hex 32`). Enquanto estiver vazia, os sensores não conseguem enviar leituras |
| `SESSION_TIMEOUT` | `12h` | Quanto tempo um login dura sem requisições (um dashboard aberto mantém a sessão viva) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origens de navegador, separadas por vírgula, que podem chamar `/api/**` com o cookie de sessão (por padrão, o servidor de desenvolvimento do dashboard) |
| `TELEGRAM_BOT_TOKEN` | (vazio) | Token do bot criado no @BotFather. Enquanto ele ou o chat id estiver vazio, os alertas só vão para o log |
| `TELEGRAM_CHAT_ID` | (vazio) | Chat que recebe os alertas |
| `ALERT_RECOVERY_READINGS` | `2` | Leituras seguidas dentro da faixa para considerar a excursão encerrada |
| `ALERT_OFFLINE_AFTER` | `5m` | Tempo sem leituras para considerar o equipamento offline (`30s`, `5m`, `1h`, …) |
| `ALERT_OFFLINE_CHECK_INTERVAL` | `15s` | De quanto em quanto tempo os equipamentos são verificados |
| `ALERT_TIME_ZONE` | `America/Sao_Paulo` | Fuso horário dos horários escritos nos alertas |

As variáveis também podem ficar num arquivo `.env` na raiz do projeto (uma `CHAVE=valor` por linha), que a aplicação
lê ao subir. O arquivo está no `.gitignore`; guarde segredos como o token do bot nele ou no ambiente, nunca no
`application.yaml`.

### Autenticação

Tudo em `/api/v1` exige autenticação, menos o próprio login; o `/actuator/health` continua público para os health
checks.

- **Pessoas (dashboard)**: existe um administrador, `ADMIN_USERNAME` / `ADMIN_PASSWORD`; para trocar a senha, mude a
  variável e reinicie. `POST /api/v1/auth/login` com os campos de formulário `username` e `password` abre uma sessão e
  grava um cookie HTTP-only, que também autoriza o fluxo ao vivo. `GET /api/v1/auth/me` devolve o usuário logado (ou
  401) e `POST /api/v1/auth/logout` encerra a sessão. As sessões ficam no PostgreSQL (Spring Session JDBC), então
  reiniciar o backend não desloga ninguém.
- **Conta só de leitura**: com `VIEWER_PASSWORD` definida, o `VIEWER_USERNAME` entra do mesmo jeito e pode chamar todos
  os endpoints `GET`, inclusive o fluxo ao vivo, mas nada que altere dados (403). A demo pública entrega esse login aos
  visitantes.
- **CSRF**: requisições que alteram dados com o cookie de sessão precisam repetir o valor do cookie `XSRF-TOKEN` no
  cabeçalho `X-XSRF-TOKEN`. O dashboard faz isso sozinho.
- **Sensores**: enviam `X-API-Key: <DEVICE_API_KEY>`. Um dispositivo pode listar e buscar equipamentos, cadastrar
  equipamentos e enviar leituras (`GET /equipment`, `GET /equipment/{id}`, `POST /equipment`,
  `POST /equipment/{id}/readings`); o resto responde 403. Dispositivos não têm sessão nem verificação de CSRF.
- As falhas vêm como problem details: 401 com `Authentication required`, `Invalid username or password` ou
  `Invalid API key`, e 403 com `Access denied`.

Os exemplos com `curl` (sensor enviando leitura e login pela linha de comando) estão na seção em inglês.

### API

Todos os endpoints ficam em `/api/v1`. Os erros seguem a RFC 9457 (`application/problem+json`); erros de validação
trazem um objeto `errors` com a mensagem de cada campo.

#### Equipamentos

| Método | Caminho | Descrição |
| --- | --- | --- |
| `POST` | `/equipment` | Cadastra um equipamento |
| `GET` | `/equipment?active=true` | Lista equipamentos, com filtro opcional por `active` |
| `GET` | `/equipment/{id}` | Busca um equipamento |
| `PUT` | `/equipment/{id}` | Atualiza o equipamento, os limites ou o campo `active` |

```json
{
  "code": "FRIDGE-01",
  "name": "Geladeira de vacinas",
  "location": "Farmácia",
  "type": "REFRIGERATOR",
  "minTemperature": 2.0,
  "maxTemperature": 8.0
}
```

`type` aceita `REFRIGERATOR`, `FREEZER`, `COLD_ROOM` ou `OTHER`. O `code` é único, as temperaturas são em °C com até
duas casas decimais entre -100 e 100, e `minTemperature` precisa ser menor que `maxTemperature`.

#### Leituras

| Método | Caminho | Descrição |
| --- | --- | --- |
| `POST` | `/equipment/{id}/readings` | Registra uma leitura |
| `GET` | `/equipment/{id}/readings?from=&to=&page=&size=` | Histórico de leituras, da mais recente para a mais antiga |
| `GET` | `/equipment/{id}/readings/latest` | Leitura mais recente |
| `GET` | `/readings/out-of-range?equipmentId=&from=&to=&page=&size=` | Leituras fora da faixa, da mais recente para a mais antiga |
| `GET` | `/readings/stream` | Fluxo ao vivo das novas leituras (Server-Sent Events) |

```json
{ "temperature": 9.3, "recordedAt": "2026-10-17T03:00:00Z" }
```

`recordedAt` é o horário do sensor e é opcional (sem ele, vale o horário do servidor). Horários mais de cinco minutos à
frente do relógio do servidor são rejeitados, e equipamentos inativos não aceitam leituras. `from`/`to` são instantes
ISO-8601 e, por padrão, cobrem as últimas 24 horas.

Cada leitura volta com um `status`:

- `IN_RANGE`: `minTemperature <= temperature <= maxTemperature` (os dois limites são inclusivos)
- `BELOW_MIN`: abaixo do mínimo
- `ABOVE_MAX`: acima do máximo

Os limites vigentes no momento da leitura ficam gravados junto com ela, então mudar os limites de um equipamento depois
não reescreve o histórico.

#### Sensores sem leituras

| Método | Caminho | Descrição |
| --- | --- | --- |
| `GET` | `/sensor-outages?open=true` | Equipamentos offline agora (sem leituras há `ALERT_OFFLINE_AFTER`) |
| `GET` | `/sensor-outages?from=&to=` | Períodos sem leituras detectados na janela (por padrão, últimas 24 horas), do mais recente para o mais antigo |

`lastReadingAt` e `endedAt` são horários de chegada no relógio do servidor; `endedAt` fica `null` enquanto o sensor
continua sem enviar leituras. O formato do JSON está na seção em inglês.

### Fluxo ao vivo

`GET /api/v1/readings/stream` é um `text/event-stream` que envia um evento `reading` para cada leitura gravada depois
que o cliente se conecta. O `id` do evento é o id da leitura e o `data` tem o mesmo JSON dos outros endpoints de
leitura:

```
event:reading
id:06fc79e3-e00f-44f1-a9b9-1d91d76d1781
data:{"id":"06fc79e3-…","equipmentId":"313de0d3-…","equipmentCode":"SIM-FRIDGE-02","temperature":5.20,"status":"IN_RANGE",…}
```

Os eventos saem depois do commit da transação da leitura. Um comentário `keep-alive` é enviado a cada 15 segundos, e o
servidor encerra cada conexão após 30 minutos; o `EventSource` do navegador reconecta sozinho (o fluxo pede 3 segundos
entre tentativas). Leituras gravadas enquanto o cliente estava desconectado não são reenviadas, então o cliente
recarrega o histórico pelos endpoints REST depois de reconectar.

O mesmo fluxo envia um evento `sensor-outage` quando um equipamento fica offline e de novo quando as leituras voltam. O
`data` tem o formato do endpoint `/sensor-outages` (`endedAt` vem `null` no primeiro). Um evento `equipment`, com o
formato dos endpoints `/equipment`, sai sempre que um equipamento é cadastrado ou atualizado (limites, campo `active`),
para os clientes atualizarem a lista de equipamentos.

```bash
curl -N -b cookies.txt http://localhost:8080/api/v1/readings/stream   # depois do login, como na seção em inglês
```

### Alertas de fora da faixa

Os alertas são por **excursão**, um trecho contínuo de leituras fora da faixa num equipamento, e não por leitura:

- A primeira leitura fora da faixa abre uma excursão e envia um alerta de "acima do máximo" (ou "abaixo do mínimo") com
  a leitura, a faixa permitida e o horário de início.
- As leituras fora da faixa seguintes não enviam nada; a excursão guarda a menor e a maior temperatura.
- Quando `ALERT_RECOVERY_READINGS` leituras seguidas voltam para a faixa (2 por padrão, para um valor oscilando no
  limite não ficar abrindo e fechando), a excursão é encerrada e vai um alerta de "de volta à faixa" com a leitura atual,
  a temperatura de pico e a duração. O horário de fim é o da primeira dessas leituras dentro da faixa.
- Leituras mais antigas que a última gravada para o equipamento (um sensor esvaziando o buffer) são gravadas, mas não
  abrem nem encerram excursões.

As excursões ficam na tabela `excursion`, então uma excursão aberta quando a aplicação reinicia continua sendo encerrada
e avisada corretamente depois. Os alertas saem em ordem, numa thread em segundo plano, depois do commit da transação da
leitura; falhas no Telegram vão para o log e são tentadas de novo até 3 vezes, respeitando o `retry_after` do Telegram
em respostas HTTP 429. Todo alerta também é registrado no log em `WARN`, e cada leitura fora da faixa continua
publicando um `OutOfRangeReadingEvent` (registrado em `INFO`). As mensagens são em português:

```
✅ Temperatura de volta à faixa
Vaccine fridge (SIM-FRIDGE-01) · Pharmacy
Leitura: 5,5 °C · faixa: 2,0 a 8,0 °C
Máxima: 10,1 °C · duração: 4 min
Fim: 17/10/2026 02:04:00
```

### Alertas de sensor sem leituras

Um sensor que para de enviar leituras (queda de energia, rede fora, bateria acabando) passaria despercebido, então o
backend também vigia o silêncio:

- A cada `ALERT_OFFLINE_CHECK_INTERVAL` (15 s), todo equipamento ativo cuja última leitura chegou há mais de
  `ALERT_OFFLINE_AFTER` (5 min) ganha um **período sem leituras** aberto e um alerta de "sensor sem leituras" com a
  última leitura e há quanto tempo ele está em silêncio. Se o equipamento estava em excursão quando parou, o alerta
  avisa, porque ninguém sabe se a temperatura voltou.
- A próxima leitura do equipamento encerra o período e envia um alerta de "sensor voltou" com a leitura nova e quanto
  tempo durou a falha. Ele sai antes de qualquer alerta de excursão sobre essa mesma leitura.
- Equipamentos que nunca enviaram leitura e equipamentos inativos não são verificados. O silêncio é medido pelo horário
  de chegada no servidor, então um sensor com relógio errado continua sendo avaliado corretamente.
- Logo depois que o backend sobe, o silêncio conta a partir da subida, então reiniciar o backend (quando nenhuma
  leitura consegue chegar) não marca todos os sensores como offline.

Os períodos ficam na tabela `sensor_outage`, então um período aberto durante um reinício não é avisado duas vezes.
Mensagens:

```
⚠️ Sensor sem leituras
Vaccine fridge (SIM-FRIDGE-01) · Pharmacy
Última leitura: 5,03 °C em 17/10/2026 02:18:21
Sem leituras há 31 s
Atenção: estava acima do máximo desde 17/10/2026 02:15:00 · máxima: 9,8 °C
```

```
🟢 Sensor voltou a enviar leituras
Vaccine fridge (SIM-FRIDGE-01) · Pharmacy
Leitura: 4,34 °C · faixa: 2,0 a 8,0 °C
Sem leituras por 46 s
Retorno: 17/10/2026 02:19:07
```

#### Como configurar o bot do Telegram

1. No Telegram, converse com o [@BotFather](https://t.me/BotFather), mande `/newbot` e escolha um nome e um usuário
   terminado em `bot`. O BotFather responde com o token do bot.
2. Abra uma conversa com o bot novo e mande `/start` (um bot não consegue puxar conversa primeiro). Para alertar um
   grupo, adicione o bot ao grupo e mande uma mensagem lá.
3. Abra `https://api.telegram.org/bot<TOKEN>/getUpdates` no navegador e copie o `message.chat.id` (ids de grupo são
   negativos).
4. Defina `TELEGRAM_BOT_TOKEN` e `TELEGRAM_CHAT_ID`, no ambiente ou no `.env`, e reinicie a aplicação. O log mostra
   `Telegram alerts on` ao subir.

A Bot API do Telegram é gratuita. Os testes e o `spring-boot:test-run` nunca enviam alertas de verdade: eles zeram as
duas configurações em `src/test/resources/config/application.yaml`, e os testes de alerta usam um servidor falso do
Telegram.

### Decisões técnicas

- **Server-Sent Events para o fluxo ao vivo.** As atualizações só vão do servidor para o dashboard, e toda alteração
  passa pelo REST, então um WebSocket bidirecional acrescentaria um protocolo sem acrescentar nada. SSE é HTTP comum: o
  cookie de sessão o autoriza (o `EventSource` não envia cabeçalhos próprios), proxies o repassam com o buffer
  desligado e o navegador reconecta sozinho. O `ReadingStream` envia depois do commit da transação
  (`@TransactionalEventListener`), numa única virtual thread, então um cliente lento nunca atrasa a ingestão e todos
  recebem os eventos na ordem do commit. Os fluxos são fechados assim que o desligamento começa, para o graceful
  shutdown não ficar esperando por eles.
- **Alertas por excursão, com histerese.** O `ExcursionTracker` abre uma excursão na primeira leitura fora da faixa e
  a fecha depois de `ALERT_RECOVERY_READINGS` leituras seguidas dentro da faixa, então um incidente gera duas mensagens
  em vez de uma por leitura, e um valor oscilando no limite não fica abrindo e fechando. Ele roda dentro da transação
  da leitura, então a leitura e o estado da excursão são confirmados ou desfeitos juntos.
- **Um lock de linha por equipamento.** Duas leituras do mesmo equipamento chegando juntas poderiam abrir duas
  excursões. O tracker e o vigia de sensores pegam um lock `PESSIMISTIC_WRITE` na linha do equipamento, o que as
  serializa por equipamento sem um lock global.
- **Leituras atrasadas são gravadas mas não mudam o estado.** Um sensor que descarrega o buffer depois de uma queda de
  rede envia leituras antigas; elas entram no histórico, mas não abrem nem fecham uma excursão que leituras mais novas
  já decidiram.
- **Sensor sem leituras pelo horário de chegada.** O `SensorWatchdog` compara o `received_at` (relógio do servidor),
  não o `recorded_at` do sensor, então um sensor com relógio errado ainda é avaliado certo, e conta o silêncio no
  mínimo a partir da inicialização, então reiniciar o backend não dispara alerta para todos os sensores.
- **Um histórico que se explica.** Cada leitura guarda os limites com que foi avaliada, então editar a faixa de um
  equipamento nunca muda o status de leituras passadas. Um índice parcial nas leituras fora da faixa mantém essa
  consulta rápida conforme a tabela cresce.
- **Sessões no PostgreSQL, chave de API para dispositivos.** Login por formulário com cookie HTTP-only mantém as
  credenciais longe do JavaScript, o Spring Session JDBC preserva os logins entre reinícios e o `csrf.spa()` protege as
  escritas. Sensores não têm navegador nem cookie, então usam o `X-API-Key`, comparado em tempo constante, sem sessão
  e sem verificação de CSRF.
- **Telegram fora do caminho da requisição.** O `AlertNotifier` envia depois do commit numa única thread em segundo
  plano, em ordem, tentando de novo em erros de rede e respeitando o `retry_after` do HTTP 429, e desiste em erros 4xx
  que uma nova tentativa não resolve. O token é removido dos erros que vão para o log.
- **Schema do Flyway, testes em PostgreSQL de verdade.** O Hibernate só valida o schema (`ddl-auto: validate`) e os
  testes de integração usam o Testcontainers, então índices parciais, `TIMESTAMPTZ` e locks se comportam como em
  produção.

### Testes

```bash
./mvnw test
```

Os testes de integração sobem o PostgreSQL pelo Testcontainers, então o Docker precisa estar rodando. O GitHub
Actions (`.github/workflows/ci.yml`) roda os testes e gera a imagem Docker a cada push e pull request.

### Estrutura do projeto

```
src/main/java/dev/carloslpv/coldmonitor
├── common      tratamento de erros, paginação, relógio, CORS
├── equipment   cadastro de equipamentos e limites
├── reading     recebimento de leituras, histórico, detecção de fora da faixa e fluxo ao vivo
├── alert       acompanhamento de excursões e de sensores sem leituras, alertas no Telegram
└── security    login, sessões, CSRF e chave de API dos dispositivos
src/main/resources/db/migration   migrações Flyway
```

### Licença

[PolyForm Noncommercial 1.0.0](LICENSE.md): você pode ler, rodar e modificar este código para uso pessoal, estudo,
pesquisa e outros fins não comerciais. Uso comercial precisa de autorização por escrito do autor; entre em contato
pelo [GitHub](https://github.com/carloslpv).
