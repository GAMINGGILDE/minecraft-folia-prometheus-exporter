# 4. Paper-/Folia-Threading und Scheduler-Regeln

Die Regeln gelten für beide offiziell unterstützten Plattformen. Das Plugin nutzt
auf Paper und Folia ausschließlich Global-, Region-, Entity- und Async-Scheduler.
Es gibt keinen Fallback auf den klassischen `BukkitScheduler`.

Diese vier Scheduler sind Teil der gemeinsamen öffentlichen Paper-API und werden
nicht als Folia-Erkennungsmerkmal verwendet. `PaperCollectionScheduler` setzt
diese Grenze für die gemeinsamen Collector um und benötigt dafür weder einen
Folia-Provider noch Plattform- oder Feature-Erkennung.

Event-Counter benötigen keinen Scheduler: Öffentliche Events werden
auf ihrem jeweiligen Paper-/Folia-Eventthread beobachtet und erhöhen dort nur
threadsichere Counter. Der Collector verschiebt insbesondere Async-Login,
Async-Chat, Ping- oder regionsgebundene Chunkereignisse nicht künstlich auf einen
anderen Scheduler.

Folia-Regionsmessungen verwenden den Region Scheduler für positionsgebundene
Zugriffe. Die Folia-Capability ist ausschließlich
`Server#getRegionTPS(World,int,int)`; die gemeinsamen Scheduler selbst bleiben
ausdrücklich kein Erkennungsmerkmal.

Der Entity-Abgleich verwendet dieselben gemeinsamen Scheduler ohne eigenen
Provider. Der Global Region Scheduler liest nur Welten und geladene Chunkanker. Entitylisten
bereits geladener Chunks werden auf dem zuständigen Region Scheduler
materialisiert; jede Entityeigenschaft wird anschließend auf dem Entity
Scheduler gelesen.

## 4.1 Scheduler-Zuordnung

| Aufgabe | Scheduler |
|---|---|
| Globale Serverzustände | Global Region Scheduler |
| Position-/Chunkgebundene Daten | Region Scheduler |
| Entitygebundene Daten | Entity Scheduler |
| JVM-MBeans | Async oder HTTP-unabhängig |
| Dateisystemoperationen | Async Scheduler |
| HTTP-Ausgabe | eigener HTTP-Thread, nur Snapshots |

Für globale Server-, Welt- und Chunk-Snapshots gilt konkret:

| Öffentlicher API-Zugriff | Ausführung |
|---|---|
| Servername, Minecraft-Version, Online-Mode, Hardcore, Distanzen | Global Region Scheduler |
| Online-/Maximal-/bekannte Spieler, Whitelist, Bans und Operatoren | Global Region Scheduler |
| Pluginliste, Plugin-Metadaten und Aktivierungszustand | Global Region Scheduler |
| Liste geladener Welten und deren Name, Spielerzahl, Zeit, Wetter, Schwierigkeit, Umgebung, World Border und PVP | Global Region Scheduler |
| `World#getChunkCount()` | Global Region Scheduler |
| `World#getWorldPath()` | Global Region Scheduler; nur der immutable Pfad wird weitergereicht |
| `Player#getGameMode()` | Entity Scheduler des jeweiligen Spielers |
| Rekursives Lesen der Weltverzeichnisse | Async Scheduler |
| Timeout-Wächter | Async Scheduler |
| Prometheus-Scrape | HTTP-Thread; nur Repositories und Registry |

Für Event-Counter gilt zusätzlich:

| Event/API-Wert | Ausführung |
|---|---|
| `PlayerLoginEvent#getResult()` | ausliefernder Login-Eventthread; nur Enumname lesen; einzige Loginquelle trotz eng begrenzter Deprecation |
| Join und Quit | ausliefernder Eventthread; nur Counter erhöhen |
| `PlayerKickEvent#getCause()` | ausliefernder Entity-/Region-Eventthread; nur Enum lesen |
| `ServerListPingEvent` | Ping-Eventthread; keine Adress-, Host- oder Antwortdaten lesen |
| `AsyncChatEvent` | synchron oder asynchron wie von Paper ausgeliefert; Inhalt nicht lesen |
| `ChunkLoadEvent`/`ChunkUnloadEvent` | besitzender Region-/Tickthread; nur Weltname und `isNewChunk()` lesen |
| `ServerLoadEvent`/`WorldLoadEvent` | globaler beziehungsweise besitzender Eventthread; nur geladene Weltnamen für Nullinitialisierung übernehmen |
| Prometheus-Scrape | HTTP-Thread; nur bereits akkumulierten Counterzustand serialisieren |

