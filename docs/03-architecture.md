# 3. Zielarchitektur

## 3.1 Datenfluss

```text
Paper/Folia Events und Scheduler
             │
             ▼
      CollectorCoordinator
             │
    ┌────────┼─────────┬────────┐
    ▼        ▼         ▼        ▼
 Global   Region     Entity   Async
Collector Collector Collector Collector
    │        │         │        │
    └────────┴─────────┴────────┘
             ▼
      Immutable Snapshots
             │
             ▼
      SnapshotRepository
             │
             ▼
       MetricsEndpoint
```

Die JVM- und Prozessinstrumentierungen aus Phase 3 bilden einen getrennten,
serverunabhängigen Datenpfad:

```text
JDK Management APIs und betriebssystemspezifische Prozessdaten
                         │
                         ▼
               JvmMetricsRegistrar
                         │
                         ▼
       private PrometheusRegistry des MetricsCore
                         │
                         ▼
                  MetricsEndpoint
```

## 3.2 Kernregel

Der HTTP-Endpunkt darf niemals live auf Bukkit-, Paper- oder Folia-Welten,
Chunks, Entities oder Spieler zugreifen.

Ein Scrape darf nur:

1. vorhandene Minecraft-Snapshots lesen,
2. offizielle Prometheus-Callbacks für reine JVM-/Prozessdaten ausführen,
3. Prometheus-Text serialisieren,
4. HTTP-Status und Header schreiben.

Die Ausnahme in Punkt 2 betrifft ausschließlich die offizielle
JVM-Instrumentierung. Sie liest keine Bukkit-, Paper-, Folia- oder
Minecraft-Liveobjekte.

## 3.3 Hauptkomponenten

### `ExporterPlugin`

- lädt und validiert zuerst die immutable Konfiguration
- lädt den von Gradle in `build-info.properties` eingebetteten Git-Commit mit
  `unknown` als robustem Fallback
- erzeugt Registry, JVM-/Prozessregistrar, Eigenmetriken und
  `CollectorCoordinator`
- fixiert zu Beginn von `onEnable()` den für Phase 4 verwendeten
  Server-Aktivierungszeitpunkt und erzeugt `PhaseFourRuntime`
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
erfolgt höchstens einmal vor dem ersten Start. `stop()` ist idempotent. Phase 4
registriert die vier verwalteten Collector `server`, `worlds`, `chunks` und
`world-sizes`; deaktivierte Gruppen bleiben sichtbar im Zustand `disabled`.

### `JvmMetricsRegistrar`

- verwendet ausschließlich die private `PrometheusRegistry` seines
  `MetricsCore`
- registriert `JvmMemoryMetrics`, `JvmGarbageCollectorMetrics`,
  `JvmThreadsMetrics`, `JvmClassLoadingMetrics`, `JvmBufferPoolMetrics` und
  `ProcessMetrics` aus dem offiziellen Prometheus Java Client 1.8.0
- registriert jede konfigurierte Instrumentierung pro Instanz höchstens einmal
- verwendet weder die globale Default-Registry noch statische Registry-Zustände
- berücksichtigt die unabhängigen Schalter `collectors.jvm` und
  `collectors.process`
- benötigt keine Minecraft-API, keinen Scheduler, keine Snapshot-Publikation und
  keinen eigenen HTTP-Server

Die Instrumentierungen sind Registry-Callbacks und lesen beim Scrape nur
JDK-/Prozessdaten. Das Snapshot-Modell bleibt für Minecraft-Livezustand
verbindlich, würde hier aber lediglich zusätzliche veraltete Kopien erzeugen.
Die verwendeten Instrumentierungen besitzen keine Start-, Stop- oder
Unregister-Operation und starten keine eigenen Hintergrundthreads. Beim
Core-Shutdown entfernt `PrometheusRegistry.clear()` ihre Callbacks.

### `CollectionScheduler`

Abstraktion für:

- Global Region Scheduler
- Region Scheduler
- Entity Scheduler
- Async Scheduler

Diese Scheduler werden auf Paper und Folia verwendet. Es gibt keinen Fallback auf
den klassischen `BukkitScheduler`.

`PaperCollectionScheduler` ist die gemeinsame konkrete Implementierung. Phase 4
plant die periodischen Erfassungsstarts über den Global Region Scheduler,
einzelne Spielmoduslesungen über den jeweiligen Entity Scheduler und
Dateisystemarbeit sowie Timeout-Wächter über den Async Scheduler. Der Region
Scheduler gehört zur Abstraktion, wird in Phase 4 aber nicht benötigt, weil die
öffentliche API aggregierte Welt- und Chunkzahlen ohne Positionszugriff anbietet.

### `PeriodicSnapshotCollector`

- startet in festem Intervall auf dem Global Region Scheduler
- lässt pro Collector höchstens einen Erfassungslauf gleichzeitig zu
- beendet die Annahme nach dem konfigurierten Timeout und ignoriert verspätete
  Callbacks anhand der Identität des aktiven Laufs
- veröffentlicht nur vollständig erfolgreiche immutable Snapshots
- behält bei Fehler oder Timeout den letzten gültigen Snapshot
- verwirft Ergebnisse nach `stop()` und beendet periodischen Task und
  Timeout-Wächter idempotent

