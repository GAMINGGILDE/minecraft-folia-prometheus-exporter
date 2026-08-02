# FoliaPrometheusExporter

Repository: `minecraft-folia-prometheus-exporter`

FoliaPrometheusExporter ist ein für Paper und Folia entwickelter
Prometheus-Exporter. Neben HTTP-Dienst und Exporter-Eigenüberwachung liefert er
standardisierte JVM-/Prozessmetriken sowie immutable Snapshots für aggregierte
Server-, Spieler-, Plugin-, Welt-, Chunk- und Weltgrößenmetriken. Phase 5 ergänzt
direkte, aggregierte Event-Counter für Verbindungen, Spieleraktionen und den
Chunk-Lifecycle.

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

collectors:
  server: true
  events: true
  worlds: true
  chunks: true
  jvm: true
  process: true
  filesystem: true
  plugin-info: false

collection:
  server-interval: "5s"
  world-interval: "10s"
  filesystem-interval: "30m"
  timeout: "10s"
  filesystem-timeout: "15m"

filesystem:
  include-world-sizes: true
  world-size-scan-concurrency: 1
```

Lokale Prüfung bei laufendem Server:

```bash
curl --fail http://127.0.0.1:9940/health
curl --fail http://127.0.0.1:9940/ready
curl --fail http://127.0.0.1:9940/metrics
```

## Metrics Core und Laufzeitmetriken

Phase 2 implementiert die Eigenmetriken `minecraft_exporter_build_info`,
`minecraft_exporter_health`, `minecraft_exporter_ready`,
`minecraft_exporter_scrapes_total`, `minecraft_exporter_scrape_errors_total`,
`minecraft_exporter_http_requests_total` und
`minecraft_exporter_collector_state`.

`minecraft_exporter_build_info` enthält den beim Build über Git ermittelten
vollständigen Commit-Hash. Gradle schreibt ihn in `build-info.properties`; fehlt
Git oder wird außerhalb eines Git-Checkouts gebaut, bleibt der Build mit dem
kontrollierten Wert `git_commit="unknown"` funktionsfähig. Optional kann der Wert
reproduzierbar mit `-PgitCommit=<Hash oder unknown>` vorgegeben werden.

Collector werden in Registrierungsreihenfolge gestartet, in umgekehrter
Reihenfolge gestoppt und bei Fehlern voneinander isoliert. Erfasste Daten werden
als vollständig konstruierte, immutable Snapshots atomar publiziert;
ereignisbasierte Counter werden bereits während des Events akkumuliert.
HTTP-Threads lesen ausschließlich Prometheus-internen Zustand, kontrollierten
Exporterstatus, Snapshots und Counter; sie greifen nie auf
Minecraft-Liveobjekte zu.

Jede `MetricsCore`-Instanz besitzt genau eine private `PrometheusRegistry` und
genau einen daran gebundenen Eigenmetrik-Satz. Es gibt keinen globalen Registry-
oder `ExporterMetrics`-Cache.

Phase 3 registriert die offiziellen Instrumentierungen `JvmMemoryMetrics`,
`JvmGarbageCollectorMetrics`, `JvmThreadsMetrics`, `JvmClassLoadingMetrics`,
`JvmBufferPoolMetrics` und `ProcessMetrics` aus dem Prometheus Java Client 1.8.0
direkt in derselben privaten Registry. Die Gruppen sind mit `collectors.jvm` und
`collectors.process` unabhängig schaltbar und standardmäßig aktiv. Sie lesen nur
JDK-/Betriebssystemdaten, keine Minecraft-Liveobjekte, und benötigen weder Paper-
noch Folia-Scheduler.

Zu den stabilen Familien gehören `jvm_memory_used_bytes`,
`jvm_gc_collection_seconds_count`/`_sum`, `jvm_threads_current`,
`jvm_classes_currently_loaded`, `jvm_buffer_pool_used_bytes`,
`process_cpu_seconds_total` und `process_start_time_seconds`. Dateideskriptoren
sind von den Fähigkeiten des Betriebssystem-MXBeans abhängig;
`process_resident_memory_bytes` und `process_virtual_memory_bytes` werden von
Client 1.8.0 nur bei lesbarem Linux-`/proc/self/status` registriert. Das Modul
bietet in dieser Version keine offiziellen `system_*`-Metriken, keine
CPU-Usage-Ratios und keine Prozess-Uptime-Metrik. Diese Werte werden nicht
nachgebaut oder umbenannt.

## Server- und Weltmetriken

Phase 4 erfasst Server- und Pluginzustand im konfigurierten Serverintervall,
Welt- und Chunkzustand im Weltintervall und Weltgrößen im Dateisystemintervall.
Der Startzeitpunkt der Servermetrik ist der Beginn der Pluginaktivierung; die
Uptime wird relativ dazu berechnet. Spielerwerte sind ausschließlich aggregiert.
Die Spielmoduslabels sind fest auf `survival`, `creative`, `adventure` und
`spectator` begrenzt. `minecraft_plugin_info` ist wegen seiner dynamischen
Labels standardmäßig deaktiviert.

Welt-, Wetter-, Schwierigkeits- und Umgebungslabels werden aus jedem vollständig
erfassten Snapshot neu aufgebaut. Entladene Welten verschwinden deshalb aus der
Ausgabe. `minecraft_world_loaded_chunks` verwendet den öffentlichen aggregierten
Chunkzähler der Welt und materialisiert keine Chunkobjekte.

`minecraft_world_size_bytes` wird asynchron als Summe der regulären Dateien
unterhalb des Weltverzeichnisses berechnet. Symbolischen Links wird nicht
gefolgt. Für den vollständigen Scanlauf gilt wegen großer Weltverzeichnisse der
eigene Standard-Timeout `collection.filesystem-timeout: "15m"`; der allgemeine
`collection.timeout` bleibt den schnellen Server-, Welt- und Chunk-Snapshots
vorbehalten. Weltverzeichnisse werden standardmäßig sequenziell gescannt.
`filesystem.world-size-scan-concurrency` begrenzt die gleichzeitig aktiven
Dateisystemscans auf einen Wert zwischen `1` und `8`. Höhere Werte können die
Erfassung beschleunigen, erzeugen aber mehr konkurrierende I/O-Last.

Bei Timeout oder Einzelfehlern bleibt der letzte gültige Wert erhalten. Ein
bereits laufender Java-Dateisystemaufruf lässt sich nicht zuverlässig physisch
unterbrechen; wartende Scans werden dennoch verworfen und verspätete Ergebnisse
nicht publiziert. Weltpfade werden niemals als Prometheus-Labels exportiert. Die
Metrik benötigt sowohl
`collectors.filesystem: true` als auch `filesystem.include-world-sizes: true`.
Die optional katalogisierten Metriken für Full Time, Autosave, allgemeine
Dateisystemkapazität, Logs und Pluginverzeichnisse gehören nicht zu Phase 4.

## Event-Counter

`collectors.events: true` aktiviert gemeinsam die zehn Phase-5-Familien für
Loginversuche und -ablehnungen, Join, Quit, Kick, Serverlisten-Ping, Chat sowie
Chunk-Load, -Unload und -Generierung. Der Collector registriert öffentliche
Paper-/Bukkit-Events genau einmal und erhöht die threadsicheren Prometheus-Counter
direkt auf dem jeweiligen Eventthread. Er erzeugt keine periodischen Tasks und
speichert keine Player-, Connection- oder Chunkobjekte.

Loginversuche werden ausschließlich am finalen `PlayerLoginEvent` gezählt, um
Doppelzählungen zwischen Loginphasen zu vermeiden. Ein abgelehnter Login erhöht
zusätzlich genau eine feste Reason-Reihe. Kicks verwenden ausschließlich
`PlayerKickEvent.Cause`; ein nicht abgebrochener Kick kann anschließend auch ein
`PlayerQuitEvent` auslösen und erhöht dann sowohl Kick als auch Quit. Chat basiert
auf dem modernen `AsyncChatEvent` bei `MONITOR`; abgebrochene Chatereignisse,
Commands und Systemnachrichten zählen nicht.

Reason-Labels sind auf `banned`, `whitelist`, `server_full`, `invalid_session`,
`idle`, `connection_lost`, `moderation`, `plugin` und `unknown` begrenzt. Freie
Kick- und Logintexte, Chatinhalt, Spielername, UUID, IP, Clienthostname und
Chunkkoordinaten werden weder gelesen noch exportiert. Chunk-Counter verwenden
ausschließlich das gemeinsame validierte `world`-Label. Ein neu generierter
Chunk zählt sowohl als geladen als auch als generiert.

Alle Event-Counter beginnen bei jedem Plugin- beziehungsweise Serverstart bei
null. Es gibt keine Persistenz; auch ein Plugin-Reload kann einen Reset erzeugen.
Prometheus-Abfragen sollten deshalb `rate()` oder `increase()` verwenden.

## Status

Phase 5 „Events“ ist implementiert. Phase 6 „Folia Regions-TPS“ ist der nächste
Umfang.
