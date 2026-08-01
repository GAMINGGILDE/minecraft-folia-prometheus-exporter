# 3. Zielarchitektur

## 3.1 Datenfluss

```text
Paper/Folia Events und Scheduler
             │
             ▼
      CollectorCoordinator
             │
    ┌────────┼─────────┐
    ▼        ▼         ▼
 Global   Region     Async
Collector Collector Collector
    │        │         │
    └────────┼─────────┘
             ▼
      Immutable Snapshots
             │
             ▼
      SnapshotRepository
             │
             ▼
       MetricsEndpoint
```

## 3.2 Kernregel

Der HTTP-Endpunkt darf niemals live auf Bukkit-, Paper- oder Folia-Welten,
Chunks, Entities oder Spieler zugreifen.

Ein Scrape darf nur:

1. vorhandene Snapshots lesen,
2. Prometheus-Text serialisieren,
3. HTTP-Status und Header schreiben.

## 3.3 Hauptkomponenten

### `ExporterPlugin`

- Plugin-Lifecycle
- Start und Stop der Komponenten
- Konfiguration laden
- Fehler beim Start sauber melden

### `CollectorCoordinator`

- Collector registrieren
- Intervalle planen
- Collector voneinander isolieren
- Timeouts und Fehler erfassen
- Snapshot atomar ersetzen

### `CollectionScheduler`

Abstraktion für:

- Global Region Scheduler
- Region Scheduler
- Entity Scheduler
- Async Scheduler

Diese Scheduler werden auf Paper und Folia verwendet. Es gibt keinen Fallback auf
den klassischen `BukkitScheduler`.

### `SnapshotRepository`

- threadsichere, unveränderliche Snapshots
- pro Collector separater Zeitstempel
- atomare Aktualisierung
- Lesen ohne Blockierung

### `MetricsEndpoint`

- `/metrics`, `/health` und `/ready` als Standardpfade
- offizieller Prometheus-Java-Client-HTTP-Server 1.8.0
- Standardbindung an `127.0.0.1`; Host, Port, Pfade und Workerzahl aus der
  Konfiguration
- offizieller Renderer statt eigener Prometheus-Textserialisierung
- keine zusätzlichen HTTP-Frameworks
- keine Minecraft-API-Aufrufe
- sauberer `close()`-/`stop()`-Aufruf beim Plugin-Disable

### `RegionObservationRegistry`

- verwaltet interne Beobachtungspunkte
- keine Spieleridentitäten in Snapshots
- Deduplizierung von Beobachtungen
- Ablauf alter Beobachtungen
- Aggregation je Welt

### Folia-spezifischer Provider

- wird erst in Phase 6 eingeführt
- ist vom allgemeinen, gegen `paper-api` kompilierten Code isoliert
- kompiliert gegen `dev.folia:folia-api:26.1.2.build.8-stable` als `compileOnly`
- verwendet ausschließlich öffentliche Folia-APIs
- wird nicht in Phase 1 auf Vorrat angelegt
- wird auch nicht in Phase 2 implementiert
- aktiviert sich ausschließlich anhand der konkret benötigten öffentlichen
  Folia-API-Capability
- wird auf Paper vor der Capability-Prüfung weder referenziert noch geladen
- verwendet weder Servername noch Versionsstring oder Scheduler-Verfügbarkeit als
  Plattformmerkmal
- verwendet insbesondere nicht die interne Klasse
  `io.papermc.paper.threadedregions.RegionizedServer`

## 3.4 Collector-Gruppen

```text
ServerCollector
EventCollector
WorldCollector
ChunkCollector
EntityCollector
FoliaRegionCollector
JvmCollector
ProcessCollector
FileSystemCollector
ExporterCollector
GameplayCollector (optional)
```

## 3.5 Fehlerisolation

- Jeder Collector hat eigenen Fehlerzähler.
- Fehler setzen den letzten gültigen Snapshot nicht automatisch auf null.
- Snapshot-Alter zeigt veraltete Daten.
- Ein Collector-Fehler darf den HTTP-Endpunkt nicht stoppen.
- Ein optionaler plattformspezifischer Provider darf das Plugin nicht am Start hindern.
- Ein konfigurierter, aber nicht unterstützter Folia-Collector wird nicht gestartet,
  einmalig verständlich als `unsupported` gemeldet und exportiert keine
  Folia-Nullwerte.
- Experimentelle oder interne Provider sind kein Bestandteil von Version 1.

## 3.6 Abhängigkeitsisolation

Der Prometheus Java Client wird ab Phase 2 über seine BOM in Version 1.8.0
eingebunden. Die Module `prometheus-metrics-core`,
`prometheus-metrics-instrumentation-jvm` und
`prometheus-metrics-exporter-httpserver` werden in das gemeinsame Plugin-JAR
geschattet. `io.prometheus` wird nach
`de.minecraftgilde.prometheus.internal.prometheus` relocatet. Dadurch bleiben die
Bibliotheken anderer Plugins vom Exporter isoliert.
