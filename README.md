# FoliaPrometheusExporter

FoliaPrometheusExporter ist ein Prometheus-Exporter für Paper und Folia. Das
Plugin stellt aggregierte Minecraft-, JVM-, Prozess- und Exporter-Metriken über
einen eigenen HTTP-Endpunkt bereit. Minecraft-Daten werden schedulerkonform
erfasst und als immutable Snapshots veröffentlicht; ein Scrape greift niemals
live auf Welten, Chunks, Entities oder Spieler zu.

## Features

- ein gemeinsames Plugin-JAR für Paper und Folia
- Prometheus-Endpunkt sowie Liveness- und Readiness-Prüfung
- aggregierte Server-, Spieler-, Welt-, Chunk- und Entity-Metriken
- Event-Counter für Login, Join, Quit, Kick, Ping, Chat und Chunk-Lifecycle
- standardisierte JVM- und Prozessmetriken des Prometheus Java Clients
- aggregierte Regions-TPS-Metriken auf Folia über öffentliche APIs
- kontrollierte Label-Kardinalität ohne individuelle Spielermetriken
- isolierte Collector mit Timeouts und Erhalt des letzten gültigen Snapshots

## Unterstützte Plattformen

Offiziell unterstützt werden Paper und Folia ab der API-Linie `26.1.2`. Andere
Serverimplementierungen und Forks werden nicht aktiv blockiert, aber nicht
offiziell getestet oder unterstützt. Das Plugin benötigt Java 25 oder neuer.

## Installation und Schnellstart

1. Das Plugin-JAR aus einem GitHub Release herunterladen.
2. Das JAR in das Verzeichnis `plugins/` des Servers kopieren.
3. Den Server mit Java 25 starten.
4. Warten, bis `plugins/FoliaPrometheusExporter/config.yml` angelegt wurde.
5. Die Endpunkte lokal prüfen:

```bash
curl --fail http://127.0.0.1:9940/health
curl --fail http://127.0.0.1:9940/ready
curl --fail http://127.0.0.1:9940/metrics
```

Die vollständige Anleitung einschließlich Sicherheits- und Neustarthinweisen
steht in der [Installationsdokumentation](docs/11-installation.md).

## HTTP-Endpunkte

Standardmäßig bindet der Exporter ausschließlich an `127.0.0.1:9940`.

| Endpunkt | Erfolgsantwort | Bedeutung |
|---|---|---|
| `GET /metrics` | `200` | Prometheus-Exposition |
| `GET /health` | `200 ok` | HTTP-Dienst ist fundamental gesund |
| `GET /ready` | `200 ready` | Initialisierung ist vollständig |

Vor vollständiger Initialisierung liefert `/ready` den Status `503`. Unbekannte
Pfade liefern `404`, andere HTTP-Methoden `405`. Bindeadresse, Port und alle drei
Pfade sind konfigurierbar.

Der Exporter besitzt keine eigene Authentifizierung. `127.0.0.1` verhindert in
der Standardkonfiguration Zugriffe von anderen Hosts. Der Port sollte nicht
direkt ins öffentliche Internet gestellt werden.

## Konfiguration

Die Konfiguration liegt unter
`plugins/FoliaPrometheusExporter/config.yml`. Die wichtigsten Standards sind:

```yaml
http:
  bind-address: "127.0.0.1"
  port: 9940
  metrics-path: "/metrics"
  health-path: "/health"
  ready-path: "/ready"

collectors:
  server: true
  events: true
  worlds: true
  chunks: true
  entities: true
  folia: true
  jvm: true
  process: true
  filesystem: true
  plugin-info: false

entities:
  reconciliation-interval: "5m"
  reconciliation-timeout: "60s"
  include-exact-types: false
  include-projectile-total: false
```

Ungültige Werte verhindern einen teilweise konfigurierten Start. Änderungen
werden nach einem vollständigen Serverneustart wirksam. Sämtliche Schlüssel,
Grenzwerte und Legacy-Aliasse beschreibt die
[Konfigurationsreferenz](docs/05-configuration.md).

## Metriken

