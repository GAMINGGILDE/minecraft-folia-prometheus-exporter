# ADR 0016: Hybride Entity-Erfassung

## Status

Angenommen und umgesetzt.

## Untersuchte öffentliche API

Untersucht wurden die tatsächlich aufgelösten Source- und Binärartefakte
`io.papermc.paper:paper-api:26.1.2.build.74-stable` und
`dev.folia:folia-api:26.1.2.build.8-stable`. Die verwendeten `World`-, `Chunk`-,
`EntityType`- und Eventquellen sind in beiden gepinnten Source-Artefakten
identisch.

`World#getEntityCount()` liefert nur eine unaufgeschlüsselte Gesamtzahl. Damit
lassen sich Spieler nicht belastbar ausschließen und weder Living-Entities noch
Gruppen oder genaue Typen bestimmen. Die Methode wird deshalb nicht als
Entity-Datenquelle verwendet. `World#getEntities()`, `getLivingEntities()` und
die `getEntitiesByClass(es)`-Varianten materialisieren weltweite Entitylisten.
Solche globalen Entityzugriffe verletzen auf Folia die Ownership-Grenze und
werden verworfen.

Der Vollabgleich verwendet stattdessen:

- `Server#getWorlds()` und `World#getLoadedChunks()` nur für die kurzlebige
  globale Topologie eines Laufs,
- `Chunk#isLoaded()` und `Chunk#isEntitiesLoaded()` auf dem zuständigen Region
  Scheduler,
- `Chunk#getEntities()` nur dann und auf demselben Regionthread, wenn die
  Entitydaten bereits geladen sind,
- anschließend den Entity Scheduler jeder gefundenen Entity für `getWorld()`,
  `getType()` und die lauflokale Identität,
- `EntityType#getEntityClass()` und öffentliche Bukkit-Interfaces für die
  zentrale Klassifizierung,
- `EntityType#getKey()` für den optionalen genauen Typ.

`Chunk#getEntities()` würde laut API noch nicht geladene Entitydaten erzwingen.
Der Abgleich prüft deshalb zuerst `isEntitiesLoaded()` und lädt weder Chunks noch
Entitydaten eigens für Metriken.

### Erneute Folia-Prüfung von `World#getLoadedChunks()`

