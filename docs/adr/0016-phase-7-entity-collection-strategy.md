# ADR 0016: Hybride Entity-Erfassung in Phase 7

## Status

Angenommen und in Phase 7 umgesetzt.

## Untersuchte öffentliche API

Untersucht wurden die tatsächlich aufgelösten Source- und Binärartefakte
`io.papermc.paper:paper-api:26.1.2.build.74-stable` und
`dev.folia:folia-api:26.1.2.build.8-stable`. Die für Phase 7 verwendeten
`World`-, `Chunk`-, `EntityType`- und Eventquellen sind in beiden gepinnten
Source-Artefakten identisch.

`World#getEntityCount()` liefert nur eine unaufgeschlüsselte Gesamtzahl. Damit
lassen sich Spieler nicht belastbar ausschließen und weder Living-Entities noch
Gruppen oder genaue Typen bestimmen. Die Methode wird deshalb nicht als
Phase-7-Datenquelle verwendet. `World#getEntities()`, `getLivingEntities()` und
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

## Verbindliche Eventstrategie

Zwischen Abgleichen werden ausschließlich folgende Zustandsquellen verwendet:

- `EntityAddToWorldEvent`: jede Aufnahme in eine Welt, ausdrücklich auch durch
  Chunk-Load,
- `EntityRemoveFromWorldEvent`: jede Entfernung aus einer Welt, ausdrücklich
  auch durch Chunk-Unload; `getWorld()` bezeichnet zuverlässig die alte Welt,
- `WorldLoadEvent`: legt nach der ersten Initialisierung einen vollständigen
  Nullstand mit zehn Gruppen an,
- nicht abgebrochenes `WorldUnloadEvent`: entfernt die komplette Welt sofort.

Die beiden Entityevents sind nicht abbrechbar und bilden eine symmetrische
einzige Quelle. Damit werden Spawn, Tod, Despawn, Pickup, Plugin-Entfernung,
Transformation, Weltwechsel sowie Chunk-Load und -Unload ohne parallele
Speziallistener abgedeckt. Ein Weltwechsel besteht aus Remove der Quellwelt und
Add der Zielwelt; eine Transformation aus Remove des alten und Add der neuen
Entity beziehungsweise Entities.

`EntitySpawnEvent`, `CreatureSpawnEvent`, `ItemSpawnEvent`,
`EntityDeathEvent`, `ItemDespawnEvent`, `EntityTransformEvent`,
`EntityTeleportEvent`, `EntitiesLoadEvent`, `EntitiesUnloadEvent` sowie Chunk-
Events werden nicht zusätzlich beobachtet. Mehrere dieser Events liegen vor der
endgültigen Änderung, sind abbrechbar oder würden denselben Zustandswechsel
doppelt zählen.

`EntityRemoveEvent` bietet öffentliche strukturierte Removal-Causes, wird aber
ebenfalls nicht kombiniert. Seine Dokumentation weist auf eine andere
Auslieferungszeit als `EntityRemoveFromWorldEvent` hin. Der zusätzliche Cause
wird für die Phase-7-Gauges nicht benötigt und eine zweite Removalquelle würde
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

## Fehler, Teilergebnisse und Genauigkeitsgrenzen

Fehler einer einzelnen Entity werden übersprungen. Fehler eines einzelnen
Chunks überspringen diesen Chunk; erfolgreiche Nachbarchunks werden weiterhin
publiziert. Wenn für eine geladene Welt kein einziger geplanter Chunk erfolgreich
erfasst werden konnte, bleibt der letzte gültige Weltwert erhalten. Ein Fehler
der globalen Weltenliste, eine verletzte interne Invariante, Timeout oder Stop
bricht den gesamten Lauf ab und erhält den letzten vollständigen Snapshot.

Ein verteilter Scan ist kein global atomarer Minecraft-Zeitpunkt. Eventjournal
und Identitätssequenzen erzeugen jedoch einen konsistenten Commitstand für alle
beobachteten Änderungen. Ein lokal übersprungener Chunk oder eine übersprungene
Entity kann vorübergehend zu Unterzählung führen; der nächste erfolgreiche
Abgleich und die fortlaufenden Events korrigieren diese Drift. Gezählt werden
nur aktuell geladene Nichtspieler-Entities. Persistierte Entities aus entladenen
Chunks sind bewusst nicht Teil der Laufzeitmetrik.

Fehler werden mit festen Schlüsseln und neutralen Meldungen rate-limitiert.
Entity-UUID, Name, Custom Name, Koordinate und Eventpayload erscheinen nicht im
Log. Auch eine Exception des Fehlerreporters wird am Event-, Region- oder
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
unmittelbar vor Commit und dem neuen Vollabgleich. Der erste Initialabgleich
zählt keine Korrekturen. Laufzeitfamilien besitzen keine Welt- oder Fehlerlabels.
Eine eigene Fehlerfamilie wird nicht eingeführt; Laufzeitfehler verwenden den
vorhandenen rate-limitierten Reporter und der Lifecycle bleibt über
`minecraft_exporter_collector_state{collector="entities",...}` sichtbar.

## Reload, Stop und Datenschutz

Bei Pluginstart wird der Listener vor dem initialen Abgleich registriert. Ein
Reload baut Registry, Journal und Snapshot neu auf; der initiale Abgleich
erfasst bereits vorhandene geladene Entities. Welt-Load erzeugt nach der
Initialisierung einen Nullstand, Welt-Unload entfernt alle Reihen. Beim Stop
werden zuerst laufende Abgleiche invalidiert, dann die Eventannahme gesperrt und
der Listener abgemeldet. Wiederholter Stop ist idempotent.

Es werden keine Spieler, Namen, UUIDs, Koordinaten, Besitzer, Tamer, Inventare,
NBT-, Item- oder Pluginmetadaten exportiert. Die einzige kurzfristige UUID-
Verwendung dient der lauflokalen Deduplizierung und überschreitet die
Commitgrenze nicht.
