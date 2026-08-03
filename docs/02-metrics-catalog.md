# 2. Verbindlicher Metrikkatalog

## 2.1 Namensregeln

- Minecraft: `minecraft_`
- Folia: `minecraft_folia_`
- Exporter: `minecraft_exporter_`
- JVM: standardisierte `jvm_`-Namen
- Prozess: `process_`
- System: `system_`
- Counter enden auf `_total`
- Zeitdauern werden in Sekunden exportiert
- Größen werden in Bytes exportiert
- Verhältnisse werden als Wert zwischen 0 und 1 exportiert

## 2.2 Server

| Metrik | Typ | Labels | Standard | Status | Bedeutung |
|---|---|---|---:|---|---|
| `minecraft_server_info` | Info | `implementation`, `minecraft_version`, `java_version` | an | Stabil | Server- und Laufzeitinformation |
| `minecraft_server_uptime_seconds` | Gauge | – | an | Stabil | Laufzeit des Servers |
| `minecraft_server_start_time_seconds` | Gauge | – | an | Stabil | Startzeit als Unix-Zeit |
| `minecraft_server_online_mode` | Gauge | – | an | Stabil | 1 bei Online-Mode |
| `minecraft_server_hardcore` | Gauge | – | an | Stabil | 1 bei Hardcore |
| `minecraft_server_view_distance_chunks` | Gauge | – | an | Stabil | View Distance |
| `minecraft_server_simulation_distance_chunks` | Gauge | – | an | Stabil | Simulation Distance |
| `minecraft_plugins_total` | Gauge | – | an | Stabil | Installierte Plugins |
| `minecraft_plugins_enabled` | Gauge | – | an | Stabil | Aktivierte Plugins |
| `minecraft_plugins_disabled` | Gauge | – | an | Stabil | Deaktivierte Plugins |
| `minecraft_plugin_info` | Info | `name`, `version`, `enabled` | aus | Optional | Einzelne Plugininformationen |

Phase 4 implementiert diese Tabelle vollständig. `implementation` stammt aus
`Server#getName()`, `minecraft_version` aus `Server#getMinecraftVersion()` und
`java_version` aus der JVM-Systemeigenschaft. Serverstart und -Uptime beziehen
sich auf den zu Beginn von `onEnable()` fixierten Aktivierungszeitpunkt des
Plugins, da keine belastbare öffentliche API für einen früheren Prozess- oder
Serverstart verwendet wird. Leere Info-Labelwerte werden als `unknown`
normalisiert. Im Prometheus-Textformat 0.0.4 erscheint die vom Client modellierte
Info-Familie technisch als `TYPE ... gauge`; OpenMetrics gibt sie als `info` aus.
Plugininstanzen werden nach Objektidentität höchstens einmal gezählt. Für die
optionale Info-Familie gelten ausschließlich `enabled="true"` und
`enabled="false"`; leere oder fehlende Namen und Versionen werden als `unknown`
abgebildet. Identische Labeltupel mehrerer ungewöhnlicher Plugininstanzen werden
nicht doppelt ausgegeben.

## 2.3 Aggregierte Spielerzahlen

| Metrik | Typ | Labels | Standard | Status | Bedeutung |
|---|---|---|---:|---|---|
| `minecraft_players_online` | Gauge | – | an | Stabil | Spieler online |
| `minecraft_players_max` | Gauge | – | an | Stabil | Maximale Spielerzahl |
| `minecraft_players_known_total` | Gauge | – | an | Snapshot | Bekannte Spieler insgesamt |
| `minecraft_players_whitelisted` | Gauge | – | an | Stabil | Whitelist-Einträge |
| `minecraft_players_banned` | Gauge | – | an | Stabil | Gebannte Profile |
| `minecraft_players_ops` | Gauge | – | an | Stabil | Operatoren |
| `minecraft_players_by_gamemode` | Gauge | `gamemode` | an | Snapshot | Aggregierte Spielmodi |
| `minecraft_world_players` | Gauge | `world` | an | Snapshot | Spieler je Welt |

Keine dieser Metriken darf Spielername oder UUID enthalten.

