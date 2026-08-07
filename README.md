<img width="145" height="150" alt="folia-prometheus-exporter-wordmark-close-transparent-theme" src="https://github.com/user-attachments/assets/977539c9-5c2d-4d3c-9f0c-2c3250b02d1e" />

# FoliaPrometheusExporter

Repository: `minecraft-folia-prometheus-exporter`

FoliaPrometheusExporter ist ein für Paper und Folia entwickelter
Prometheus-Exporter. Neben HTTP-Dienst und Exporter-Eigenüberwachung liefert er
standardisierte JVM-/Prozessmetriken sowie immutable Snapshots für aggregierte
Server-, Spieler-, Plugin-, Welt-, Chunk- und Weltgrößenmetriken. Phase 5 ergänzt
direkte, aggregierte Event-Counter für Verbindungen, Spieleraktionen und den
Chunk-Lifecycle. Phase 6 liefert auf Folia aggregierte TPS-Verteilungen für
tatsächlich über öffentliche Anker beobachtete Regionen. Phase 7 ergänzt
aggregierte, hybrid aus Events und verteilten Vollabgleichen gepflegte
Entitybestände.

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
./gradlew foliaTest
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
  entities: true
  folia: true
  jvm: true
  process: true
  filesystem: true
  plugin-info: false

collection:
  server-interval: "5s"
  world-interval: "10s"
  folia-interval: "5s"
  filesystem-interval: "30m"
  timeout: "10s"
  filesystem-timeout: "15m"

entities:
  reconciliation-interval: "5m"
  reconciliation-timeout: "60s"
  include-exact-types: false
  include-projectile-total: false

filesystem:
  include-world-sizes: true
  world-size-scan-concurrency: 1

folia:
  observation-sources:
    player-regions: true
    world-spawns: true
    force-loaded-chunks: false
    configured-locations: []
  observation-ttl: "60s"
  tps-windows: ["5s", "15s", "1m", "5m", "15m"]
  tps-statistics: ["min", "p05", "p50", "p95", "max", "average"]
  tps-thresholds: [19.0, 18.0, 15.0]
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
zusätzlich genau eine feste Reason-Reihe. Die Quelle ist seit 1.21.6 deprecated,
bleibt in Paper und Folia 26.1.2 jedoch die verlässlichste einzelne Quelle für
eine finale, strukturierte Entscheidung. `AsyncPlayerPreLoginEvent` liegt davor,
das experimentelle `PlayerConnectionValidateLoginEvent` kann in zwei Phasen
feuern und `PlayerServerFullCheckEvent` deckt nur einen Teilgrund ab. Sobald eine
stabile öffentliche API eine einzelne finale Auslieferung samt strukturierten
Gründen garantiert, ist diese schmale Listenergrenze der vorgesehene
Migrationspunkt. Kicks verwenden ausschließlich `PlayerKickEvent.Cause`; ein
nicht abgebrochener Kick kann anschließend auch ein `PlayerQuitEvent` auslösen
und erhöht dann sowohl Kick als auch Quit. Chat basiert auf dem modernen
`AsyncChatEvent` bei `MONITOR`; abgebrochene Chatereignisse, Commands und
Systemnachrichten zählen nicht.

Reason-Labels sind auf `banned`, `whitelist`, `server_full`, `invalid_session`,
`idle`, `connection_lost`, `moderation`, `plugin` und `unknown` begrenzt. Freie
Kick- und Logintexte, Chatinhalt, Spielername, UUID, IP, Clienthostname und
Chunkkoordinaten werden weder gelesen noch exportiert. Chunk-Counter verwenden
ausschließlich das gemeinsame validierte `world`-Label. Ein neu generierter
Chunk zählt sowohl als geladen als auch als generiert. Bei Kicks gilt
`TIMEOUT → connection_lost` und `IDLING → idle`; zukünftige strukturierte
`CONNECTION_LOST`- oder `NETWORK_ERROR`-Werte würden ebenfalls
`connection_lost` ergeben. Von den neun Kategorien ist allein
`invalid_session` mit den aktuellen strukturierten 26.1.2-Enums nicht
erzeugbar und für eine zukünftige eindeutige API-Ursache reserviert.

