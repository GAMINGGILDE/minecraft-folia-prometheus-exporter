# FoliaPrometheusExporter

Repository: `minecraft-folia-prometheus-exporter`

FoliaPrometheusExporter ist ein für Paper und Folia entwickelter
Prometheus-Exporter. Der implementierte Metrics Core stellt den HTTP-Dienst, die
Exporter-Eigenüberwachung und die threadsichere Snapshot-/Collector-Grundlage für
die fachlichen Metriken der folgenden Phasen bereit.

## Verbindliche Eckdaten

- Pluginname: `FoliaPrometheusExporter`
- Repository: `minecraft-folia-prometheus-exporter`
- Package: `de.minecraftgilde.prometheus`
- Java: 25 oder neuer
- Plattformen: Paper und Folia
- Startversion: API-Linie 26.1.2
- Auslieferung: ein gemeinsames Plugin-JAR
- Lizenz: MIT
- Build: Gradle Kotlin DSL

## Datenschutz

Das Plugin implementiert niemals individuelle Spielermetriken oder
Spielerstatistiken. Spielernamen und UUIDs werden nicht als Labels exportiert.

## Stabilitätsprinzip

Version 1 verwendet keine internen Paper- oder Folia-Klassen, keine
NMS-Abhängigkeiten und keinen experimentellen Internal Provider. Metriken ohne
belastbare öffentliche API werden nicht vorgetäuscht.

## Dokumentation

- [Projektumfang](docs/01-project-scope.md)
- [Metrikkatalog](docs/02-metrics-catalog.md)
- [Architektur](docs/03-architecture.md)
- [Folia-Threading](docs/04-folia-threading.md)
- [Konfiguration](docs/05-configuration.md)
- [Tests und Abnahme](docs/07-testing.md)
- [Entscheidungen](docs/09-open-decisions.md)
- [Codex-Plan](docs/10-codex-implementation-plan.md)

## Build und Tests

Voraussetzung ist ein installiertes JDK 25. Der Build verwendet den mitgelieferten
Gradle Wrapper:

```bash
./gradlew clean build
./gradlew test
```

Das einzige auslieferbare, schattierte Plugin-JAR wird unter `build/libs/`
erzeugt. Es enthält den Prometheus Java Client 1.8.0; dessen Klassen sind nach
`de.minecraftgilde.prometheus.internal.prometheus` relocatet. Der Build prüft den
Descriptor, die Relocation, ausgeschlossene Server-APIs und Signaturdateien
automatisch.

## HTTP-Endpunkte

Standardmäßig bindet der Exporter ausschließlich an `127.0.0.1:9940`:

| Endpunkt | Bedeutung |
|---|---|
| `GET /metrics` | Prometheus-Exposition durch den offiziellen Java Client |
| `GET /health` | `200 ok`, solange der HTTP-Dienst fundamental gesund ist |
| `GET /ready` | `200 ready` erst nach vollständiger Core-Initialisierung, sonst `503` |

Unbekannte Pfade liefern `404`, andere HTTP-Methoden `405`. Host, Port, Pfade
und Workerzahl lassen sich in `plugins/FoliaPrometheusExporter/config.yml`
konfigurieren:

```yaml
http:
  bind-address: "127.0.0.1"
  port: 9940
  metrics-path: "/metrics"
  health-path: "/health"
  ready-path: "/ready"
  worker-threads: 2
```

Lokale Prüfung bei laufendem Server:

```bash
curl --fail http://127.0.0.1:9940/health
curl --fail http://127.0.0.1:9940/ready
curl --fail http://127.0.0.1:9940/metrics
```

## Metrics Core

Phase 2 implementiert die Eigenmetriken `minecraft_exporter_build_info`,
`minecraft_exporter_health`, `minecraft_exporter_ready`,
`minecraft_exporter_scrapes_total`, `minecraft_exporter_scrape_errors_total`,
`minecraft_exporter_http_requests_total` und
`minecraft_exporter_collector_state`.

Collector werden in Registrierungsreihenfolge gestartet, in umgekehrter
Reihenfolge gestoppt und bei Fehlern voneinander isoliert. Erfasste Daten werden
als vollständig konstruierte, immutable Snapshots atomar publiziert. HTTP-Threads
lesen ausschließlich Prometheus-internen Zustand, kontrollierten Exporterstatus
und später diese Snapshots; sie greifen nie auf Minecraft-Liveobjekte zu.

## Status

Phase 2 „Metrics Core“ ist implementiert. JVM- und Prozessmetriken sind der
nächste Umfang in Phase 3 und werden noch nicht registriert.