Phase 4 liest ausschließlich aggregierte Mengen. Nur die Spielmodi erfordern
Entity-Ownership: Je Online-Spieler wird allein `getGameMode()` über dessen
Entity Scheduler gelesen und direkt in die festen Labels `survival`, `creative`,
`adventure` und `spectator` aggregiert. Spielerobjekte werden nicht in einem
Snapshot gespeichert; Namen und UUIDs werden weder abgefragt noch protokolliert.

## 2.4 Verbindung und Events

| Metrik | Typ | Labels | Standard | Status |
|---|---|---|---:|---|
| `minecraft_login_attempts_total` | Counter | – | an | Eventbasiert |
| `minecraft_player_joins_total` | Counter | – | an | Eventbasiert |
| `minecraft_player_quits_total` | Counter | – | an | Eventbasiert |
| `minecraft_player_kicks_total` | Counter | `reason` | an | Eventbasiert |
| `minecraft_login_denied_total` | Counter | `reason` | an | Eventbasiert |
| `minecraft_server_list_pings_total` | Counter | – | an | Eventbasiert |
| `minecraft_chat_messages_total` | Counter | – | an | Eventbasiert |
| `minecraft_commands_total` | Counter | `command`, `source` | aus | Optional |

Erlaubte normalisierte Gründe:

- `banned`
- `whitelist`
- `server_full`
- `invalid_session`
- `idle`
- `connection_lost`
- `moderation`
- `plugin`
- `unknown`

Keine freien Nachrichten als Labels.

Phase 5 implementiert alle Familien dieser Tabelle außer
`minecraft_commands_total`. Genau ein `PlayerLoginEvent` zählt einen final
verarbeiteten Loginversuch; ein nicht erlaubtes strukturiertes Ergebnis zählt
zusätzlich genau eine Denial-Reihe. `KICK_BANNED`, `KICK_WHITELIST` und
`KICK_FULL` werden auf `banned`, `whitelist` und `server_full` abgebildet;
`KICK_OTHER` bleibt konservativ `unknown`. Die Ziel-API stellt an dieser finalen
Eventquelle keinen strukturierten Wert für Authentifizierungsfehler bereit,
weshalb `invalid_session` derzeit nicht erzeugt wird. Freie Logintexte dienen
auch nicht als Fallback. `PlayerLoginEvent` ist seit 1.21.6 deprecated, bleibt
für die API-Linie 26.1.2 aber die einzige verwendete Loginquelle: Das
experimentelle `PlayerConnectionValidateLoginEvent` kann in zwei Loginphasen
feuern und besitzt keinen strukturierten Ablehnungsgrund, während
`AsyncPlayerPreLoginEvent` vor der finalen Serverentscheidung und
`PlayerServerFullCheckEvent` nur für den Full-Check ausgeliefert werden. Eine
Kombination dieser Quellen würde ohne personenbezogene Korrelation
Doppelzählungen oder unvollständige Denial-Semantik erzeugen.

`PlayerKickEvent.Cause` wird ausschließlich über feste Enumwerte normalisiert:
Bans und Whitelist behalten ihre gleichnamige Kategorie, `TIMEOUT` wird
`connection_lost`, `IDLING` wird `idle`, `PLUGIN` wird `plugin` und
administrative beziehungsweise Protokoll-/Chat-Regelverstöße werden
`moderation`. Nicht eindeutig zuordenbare Werte werden `unknown`. Die aktuelle
API erzeugt `connection_lost` bereits über den strukturierten Wert `TIMEOUT`;
die vorsorglich unterstützten Namen `CONNECTION_LOST` und `NETWORK_ERROR`
würden dieselbe Kategorie verwenden, falls eine spätere öffentliche API sie
einführt. Kicknachrichten werden nicht gelesen.

Damit können die aktuellen strukturierten Login- und Kick-Enums die Kategorien
`banned`, `whitelist`, `server_full`, `idle`, `connection_lost`, `moderation`,
`plugin` und `unknown` tatsächlich erzeugen. `invalid_session` bleibt allein für
einen zukünftigen eindeutigen strukturierten Loginwert reserviert. Die ebenfalls
vorsorglich erkannten Namen `KICK_PLUGIN`, `CONNECTION_LOST` und `NETWORK_ERROR`
sind keine aktuellen Enumwerte der jeweils verwendeten 26.1.2-Quelle.