Für Folia-Regionsbeobachtungen gilt zusätzlich:

| Öffentlicher API-Zugriff | Ausführung |
|---|---|
| Liste geladener Welten, Weltspawn, force-loaded Chunks und Online-Spieler-Sicht | Global Region Scheduler; Liveobjekte nur im laufenden Capture |
| `Player#getLocation()` | Entity Scheduler des jeweiligen Spielers; sofortige Reduktion auf Weltlabel und Chunkanker |
| `isOwnedByCurrentRegion(World,int,int)` | Region Scheduler des aktuellen Beobachtungsankers |
| `getRegionTPS(World,int,int)` | derselbe besitzende Region Scheduler |
| Timeout-Wächter | Async Scheduler |
| Prometheus-Scrape | HTTP-Thread; genau ein immutable Folia-Snapshot und eine Zeitablesung |

Für den Entity-Abgleich gilt zusätzlich:

| Öffentlicher API-Zugriff | Ausführung |
|---|---|
| `Server#getWorlds()` und `World#getLoadedChunks()` | Global Region Scheduler; nur laufzeitkurze Welt-/Chunkanker |
| `Chunk#isLoaded()`, `isEntitiesLoaded()` und `getEntities()` | Region Scheduler des Chunks; `getEntities()` nur bei bereits geladenen Entitydaten |
| `Entity#getWorld()`, `getType()` und lauflokale Identität | Entity Scheduler der Entity |
| `EntityAddToWorldEvent`/`EntityRemoveFromWorldEvent` | ausliefernder besitzender Eventthread; sofortige Reduktion auf immutable Werte |
| `WorldLoadEvent`/nicht abgebrochenes `WorldUnloadEvent` | ausliefernder Eventthread; ausschließlich Weltlabel und Aggregate |
| Timeout-Wächter | Async Scheduler |
| Prometheus-Scrape | HTTP-Thread; genau ein immutable Entity-Snapshot |

### Nachweis für `World#getLoadedChunks()` in den gepinnten Builds

Die erneute Prüfung umfasst die aufgelösten API-Source-/Binärartefakte
`paper-api:26.1.2.build.74-stable` und
`folia-api:26.1.2.build.8-stable` sowie die zu Folia Build 8 gehörende
Serverquellenkette. Folia-Commit
`62dc0f257a4f5de1ef2eae8cf1627156a769c67f` pinnt Paper-Commit
`b4682bfef616ac62e73cc96046dacdf4a6f53eeb`.

Die gemeinsame `World`-API dokumentiert nur die Rückgabe aller geladenen
`Chunk`-Handles und trägt keine Threadingannotation. Der belastbare Nachweis
stammt deshalb aus der gepinnten Implementierung: `CraftWorld#getLoadedChunks()`
liest `ServerChunkCache.fullChunks`, eine
`ConcurrentChainedLong2ReferenceHashTable`, über deren concurrent Key-Iterator.
Für jeden Key wird lediglich ein `CraftChunk` aus Weltreferenz und X/Z erzeugt;
es wird weder ein NMS-Chunkwert gelesen noch ein Chunk oder dessen Entitydaten
geladen. Die Folia-Patches ändern diese Methode nicht und fügen dort im Gegensatz
zu tatsächlich regions- oder globalgebundenen Nachbarmethoden keinen
`TickThread.ensureTickThread`- beziehungsweise
`ensureGlobalTickThread`-Check ein. Folia hält dieselbe concurrent Full-Chunk-
Tabelle während der Chunkstatusübergänge aktuell.

Dieser Quellenbefund wurde zusätzlich am tatsächlich gestarteten Paper-26.1.2-
Build-74- und Folia-26.1.2-Build-8-Serverartefakt verifiziert: Der normalisierte
Bytecode beider `getLoadedChunks()`-Methoden ist identisch, verwendet den
concurrent `fullChunks`-Schlüsseliterator und enthält keinen Threadcheck.

