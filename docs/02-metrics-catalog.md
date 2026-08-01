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

## 2.8 Folia

Diese Metrikgruppe ist Folia-spezifisch und wird später über einen isolierten,
ausschließlich auf öffentlichen APIs basierenden Provider implementiert. Der
Provider und seine Feature-Erkennung sind nicht Bestandteil von Phase 1.

| Metrik | Typ | Labels | Standard | Status |
|---|---|---|---:|---|
| `minecraft_folia_observed_regions` | Gauge | `world` | an | Stabil |
| `minecraft_folia_region_tps` | Gauge | `world`, `window`, `stat` | an | Stabil |
| `minecraft_folia_regions_below_tps` | Gauge | `world`, `window`, `threshold` | an | Abgeleitet |
| `minecraft_folia_regions_with_players` | Gauge | `world` | an | Abgeleitet |
| `minecraft_folia_players_per_region` | Gauge | `world`, `stat` | an | Abgeleitet |
| `minecraft_folia_region_snapshot_age_seconds` | Gauge | `world` | an | Stabil |
| `minecraft_folia_active_regions` | Gauge | `world` | aus | Experimentell |
| `minecraft_folia_region_tick_duration_seconds` | Gauge/Histogram | `world`, `window`, `stat` | aus | Experimentell |
| `minecraft_folia_overloaded_regions` | Gauge | `world`, `threshold_seconds` | aus | Experimentell |
| `minecraft_folia_region_tick_delay_seconds` | Gauge | `world`, `window`, `stat` | aus | Experimentell |

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

Wichtig: Ohne vollständige öffentliche Regionsauflistung bedeutet
`observed_regions` nur die vom Plugin erkannten Messregionen.

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
