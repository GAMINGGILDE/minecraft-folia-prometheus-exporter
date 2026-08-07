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

Event-Counter verwenden einen getrennten ereignisbasierten Pfad:

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

Auf Folia ergänzt der capability-geschützte Provider einen Snapshotpfad:

```text
öffentliche Global-/Entity-/Region-Scheduler
                      │
                      ▼
       neutrale Spawn-/Chunk-/Spieleranker
                      │
                      ▼
 öffentliche Ownership-Deduplizierung + getRegionTPS
                      │
                      ▼
       transaktionale RegionObservationRegistry
                      │
                      ▼
          ein immutable Folia-Snapshot
                      │
                      ▼
       private PrometheusRegistry / MetricsEndpoint
```

Der konkrete Provider wird auf Paper nicht geladen. Ein Scrape liest die
Observation-Liste genau einmal, filtert nur noch anhand der TTL und greift nicht
auf Server, Welten, Spieler, Chunks oder Scheduler zu.

Entitymetriken verwenden einen gemeinsamen hybriden Pfad:

```text
EntityAddToWorldEvent / EntityRemoveFromWorldEvent
                    +
Globaler Welt-/Chunkanker-Abgleich
                    │
          Region Scheduler je Chunk
                    │
          Entity Scheduler je Entity
                    │
                    ▼
  sequenziertes lauflokales Eventjournal
                    │
                    ▼
 atomarer vollständiger EntityWorldSnapshot
                    │
                    ▼
 private PrometheusRegistry / MetricsEndpoint
```

Das Eventjournal enthält eine Identität nur während eines Abgleichs. Publizierte
Snapshots enthalten ausschließlich Weltlabel, feste Gruppen, Aggregate und
optional kontrollierte Namespaced EntityType-Keys.

Die JVM- und Prozessinstrumentierungen bilden einen getrennten,
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
- fixiert zu Beginn von `onEnable()` den für Servermetriken verwendeten
  Server-Aktivierungszeitpunkt und erzeugt `MinecraftSnapshotRuntime`
- erzeugt `EventRuntime`, das den konfigurierten Event-Collector vor dem
  Core-Start beim vorhandenen Coordinator registriert
- erzeugt `FoliaRuntime`, das nur die neutrale Capability- und Factory-Grenze
  registriert; die konkrete Providerklasse wird erst beim Collectorstart nach
  erfolgreicher Capability geladen
- erzeugt `EntityRuntime`, das Entityfamilien, Listener, Vollabgleich und
  den gemeinsamen Zustandsstore registriert
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
erfolgt höchstens einmal vor dem ersten Start. `stop()` ist idempotent. Die
Collector `server`, `worlds`, `chunks`, `world-sizes` und `events` werden
unabhängig registriert. Deaktivierte Gruppen bleiben sichtbar im Zustand
`disabled`.

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

`PaperCollectionScheduler` ist die gemeinsame konkrete Implementierung. Die
periodischen Erfassungsstarts laufen über den Global Region Scheduler,
einzelne Spielmoduslesungen über den jeweiligen Entity Scheduler und
Dateisystemarbeit sowie Timeout-Wächter über den Async Scheduler. Der
Weltgrößen-Capture stellt nur bis zu
`filesystem.world-size-scan-concurrency` Async-Tasks gleichzeitig bereit; die
interne FIFO-Warteschlange ist nach Weltname sortiert und startet standardmäßig
genau einen Scan. Der Region
Scheduler gehört zur Abstraktion, wird von den Server-, Welt- und Chunk-
Snapshots aber nicht benötigt, weil die öffentliche API aggregierte Werte ohne
Positionszugriff anbietet.

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
- kann transaktionale Capture-Werte erst innerhalb seiner atomaren
  Erfolgsannahme materialisieren, sodass Stop oder Timeout keinen verspäteten
  Registry-Commit zulassen
- unterstützt über `SnapshotPublisher` eine fachliche transaktionale
  Publikation innerhalb derselben Erfolgsannahme; der Entity-Collector
  kombiniert dort Scanbasis und Events bis zur Commitgrenze
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

