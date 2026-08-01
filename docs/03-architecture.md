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

- lädt und validiert zuerst die immutable Konfiguration
- lädt den von Gradle in `build-info.properties` eingebetteten Git-Commit mit
  `unknown` als robustem Fallback
- erzeugt Registry, Eigenmetriken und `CollectorCoordinator`
- startet anschließend den HTTP-Dienst
- aktiviert Readiness erst nach vollständiger Initialisierung
- setzt Readiness beim Disable zuerst zurück und schließt alle Komponenten
- räumt auch nur teilweise gestartete Komponenten idempotent auf

### `CollectorCoordinator`

- lehnt doppelte stabile Collector-Namen ab
- startet in Registrierungsreihenfolge und stoppt in umgekehrter Reihenfolge
- verhindert parallele Mehrfachstarts über den Collector-Lifecycle
- isoliert Start- und Stopfehler einzelner Collector
- veröffentlicht eine immutable Sicht auf die aktuellen Zustände

Der allgemeine Lifecycle verwendet die feste Zustandsmenge `disabled`,
`starting`, `running`, `unsupported`, `failed` und `stopped`. Initialisierung
erfolgt höchstens einmal vor dem ersten Start. `stop()` ist idempotent. Phase 2
registriert noch keinen fachlichen Collector und plant keine Erfassungsintervalle.

### `CollectionScheduler`

Abstraktion für:

- Global Region Scheduler
- Region Scheduler
- Entity Scheduler
- Async Scheduler

Diese Scheduler werden auf Paper und Folia verwendet. Es gibt keinen Fallback auf
den klassischen `BukkitScheduler`.

### `SnapshotRepository`

- `ImmutableSnapshot<T>` enthält einen eindeutigen `Instant` und eine defensiv
  kopierte, unveränderliche Werteliste
- Snapshot-Werttypen müssen selbst immutable sein und dürfen keine
  Minecraft-Liveobjekte enthalten
- `SnapshotRepository<T>` veröffentlicht den neuesten vollständigen Snapshot per
  `AtomicReference`
- Lesen, Ersetzen und Entfernen erfolgen atomar und ohne große Lockbereiche
- Erfassungszeitpunkt und Snapshot-Alter sind direkt abfragbar

### `MetricsEndpoint`

- `/metrics`, `/health` und `/ready` als Standardpfade
- offizieller `MetricsHandler` des Prometheus Java Client 1.8.0 auf dessen
  vorgesehenem schlanken JDK-`HttpServer`
- Standardbindung an `127.0.0.1`; Host, Port, Pfade und Workerzahl aus der
  Konfiguration
- offizieller Renderer statt eigener Prometheus-Textserialisierung
- keine zusätzlichen HTTP-Frameworks
- exakt kontrolliertes Routing: unbekannt `404`, andere Methoden `405`
- keine Minecraft-API-Aufrufe
- eigener benannter Daemon-Workerpool und idempotenter Shutdown ohne verbleibenden
  Listener

`/health` liefert `200`, solange der HTTP-Dienst aktiv und nicht fundamental
beschädigt ist. Der Zustand einzelner optionaler Collector beeinflusst Health
nicht. `/ready` liefert nur dann `200`, wenn Registry, Metrics Core und HTTP-Dienst
aktiv sind und die Plugininitialisierung abgeschlossen wurde; andernfalls `503`.
Nicht unterstützte oder optionale Collector machen den Exporter später nicht
automatisch unbereit.

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

Der Shadow-Build verwendet das fest gepinnte Plugin `com.gradleup.shadow:9.6.1`,
deaktiviert das ungeschattete Standard-JAR und erzeugt genau ein Artefakt ohne
Abhängigkeitssignaturen. Eine Buildprüfung kontrolliert Descriptor, Hauptklasse,
Relocation und den Ausschluss von Paper-, Folia-, Bukkit- und Minecraft-Klassen.

Jede `MetricsCore` erzeugt genau eine eigene `PrometheusRegistry` und unmittelbar
danach genau eine instanzgebundene `ExporterMetrics`. Die Registry wird nicht
geteilt; daher ist kein statischer Registry- oder `WeakHashMap`-Cache nötig.
Duplicate-Registration wird strukturell verhindert, weil der paketprivate
Metrikkonstruktor ausschließlich einmal aus `MetricsCore` aufgerufen wird.

Gradle ermittelt `git rev-parse HEAD`, validiert den Hash und expandiert ihn in
`build-info.properties`. Bei fehlendem Git-Programm, fehlendem Checkout oder
ungültigem Ergebnis wird `unknown` eingebettet. Ein explizites
`-PgitCommit=<Hash oder unknown>` ermöglicht reproduzierbare externe Builds.

## 3.7 Phase-2-Lifecycle

```text
onEnable
  Konfiguration validieren
  → Buildinformation laden
  → eigene PrometheusRegistry und einmalig Eigenmetriken registrieren
  → CollectorCoordinator starten
  → HTTP-Server starten
  → Initialisierung vollständig / ready = 1

onDisable oder Startfehler
  ready = 0
  → HTTP-Server und Workerpool stoppen
  → gestartete Collector rückwärts stoppen
  → Registry und Core-Zustand freigeben
```

Alle Schritte sind gegen wiederholten Aufruf geschützt. Ein Bindefehler, etwa bei
belegtem Port, stoppt bereits gestartete Collector wieder und lässt keine
teilweise aktive HTTP-Infrastruktur zurück.
