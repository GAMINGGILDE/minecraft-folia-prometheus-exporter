# 5. Konfiguration

Beim ersten Start legt das Plugin die Datei
`plugins/FoliaPrometheusExporter/config.yml` mit folgenden Standardwerten an:

```yaml
http:
  bind-address: "127.0.0.1"
  port: 9940
  metrics-path: "/metrics"
  health-path: "/health"
  ready-path: "/ready"
  worker-threads: 2

collection:
  server-interval: "5s"
  world-interval: "10s"
  folia-interval: "5s"
  filesystem-interval: "30m"
  timeout: "10s"
  filesystem-timeout: "15m"

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

folia:
  observation-sources:
    player-regions: true
    world-spawns: true
    force-loaded-chunks: false
    configured-locations: []

  observation-ttl: "60s"
  tps-windows:
    - "5s"
    - "15s"
    - "1m"
    - "5m"
    - "15m"
  tps-statistics:
    - "min"
    - "p05"
    - "p50"
    - "p95"
    - "max"
    - "average"
  tps-thresholds:
    - 19.0
    - 18.0
    - 15.0

filesystem:
  include-world-sizes: true
  world-size-scan-concurrency: 1

privacy:
  individual-player-metrics-supported: false

logging:
  collection-errors: true
```

Konfigurationsänderungen werden nach einem vollständigen Serverneustart wirksam.
Ein Hot-Reload wird nicht empfohlen; Event-Counter können bei einem
Plugin-Reload außerdem zurückgesetzt werden.

## 5.1 HTTP

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `http.bind-address` | `127.0.0.1` | lokale Bindeadresse |
| `http.port` | `9940` | TCP-Port des Exporters |
| `http.metrics-path` | `/metrics` | Prometheus-Scrape-Pfad |
| `http.health-path` | `/health` | Liveness-Pfad |
| `http.ready-path` | `/ready` | Readiness-Pfad |
| `http.worker-threads` | `2` | Größe des benannten HTTP-Workerpools |

Die Bindeadresse darf nicht leer sein und muss beim Start auflösbar und bindbar
sein. Der Port muss zwischen `1` und `65535` liegen. HTTP-Pfade beginnen mit
`/`, enthalten mindestens ein Segment und müssen eindeutig sein. Die Workerzahl
muss positiv sein. Ein belegter Port führt zu einem kontrollierten Startfehler
und vollständigem Lifecycle-Cleanup.

Die Standardbindung `127.0.0.1` ist eine Sicherheitsgrenze: Nur Prozesse auf
demselben Host können den Exporter erreichen. Das Plugin besitzt keine eigene
Authentifizierung. Eine externe Bindung darf daher nur zusammen mit geeigneten
Netzwerk-, Firewall- oder Proxyregeln erfolgen.

## 5.2 Collector und Intervalle

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `collectors.server` | `true` | Server-, Spieler- und Plugin-Summen |
| `collectors.events` | `true` | implementierte Login-, Join-, Quit-, Kick-, Ping- und Chat-Eventmetriken samt Listener sowie Chunk-Lifecycle-Counter |
| `collectors.worlds` | `true` | Weltzustandsmetriken |
| `collectors.chunks` | `true` | Snapshot der aktuell geladenen Chunks je Welt |
| `collectors.entities` | `true` | Entitygruppen, Aggregate und Abgleich |
| `collectors.folia` | `true` | capability-geschützte Folia-Metriken |
| `collectors.jvm` | `true` | JVM-Speicher, GC, Threads, Klassen und Buffer Pools |
| `collectors.process` | `true` | offizielle Prozessinstrumentierung |
| `collectors.filesystem` | `true` | übergeordneter Schalter für Weltgrößen |
| `collectors.plugin-info` | `false` | dynamische Pluginname-/Versionsreihen |

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `collection.server-interval` | `5s` | Server- und aggregierte Spielerwerte |
| `collection.world-interval` | `10s` | Welt- und Chunkwerte |
| `collection.folia-interval` | `5s` | vollständiger Folia-Beobachtungslauf |
| `collection.filesystem-interval` | `30m` | asynchrone Weltgrößenberechnung |
| `collection.timeout` | `10s` | Server-, Welt- und Chunk-Snapshotläufe |
| `collection.filesystem-timeout` | `15m` | kompletter Weltgrößenlauf einschließlich Queue |

Server-, Welt- und Folia-Intervalle müssen mindestens `50ms` betragen. Die
übrigen Dauern müssen positiv, mindestens `1ms` und ohne Überlauf als
Millisekunden darstellbar sein. Ein Lauf überlappt niemals mit einem zweiten Lauf
desselben Collectors; verspätete Ergebnisse werden verworfen.

Auf Paper wird ein aktivierter Folia-Collector als `unsupported` markiert und
registriert keine `minecraft_folia_*`-Familien. Das beeinträchtigt Health,
Readiness und die übrigen Collector nicht.

`collectors.plugin-info` wirkt nur zusammen mit `collectors.server`. Die Option
ist wegen ihrer dynamischen Pluginname- und Versionslabels standardmäßig aus.