Join, Quit und Ping zählen das jeweilige Event. Ein erfolgreicher Kick kann
anschließend regulär ein Quit-Event auslösen; in diesem Fall steigen bewusst
sowohl Kick als auch Quit. Chat zählt nur nicht abgebrochene `AsyncChatEvent`s
bei `MONITOR`. Commands und Systemnachrichten lösen diese Quelle nicht aus.

Die Counter beginnen je Plugin-/Serverstart bei null, werden nicht persistiert
und können auch nach einem Plugin-Reload zurückgesetzt sein. Für Zeiträume sind
PromQL `rate()` und `increase()` vorgesehen.

Schlägt ein Eventupdate fehl, meldet der Collector eine
`IllegalStateException` mit der ursprünglichen `RuntimeException` als Cause an
den vorhandenen rate-limitierten Reporter. Auch eine Exception dieses Reporters
wird innerhalb des Eventbereichs abgefangen; sie verlässt niemals den
Minecraft-Eventthread.

## 2.5 Welten

| Metrik | Typ | Labels | Standard | Status |
|---|---|---|---:|---|
| `minecraft_world_loaded_chunks` | Gauge | `world` | an | Snapshot |
| `minecraft_world_players` | Gauge | `world` | an | Snapshot |
| `minecraft_world_entities` | Gauge | `world` | an | Snapshot |
| `minecraft_world_living_entities` | Gauge | `world` | an | Snapshot |
| `minecraft_world_villagers` | Gauge | `world` | an | Snapshot |
| `minecraft_world_item_entities` | Gauge | `world` | an | Snapshot |
| `minecraft_world_projectiles` | Gauge | `world` | aus | Optional |
| `minecraft_world_size_bytes` | Gauge | `world` | an | Async |
| `minecraft_world_time_ticks` | Gauge | `world` | an | Snapshot |
| `minecraft_world_full_time_ticks` | Gauge | `world` | aus | Optional |
| `minecraft_world_border_size_blocks` | Gauge | `world` | an | Stabil |
| `minecraft_world_weather` | Gauge | `world`, `weather` | an | Snapshot |
| `minecraft_world_difficulty` | Gauge | `world`, `difficulty` | an | Stabil |
| `minecraft_world_environment` | Gauge | `world`, `environment` | an | Stabil |
| `minecraft_world_pvp_enabled` | Gauge | `world` | an | Stabil |
| `minecraft_world_autosave_enabled` | Gauge | `world` | aus | Optional |

Phase 4 implementiert aus dieser Tabelle `minecraft_world_players`,
`minecraft_world_size_bytes`, `minecraft_world_time_ticks`,
`minecraft_world_border_size_blocks`, `minecraft_world_weather`,
`minecraft_world_difficulty`, `minecraft_world_environment` und
`minecraft_world_pvp_enabled`. Full Time und Autosave bleiben als optionale,
deaktivierte Katalogeinträge einer späteren Phase vorbehalten. Entityfamilien
werden in Phase 4 nicht registriert.

Wetter ist One-Hot über `clear`, `rain`, `thunder`; Schwierigkeit über
`peaceful`, `easy`, `normal`, `hard`; Umgebung über `normal`, `nether`,
`the_end`, `custom`. Die Weltenliste wird pro Snapshot neu aufgebaut, sodass
entladene Welten keine veralteten Labelreihen hinterlassen.

## 2.6 Entities

| Metrik | Typ | Labels | Standard | Status |
|---|---|---|---:|---|
| `minecraft_entity_group_count` | Gauge | `world`, `group` | an | Snapshot/Hybrid |
| `minecraft_entities` | Gauge | `world`, `type` | aus | Optional |
| `minecraft_entities_spawned_total` | Counter | `world`, `type`, `reason` | aus | Eventbasiert |
| `minecraft_entities_removed_total` | Counter | `world`, `type`, `reason` | aus | Eventbasiert |
| `minecraft_mobs_killed_total` | Counter | `world`, `type` | aus | Eventbasiert |
| `minecraft_items_despawned_total` | Counter | `world`, `item` | aus | Eventbasiert |

Standardgruppen:

- `monster`
- `animal`
- `ambient`
- `water`
- `villager`
- `item`
- `projectile`
- `vehicle`
- `display`
- `other`