Ein vorübergehender Erfassungsfehler ändert den Collector-Lifecycle nicht von
`running` zu `failed`: Dieser Zustand bezeichnet weiterhin einen strukturellen
Startfehler. Laufzeitfehler werden höchstens einmal je Collector und fünf
Minuten protokolliert, sofern `logging.collection-errors` aktiv ist.

### `SnapshotRepository`

- `ImmutableSnapshot<T>` enthält einen eindeutigen `Instant` und eine defensiv
  kopierte, unveränderliche Werteliste
- Snapshot-Werttypen müssen selbst immutable sein und dürfen keine
  Minecraft-Liveobjekte enthalten
- `SnapshotRepository<T>` veröffentlicht den neuesten vollständigen Snapshot per
  `AtomicReference`
- Lesen, Ersetzen und Entfernen erfolgen atomar und ohne große Lockbereiche
- Erfassungszeitpunkt und Snapshot-Alter sind direkt abfragbar

### `PhaseFourRuntime` und `MinecraftMetrics`

`PhaseFourRuntime` verdrahtet die vier Erfassungsgruppen mit ihren privaten
Repositories und dem vorhandenen Coordinator. `MinecraftMetrics` registriert
pro aktivierter Gruppe genau einen Prometheus-`MultiCollector` in der privaten
Core-Registry. Ein `MultiCollector` liest für sämtliche Familien seiner Gruppe
genau einen aktuellen Snapshot; dadurch bilden zusammengehörige Samples einen
konsistenten Stand. Die Registrierung ist pro Runtime idempotent und wird bei
einem Teilfehler zurückgerollt.

Snapshots enthalten nur primitive Werte, Strings, Enums und immutable
Sammlungen. Insbesondere werden keine `Server`-, `World`-, `Player`-, `Plugin`-
oder `Chunk`-Referenzen publiziert. Da Welt-Labelreihen bei jeder Collection aus
dem aktuellen Snapshot entstehen, verschwinden entladene Welten automatisch.

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

`JvmCollector` und `ProcessCollector` sind logische Metrikgruppen, keine
`ManagedCollector`: Sie werden durch `JvmMetricsRegistrar` direkt an die
Registry gebunden. In Phase 4 sind Server, Welten, Chunks und Weltgrößen separate
`ManagedCollector` mit jeweils eigenem Snapshot-Repository. Event-, Entity-,
Folia- und Gameplay-Collector bleiben späteren Phasen vorbehalten.

## 3.5 Fehlerisolation

- Fehler setzen den letzten gültigen Snapshot nicht automatisch auf null.
- Ein Fehler in einer einzelnen Welt verhindert die Aktualisierung der übrigen
  Welten nicht; für die betroffene weiterhin geladene Welt bleibt ihr letzter
  gültiger Wert erhalten.
- Timeout, Überlappungsschutz und Laufidentität verhindern, dass verspätete
  Ergebnisse einen neueren Snapshot überschreiben.
- Laufzeitfehler werden je Collector rate-limitiert protokolliert.
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
danach genau einen instanzgebundenen `JvmMetricsRegistrar` und eine
`ExporterMetrics`. Die Registry wird nicht geteilt; daher ist kein statischer
Registry- oder `WeakHashMap`-Cache nötig. Duplicate-Registration wird
strukturell verhindert, weil `MetricsCore` den Registrar genau einmal erzeugt
und dessen idempotente `register()`-Methode jede Gruppe höchstens einmal bindet.

Gradle ermittelt `git rev-parse HEAD`, validiert den Hash und expandiert ihn in
`build-info.properties`. Bei fehlendem Git-Programm, fehlendem Checkout oder
ungültigem Ergebnis wird `unknown` eingebettet. Ein explizites
`-PgitCommit=<Hash oder unknown>` ermöglicht reproduzierbare externe Builds.

## 3.7 Phase-4-Lifecycle

```text
onEnable
  Aktivierungszeitpunkt fixieren
  Konfiguration validieren
  → Buildinformation laden
  → eigene PrometheusRegistry erzeugen
  → konfigurierte offizielle JVM-/Prozessinstrumentierungen einmalig registrieren
  → Eigenmetriken einmalig registrieren
  → Phase-4-Metrikfamilien registrieren
  → Phase-4-Collector beim Coordinator registrieren
  → CollectorCoordinator starten
  → HTTP-Server starten
  → Initialisierung vollständig / ready = 1

onDisable oder Startfehler
  ready = 0
  → HTTP-Server und Workerpool stoppen
  → gestartete Collector rückwärts stoppen
  → alle verbliebenen Phase-4-Schedulertasks abbrechen
  → Registry und Core-Zustand freigeben
```

Alle Schritte sind gegen wiederholten Aufruf geschützt. Ein Bindefehler, etwa bei
belegtem Port, stoppt bereits gestartete Collector wieder und lässt keine
teilweise aktive HTTP-Infrastruktur zurück. Ein Fehler bei der
JVM-/Prozessregistrierung propagiert aus der Core-Konstruktion und verhindert den
Pluginstart kontrolliert; zu diesem Zeitpunkt existieren weder HTTP-Listener noch
Collector- oder Scheduler-Threads. Der Registrar leert bei einem
Registrierungsfehler die noch nicht freigegebene private Registry und wiederholt
den fehlgeschlagenen Registrierungsversuch nicht.