Damit ist der kurze Aufruf auf dem Global Region Scheduler für genau die
gepinnten Builds zulässig. Er materialisiert regionsübergreifend ausschließlich
öffentliche Chunk-Handles; `isLoaded`, `isEntitiesLoaded` und `getEntities`
bleiben auf dem Region Scheduler jedes Handles. Der echte Folia-Smoke-Test
erzeugt eine kurzlebige Area-Effect-Cloud, beobachtet ihre natürliche Entfernung
und prüft das vollständige Log unter anderem auf `Cannot getLoadedChunks asynchronously`,
`Cannot getEntities asynchronously`, `TickThread` und Ownershipverletzungen.
Ein zusätzlicher Lifecycle-Chunkindex ist deshalb nicht erforderlich. Diese
Entscheidung muss bei einer Änderung der gepinnten Serverlinie erneut geprüft
werden.

Die Chunk-Entityliste wird nicht auf dem Global Region Scheduler gelesen.
`World#getEntities()` und `World#getLivingEntities()` sind im Entity-Collector
verboten. Ein Regiontask wertet auch nicht die Eigenschaften beweglicher
Entities aus, sondern plant dafür deren mitwandernden Entity Scheduler.

Alle Eventhandler sind kurz und nicht blockierend. Sie führen keine Datei- oder
Netzwerkoperation aus, speichern kein Event- oder Minecraft-Objekt und lesen
weder Spieleridentität noch Chunkkoordinaten. Die gemeinsame
`WorldLabel`-Validierung übernimmt ausschließlich den während des Events
gelesenen Weltname-String. Die deprecated Loginquelle wird nicht mit
`AsyncPlayerPreLoginEvent`, `PlayerConnectionValidateLoginEvent` oder
`PlayerServerFullCheckEvent` kombiniert; deshalb entstehen weder zusätzliche
Loginphasen noch ein Bedarf an identitätsbasierter Deduplizierung.

Ein `RuntimeException` aus Validierung oder Counterupdate wird noch unter der
kurzen Event-Annahmegrenze abgefangen und als Cause einer neutralen
`IllegalStateException` an den rate-limitierten Reporter weitergereicht. Wirft
dieser Beobachter selbst, wird seine Exception ebenfalls abgefangen. Weder der
ursprüngliche Fehler noch ein Reporterfehler kann dadurch den ausliefernden
Paper-/Folia-Eventthread beschädigen.

Die Server-, Welt- und Chunk-Snapshots brauchen keinen Region-Scheduler-Aufruf:
Der öffentliche aggregierte
Chunkzähler vermeidet Positions- und Chunkobjektzugriffe. Das Interface behält
die Region-Methode für spätere, tatsächlich positionsgebundene Collector.

Der Weltgrößen-Capture plant Dateisystemscans in deterministischer Reihenfolge
nach Weltname und hält höchstens
`filesystem.world-size-scan-concurrency` Async-Scans gleichzeitig aktiv. Der
Standard `1` verarbeitet Weltverzeichnisse sequenziell und minimiert konkurrierende
I/O-Last. Ein höherer Wert bis `8` erhöht ausschließlich die Dateisystemparallelität;
Minecraft-Livezugriffe bleiben im globalen Scheduler.

## 4.2 Verbotene Muster

Nicht verwenden:

```java
Bukkit.getScheduler().runTask(plugin, task);
```

Keine Minecraft-API-Aufrufe aus:

```java
CompletableFuture.runAsync(...)
```

wenn Regions- oder Entity-Ownership erforderlich ist.

Keine Live-Scans vom HTTP-Thread:

```java
world.getEntities();
world.getLoadedChunks();
player.getLocation();
```

## 4.3 Scheduler-Abstraktion

Implementiertes Interface, gekürzt um Rückgabetypdetails:

```java
public interface CollectionScheduler {
    CollectionTask scheduleGlobalAtFixedRate(Duration interval, Runnable task);

    CollectionTask executeAt(
        org.bukkit.World world,
        int chunkX,
        int chunkZ,
        Runnable task
    );

    Optional<CollectionTask> executeFor(
        org.bukkit.entity.Entity entity,
        Runnable task,
        Runnable retired
    );

    CollectionTask executeAsync(Runnable task);
    CollectionTask executeAsyncAfter(Duration delay, Runnable task);
    void cancelAll();
}
```