## 2.7 Chunks

| Metrik | Typ | Labels | Standard | Status |
|---|---|---|---:|---|
| `minecraft_world_loaded_chunks` | Gauge | `world` | an | Snapshot |
| `minecraft_chunks_loaded_total` | Counter | `world` | an | Eventbasiert |
| `minecraft_chunks_unloaded_total` | Counter | `world` | an | Eventbasiert |
| `minecraft_chunks_generated_total` | Counter | `world` | an | Eventbasiert |
| `minecraft_chunk_load_failures_total` | Counter | `world`, `reason` | aus | API-abhängig |

Phase 4 implementiert `minecraft_world_loaded_chunks`. Grundlage ist
`World#getChunkCount()` aus der öffentlichen Paper-API; es werden weder
`getLoadedChunks()` noch Chunkobjekte oder regiongebundene Einzelabfragen
verwendet. Phase 5 implementiert die drei Lifecycle-Counter über
`ChunkLoadEvent` und `ChunkUnloadEvent`. `ChunkLoadEvent#isNewChunk()` entscheidet
über die zusätzliche Generated-Inkrementierung: Ein neuer Chunk erhöht Loaded
und Generated, ein bestehender nur Loaded. Alle drei Counter verwenden
ausschließlich das gemeinsam validierte Weltlabel; Chunkkoordinaten werden nicht
gelesen oder gespeichert. `ServerLoadEvent` und `WorldLoadEvent` legen lediglich
Nullreihen für tatsächlich geladene Welten an, damit alle drei Familien bereits
vor dem ersten Chunk-Lifecycle-Event sichtbar sind.
`minecraft_chunk_load_failures_total` bleibt
unimplementiert.

## 2.8 Folia

Phase 6 implementiert diese Metrikgruppe über einen isolierten, ausschließlich
auf öffentlichen APIs basierenden Provider. Messquelle ist
`Server#getRegionTPS(World,int,int)` aus
`folia-api:26.1.2.build.8-stable`.

| Metrik | Typ | Labels | Standard | Status |
|---|---|---|---:|---|
| `minecraft_folia_observed_regions` | Gauge | `world` | an | Stabil |
| `minecraft_folia_region_tps` | Gauge | `world`, `window`, `stat` | an | Stabil |
| `minecraft_folia_regions_below_tps` | Gauge | `world`, `window`, `threshold` | an | Abgeleitet |
| `minecraft_folia_regions_with_players` | Gauge | `world` | an | Abgeleitet |
| `minecraft_folia_players_per_region` | Gauge | `world`, `stat` | an | Abgeleitet |
| `minecraft_folia_region_snapshot_age_seconds` | Gauge | `world` | an | Stabil |
| `minecraft_folia_active_regions` | Gauge | `world` | aus | Nicht verfügbar |
| `minecraft_folia_region_tick_duration_seconds` | Gauge/Histogram | `world`, `window`, `stat` | aus | Nicht verfügbar |
| `minecraft_folia_overloaded_regions` | Gauge | `world`, `threshold_seconds` | aus | Nicht verfügbar |
| `minecraft_folia_region_tick_delay_seconds` | Gauge | `world`, `window`, `stat` | aus | Nicht verfügbar |

TPS-Fenster:

- `5s`
- `15s`
- `1m`
- `5m`
- `15m`

Statistiken:

- `min`
- `p05`
- `p50`
- `p95`
- `max`
- `average`

Die öffentliche API liefert keine vollständige Regionsauflistung und keine
Regions-ID. Beobachtungsanker stammen aus Spielerpositionen, Weltspawns und
optional force-loaded Chunks. Auf dem Region-Thread werden Anker derselben
aktuellen Region mit `isOwnedByCurrentRegion` dedupliziert. Deshalb ist
`minecraft_folia_observed_regions` ausschließlich die Anzahl aktuell gültiger,
tatsächlich beobachteter Regionen je Welt und niemals die Zahl aller aktiven
Regionen.