| Kategorie | Beispiele | Standard |
|---|---|---:|
| Exporter | `minecraft_exporter_health`, `minecraft_exporter_collector_state` | an |
| Server und Spieler | `minecraft_server_info`, `minecraft_players_online` | an |
| Welten und Chunks | `minecraft_world_time_ticks`, `minecraft_world_loaded_chunks` | an |
| Entities | `minecraft_entity_group_count`, `minecraft_world_entities` | an |
| Events | `minecraft_login_attempts_total`, `minecraft_chunks_loaded_total` | an |
| JVM und Prozess | `jvm_memory_used_bytes`, `process_start_time_seconds` | an |
| Folia | `minecraft_folia_region_tps`, `minecraft_folia_observed_regions` | nur Folia |
| Plugininformationen | `minecraft_plugin_info` | aus |
| genaue Entitytypen | `minecraft_entities{world,type}` | aus |

Der [Metrikkatalog](docs/02-metrics-catalog.md) enthält Namen, Typen, Labels,
Standardzustände und Plattformverfügbarkeit aller Familien. Event-Counter sind
nicht persistent und können bei Serverstart oder Plugin-Reload zurückgesetzt
werden; für Zeiträume sind `rate()` und `increase()` vorgesehen.

## Prometheus, Grafana Alloy und Alerting

Direkt nutzbare Beispiele befinden sich unter `examples/`:

- [Prometheus-Scrape-Konfiguration](examples/prometheus/prometheus.yml)
- [Grafana-Alloy-Konfiguration](examples/grafana-alloy/config.alloy)
- [Prometheus-Alertregeln](examples/prometheus/alerts.yml)

Einrichtung, Remote-Write-Platzhalter und die Paper-/Folia-Semantik der Alerts
erklärt die [Monitoring-Dokumentation](docs/12-monitoring.md).

## Datenschutz und Kardinalität

Der Exporter veröffentlicht keine Spielernamen, UUIDs, IP-Adressen,
Chat-Inhalte, freien Kick-/Logintexte, Chunk- oder Regionskoordinaten und keine
Entity-UUIDs. Spielerwerte sind ausschließlich aggregiert.

`minecraft_plugin_info` ist wegen dynamischer Pluginname-/Versionslabels
standardmäßig deaktiviert. Auch genaue Entitytypen sind standardmäßig aus, weil
die Zahl der Reihen mit den tatsächlich vorhandenen Typen je Welt wächst. Es
gibt keine individuellen Spielermetriken und keine Gameplay-Counter.

## Folia-Hinweise

Folia-Metriken beruhen ausschließlich auf Regionen, die über öffentliche
Spieler-, Weltspawn- oder optional Force-Load-Anker beobachtet werden. Der Wert
`minecraft_folia_observed_regions` ist daher keine vollständige Zahl aller
aktiven Regionen. Regionale Tickdauer, Tickverzögerung und eine vollständige
Regionszahl werden mangels belastbarer öffentlicher API nicht exportiert.

Auf Paper bleibt der aktivierte Folia-Collector im Zustand `unsupported`; der
übrige Exporter einschließlich Health und Readiness funktioniert normal, und es
werden keine künstlichen Folia-Nullreihen erzeugt.

## Build aus dem Quellcode

Voraussetzung ist ein JDK 25. Der Gradle Wrapper erzeugt genau ein schattiertes
Plugin-JAR unter `build/libs/`:

```bash
./gradlew clean build
./gradlew test
./gradlew foliaTest
```

Der Build prüft Descriptor, Dependency-Relocation, die Isolation der
Server-APIs und den Inhalt des auslieferbaren JARs automatisch.

## Dokumentation

- [Projektumfang](docs/01-project-scope.md)
- [Metrikkatalog](docs/02-metrics-catalog.md)
- [Architektur](docs/03-architecture.md)
- [Paper-/Folia-Threading](docs/04-folia-threading.md)
- [Konfiguration](docs/05-configuration.md)
- [Prometheus-Format und Kardinalität](docs/06-prometheus-format.md)
- [Tests und Abnahme](docs/07-testing.md)
- [Release-Prozess](docs/08-release-process.md)
- [Entscheidungen](docs/09-open-decisions.md)
- [Historischer Umsetzungsplan](docs/10-codex-implementation-plan.md)

## Lizenz

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE).
