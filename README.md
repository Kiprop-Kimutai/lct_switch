# LCT switch

Java middleware exposing REST endpoints backed by MongoDB, an upstream REST
service, and an upstream SOAP service.

## Build and run

Install a JDK 17 or newer and set `JAVA_HOME` to its installation directory.
The Maven wrapper downloads the pinned Maven 3.9.11 distribution on first use.
Internet access is needed for the initial dependency download.

```powershell
.\mvnw.cmd clean verify
java -jar .\target\LCT_SWITCH-1.0-SNAPSHOT.jar
```

On Linux/macOS, use `./mvnw clean verify` and
`java -jar target/LCT_SWITCH-1.0-SNAPSHOT.jar`.

The HTTP server defaults to port 8080. Normal startup no longer runs the legacy
demo queries, greetings, or device-save calls. No demo profile is installed;
invoke the documented endpoints explicitly when testing integrations.
MongoDB's driver may establish background connections at startup.

## Configuration

Spring configuration supports environment variables, external property files,
and command-line overrides.

| Property | Default | Environment variable |
| --- | --- | --- |
| `spring.mongodb.uri` | `mongodb://127.0.0.1:27017/lct` | `MONGODB_URI` |
| `lct.rest.base-url` | `http://localhost:3000` | `LCT_REST_BASE_URL` |
| `lct.rest.connect-timeout` | `5s` | `LCT_REST_CONNECTTIMEOUT` |
| `lct.rest.read-timeout` | `10s` | `LCT_REST_READTIMEOUT` |
| `lct.soap.uri` | `http://localhost:9000/codenotfound/ws/lct` | `LCT_SOAP_URI` |
| `lct.soap.connect-timeout` | `5s` | `LCT_SOAP_CONNECTTIMEOUT` |
| `lct.soap.read-timeout` | `10s` | `LCT_SOAP_READTIMEOUT` |

URLs must be absolute HTTP(S) URLs; timeouts must be at least one millisecond.
Use HTTPS for remote upstream services where available. Never commit credentials
or production connection strings.

```powershell
$env:MONGODB_URI = 'mongodb://127.0.0.1:27017/lct'
$env:LCT_REST_BASE_URL = 'http://localhost:3000'
java -jar .\target\LCT_SWITCH-1.0-SNAPSHOT.jar --lct.soap.read-timeout=15s
```

## API contracts

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/lct/rest` | Legacy REST greeting |
| GET | `/lct/rest/` | Billing connectivity probe returning `ok` |
| GET | `/lct/ws`, `/lct/ws/` | Legacy SOAP-controller greeting |
| GET | `/lct/rest/getdevice?mac=...`, `/lct/ws/getdevice?mac=...` | Find the first matching MongoDB device |
| POST | `/lct/rest/savedevice` | Send JSON to upstream `/hmis/rest/save_devices`; return the submitted device after a successful upstream response |
| POST | `/lct/ws/savedevice` | Send the WSDL `deviceToSave` request; return the SOAP `savedDevice` response |
| POST | `/lct/rest/saveDevices`, `/lct/ws/saveDevices` | Echo the submitted device array; **neither endpoint saves devices** |
| GET | `/lct/rest/test` | Fetch the REST upstream's root; preserve the legacy line-concatenated text response |
| CRUD | `/people` | Existing Spring Data REST/HAL resource for the `devices` collection |

Device-operation and REST-test endpoints accept an optional trailing slash.
The REST greeting and billing probe intentionally use distinct paths: the legacy
controllers otherwise competed for `/lct/rest/`.
The name `/people` is retained for compatibility and does not represent a new
domain model. Internal MAC lookup is not exported as a Data REST search endpoint.

Device request fields are `macAddress`, `serial`, `retailer`, and `agency`.
Custom endpoints continue to expose the legacy device getter as the JSON property
`string`; the MongoDB identifier is not newly exposed. Outgoing REST JSON uses
Gson and UTF-8 and omits null fields, as before.

The WSDL under `src\main\resources\wsdl\lct.wsdl` remains the source of truth for
SOAP namespaces and operation payloads. Generated `hellios.wsdl` types are
recreated during the Maven build, not edited or committed. XML element spelling
such as query `sNO` versus response `sNo` is preserved.

### Intentional failure corrections

Missing devices now return **404** instead of a null dereference. REST/SOAP
upstream status, connection, fault, or malformed-response failures return **502**;
transport timeouts return **504**. Custom endpoint failures use a problem-detail
JSON body without stack traces. Database errors propagate instead of being
reported as successful operations or missing devices. All successful upstream
2xx statuses are accepted, including empty 204 responses.

## Code structure

- `device`: HTTP mappings, a constructor-injected device service, MongoDB model,
  and the single Spring Data repository.
- `restservice`: REST transport, shared outgoing JSON serializer, and typed
  connection properties.
- `webservice`: SOAP transport, JAXB marshalling, and typed connection properties.
- `api`: narrowly scoped HTTP error translation for the custom device controllers.
- `billing`: retained placeholders/probe; no billing features were introduced.

Dependencies use one Spring-managed context. Controllers do not create
repositories, and repositories do not create independent MongoDB connections.
Prefer adding behavior to the existing domain service and testing it with mocked
collaborators over adding transport logic to controllers.

## Tests and migration notes

```powershell
.\mvnw.cmd test "-Dtest=DeviceServiceTest,DeviceRestClientTest,WsClientTest"
.\mvnw.cmd clean verify
.\mvnw.cmd dependency:tree
```

The suite exercises service delegation, missing-device/database failures, API
and HAL contracts, SOAP XML and faults, JSON encoding, configuration binding, and
actual transport read timeouts. Tests mock MongoDB and upstream integrations or
use isolated loopback HTTP fixtures; no running MongoDB/REST/SOAP server is
required. Fixture servers are stopped after each test.

The project moves from Java 8/Spring Boot 1.5 to Java 17/Spring Boot 4.1.3.
Spring Boot's BOM manages the framework, driver, Gson, JAXB runtime, and test
versions; JAXB generation uses the Jakarta-compatible plugin 4.0.16.
Do not mix the old `javax.xml.bind` runtime with the new Jakarta-generated types.
Java SE XML APIs such as `javax.xml.transform` remain valid.

**Deployment gate:** verify the deployed MongoDB server against the resolved
MongoDB driver 5.x compatibility matrix and smoke-test the real upstream services.
The repository does not identify their deployed versions, and mocked tests
cannot establish their compatibility. Also verify any clients depending on
legacy Spring Data REST metadata beyond the covered `/people` contracts.

Build outputs (`target`), IDE metadata, and module files are ignored. Generated
classes must never substitute for a clean build.