Die drei Chunk-Lifecycle-Counter werden ereignisbasiert erfasst und gehören
technisch zu `collectors.events`. `collectors.chunks` steuert ausschließlich die
Snapshot-Metrik `minecraft_world_loaded_chunks`.

## 5.3 Entities

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `entities.reconciliation-interval` | `5m` | Abstand vollständiger Entity-Abgleiche |
| `entities.reconciliation-timeout` | `60s` | maximale Annahmezeit eines Abgleichs |
| `entities.include-exact-types` | `false` | zusätzliche Familie `minecraft_entities{world,type}` |
| `entities.include-projectile-total` | `false` | zusätzliche Familie `minecraft_world_projectiles{world}` |

Das Abgleichsintervall muss mindestens eine Minute betragen. Ein zu kurzer
Vollscan könnte auf Servern mit vielen geladenen Chunks und Entities dauerhaft
Scheduler- und Allokationslast erzeugen. Timeout und Intervall dürfen in beiden
Größenordnungen zueinander stehen; der Überlappungsschutz verhindert parallele
Läufe.

Genaue Typen verwenden vollständige öffentliche Namespaced Bukkit-Keys wie
`minecraft:zombie`. Die Reiheanzahl wächst mit der Zahl tatsächlich vorhandener
Entitytypen je Welt, weshalb diese Option standardmäßig deaktiviert ist. Die
Projektilsumme ist niedrig-kardinal und unabhängig davon schaltbar.

## 5.4 Folia

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `folia.observation-sources.player-regions` | `true` | Spielerpositionen kurz als Regionsanker verwenden |
| `folia.observation-sources.world-spawns` | `true` | Weltspawns als Regionsanker verwenden |
| `folia.observation-sources.force-loaded-chunks` | `false` | Force-Load-Chunks zusätzlich als Anker verwenden |
| `folia.observation-sources.configured-locations` | `[]` | muss leer bleiben; freie Positionen werden nicht unterstützt |
| `folia.observation-ttl` | `60s` | Gültigkeit einer Observation |
| `folia.tps-windows` | alle fünf API-Fenster | Teilmenge aus `5s`, `15s`, `1m`, `5m`, `15m` |
| `folia.tps-statistics` | alle sechs Werte | Teilmenge aus `min`, `p05`, `p50`, `p95`, `max`, `average` |
| `folia.tps-thresholds` | `19`, `18`, `15` | eindeutige endliche Werte in `(0,20]` |

Mindestens eine öffentliche Beobachtungsquelle muss aktiv sein. Die TTL muss
mindestens so lang wie `collection.folia-interval` sein. Leere Listen,
unbekannte oder doppelte Fenster und Statistiken sowie doppelte oder ungültige
Schwellen verhindern den Start. Die Ausgabe ist unabhängig von der YAML-Reihenfolge
deterministisch.

Force-Load-Anker sind standardmäßig aus, weil sie auf Servern mit vielen
force-loaded Chunks zusätzliche periodische Schedulerarbeit erzeugen können.
Es gibt keine Konfiguration für interne Provider, Tickdauer, Tickverzögerung
oder eine vollständige Regionsauflistung.

## 5.5 Dateisystem

`minecraft_world_size_bytes` benötigt sowohl `collectors.filesystem: true` als
auch `filesystem.include-world-sizes: true`.

`filesystem.world-size-scan-concurrency` begrenzt die Zahl gleichzeitig aktiver
Weltverzeichnis-Scans auf `1` bis `8`. Der Standard `1` scannt Welten
sequenziell und minimiert konkurrierende I/O-Last. Höhere Werte können einen Lauf
beschleunigen, erhöhen aber die Dateisystemlast. Symbolischen Links wird nicht
gefolgt; Weltpfade werden nie als Labels exportiert.

## 5.6 Datenschutz und Logging

`privacy.individual-player-metrics-supported` muss `false` bleiben. Individuelle
Spielermetriken können nicht aktiviert werden. `logging.collection-errors`
steuert rate-limitierte Laufzeitfehlermeldungen der Collector. Fehlertexte werden
nicht in Prometheus-Labels übernommen.

## 5.7 Legacy-Aliasse

Folgende ältere Schlüssel werden weiterhin gelesen. Sind Alias und aktueller
Schlüssel gleichzeitig vorhanden, gewinnt der aktuelle Schlüssel:

| Aktueller Schlüssel | Legacy-Alias |
|---|---|
| `collection.folia-interval` | `collection.region-interval` |
| `collectors.folia` | `collectors.folia-regions` |
| `folia.tps-windows` | `folia.tps.windows` |
| `folia.tps-statistics` | `folia.tps.statistics` |
| `folia.tps-thresholds` | `folia.tps.thresholds` |
| `entities.reconciliation-interval` | `collection.entity-interval` |
| `entities.include-exact-types` | `collectors.detailed-entity-types` |

Werte mit einem falschen YAML-Typ oder außerhalb der beschriebenen Grenzen
werden mit vollständigem Konfigurationspfad abgelehnt; sie werden nicht still
korrigiert.
