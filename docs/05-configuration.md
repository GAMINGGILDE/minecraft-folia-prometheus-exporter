# 5. Konfigurationsentwurf

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
  region-interval: "5s"
  entity-interval: "30s"
  filesystem-interval: "30m"
  timeout: "10s"
  filesystem-timeout: "15m"

collectors:
  server: true
  events: true
  worlds: true
  chunks: true
  entities: true
  folia-regions: true
  jvm: true
  process: true
  filesystem: true
  exporter: true
  gameplay: false
  plugin-info: false
  detailed-entity-types: false
  commands: false

folia:
  observation-sources:
    player-regions: true
    world-spawns: true
    force-loaded-chunks: true
    configured-locations: []

  observation-ttl: "60s"

  tps:
    windows:
      - "5s"
      - "15s"
      - "1m"
      - "5m"
      - "15m"
    statistics:
      - "min"
      - "p05"
      - "p50"
      - "p95"
      - "max"
      - "average"
    thresholds:
      - 19.5
      - 18.0
      - 15.0
      - 10.0

filesystem:
  include-world-sizes: true
  world-size-scan-concurrency: 1
  include-server-filesystem: true
  include-log-size: false
  include-plugin-size: false

privacy:
  individual-player-metrics-supported: false

logging:
  collection-errors: true
  debug: false
