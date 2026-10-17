# cold-monitor-backend

[English](#english) · [Português (Brasil)](#português-brasil)

---

## English

Backend for **cold-monitor**, a cold-chain temperature monitoring system. Sensors in fridges, freezers and cold rooms
send temperature readings to this API; each reading is stored and checked against the minimum and maximum thresholds
registered for its equipment. Readings outside that range are flagged, every reading is pushed live to the
[dashboard](https://github.com/carloslpv/cold-monitor-dashboard) over Server-Sent Events, and a Telegram alert goes out
when an equipment leaves its range and again when it comes back.

### Stack

- Java 25
- Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL 18 with Flyway migrations
- Maven (wrapper included)
- Testcontainers for integration tests

### Running locally

Requirements: JDK 25 and Docker.

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

### Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5433` | PostgreSQL port (also used by `compose.yaml`) |
| `DB_NAME` | `coldmonitor` | Database name |
| `DB_USER` | `coldmonitor` | Database user |
| `DB_PASSWORD` | `coldmonitor` | Database password |
| `SERVER_PORT` | `8080` | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins allowed to call `/api/**` (the dashboard dev server by default) |
| `TELEGRAM_BOT_TOKEN` | (empty) | Bot token from @BotFather. Alerts are only logged while this or the chat id is empty |
| `TELEGRAM_CHAT_ID` | (empty) | Chat that receives the alerts |
| `ALERT_RECOVERY_READINGS` | `2` | Consecutive in-range readings needed before an excursion counts as over |
| `ALERT_TIME_ZONE` | `America/Sao_Paulo` | Time zone of the times written in alert messages |

Variables can also go in a `.env` file at the project root (one `KEY=value` per line), which the app reads on startup.
The file is git-ignored; keep secrets such as the bot token there or in the environment, never in `application.yaml`.

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

```bash
curl -N http://localhost:8080/api/v1/readings/stream
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

### Tests

```bash
./mvnw test
```

Integration tests start PostgreSQL through Testcontainers, so Docker must be running.

### Project layout

```
src/main/java/dev/carloslpv/coldmonitor
├── common      error handling, paging, clock, CORS
├── equipment   equipment registration and thresholds
├── reading     reading ingestion, history, out-of-range detection and live stream
└── alert       excursion tracking and Telegram alerts
src/main/resources/db/migration   Flyway migrations
```

---

## Português (Brasil)

Backend do **cold-monitor**, um sistema de monitoramento de temperatura da cadeia fria. Sensores em geladeiras, freezers
e câmaras frias enviam leituras de temperatura para esta API; cada leitura é gravada e comparada com os limites mínimo
e máximo cadastrados para o equipamento. Leituras fora da faixa são sinalizadas, toda leitura é enviada ao vivo para o
[dashboard](https://github.com/carloslpv/cold-monitor-dashboard) por Server-Sent Events, e um alerta vai para o
Telegram quando um equipamento sai da faixa e de novo quando ele volta.

### Tecnologias

- Java 25
- Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL 18 com migrações Flyway
- Maven (wrapper incluído)
- Testcontainers nos testes de integração

### Como rodar localmente

Requisitos: JDK 25 e Docker.

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

### Configuração

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5433` | Porta do PostgreSQL (também usada pelo `compose.yaml`) |
| `DB_NAME` | `coldmonitor` | Nome do banco |
| `DB_USER` | `coldmonitor` | Usuário do banco |
| `DB_PASSWORD` | `coldmonitor` | Senha do banco |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origens de navegador, separadas por vírgula, que podem chamar `/api/**` (por padrão, o servidor de desenvolvimento do dashboard) |
| `TELEGRAM_BOT_TOKEN` | (vazio) | Token do bot criado no @BotFather. Enquanto ele ou o chat id estiver vazio, os alertas só vão para o log |
| `TELEGRAM_CHAT_ID` | (vazio) | Chat que recebe os alertas |
| `ALERT_RECOVERY_READINGS` | `2` | Leituras seguidas dentro da faixa para considerar a excursão encerrada |
| `ALERT_TIME_ZONE` | `America/Sao_Paulo` | Fuso horário dos horários escritos nos alertas |

As variáveis também podem ficar num arquivo `.env` na raiz do projeto (uma `CHAVE=valor` por linha), que a aplicação
lê ao subir. O arquivo está no `.gitignore`; guarde segredos como o token do bot nele ou no ambiente, nunca no
`application.yaml`.

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

```bash
curl -N http://localhost:8080/api/v1/readings/stream
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

### Testes

```bash
./mvnw test
```

Os testes de integração sobem o PostgreSQL pelo Testcontainers, então o Docker precisa estar rodando.

### Estrutura do projeto

```
src/main/java/dev/carloslpv/coldmonitor
├── common      tratamento de erros, paginação, relógio, CORS
├── equipment   cadastro de equipamentos e limites
├── reading     recebimento de leituras, histórico, detecção de fora da faixa e fluxo ao vivo
└── alert       acompanhamento de excursões e alertas no Telegram
src/main/resources/db/migration   migrações Flyway
```
