# cold-monitor-backend

[English](#english) · [Português (Brasil)](#português-brasil)

---

## English

Backend for **cold-monitor**, a cold-chain temperature monitoring system. Sensors in fridges, freezers and cold rooms
send temperature readings to this API; each reading is stored and checked against the minimum and maximum thresholds
registered for its equipment. Readings outside that range are flagged and published as events, which is where alerts
and the dashboard will plug in.

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

### Out-of-range detection

When a reading is out of range, the service publishes an `OutOfRangeReadingEvent` (Spring application event). For now
a listener logs a warning after the transaction commits; alert channels will subscribe to the same event.

### Tests

```bash
./mvnw test
```

Integration tests start PostgreSQL through Testcontainers, so Docker must be running.

### Project layout

```
src/main/java/dev/carloslpv/coldmonitor
├── common      error handling, paging, clock
├── equipment   equipment registration and thresholds
└── reading     reading ingestion, history and out-of-range detection
src/main/resources/db/migration   Flyway migrations
```

---

## Português (Brasil)

Backend do **cold-monitor**, um sistema de monitoramento de temperatura da cadeia fria. Sensores em geladeiras, freezers
e câmaras frias enviam leituras de temperatura para esta API; cada leitura é gravada e comparada com os limites mínimo
e máximo cadastrados para o equipamento. Leituras fora da faixa são sinalizadas e publicadas como eventos, que é onde os
alertas e o dashboard vão se conectar.

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

### Detecção de fora da faixa

Quando uma leitura está fora da faixa, o serviço publica um `OutOfRangeReadingEvent` (evento de aplicação do Spring).
Por enquanto um listener registra um aviso no log depois do commit da transação; os canais de alerta vão assinar o mesmo
evento.

### Testes

```bash
./mvnw test
```

Os testes de integração sobem o PostgreSQL pelo Testcontainers, então o Docker precisa estar rodando.

### Estrutura do projeto

```
src/main/java/dev/carloslpv/coldmonitor
├── common      tratamento de erros, paginação, relógio
├── equipment   cadastro de equipamentos e limites
└── reading     recebimento de leituras, histórico e detecção de fora da faixa
src/main/resources/db/migration   migrações Flyway
```
