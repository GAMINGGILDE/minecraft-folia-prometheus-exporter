# 3. Zielarchitektur

## 3.1 Datenfluss

```text
Paper/Folia Scheduler
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

Phase 5 ergänzt einen getrennten ereignisbasierten Pfad:

```text
öffentliche Paper-/Bukkit-Events auf ihrem jeweiligen Eventthread
                            │
                            ▼
                    EventCollector
                            │
                            ▼
       threadsichere Counter der privaten PrometheusRegistry
                            │
                            ▼
                    MetricsEndpoint
```

Der Eventpfad fragt beim Scrape keine Minecraft-Daten ab. Er akkumuliert während
des Events nur primitive Werte, strukturierte Enumwerte und validierte
Weltlabels. Er verwendet keine periodischen Tasks und speichert keine
Minecraft-Objekte im Registryzustand.

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
2. bereits akkumulierten threadsicheren Event-Counterzustand lesen,
3. offizielle Prometheus-Callbacks für reine JVM-/Prozessdaten ausführen,
4. Prometheus-Text serialisieren,
5. HTTP-Status und Header schreiben.

Die Ausnahme in Punkt 3 betrifft ausschließlich die offizielle
JVM-Instrumentierung. Sie liest keine Bukkit-, Paper-, Folia- oder
Minecraft-Liveobjekte. Punkt 2 löst ebenfalls keine Liveabfrage aus; die Werte
wurden bereits während öffentlicher Events erhöht.

## 3.3 Hauptkomponenten

### `ExporterPlugin`

- lädt und validiert zuerst die immutable Konfiguration
- lädt den von Gradle in `build-info.properties` eingebetteten Git-Commit mit
  `unknown` als robustem Fallback
- erzeugt Registry, JVM-/Prozessregistrar, Eigenmetriken und
  `CollectorCoordinator`
- fixiert zu Beginn von `onEnable()` den für Phase 4 verwendeten
  Server-Aktivierungszeitpunkt und erzeugt `PhaseFourRuntime`
- erzeugt `PhaseFiveRuntime`, das den konfigurierten Event-Collector vor dem
  Core-Start beim vorhandenen Coordinator registriert
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
registriert `server`, `worlds`, `chunks` und `world-sizes`; Phase 5 ergänzt
`events`. Deaktivierte Gruppen bleiben sichtbar im Zustand `disabled`.

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
Dateisystemarbeit sowie Timeout-Wächter über den Async Scheduler. Der
Weltgrößen-Capture stellt nur bis zu
`filesystem.world-size-scan-concurrency` Async-Tasks gleichzeitig bereit; die
interne FIFO-Warteschlange ist nach Weltname sortiert und startet standardmäßig
genau einen Scan. Der Region
Scheduler gehört zur Abstraktion, wird in Phase 4 aber nicht benötigt, weil die
öffentliche API aggregierte Welt- und Chunkzahlen ohne Positionszugriff anbietet.

### `PeriodicSnapshotCollector`

- startet in festem Intervall auf dem Global Region Scheduler
- lässt pro Collector höchstens einen Erfassungslauf gleichzeitig zu
- beendet die Annahme nach dem konfigurierten Timeout und ignoriert verspätete
  Callbacks anhand der Identität des aktiven Laufs
- signalisiert dem Capture bei Timeout, Fehler und Stop, dass der Lauf keine
  Ergebnisse mehr annehmen kann; dadurch können interne Warteschlangen ihre noch
  nicht gestartete Arbeit verwerfen
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

Nur `world-sizes` verwendet `collection.filesystem-timeout` mit dem Standard
`15m`; `server`, `worlds` und `chunks` verwenden weiterhin den allgemeinen
`collection.timeout`. Der Filesystem-Timeout umfasst die interne Wartezeit, alle
Scans und die vollständige Snapshot-Publikation. Ein abgelaufener Lauf startet
keine weiteren wartenden Scans. Physisch bereits laufende
`Files.walkFileTree`-Aufrufe dürfen zu Ende laufen, können aber weder publizieren
noch einen neueren Snapshot überschreiben.

### `PhaseFiveRuntime`, `EventCollector` und `EventMetrics`

`PhaseFiveRuntime` registriert genau einen verwalteten Collector namens
`events`. Bei `collectors.events: false` bleibt dessen Zustand `disabled`; weder
Listener noch Phase-5-Metrikfamilien werden registriert. Bei Aktivierung legt
der Collector einmalig zehn Counter in der privaten Core-Registry an und
registriert genau einen Bukkit-Listener.

Der Listener beobachtet `PlayerLoginEvent`, `PlayerJoinEvent`,
`PlayerQuitEvent`, `PlayerKickEvent`, `ServerListPingEvent`, `AsyncChatEvent`,
`ChunkLoadEvent` und `ChunkUnloadEvent` bei `MONITOR`. `ServerLoadEvent` und
`WorldLoadEvent` initialisieren zusätzlich ausschließlich nullwertige
Chunkreihen für tatsächlich geladene Welten, damit die registrierten Familien
vor dem ersten Lifecycle-Ereignis im Textformat sichtbar sind. Kick und Chat
verwenden `ignoreCancelled = true`. Ein Read-/Write-Lock trennt Eventinkremente vom Stop:
Stop deaktiviert zuerst die Annahme, wartet auf bereits laufende kurze
Inkremente und meldet den Listener anschließend über `HandlerList.unregisterAll`
ab. Nach Rückkehr von Stop kann kein Counter mehr steigen; Mehrfachstart und
Mehrfachstop bleiben über den vorhandenen Collector-Lifecycle idempotent.

`EventReasonMapper` liest ausschließlich strukturierte Result-/Cause-Enumnamen
und gibt nur die neun katalogisierten Reasonwerte aus. Nachrichten, Identitäten,
Adressen, Hostnamen und Koordinaten gelangen nicht in den Mapper. Chunkfamilien
verwenden dieselbe `WorldLabel`-Validierung wie die Phase-4-Werttypen.

Prometheus-Counter sind threadsicher. Deshalb wechseln Login-, Ping-, Chat- und
Chunkereignisse nicht auf Global-, Region-, Entity- oder Async-Scheduler. Fehler
eines einzelnen Updates werden innerhalb des Eventbereichs abgefangen und ohne
Eventdaten rate-limitiert gemeldet; andere Ereignisbereiche und der HTTP-Dienst
laufen weiter.

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
Registry gebunden. Seit Phase 5 ist `EventCollector` ebenfalls ein
`ManagedCollector`, verwendet aber wegen seines fortlaufenden ereignisbasierten
Zustands kein Snapshot-Repository und keinen Scheduler. Server, Welten, Chunks
und Weltgrößen bleiben separate periodische Snapshot-Collector. Entity-, Folia-
und Gameplay-Collector bleiben späteren Phasen vorbehalten.

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

## 3.7 Phase-5-Lifecycle

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
  → Event-Collector beim Coordinator registrieren
  → CollectorCoordinator starten
    → bei aktiviertem Event-Collector Counter und einen Listener registrieren
  → HTTP-Server starten
  → Initialisierung vollständig / ready = 1

onDisable oder Startfehler
  ready = 0
  → HTTP-Server und Workerpool stoppen
  → gestartete Collector rückwärts stoppen
    → Eventannahme sperren und Listener abmelden
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