Zur Absicherung der Threadingentscheidung wurden nicht nur die API-Signaturen,
sondern die exakt gepinnten Serverquellen untersucht. Folia 26.1.2 Build 8
entspricht dem
Folia-Commit `62dc0f257a4f5de1ef2eae8cf1627156a769c67f`; dessen
`gradle.properties` pinnt Paper-Commit
`b4682bfef616ac62e73cc96046dacdf4a6f53eeb`.
Die überprüfbaren Primärquellen sind die
[Folia-Pinnung](https://github.com/PaperMC/Folia/blob/62dc0f257a4f5de1ef2eae8cf1627156a769c67f/gradle.properties)
und die gepinnte
[CraftWorld-Implementierung](https://github.com/PaperMC/Paper/blob/b4682bfef616ac62e73cc96046dacdf4a6f53eeb/paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java#L435-L450).

Zusätzlich wurde `CraftWorld#getLoadedChunks()` direkt aus den im Smoke-Test
gestarteten Serverartefakten Paper 26.1.2 Build 74 (`e4e17fc`) und Folia 26.1.2
Build 8 (`62dc0f2`) disassembliert. Nach Normalisierung der konstanten
Poolindizes ist der Bytecode beider Methoden identisch. Beide lesen denselben
concurrent `fullChunks`-Schlüsseliterator, erzeugen `CraftChunk`-Handles und
enthalten weder einen TickThread- noch einen Ownershipcheck.

Die gemeinsame API versieht `World#getLoadedChunks()` mit keiner besonderen
Threadingannotation. Die gepinnte `CraftWorld`-Implementierung liefert den
fehlenden konkreten Vertrag: Sie liest die Schlüssel von
`ServerChunkCache.fullChunks`, einer
`ConcurrentChainedLong2ReferenceHashTable`, über deren concurrent Iterator und
erzeugt je Schlüssel nur einen `CraftChunk`-Handle aus Welt und Koordinaten. Sie
liest keine Chunk- oder Entitydaten und löst keinen Chunkload aus. Die zu Build 8
gehörenden Folia-Patches lassen diese Methode unverändert und fügen dort keinen
`TickThread.ensureTickThread`- oder `ensureGlobalTickThread`-Check ein. Im selben
Patchsatz erhalten tatsächlich gebundene Nachbarmethoden ausdrücklich solche
Checks; die Full-Chunk-Tabelle bleibt durch die Moonrise-Chunkstatusübergänge
concurrent aktuell.

Der Aufruf ist damit für die beiden gepinnten Builds vom Global Region Scheduler
aus belastbar zulässig. Er materialisiert zwar regionsübergreifend öffentliche
`CraftChunk`-Handles, aber keine regionsgebundenen Daten. Jeder nachfolgende
Zugriff auf `isLoaded`, `isEntitiesLoaded` und `getEntities` wird weiterhin an
den Region Scheduler des Handles delegiert. Eine öffentliche Alternative mit
derselben Vollständigkeit existiert nicht: Chunk-Load/-Unload-Events könnten
einen threadsicheren Index ab Listenerregistrierung pflegen, erfassen bei Reload
aber bereits geladene Chunks nicht vollständig; Spawn-, Spieler- und Force-Load-
Anker wären nur eine dokumentiert unvollständige Teilmenge. Deshalb wird kein
paralleler Chunkindex eingeführt.

Diese Entscheidung gilt ausdrücklich für die gepinnte Linie und muss bei deren
Änderung erneut gegen Implementierung und echten Folia-Smoke-Test geprüft werden.

## Verbindliche Eventstrategie

Zwischen Abgleichen werden ausschließlich folgende Zustandsquellen verwendet:

- `EntityAddToWorldEvent`: jede Aufnahme in eine Welt, ausdrücklich auch durch
  Chunk-Load,
- `EntityRemoveFromWorldEvent`: jede Entfernung aus einer Welt, ausdrücklich
  auch durch Chunk-Unload; `getWorld()` bezeichnet zuverlässig die alte Welt,
- `WorldLoadEvent`: markiert den Weltlebenszyklus, erzeugt für sich allein aber
  keinen gültigen Null- oder Teilsnapshot,
- nicht abgebrochenes `WorldUnloadEvent`: entfernt die komplette Welt sofort.

Die beiden Entityevents sind nicht abbrechbar und bilden eine symmetrische
einzige Quelle. Damit werden Spawn, Tod, Despawn, Pickup, Plugin-Entfernung,
Transformation, Weltwechsel sowie Chunk-Load und -Unload ohne parallele
Speziallistener abgedeckt. Ein Weltwechsel besteht aus Remove der Quellwelt und
Add der Zielwelt; eine Transformation aus Remove des alten und Add der neuen
Entity beziehungsweise Entities.

Entityevents aktualisieren nur eine Welt, für die bereits eine gültige Baseline
existiert. Für eine bisher nicht erfolgreich erfasste Welt werden sie im aktiven
Journal weiter sequenziert, dürfen außerhalb eines erfolgreichen Vollabgleichs
aber keinen scheinbar vollständigen Stand aus null plus einzelnen Events
erfinden.

`EntitySpawnEvent`, `CreatureSpawnEvent`, `ItemSpawnEvent`,
`EntityDeathEvent`, `ItemDespawnEvent`, `EntityTransformEvent`,
`EntityTeleportEvent`, `EntitiesLoadEvent`, `EntitiesUnloadEvent` sowie Chunk-
Events werden nicht zusätzlich beobachtet. Mehrere dieser Events liegen vor der
endgültigen Änderung, sind abbrechbar oder würden denselben Zustandswechsel
doppelt zählen.

`EntityRemoveEvent` bietet öffentliche strukturierte Removal-Causes, wird aber
ebenfalls nicht kombiniert. Seine Dokumentation weist auf eine andere
Auslieferungszeit als `EntityRemoveFromWorldEvent` hin. Der zusätzliche Cause
wird für die Entity-Bestandsgauges nicht benötigt und eine zweite Removalquelle würde
Deduplizierung oder Drift erzeugen. Öffentliche Spawn-, Removal-, Kill- und
Item-Despawn-Counter bleiben ausdrücklich unimplementiert.

## Klassifizierung

Spieler werden vor jeder Aggregation über `EntityType.PLAYER` ausgeschlossen.
Jede andere Entity erhält genau eine Gruppe. Die feste Priorität lautet:

1. `AbstractVillager` → `villager`
2. `Item` → `item`
3. `Projectile` → `projectile`
4. `Vehicle` → `vehicle`
5. `Display` → `display`
6. `Enemy` → `monster`
7. `WaterMob` → `water`
8. `Ambient` → `ambient`
9. `Animals` → `animal`
10. alles Übrige → `other`

Die Priorität verhindert Überschneidungen. Sie verwendet keine Klassennamen-
Strings, Custom Names, Pluginmetadaten, NMS oder interne Kategorien. Unbekannte
oder zukünftige Typen ohne öffentlich zuordenbare Klasse ergeben konservativ
`other` und lösen keinen Fehler aus.

Bewusst `other` sind insbesondere Armor Stand, Interaction, Marker, Falling
Block, Experience Orb, Area Effect Cloud, End Crystal, Leash Knot, Painting,
Item Frame und Glow Item Frame. Block Display, Item Display und Text Display
werden über `Display` ausdrücklich als `display` klassifiziert. Die Tests leiten
für sämtliche Typen mit `AbstractVillager`, `Item`, `Projectile`, `Vehicle`,
`Display`, `Enemy`, `WaterMob`, `Ambient` oder `Animals` die vollständige
erwartete Zuordnung aus der öffentlichen Interfacehierarchie ab und prüfen die
genannten Sondertypen zusätzlich einzeln.

Der optionale Typwert ist der vollständige kleingeschriebene öffentliche
Namespaced Key, zum Beispiel `minecraft:zombie`. `UNKNOWN`, fehlende Keys und
unerwartete Formate werden fest als `unknown` normalisiert. Spieler sind auch
aus dieser Familie ausgeschlossen.

## Vollabgleich und Folia-Threading

Der vorhandene periodische Collector startet initial nach einem Tick und danach
im konfigurierten Intervall. Pro Lauf existiert genau eine monotone Run-ID. Ein
zweiter Tick startet keinen überlappenden Lauf.

Der Global Region Scheduler liest die aktuelle Weltenliste und nur die
Chunkanker. Je Chunk wird eine Aufgabe auf dessen Region Scheduler geplant. Sie
materialisiert nur die bereits geladene Chunk-Entityliste. Jede Entity wird
danach über ihren eigenen Entity Scheduler ausgewertet, damit ein gleichzeitiger
Regionswechsel nicht zu einem Zugriff vom alten Chunkthread führt. Region- und
Entitythreads warten niemals blockierend aufeinander. Sie liefern ausschließlich
immutable Werte an die threadsichere Aggregation zurück.

Liveobjekte aus `World`, `Chunk` und `Entity` bleiben ausschließlich im
begrenzten Capture-Lauf. Weder Snapshot noch Registry, HTTP-Callback oder
Fehlerlog halten diese Objekte langfristig. Ein separater Folia-Provider ist
nicht erforderlich: sämtliche verwendeten Scheduler und APIs gehören zur
gemeinsamen öffentlichen Paper-API und sind in den gepinnten Paper-/Folia-
Artefakten identisch.

## Deduplizierung und Event-/Commit-Rennen

Während eines Abgleichs wird pro beobachteter Entity kurzfristig die öffentliche
UUID ausschließlich als interne Identität gehalten. Sie ist erforderlich, weil
eine Entity während verteilter Chunktasks die Region oder Welt wechseln kann.
Die UUID wird niemals exportiert, geloggt oder in einem publizierten Snapshot
gespeichert und nach Ende des Laufs verworfen.

Der gemeinsame `EntityStateStore` serialisiert Eventupdates und
Reconciliation-Commit:

1. Der Lauf öffnet ein Journal mit Run-ID und monotoner Eventsequenz.
2. Events aktualisieren den bereits publizierten Aggregatstand atomar und werden
   zusätzlich in das aktive Laufjournal geschrieben.
3. Jede Entitybeobachtung merkt sich die Eventsequenz nach ihrer Auswertung.
4. Beim Commit wird die deduplizierte Scanbasis aufgebaut. Für jede Identität
   werden nur spätere Events wiedergegeben; frühere Events sind bereits in der
   Beobachtung enthalten.
5. Welt-Load/-Unload wird in derselben Reihenfolge angewendet.
6. Scanbasis, alle bis zur Commitgrenze eingegangenen Events und der vollständige
   neue Weltstand werden unter einem Lock kombiniert und als ein immutable
   Snapshot atomar publiziert.
7. Events nach dem Commit ändern ausschließlich den neuen Stand.

Damit gehen Events unmittelbar vor oder nach dem Commit weder verloren noch
werden sie doppelt angewendet. Timeout, Stop oder ein alter Callback schließen
das Journal nicht erfolgreich und können keinen Snapshot publizieren. Deltas
eines fehlgeschlagenen Laufs müssen nicht in eine eigene Basis übernommen werden:
bei bereits initialisiertem Zustand wurden sie direkt auf den gültigen Stand
angewendet; vor der ersten erfolgreichen Initialisierung bildet der nächste
Vollscan den aktuellen Livezustand erneut ab.

## Fehler, Weltstatus und Genauigkeitsgrenzen

Jede während der globalen Topologie bekannte Welt erhält genau einen immutable
Status:

- `SUCCESS`: Chunkliste und alle ausgewählten Chunk-/Entityarbeiten wurden
  vollständig ausgeführt. Der neue Weltwert wird publiziert; bei null geladenen
  Chunks ist dies ein belastbar leerer Stand mit zehn Nullgruppen.
- `PARTIAL`: Mindestens ein Chunkzugriff, eine Entityauswertung, ein Scheduling-
  Versuch oder ein Entity-Retire ist lokal fehlgeschlagen. Weil der Store keinen
  unbeschränkten Vorwert je Chunk hält, wäre das Zusammenführen erfolgreicher
  Chunks mit null für fehlgeschlagene Chunks eine Unterzählung. Deshalb wird kein
  neuer Teilstand publiziert.
- `UNAVAILABLE`: Die geladene Chunkliste der benannten Welt konnte nicht gelesen
  werden. Auch dieser Zustand darf nicht als leere Welt interpretiert werden.

Für `PARTIAL` und `UNAVAILABLE` bleibt ein vorhandener gültiger Weltwert
einschließlich der bereits atomar angewendeten Events erhalten. Ohne vorherigen
gültigen Wert fehlen sämtliche Entityreihen dieser Welt. Andere `SUCCESS`-
Welten desselben Laufs werden normal aktualisiert. Kann bei einer nichtleeren
Weltenliste keine einzige Welt `SUCCESS` erreichen, ist der Lauf systemisch
fehlgeschlagen und der gesamte vorherige Snapshot bleibt erhalten. Nur wenn
`Server#getWorlds()` nachweislich eine leere Liste liefert, ist ein erfolgreicher
leerer Snapshot zulässig und entfernt alte Welten. Ein Fehler der Weltenliste,
ein nicht bestimmbarer Weltname, eine verletzte interne Invariante, Timeout oder
Stop bricht ebenfalls den gesamten Lauf ab.

Ein verteilter Scan ist kein global atomarer Minecraft-Zeitpunkt. Eventjournal
und Identitätssequenzen erzeugen jedoch einen konsistenten Commitstand für alle
beobachteten Änderungen. Ein lokaler Fehler macht bewusst die gesamte Welt für
diesen Lauf konservativ statt fehlende Daten als null auszulegen. Der nächste
vollständige Abgleich kann den Stand korrigieren. Gezählt werden nur aktuell
geladene Nichtspieler-Entities. Persistierte Entities aus entladenen Chunks sind
bewusst nicht Teil der Laufzeitmetrik.

Fehler werden mit festen Schlüsseln und neutralen Meldungen rate-limitiert. Die
ursprüngliche Exception bleibt dabei als Cause vollständig erhalten; Typ,
Cause-Kette und Suppressed Exceptions gehen nicht verloren. Entity-UUID, Name,
Custom Name, Koordinate und Eventpayload erscheinen nicht in der äußeren
Meldung. Auch eine Exception des Fehlerreporters wird am Event-, Region- oder
Entitythread abgefangen.

## Konfiguration und Ressourcenverbrauch

```yaml
collectors:
  entities: true

entities:
  reconciliation-interval: "5m"
  reconciliation-timeout: "60s"
  include-exact-types: false
  include-projectile-total: false
```

Das Mindestintervall beträgt eine Minute. Ein kürzerer Vollscan könnte bei
vielen geladenen Chunks und Entities dauerhaft erhebliche Scheduler- und
Allokationslast erzeugen; ungültige oder kürzere Werte werden abgelehnt und
nicht korrigiert. Timeout und Intervall haben bewusst keine Ordnungsbedingung.
Ist der Timeout länger als das Intervall, überspringt der Überlappungsschutz die
betroffenen Intervallticks.

`collection.entity-interval` und `collectors.detailed-entity-types` bleiben als
Legacy-Aliasse lesbar; neue Schlüssel gewinnen bei gleichzeitiger Angabe.

Genaue Typen sind standardmäßig aus. Bei Aktivierung wächst die Anzahl Reihen
mit der Anzahl tatsächlich vorhandener öffentlicher `EntityType`-Werte je Welt.
Die optionale aggregierte Projektilfamilie besitzt einen getrennten Schalter.

## Metriken und Korrekturdefinition

Standardmäßig implementiert:

- `minecraft_entity_group_count{world,group}`
- `minecraft_world_entities{world}`
- `minecraft_world_living_entities{world}`
- `minecraft_world_villagers{world}`
- `minecraft_world_item_entities{world}`
- `minecraft_entity_reconciliation_duration_seconds`
- `minecraft_entity_reconciliation_last_success_timestamp_seconds`
- `minecraft_entity_reconciliation_corrections_total`

Optional:

- `minecraft_world_projectiles{world}` über `include-projectile-total`
- `minecraft_entities{world,type}` über `include-exact-types`

Eine Korrektur ist ein numerisch abweichender Welt-/Gruppen-, Gesamt-, Living-,
Villager-, Item- oder bei Aktivierung Typwert zwischen dem eventbasierten Stand
unmittelbar vor Commit und einer neuen `SUCCESS`-Welt. Der erste Initialabgleich
zählt keine Korrekturen. `PARTIAL`-/`UNAVAILABLE`-Retention und das Auslassen
einer Welt ohne Baseline zählen nicht als Korrektur. Das Entfernen einer
tatsächlich nicht mehr geladenen Welt in einem erfolgreichen Lauf zählt dagegen
die numerisch geänderten Reihen. Ein fehlgeschlagener Lauf aktualisiert weder
Dauer noch Erfolgszeitpunkt. Laufzeitfamilien besitzen keine Welt- oder
Fehlerlabels.
Eine eigene Fehlerfamilie wird nicht eingeführt; Laufzeitfehler verwenden den
vorhandenen rate-limitierten Reporter und der Lifecycle bleibt über
`minecraft_exporter_collector_state{collector="entities",...}` sichtbar.

## Collectorstatus, Health, Readiness und Smoke-Test

Der verwaltete Collector erreicht nach strukturell erfolgreichem Start
`running`. Ein gemischter Lauf mit mindestens einer `SUCCESS`-Welt darf
erfolgreich committen; lokale `PARTIAL`-/`UNAVAILABLE`-Welten verschlechtern den
Lifecycle nicht. Ein Lauf ohne einzige belastbare Welt, ein globaler Fehler oder
ein Timeout ist als Lauf fehlgeschlagen, lässt den Collector aber ebenfalls
`running` und bewahrt den letzten gültigen Snapshot. `failed` bleibt einem
strukturellen Startfehler vorbehalten, `stopped` dem Stop. Isolierte lokale und
temporäre systemische Laufzeitfehler verändern weder `/health` noch `/ready`.
Vor dem ersten Erfolg fehlen nicht erfassbare Weltreihen; sie werden nicht durch
Nullwerte als initialisiert dargestellt.

Der gepinnte Paper-/Folia-Smoke-Test wartet auf Readiness und einen erfolgreichen
Initialabgleich, force-loadet den Testchunk `0/0`, erzeugt eine kurzlebige,
markierte Area-Effect-Cloud in der Standardwelt und verlangt einen Anstieg von
`minecraft_entity_group_count{world="world",group="other"}`. Nach ihrer
natürlichen Entfernung verlangt er einen sinkenden Bestand und entfernt den
Force-Load wieder. So benötigt das Test-Fixture auf Folia keinen globalen Entity-
Selektor. Das vollständige Log wird auf konkrete Ownership- und
Threadingmuster einschließlich `Thread failed main thread check`,
`Cannot getEntities asynchronously`, `Cannot getLoadedChunks asynchronously`,
`not owned by current region`, TickThread-Stackframes und fehlerhafte Region-
beziehungsweise Entity-Scheduler-Callbacks geprüft. Eine beliebige
`IllegalStateException` ohne Threadingkontext ist nicht pauschal verboten.

## Reload, Stop und Datenschutz

Bei Pluginstart wird der Listener vor dem initialen Abgleich registriert. Ein
Reload baut Registry, Journal und Snapshot neu auf; der initiale Abgleich
erfasst bereits vorhandene geladene Entities. Welt-Load wartet auf eine gültige
Baseline und erzeugt keinen Nullstand, Welt-Unload entfernt alle Reihen. Beim Stop
werden zuerst laufende Abgleiche invalidiert, dann die Eventannahme gesperrt und
der Listener abgemeldet. Wiederholter Stop ist idempotent.

Es werden keine Spieler, Namen, UUIDs, Koordinaten, Besitzer, Tamer, Inventare,
NBT-, Item- oder Pluginmetadaten exportiert. Die einzige kurzfristige UUID-
Verwendung dient der lauflokalen Deduplizierung und überschreitet die
Commitgrenze nicht.