```

## Konfigurationsregeln

- Ungültige Konfigurationswerte verhindern den Pluginstart und werden mit einer
  verständlichen Fehlermeldung protokolliert.
- Der HTTP-Endpunkt bindet standardmäßig nur lokal.
- `http.bind-address` darf nicht leer sein und muss beim Start zu einer bindbaren
  Adresse auflösbar sein.
- `http.port` muss zwischen `1` und `65535` liegen. Ein belegter Port führt zu
  einem kontrollierten Startfehler und vollständigem Lifecycle-Cleanup.
- HTTP-Pfade beginnen mit `/`, enthalten mindestens ein Segment und müssen
  eindeutig sein.
- `http.worker-threads` muss positiv sein.
- `collection.server-interval`, `world-interval`, `region-interval` und
  `entity-interval` müssen mindestens `50ms` betragen, weil sie in Serverticks
  darstellbar sein müssen.
- `collection.filesystem-interval`, `collection.timeout` und
  `collection.filesystem-timeout` müssen mindestens `1ms` betragen. Alle
  Erfassungsdauern müssen ohne Überlauf als Millisekunden darstellbar sein;
  Fehlermeldungen nennen den vollständigen Konfigurationspfad.
- `filesystem.world-size-scan-concurrency` muss eine Ganzzahl zwischen `1` und
  `8` sein.
- Werte mit falschem YAML-Datentyp werden bereits beim Laden mit Pfadangabe
  abgelehnt.
- Experimentelle oder interne Provider sind in Version 1 nicht konfigurierbar.
- Spielermetriken existieren nicht als aktivierbare Option.
- Die genaue Behandlung von `folia-regions: true` auf Paper wird erst zusammen
  mit dem isolierten Folia-Provider in Phase 6 implementiert: Der Collector wird
  nicht gestartet, einmalig als nicht unterstützt protokolliert und intern als
  `unsupported` markiert. Der Pluginstart läuft weiter und es werden keine
  Folia-Metriken mit künstlichen Nullwerten exportiert.

## Konfigurationsmodell

- Konfigurationswerte werden in immutable Java-Records oder unveränderlichen
  finalen Klassen abgebildet.
- Laden und Validieren sind getrennte Komponenten.
- Phase 1 testet Standardwerte und ungültige Konfigurationen.
- Phase 2 übernimmt Bindeadresse, Port, Endpunktpfade und Workerzahl aus der
  bestehenden HTTP-Konfiguration. Die Standardbindung bleibt `127.0.0.1`; die
  Standardpfade sind `/metrics`, `/health` und `/ready`.
- Phase 3 verwendet die bereits vorgesehenen unabhängigen Schalter
  `collectors.jvm` und `collectors.process`. Beide sind standardmäßig aktiv und
  werden vor dem HTTP-Start ausgewertet.
- Phase 4 verwendet die Schalter `collectors.server`, `collectors.worlds`,
  `collectors.chunks` und `collectors.filesystem` sowie die Optionen
  `collectors.plugin-info`, `filesystem.include-world-sizes` und
  `filesystem.world-size-scan-concurrency`.
- Phase 5 verwendet ausschließlich den bereits vorgesehenen gemeinsamen Schalter
  `collectors.events`. Es gibt keine einzelnen Schalter pro Eventfamilie.
- Das Konfigurationsmodell selbst bleibt serverunabhängig und immutable. Erst der
  Plugin-Lifecycle startet nach erfolgreicher Validierung Registry, Coordinator
  und HTTP-Dienst.

## Phase-2-relevante Werte

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `http.bind-address` | `127.0.0.1` | lokale Bindeadresse; keine externe Freigabe ohne bewusste Änderung |
| `http.port` | `9940` | TCP-Port des Exporters |
| `http.metrics-path` | `/metrics` | offizieller Prometheus-Scrape-Handler |
| `http.health-path` | `/health` | Liveness-Endpunkt |
| `http.ready-path` | `/ready` | Readiness-Endpunkt |
| `http.worker-threads` | `2` | feste Größe des benannten HTTP-Workerpools |

## Phase-3-relevante Werte

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `collectors.jvm` | `true` | registriert Speicher-, GC-, Thread-, Klassen- und Buffer-Pool-Instrumentierung |
| `collectors.process` | `true` | registriert die offizielle Prozessinstrumentierung |

Die Schalter wirken unabhängig. Sind beide deaktiviert, enthält die private
Registry weiterhin die Exporter-Eigenmetriken, aber keine `jvm_*`- oder
`process_*`-Familien. Eine einzelne Schaltung je JVM-Metrik ist nicht vorgesehen.
Das Instrumentierungsmodul 1.8.0 bietet keine `system_*`-Gruppe; daher existiert
kein irreführender `collectors.system`-Schalter.

## Phase-4-relevante Werte

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `collection.server-interval` | `5s` | Intervall für Server-, aggregierte Spieler- und Pluginwerte |
| `collection.world-interval` | `10s` | gemeinsames Intervall für Welt- und geladenen Chunkzustand |
| `collection.filesystem-interval` | `30m` | Intervall für die asynchrone Weltgrößenberechnung |
| `collection.timeout` | `10s` | maximale Annahmezeit für Server-, Welt- und Chunk-Snapshotläufe |
| `collection.filesystem-timeout` | `15m` | maximale Zeit des vollständigen Weltgrößenlaufs einschließlich Warteschlange und aller Scans |
| `collectors.server` | `true` | Server-, Spieler- und Plugin-Zählmetriken |
| `collectors.worlds` | `true` | Weltzustandsmetriken außer Chunkzahl und Größe |
| `collectors.chunks` | `true` | `minecraft_world_loaded_chunks` |
| `collectors.filesystem` | `true` | übergeordneter Schalter für Dateisystemerfassung |
| `collectors.plugin-info` | `false` | dynamische `minecraft_plugin_info`-Labelreihen zusätzlich zu Plugin-Summen |
| `filesystem.include-world-sizes` | `true` | Weltgrößen, nur zusammen mit `collectors.filesystem` |
| `filesystem.world-size-scan-concurrency` | `1` | maximale Zahl gleichzeitig aktiver Weltverzeichnis-Scans; gültig sind `1` bis `8` |
| `logging.collection-errors` | `true` | rate-limitierte Laufzeitfehler der Erfassung protokollieren |

Alle Schalter wirken unabhängig. Ist eine Gruppe deaktiviert, werden ihre
fachlichen Metrikfamilien nicht registriert und ihr verwalteter Collector bleibt
im Zustand `disabled`. `collectors.plugin-info` hat nur bei aktivem
`collectors.server` eine Wirkung. Weltgrößen benötigen beide zugehörigen
Dateisystemschalter; sie laufen niemals im HTTP- oder Tickthread.

Weltgrößen erhalten ihren eigenen längeren Timeout, weil rekursive Scans großer
Welten deutlich länger als die schnellen Minecraft-Snapshots dauern können.
`collection.filesystem-timeout` umfasst Wartezeit in der internen Queue, alle
gestarteten Scans sowie Zusammenführung und Publikation. Nach Ablauf darf der
nächste Intervalllauf beginnen; wartende Arbeit des alten Laufs wird nicht mehr
gestartet und verspätete Ergebnisse werden anhand der Laufidentität verworfen.
Ein bereits laufender Java-Dateisystemaufruf ist möglicherweise nicht physisch
unterbrechbar. Der letzte erfolgreiche Snapshot bleibt während Fehlern und
Timeouts erhalten.

Mit dem Standard `world-size-scan-concurrency: 1` werden Weltverzeichnisse in
sortierter Reihenfolge sequenziell gescannt. Ein höherer Wert kann den Lauf
beschleunigen, erhöht aber die konkurrierende I/O-Last. Die Option verändert
weder den Namen noch die Labels von `minecraft_world_size_bytes`; interne
Weltpfade werden niemals exportiert.

`collection.region-interval`, `collection.entity-interval`, die Entity-, Folia-,
Gameplay- und detaillierten Schalter sowie die übrigen
`filesystem.include-*`-Optionen bleiben für spätere Phasen in der Konfiguration.
Sie lösen in Phase 4 keine zusätzliche Metrikerfassung aus.

## Phase-5-relevante Werte

| Schlüssel | Standard | Wirkung |
|---|---:|---|
| `collectors.events` | `true` | registriert gemeinsam die zehn Event-Counter und genau einen Listener |

Bei `collectors.events: false` bleibt der verwaltete Collector im Zustand
`disabled`. Es werden weder Listener noch eine der folgenden Familien
registriert: Loginversuche, Loginablehnungen, Join, Quit, Kick,
Serverlisten-Ping, Chat sowie Chunk-Load, -Unload und -Generierung. JVM-, Prozess-
und Phase-4-Collector funktionieren unverändert weiter.

Der Event-Collector besitzt bewusst keine Intervalle oder Timeouts. Seine
Counter werden direkt beim jeweiligen Ereignis erhöht. Alle zehn Familien werden
gemeinsam geschaltet; feinere Optionen würden in Phase 5 nur zusätzliche
Konfigurationskomplexität ohne technische Notwendigkeit erzeugen.