### `MinecraftSnapshotRuntime` und `MinecraftMetrics`

`MinecraftSnapshotRuntime` verdrahtet die vier Erfassungsgruppen mit ihren privaten
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

### `EventRuntime`, `EventCollector` und `EventMetrics`

`EventRuntime` registriert genau einen verwalteten Collector namens
`events`. Bei `collectors.events: false` bleibt dessen Zustand `disabled`; weder
Listener noch Event-Metrikfamilien werden registriert. Bei Aktivierung legt
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

`PlayerLoginEvent` ist trotz seiner Deprecation seit 1.21.6 bewusst die einzige
Loginquelle. Seine Verwendung ist auf die eine Handler-Methode begrenzt; sie
reicht nur den Namen des strukturierten `Result` an den API-unabhängigen
Metrikkern weiter. `AsyncPlayerPreLoginEvent` liegt vor der finalen Entscheidung,
`PlayerServerFullCheckEvent` deckt nur den Full-Check ab und das experimentelle
`PlayerConnectionValidateLoginEvent` kann beim ersten Login und nach der
Konfigurationsphase feuern, ohne einen strukturierten Cause anzubieten. Mehrere
Quellen werden nicht kombiniert und können daher keinen Versuch doppelt zählen.
Der Migrationspunkt ist eine stabile öffentliche Quelle, die genau eine finale
Auslieferung und strukturierte Ablehnungsgründe garantiert.

`EventReasonMapper` liest ausschließlich strukturierte Result-/Cause-Enumnamen
und gibt nur die neun katalogisierten Reasonwerte aus. Nachrichten, Identitäten,
Adressen, Hostnamen und Koordinaten gelangen nicht in den Mapper. Chunkfamilien
verwenden dieselbe `WorldLabel`-Validierung wie die übrigen Snapshot-Werttypen.

Für Kickursachen gilt insbesondere `TIMEOUT → connection_lost` und
`IDLING → idle`. `CONNECTION_LOST` und `NETWORK_ERROR` bleiben zusätzlich als
vorsorgliche strukturierte Namen auf `connection_lost` abgebildet. Die aktuelle
Loginquelle kann `banned`, `whitelist`, `server_full` und `unknown` erzeugen;
`invalid_session` bleibt mangels eindeutigem aktuellem Result nur reserviert.

Prometheus-Counter sind threadsicher. Deshalb wechseln Login-, Ping-, Chat- und
Chunkereignisse nicht auf Global-, Region-, Entity- oder Async-Scheduler. Fehler
eines einzelnen Updates werden innerhalb des Eventbereichs abgefangen und ohne
Eventdaten rate-limitiert als `IllegalStateException` gemeldet. Die ursprüngliche
`RuntimeException` bleibt deren Cause. Auch wenn der Fehlerbeobachter selbst
wirft, verlässt keine Exception den Eventthread; andere Ereignisbereiche und der
HTTP-Dienst laufen weiter.

### `EntityRuntime`, `EntityCollector` und `EntityStateStore`

`EntityRuntime` registriert den verwalteten Collector `entities`. Ist er
deaktiviert, fehlen Listener, Scheduleraufgaben und sämtliche Entityfamilien.
Bei Aktivierung werden genau ein Listener, ein periodischer Vollabgleich und die
private Entity-Metrikgruppe erzeugt.

Der Listener verwendet ausschließlich `EntityAddToWorldEvent`,
`EntityRemoveFromWorldEvent`, `WorldLoadEvent` und ein nicht abgebrochenes
`WorldUnloadEvent`. Add und Remove bilden die einzige symmetrische Entityquelle
und decken dadurch auch Chunk-Lifecycle, Weltwechsel und Transformation ab.

`BukkitEntityReconciliationCapture` liest die globale Welt-/Chunkankertopologie,
plant je Chunk eine Regionaufgabe und wertet jede gefundene Entity anschließend
auf ihrem Entity Scheduler aus. `Chunk#getEntities()` wird nur für bereits
geladene Entitydaten verwendet. Ein erfolgreicher Initiallauf erfasst deshalb
bereits vorhandene Entities, ohne entladene Chunks oder Entitydaten zu laden.