Die Abstraktion enthält keine klassische Scheduler-Alternative. Globale
Intervalle werden auf mindestens einen Tick aufgerundet; ungültige Intervalle
werden bereits durch die Konfigurationsvalidierung abgelehnt. Beim Disable
bricht `cancelAll()` alle durch das Plugin geplanten Aufgaben ab.

Die spätere Capability-Prüfung des isolierten Folia-Metrikproviders bezieht sich
ausschließlich auf die konkrete öffentliche Folia-API, die seine Messung benötigt.
Sie prüft weder Servername noch Versionsstring und lädt die Providerklasse auf
Paper nicht vorzeitig.

## 4.4 Snapshot-Regel

Jeder Scheduler-Task erzeugt nur lokale Werte. Erst nach Abschluss aller nicht
abgebrochenen Observationen eines erfolgreichen Laufs wird ein unveränderlicher
Snapshot veröffentlicht. Lokal fehlerhafte Spieler- oder Regionsobservationen
fehlen darin; ihre erfolgreichen Nachbarn bleiben enthalten.

Unvollständige Zwischenstände dürfen nicht sichtbar werden. Pro Collector ist
nur ein Lauf aktiv. Ein Timeout entfernt diesen Lauf atomar; ein später Callback
kann wegen der abweichenden Laufidentität nicht mehr publizieren. Nach `stop()`
werden überhaupt keine Ergebnisse mehr angenommen.

Der Entity-Abgleich ergänzt innerhalb derselben Erfolgsannahme ein sequenziertes Eventjournal.
Die Scanbasis trägt je lauflokaler Identität die zuletzt beobachtete Eventsequenz;
beim Commit werden nur spätere Events angewendet. Der gemeinsame Store sperrt
Eventupdate und Reconciliation-Publikation kurz gegeneinander. Region- und
Entitythreads warten dabei niemals auf Minecraft-Schedulerthreads, sondern nur
auf den kurzen plugininternen Aggregationslock.

Pro Welt gilt zusätzlich ein expliziter Zuverlässigkeitsstatus. Nur `SUCCESS`
publiziert einen neu aggregierten Stand. Chunk- oder Entityfehler führen zu
`PARTIAL`, eine nicht lesbare Chunkankerliste zu `UNAVAILABLE`; beide Zustände
behalten einen vorhandenen gültigen Weltstand oder lassen die Welt ohne
vorherigen Stand vollständig fehlen. Wenn bei existierenden Welten keine einzige
Welt `SUCCESS` erreicht, wird der Lauf systemisch verworfen. Eine leere
Weltenliste darf dagegen erfolgreich den leeren Snapshot publizieren.

Entity-Fehlerwrapper verwenden neutrale äußere Meldungen und die ursprüngliche
Exception als Cause. Reporterfehler werden an Event-, Region- und Entitygrenzen
abgefangen. UUIDs, Namen, Koordinaten und freie Eventdaten werden nicht in die
äußere Meldung übernommen.

Für `world-sizes` signalisiert der Collector Timeout und Stop zusätzlich an die
interne Scan-Warteschlange. Sie verwirft alle noch nicht laufenden Arbeiten und
startet für den ungültigen Lauf nichts mehr. Ein bereits in
`Files.walkFileTree` befindlicher Java-Dateisystemaufruf ist nicht garantiert
hart unterbrechbar. Sein Slot und sein Pfad bleiben deshalb bis zur tatsächlichen
Rückkehr belegt; danach werden sie zuverlässig freigegeben und das verspätete
Ergebnis verworfen. Weltpfade verlassen diese Schedulergrenze nicht und werden
insbesondere nicht als Metriklabels exportiert.

Der Metrics Core setzt diese Grenze mit `ImmutableSnapshot<T>` und
`SnapshotRepository<T>` um. Der Snapshot kopiert seine Werteliste defensiv und
trägt einen `Instant` als Erfassungszeitpunkt. Das Repository tauscht ausschließlich
vollständig konstruierte Instanzen über eine `AtomicReference` aus. Der enthaltene
Werttyp muss selbst immutable sein und darf keine veränderlichen Bukkit-, Paper-,
Folia- oder Minecraft-Objekte enthalten.

## 4.5 HTTP-Grenze