`minecraft_folia_region_tps` aggregiert die gültigen Regionswerte exakt über
`min`, `p05`, `p50`, `p95`, `max` und `average`. Quantile verwenden aufsteigend
sortierte Werte und lineare Typ-7-Interpolation `h=(n-1)q`; es wird nicht
gerundet. Ein Einzelwert ergibt für jede Statistik denselben Wert. Nichtfinite,
negative oder über der öffentlichen maximalen Tickrate `10000` liegende Werte
werden nicht publiziert. Ohne gültige Beobachtung fehlt die dynamische Reihe.

`minecraft_folia_regions_below_tps` zählt mit einem strikt kleineren Vergleich.
Schwellenwerte sind endlich, liegen in `(0,20]`, werden absteigend ausgegeben und
kanonisch als beispielsweise `19`, `18` und `15` formatiert. Fenster und
Statistiken besitzen ausschließlich die oben genannten festen Labelmengen.

Regionale Spielerzahlen werden aus Positionsankern aggregiert, die zuvor auf dem
Entity Scheduler gelesen wurden. `regions_with_players` zählt beobachtete
Regionen mit mindestens einem solchen Anker; `players_per_region` verwendet
dieselbe exakte Statistikdefinition. Es werden weder Playerobjekte noch Namen
oder UUIDs gespeichert.

`minecraft_folia_region_snapshot_age_seconds` ist je Welt das Alter der ältesten
noch gültigen Beobachtung. Die Standard-TTL beträgt 60 Sekunden. Entladene
Welten, entfernte Anker und abgelaufene Beobachtungen hinterlassen keine Reihen.

Fehler einzelner Spieleranker und Regionsbeobachtungen werden isoliert; ein
erfolgreicher Lauf publiziert die übrigen gültigen Beobachtungen als
Teilsnapshot. Sind keine gültigen Regionen übrig, ersetzt ein erfolgreicher
leerer Snapshot den vorherigen Stand und entfernt alte dynamische Reihen. Es
entstehen weder `null`-Samples noch nullwertige Ersatzreihen. Nur ein systemischer
Laufabbruch behält den letzten gültigen Snapshot.

Die öffentliche API stellt keine vollständige Zahl aktiver Regionen, regionale
Tickdauer, Tickverzögerung oder einen Überlastungszustand bereit. Die vier als
„Nicht verfügbar“ markierten Familien werden daher nicht registriert. Tickdauer
wird insbesondere nicht aus `20 / TPS` geschätzt.

## 2.9 Aggregiertes Gameplay

Standardmäßig deaktiviert.

| Metrik | Typ | Labels | Status |
|---|---|---|---|
| `minecraft_blocks_broken_total` | Counter | `world`, `block` | Optional |
| `minecraft_blocks_placed_total` | Counter | `world`, `block` | Optional |
| `minecraft_items_crafted_total` | Counter | `item` | Optional |
| `minecraft_items_smelted_total` | Counter | `item` | Optional |
| `minecraft_items_picked_up_total` | Counter | `item` | Optional |
| `minecraft_items_dropped_total` | Counter | `item` | Optional |
| `minecraft_items_consumed_total` | Counter | `item` | Optional |
| `minecraft_player_deaths_total` | Counter | `cause` | Optional, nur aggregiert |
| `minecraft_player_kills_total` | Counter | – | Optional, nur aggregiert |

Auch hier niemals Spieleridentitäten exportieren.

## 2.10 JVM und Prozess

Phase 3 registriert ausschließlich offizielle Instrumentierungen aus
`prometheus-metrics-instrumentation-jvm:1.8.0`. Die Tabellen verwenden die vom
Client tatsächlich exportierten Namen; offizielle Namen werden nicht an frühere
Katalogentwürfe angepasst.

### JVM-Speicher

Quelle: `JvmMemoryMetrics`. Alle Metriken sind Gauges.

| Metrik | Labels | Status |
|---|---|---|
| `jvm_memory_objects_pending_finalization` | – | Stabil |
| `jvm_memory_used_bytes` | `area` | Stabil |
| `jvm_memory_committed_bytes` | `area` | Stabil |
| `jvm_memory_max_bytes` | `area` | Stabil |
| `jvm_memory_init_bytes` | `area` | Stabil |
| `jvm_memory_pool_used_bytes` | `pool` | Stabil |
| `jvm_memory_pool_committed_bytes` | `pool` | Stabil |
| `jvm_memory_pool_max_bytes` | `pool` | Stabil |
| `jvm_memory_pool_init_bytes` | `pool` | Stabil |
| `jvm_memory_pool_collection_used_bytes` | `pool` | JVM-abhängig |
| `jvm_memory_pool_collection_committed_bytes` | `pool` | JVM-abhängig |
| `jvm_memory_pool_collection_max_bytes` | `pool` | JVM-abhängig |
| `jvm_memory_pool_collection_init_bytes` | `pool` | JVM-abhängig |