Fehler beim Counterupdate werden ohne Eventpayload rate-limitiert als
`IllegalStateException` gemeldet; die ursprüngliche `RuntimeException` bleibt
als Cause erhalten. Wirft der Fehlerbeobachter selbst, wird auch diese Exception
abgefangen, damit kein Fehler den Minecraft-Eventthread verlässt.

Alle Event-Counter beginnen bei jedem Plugin- beziehungsweise Serverstart bei
null. Es gibt keine Persistenz; auch ein Plugin-Reload kann einen Reset erzeugen.
Prometheus-Abfragen sollten deshalb `rate()` oder `increase()` verwenden.

## Folia-Regions-TPS

Phase 6 verwendet ausschließlich die öffentliche Folia-Methode
`Server#getRegionTPS(World,int,int)` mit den festen Fenstern `5s`, `15s`, `1m`,
`5m` und `15m`. Online-Spielerpositionen, Weltspawns und optional force-loaded
Chunks dienen nur als Beobachtungsanker. Auf dem jeweiligen Region-Thread werden
Anker derselben aktuellen Region über die öffentliche Ownership-Prüfung
dedupliziert. Deshalb bedeutet `minecraft_folia_observed_regions` ausdrücklich
nur beobachtete und nicht alle aktiven Regionen.

Implementiert sind `minecraft_folia_observed_regions`,
`minecraft_folia_region_tps`, `minecraft_folia_regions_below_tps`,
`minecraft_folia_regions_with_players`, `minecraft_folia_players_per_region`
und `minecraft_folia_region_snapshot_age_seconds`. Ohne gültige Beobachtung
fehlen dynamische Samples; es werden keine Nullwerte erfunden. Der Prometheus-
Client unterdrückt bei einem leeren Snapshot die leeren dynamischen Familien im
Textformat vollständig, einschließlich `HELP` und `TYPE`. Das ist bei null
beobachteten Regionen korrekt und keine Fehlfunktion.

Fehler eines einzelnen Spielerankers oder einer einzelnen Regionsbeobachtung
werden neutral und rate-limitiert gemeldet und überspringen nur diesen Anker.
Alle übrigen Tasks laufen weiter; ein erfolgreicher Teilsnapshot enthält genau
die gültigen Beobachtungen. Auch ein erfolgreicher Lauf ohne einzige gültige
Region publiziert einen leeren immutable Snapshot und entfernt dadurch alte
dynamische Folia-Reihen. Der Collector bleibt dabei `running`; `/health` und
`/ready` ändern ihren Zustand nicht. Nur systemische Laufabbrüche wie eine
fehlgeschlagene globale Weltlistenerfassung, Stop oder Timeout erhalten den
vorherigen Snapshot.

Regionale
Tickdauer, Tickverzögerung, Überlastung und eine vollständige Zahl aktiver
Regionen bleiben mangels öffentlicher API unimplementiert.

Der konkrete Provider wird in einem getrennten Source-Set gegen
`folia-api:26.1.2.build.8-stable` als `compileOnly` gebaut und erst nach
Capability-Erfolg geladen. Auf Paper bleibt ein aktivierter Collector
`unsupported`, protokolliert genau eine Warnung und registriert keine
Folia-Familie; Health und Readiness bleiben normal verfügbar.

Der gepinnte Folia-Smoke-Test verlangt deshalb keine existierende beobachtbare
Region. Sobald `minecraft_folia_observed_regions` einen Wert größer null
ausgibt, müssen alle implementierten TPS- und Aggregationsfamilien vollständige
und gültige Samples liefern; ein erfolgreicher leerer Snapshot ist ebenfalls
ein gültiger Testzustand.

## Entity-Metriken

`collectors.entities: true` aktiviert die Phase-7-Gauges. Standardmäßig werden
für jede erfolgreich erfasste geladene Welt genau zehn feste Gruppen ausgegeben:
`monster`, `animal`, `ambient`, `water`, `villager`, `item`, `projectile`,
`vehicle`, `display` und `other`. Spieler werden vollständig ausgeschlossen.

Implementiert sind `minecraft_entity_group_count`,
`minecraft_world_entities`, `minecraft_world_living_entities`,
`minecraft_world_villagers` und `minecraft_world_item_entities`. Der optionale
Schalter `entities.include-projectile-total` ergänzt
`minecraft_world_projectiles`. `entities.include-exact-types` ergänzt die
standardmäßig fehlende Familie `minecraft_entities{world,type}`; `type` ist ein
kontrollierter Namespaced Bukkit-Key wie `minecraft:zombie`.

