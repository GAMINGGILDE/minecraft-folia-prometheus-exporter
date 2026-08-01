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

Die übrigen Collector- und Erfassungswerte bleiben für die späteren fachlichen
Phasen in der Konfiguration erhalten und lösen in Phase 3 noch keine
Minecraft-Erfassung aus.