### Garbage Collection

Quelle: `JvmGarbageCollectorMetrics`. Die Summary-Familie
`jvm_gc_collection_seconds` wird im Prometheus-Textformat als folgende Samples
mit dem Label `gc` ausgegeben:

| Metrik | Typ | Status |
|---|---|---|
| `jvm_gc_collection_seconds_count` | Summary count | Stabil |
| `jvm_gc_collection_seconds_sum` | Summary sum | Stabil |

### Threads

Quelle: `JvmThreadsMetrics`.

| Metrik | Typ | Labels | Status |
|---|---|---|---|
| `jvm_threads_current` | Gauge | – | Stabil |
| `jvm_threads_daemon` | Gauge | – | Stabil |
| `jvm_threads_peak` | Gauge | – | Stabil |
| `jvm_threads_started_total` | Counter | – | Stabil |
| `jvm_threads_deadlocked` | Gauge | – | Stabil |
| `jvm_threads_deadlocked_monitor` | Gauge | – | Stabil |
| `jvm_threads_state` | Gauge | `state` | Stabil |

Die früher vorgesehenen Namen `jvm_threads_live_threads`,
`jvm_threads_daemon_threads`, `jvm_threads_peak_threads`,
`jvm_threads_started_threads_total` und `jvm_threads_deadlocked_threads` werden
nicht exportiert, weil Client 1.8.0 die obigen offiziellen Namen verwendet.

### Klassen

Quelle: `JvmClassLoadingMetrics`.

| Metrik | Typ | Status |
|---|---|---|
| `jvm_classes_currently_loaded` | Gauge | Stabil |
| `jvm_classes_loaded_total` | Counter | Stabil |
| `jvm_classes_unloaded_total` | Counter | Stabil |

Diese Namen ersetzen die früher katalogisierten Varianten
`jvm_classes_loaded_classes`, `jvm_classes_loaded_classes_total` und
`jvm_classes_unloaded_classes_total`.

### Buffer Pools

Quelle: `JvmBufferPoolMetrics`; alle Metriken sind Gauges mit Label `pool`.

| Metrik | Status |
|---|---|
| `jvm_buffer_pool_used_bytes` | Stabil |
| `jvm_buffer_pool_capacity_bytes` | Stabil |
| `jvm_buffer_pool_used_buffers` | Stabil |

### Prozess

Quelle: `ProcessMetrics`.

| Metrik | Typ | Status |
|---|---|---|
| `process_cpu_seconds_total` | Counter | MXBean-abhängig |
| `process_start_time_seconds` | Gauge | Stabil |
| `process_open_fds` | Gauge | Betriebssystem-/MXBean-abhängig |
| `process_max_fds` | Gauge | Betriebssystem-/MXBean-abhängig |
| `process_virtual_memory_bytes` | Gauge | nur bei lesbarem Linux-`/proc/self/status` |
| `process_resident_memory_bytes` | Gauge | nur bei lesbarem Linux-`/proc/self/status` |

### In Client 1.8.0 nicht verfügbar

Das JVM-Instrumentierungsmodul bietet keine offiziellen Instrumentierungen für
die folgenden früher vorgesehenen Metriken. Phase 3 baut sie deshalb nicht frei
nach:

- `process_cpu_usage_ratio`
- `process_uptime_seconds`
- `system_cpu_usage_ratio`
- `system_cpu_count`
- `system_load_average_1m`

## 2.11 Dateisystem

| Metrik | Typ | Labels | Standard |
|---|---|---|---:|
| `minecraft_filesystem_usable_bytes` | Gauge | `path` | an |
| `minecraft_filesystem_total_bytes` | Gauge | `path` | an |
| `minecraft_filesystem_usage_ratio` | Gauge | `path` | an |
| `minecraft_world_size_bytes` | Gauge | `world` | an |
| `minecraft_logs_size_bytes` | Gauge | – | aus |
| `minecraft_plugins_size_bytes` | Gauge | – | aus |