Bereits vorhandene Entities werden initial erfasst. Danach halten ausschließlich
`EntityAddToWorldEvent` und `EntityRemoveFromWorldEvent` den Stand zwischen den
Vollabgleichen aktuell. Diese einzelne symmetrische Eventgrenze deckt auch
Chunk-Load/-Unload, Weltwechsel und Transformation ab, ohne Spawn-, Death-,
Teleport- oder Chunkevents doppelt zu zählen. Chunkzugriffe laufen auf dem
Region Scheduler, jede Entity-Auswertung auf ihrem Entity Scheduler. Scrapes
lesen nur den atomar publizierten immutable Snapshot.

Jede Welt besitzt im Vollabgleich einen expliziten Zuverlässigkeitsstatus.
Nur eine vollständig erfolgreiche Erfassung veröffentlicht einen neuen Wert;
ein lokaler Chunk- oder Entityfehler macht die Welt konservativ `PARTIAL`, eine
nicht lesbare Chunkliste `UNAVAILABLE`. In beiden Fällen bleibt ein vorhandener
gültiger Weltwert erhalten, ohne vorherigen gültigen Wert fehlt die Weltreihe
vollständig. Insbesondere entstehen beim ersten fehlgeschlagenen Lauf keine
künstlichen Gesamt- oder zehn Nullgruppen. Eine erfolgreich erfasste leere Welt
liefert dagegen weiterhin genau zehn Nullgruppen. Nur eine tatsächlich leere
globale Weltenliste entfernt alle alten Welten erfolgreich; existieren Welten,
aber keine ist belastbar erfassbar, gilt der Lauf als technisch fehlgeschlagen.

Für die gepinnten Builds ist `World#getLoadedChunks()` als globaler
Topologiezugriff geprüft: Folia 26.1.2 Build 8 basiert auf Paper-Commit
`b4682bfef616ac62e73cc96046dacdf4a6f53eeb`; dessen `CraftWorld` iteriert eine
concurrent Chunk-Key-Tabelle und konstruiert nur kurzlebige `CraftChunk`-Handles.
Die Folia-Patches ändern diese Methode nicht und fügen dort keinen TickThread-
oder Ownership-Check ein. Chunk- und Entitydaten werden weiterhin ausschließlich
auf Region- beziehungsweise Entity-Schedulern gelesen.

Das Standardintervall beträgt fünf Minuten und darf nicht unter eine Minute
gesetzt werden. Der eigene Timeout beträgt 60 Sekunden. Laufzeit, letzter Erfolg
und Korrekturen erscheinen als
`minecraft_entity_reconciliation_duration_seconds`,
`minecraft_entity_reconciliation_last_success_timestamp_seconds` und
`minecraft_entity_reconciliation_corrections_total`. Timeout und systemische
Fehler erhalten den letzten gültigen Snapshot; ein erfolgreicher Lauf mit
tatsächlich leerer globaler Weltenliste entfernt alte Welt- und Typreihen.

Lokale und systemische Fehlermeldungen bleiben nach außen neutral, bewahren aber
die ursprüngliche Exception einschließlich Typ, Cause-Kette und Suppressed-
Informationen als Cause. Isolierte Laufzeitfehler lassen den Collector
`running` und verändern `/health` oder `/ready` nicht; ein noch nie erfolgreich
erfasster Collector wird nicht durch Nullwerte als initialisiert dargestellt.

Gezählt werden aktuell geladene Nichtspieler-Entities. Persistierte Entities in
entladenen Chunks werden nicht eigens geladen. Kurzfristige UUIDs dienen nur der
Deduplizierung innerhalb eines Abgleichs; sie werden nie exportiert, geloggt oder
publiziert.

Der gepinnte Paper-/Folia-Smoke-Test lädt kontrolliert einen Chunk und erzeugt
eine kurzlebige, markierte Area-Effect-Cloud. Er verlangt ihren Anstieg in der
Gruppe `other` und nach ihrer natürlichen Entfernung einen sinkenden Bestand.
Das vollständige Serverlog wird danach gezielt auf TickThread-, Region-/Entity-
Scheduler- und Ownershipfehler geprüft.

## Status

Phase 7 „Entities“ ist implementiert. Phase 8 „Dokumentation und Dashboard“ ist
der nächste Umfang.
