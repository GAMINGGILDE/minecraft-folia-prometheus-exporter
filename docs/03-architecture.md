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

- `/metrics`
- `/health`
- `/ready`
- eigener HTTP-Executor
- keine Minecraft-API-Aufrufe

### `RegionObservationRegistry`

- verwaltet interne Beobachtungspunkte
- keine Spieleridentitäten in Snapshots
- Deduplizierung von Beobachtungen
- Ablauf alter Beobachtungen
- Aggregation je Welt

### Folia-spezifischer Provider

- wird erst in Phase 6 eingeführt
- ist vom allgemeinen, gegen `paper-api` kompilierten Code isoliert
- verwendet ausschließlich öffentliche Folia-APIs
- wird nicht in Phase 1 auf Vorrat angelegt
- führt seine Plattform- oder Feature-Erkennung erst bei tatsächlichem Bedarf ein

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
- Experimentelle oder interne Provider sind kein Bestandteil von Version 1.