Der benannte HTTP-Workerpool arbeitet unabhängig von Minecraft-Tickthreads. Seine
Handler dürfen ausschließlich:

1. die Prometheus-Registry serialisieren,
2. atomar veröffentlichte immutable Snapshots lesen,
3. den atomaren Health-/Readiness-Zustand lesen und
4. HTTP-Antworten und niedrig-kardinale Eigenmetriken aktualisieren.

Im Metrics Core existiert keine Referenz von einem HTTP-Handler auf Bukkit-,
Paper-, Folia- oder Minecraft-Objekte. Parallele Scrapes werden vom offiziellen
Prometheus-Handler und threadsicheren Client-Metriken verarbeitet. Direkte
Callbacks der JVM-/Prozessinstrumentierung sind zulässig, weil sie ausschließlich
JDK- und Betriebssystemdaten lesen und keine Ownership-Regel von Paper oder Folia
berühren.

Die Server-, Welt- und Chunk-Callbacks lesen ebenfalls keine Minecraft-Objekte. Sie
wandeln den jeweils einmal geladenen immutable Snapshot einer Gruppe in
Prometheus-Snapshots um. Insbesondere lösen parallele Scrapes keine zusätzliche
Minecraft-Erfassung oder Weltgrößenberechnung aus.

Die Event-Counter sind ebenfalls scrape-sicher: Ihre Datenpunkte werden von
den threadsicheren Counterimplementierungen des Prometheus Java Client
akkumuliert. Der HTTP-Thread besitzt keine Referenz auf Listener, Events,
Connections, Spieler, Welten oder Chunks und löst keine Eventarbeit aus.

Der Folia-Callback liest den Regionssnapshot genau einmal. TTL-Filterung,
Aggregation und Snapshot-Alter verwenden nur dessen primitive beziehungsweise
immutable Werte. Weder Capability-Prüfung noch Provider, Scheduler oder
Minecraft-Liveobjekte sind vom HTTP-Thread aus erreichbar.

Der Entity-Callback liest ebenfalls genau einen immutable Repositorywert. Die
zehn Gruppen und optionalen Typreihen werden ausschließlich aus Strings, Enums,
Zählern und unveränderlichen Maps erzeugt. Weder Eventjournal noch Scheduler,
UUID, World, Chunk oder Entity sind vom HTTP-Thread erreichbar.

## 4.6 Regionsbeobachtung

Spielerpositionen werden ausschließlich auf dem jeweiligen Entity Scheduler
gelesen. Player und `Location` bleiben temporär im Capture; danach existieren nur
Weltlabel, Chunk-X/Z und ein aggregierter Zähler. Weltspawn und optional
force-loaded Chunks werden auf dem Global Region Scheduler ebenfalls zu solchen
Ankern reduziert. Die Registry hält weder Player, World, Entity, Chunk, Location
noch Scheduler-Handle.

Jeder neutrale Anker wird auf dem Region Scheduler ausgeführt. Dort prüft
`Server#isOwnedByCurrentRegion(World,int,int)`, welche lexikografisch früheren
Anker derselben Region gehören. Nur der erste Anker fragt TPS ab; damit entsteht
keine Reihe pro Spieler oder Chunk und keine interne Regions-ID. Spielerzahlen
werden auf demselben Thread als Summe der aktuell derselben Region gehörenden
Spieleranker gebildet.

Es gibt keine öffentliche vollständige Regionsauflistung und keine Create-,
Split-, Merge- oder Destroy-Events. Die Ankerliste und Ownership werden deshalb
in jedem Lauf neu aufgebaut. Fehler eines Spielerankers oder einer Region werden
genau einmal abgeschlossen, neutral rate-limitiert gemeldet und übersprungen;
andere Scheduler-Tasks werden dafür nicht storniert. Erfolgreiche Läufe ersetzen
die gesamte Registry durch den Teilstand der gültigen Observationen oder durch
eine leere Liste. Nur systemische Fehler, Timeout und Stop behalten den letzten
vollständigen Snapshot. Die Erfolgsannahme koppelt Registry-Commit und
Snapshot-Publikation gegen konkurrierenden Timeout beziehungsweise Stop. Die TTL
entfernt alte Messpunkte auch bei anhaltenden systemischen Fehlern.
Region-Threads warten niemals blockierend auf andere Callbacks.