Phase 4 implementiert in dieser Gruppe ausschließlich
`minecraft_world_size_bytes`. Der Wert ist die rekursive Summe der Größen aller
regulären Dateien innerhalb des jeweiligen Weltpfads. Symbolischen Links wird
nicht gefolgt. Nicht lesbare oder während des Laufs verschwindende Einträge
werden übersprungen; schlägt die Berechnung der Welt als Ganzes fehl, bleibt der
letzte gültige Wert erhalten. Allgemeine Dateisystem-, Log- und Pluginpfadgrößen
sind nicht Bestandteil von Phase 4. Normalisierte Weltpfade sind rein intern und
werden niemals als Prometheus-Label oder als zusätzliche öffentliche Metrik
exportiert.

## 2.12 Exporter-Eigenüberwachung

In Phase 2 implementiert:

| Metrik | Typ | Labels | Bedeutung |
|---|---|---|---|
| `minecraft_exporter_build_info` | Info | `version`, `git_commit`, `provider` | Buildinformation; `git_commit` ist der beim Build ermittelte vollständige Hash, außerhalb eines Git-Kontexts `unknown`; der gemeinsame Kern verwendet `provider="common"` |
| `minecraft_exporter_health` | Gauge | – | `1`, wenn der HTTP-Dienst aktiv und fundamental gesund ist |
| `minecraft_exporter_ready` | Gauge | – | `1`, wenn Registry, Metrics Core, HTTP-Server und Initialisierung vollständig bereit sind |
| `minecraft_exporter_scrapes_total` | Counter | – | Versuchte Anfragen an den Metrics-Endpunkt |
| `minecraft_exporter_scrape_errors_total` | Counter | – | Metrics-Anfragen mit serverseitigem Fehler |
| `minecraft_exporter_http_requests_total` | Counter | `endpoint`, `status_class` | HTTP-Anfragen mit kontrollierter Endpunkt- und Statusklassifikation |
| `minecraft_exporter_collector_state` | Gauge | `collector`, `state` | One-Hot-Zustand jedes registrierten internen Collectors |

Erlaubte Werte für `endpoint` sind ausschließlich `metrics`, `health`, `ready`
und `not_found`. `status_class` ist auf `2xx`, `4xx`, `5xx` und `other`
beschränkt. `state` verwendet ausschließlich `disabled`, `starting`, `running`,
`unsupported`, `failed` und `stopped`. Collector-Namen werden bei der internen
Registrierung validiert; freie Fehlertexte, URLs, Methoden, Clientadressen und
User-Agents werden nicht als Labels exportiert. Ohne registrierte fachliche
Collector enthält `minecraft_exporter_collector_state` noch keine Zeitreihe.

Für spätere Phasen katalogisiert, aber in Phase 2 noch nicht registriert:

| Metrik | Typ | Labels |
|---|---|---|
| `minecraft_exporter_uptime_seconds` | Gauge | – |
| `minecraft_exporter_collection_duration_seconds` | Histogram | `collector` |
| `minecraft_exporter_collection_errors_total` | Counter | `collector`, `reason` |
| `minecraft_exporter_scheduler_rejections_total` | Counter | `scheduler` |
| `minecraft_exporter_snapshot_failures_total` | Counter | `collector` |
| `minecraft_exporter_snapshot_age_seconds` | Gauge | `collector` |
| `minecraft_exporter_last_scrape_duration_seconds` | Gauge | – |
| `minecraft_exporter_metrics_exposed` | Gauge | – |

## 2.13 Verbotene Metriken

Folgende Metriken dürfen weder standardmäßig noch optional implementiert werden:

- Spielername oder UUID als Label
- individuelle Spielzeit
- Ping einzelner Spieler
- Gesundheit, Hunger, Erfahrung oder Inventar einzelner Spieler
- Position einzelner Spieler
- individuelle Kills, Tode oder Fortschritte
- individuelle Minecraft-Statistiken
- Zuordnung einer TPS- oder Regionsmessung zu einem Spieler