`EntityStateStore` hält genau einen aktiven Run mit monotoner Eventsequenz.
Entitybeobachtungen tragen die Sequenz nach ihrer Auswertung. Der Commit spielt
nur spätere Events je lauflokaler Identität wieder und publiziert danach einen
vollständigen immutable Snapshot. Eventupdates nach dem Commit bauen atomar auf
diesem neuen Stand auf. Timeout, Stop und alte Callback-Ergebnisse können diese
Commitgrenze nicht überschreiten.

Die lauflokale Deduplizierung verwendet kurzzeitig UUIDs, speichert sie aber
weder in Snapshot noch Registry oder Log. Der Snapshot enthält für jede Welt
alle zehn Gruppen, Gesamt-, Living-, Villager-, Item- und Projectile-Zahl sowie
optional exakte Typen. Prometheus liest pro Scrape genau einen Repositorywert.

Der Scan weist jeder bekannten Welt explizit `SUCCESS`, `PARTIAL` oder
`UNAVAILABLE` zu. Nur `SUCCESS` ersetzt den Weltstand. Die beiden konservativen
Fehlerzustände behalten einen vorhandenen gültigen Weltstand oder erzeugen ohne
einen solchen überhaupt keine Weltreihe. Ein Welt-Load-Event und Entityevents
können deshalb keinen fehlenden Baseline-Snapshot durch künstliche Null- oder
Teilwerte ersetzen. Existieren geladene Welten, aber keine einzige
`SUCCESS`-Welt, schlägt der gesamte Lauf fehl; nur eine tatsächlich leere
Weltenliste publiziert erfolgreich den leeren Snapshot.

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

- arbeitet threadsicher und transaktional mit genau einem aktiven Lauf
- akzeptiert Updates nur für die aktuelle monotone Laufidentität
- verwirft ältere, verspätete und nach Stop eintreffende Ergebnisse
- ersetzt nach Erfolg die vollständige Beobachtungsmenge und behält nach Fehler
  den letzten vollständigen Stand
- ersetzt den vorherigen Stand auch bei einem erfolgreichen Lauf ohne gültige
  Beobachtung ausdrücklich durch eine leere immutable Liste
- entfernt ehemalige und abgelaufene Beobachtungen ohne wachsenden Labelcache
- speichert nur Weltlabel, internen neutralen Chunkanker, Zeitpunkt, TPS-Fenster
  und aggregierte Spielerzahl; keine Minecraft-Liveobjekte oder Identitäten

### Folia-spezifischer Provider

- ist implementiert
- ist vom allgemeinen, gegen `paper-api` kompilierten Code isoliert
- liegt in einem getrennten Gradle-Source-Set unter `src/folia/java`
- kompiliert gegen `dev.folia:folia-api:26.1.2.build.8-stable` als `compileOnly`
- verwendet ausschließlich öffentliche Folia-APIs
- aktiviert sich ausschließlich anhand von
  `Server#getRegionTPS(World,int,int)` samt Rückgabetyp `double[]`
- wird auf Paper vor der Capability-Prüfung weder referenziert noch geladen
- verwendet weder Servername noch Versionsstring oder Scheduler-Verfügbarkeit als
  Plattformmerkmal
- verwendet insbesondere nicht die interne Klasse
  `io.papermc.paper.threadedregions.RegionizedServer`
- beobachtet nur Regionen mit öffentlichen Spieler-, Spawn- oder optionalen
  Force-Load-Ankern und behauptet keine vollständige aktive Regionszahl
- dedupliziert Anker auf dem Region-Thread mit
  `Server#isOwnedByCurrentRegion(World,int,int)`
- registriert sechs Folia-Familien gemeinsam in der privaten Registry; ohne
  gültige Beobachtung fehlen dynamische Samples

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
```

`JvmCollector` und `ProcessCollector` sind logische Metrikgruppen, keine
`ManagedCollector`: Sie werden durch `JvmMetricsRegistrar` direkt an die
Registry gebunden. `EventCollector` ist ein `ManagedCollector`, verwendet aber
wegen seines fortlaufenden ereignisbasierten Zustands kein Snapshot-Repository
und keinen Scheduler. Server, Welten, Chunks und Weltgrößen bleiben separate
periodische Snapshot-Collector. `FoliaRegionCollector` ist ein capability-
geschützter `ManagedCollector`, dessen Provider intern den vorhandenen
periodischen Snapshot-Collector wiederverwendet. `EntityCollector` ist ein
hybrider `ManagedCollector` mit genau einem Listener und einem intern
wiederverwendeten periodischen Collector.

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
- Ein Fehler eines Folia-Spielerankers oder einer einzelnen Regionsbeobachtung
  überspringt nur diese Observation. Die übrigen Aufgaben laufen weiter und ein
  erfolgreicher Teil- oder Leersnapshot ersetzt den vorherigen Regionssnapshot.
- Nur systemische Folia-Lauffehler, Timeout und Stop verwerfen den gesamten Lauf
  und erhalten den letzten gültigen Snapshot bis zur TTL.
- Experimentelle oder interne Provider sind kein Bestandteil von Version 1.
- Ein lokaler Entity- oder Chunkfehler macht die betroffene Welt `PARTIAL`; ein
  Fehler ihrer Chunk-Enumeration macht sie `UNAVAILABLE`. Ein vorheriger gültiger
  Weltwert bleibt erhalten, andernfalls fehlt die Reihe. Andere erfolgreiche
  Welten desselben Laufs werden publiziert. Kann keine existierende Welt
  belastbar erfasst werden, behalten globale Fehler, Timeout und Stop den
  gesamten letzten Snapshot.

## 3.6 Abhängigkeitsisolation

Der Prometheus Java Client wird über seine BOM in Version 1.8.0
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

Getrennte `folia`- und `foliaTest`-Source-Sets halten die APIs isoliert: Der
allgemeine Compile-Classpath enthält keine Folia-API; der Provider-Classpath
enthält die Folia-API ausschließlich zum Kompilieren. `check` prüft diese Trennung, den
Ausschluss der Folia-API aus der Runtime, das Vorhandensein des Providers im
gemeinsamen JAR, verbotene interne Bytecodereferenzen und das Fehlen einer
statischen Providerreferenz im gemeinsamen Bootstrap.

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

## 3.7 Plugin-Lifecycle

```text
onEnable
  Aktivierungszeitpunkt fixieren
  Konfiguration validieren
  → Buildinformation laden
  → eigene PrometheusRegistry erzeugen
  → konfigurierte offizielle JVM-/Prozessinstrumentierungen einmalig registrieren
  → Eigenmetriken einmalig registrieren
  → Server-, Welt-, Chunk- und Weltgrößenfamilien registrieren
  → zugehörige Snapshot-Collector beim Coordinator registrieren
  → Event-Collector beim Coordinator registrieren
  → neutralen Folia-Collector beim Coordinator registrieren
  → Entity-Collector beim Coordinator registrieren
  → CollectorCoordinator starten
    → bei aktiviertem Event-Collector Counter und einen Listener registrieren
    → Folia-Capability prüfen
      → Paper: einmal warnen, unsupported, keine Providerklasse/Familie laden
      → Folia: eigene Providerklasse laden, Familien registrieren, Capture starten
    → bei aktiviertem Entity-Collector Familien und Listener registrieren
      → initialen verteilten Entity-Abgleich starten
  → HTTP-Server starten
  → Initialisierung vollständig / ready = 1

onDisable oder Startfehler
  ready = 0
  → HTTP-Server und Workerpool stoppen
  → gestartete Collector rückwärts stoppen
    → Eventannahme sperren und Listener abmelden
    → Folia-Capture invalidieren und keine Registryupdates mehr annehmen
    → Entity-Capture invalidieren, Eventannahme sperren und Listener abmelden
  → alle verbliebenen Snapshot- und Folia-Schedulertasks abbrechen
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
