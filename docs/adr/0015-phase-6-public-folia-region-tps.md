# ADR 0015: Öffentliche Folia-Regions-TPS in Phase 6

## Status

Angenommen und in Phase 6 umgesetzt.

## Untersuchte öffentliche API

Untersucht wurden das tatsächlich aufgelöste Source- und Binärartefakt
`dev.folia:folia-api:26.1.2.build.8-stable` sowie zum Gegenvergleich
`io.papermc.paper:paper-api:26.1.2.build.74-stable`.

Folia ergänzt im öffentlichen `org.bukkit.Server` drei
`getRegionTPS`-Überladungen für `Location`, `Chunk` und
`World, chunkX, chunkZ`. Alle liefern `double[]` oder `null`, wenn am Anker keine
Region existiert. Die dokumentierte Arrayreihenfolge ist fest:

```text
5s, 15s, 1m, 5m, 15m
```

Gewählt wird `Server#getRegionTPS(World,int,int)`, weil sie weder ein `Location`-
noch ein `Chunk`-Liveobjekt über die Providergrenze tragen muss. Genau diese
Signatur ist die Capability-Grenze. Sie fehlt in der gepinnten Paper-API.

Die ebenfalls untersuchten öffentlichen Global-, Region- und Entity-Scheduler
sowie `Server#isOwnedByCurrentRegion(World,int,int)` existieren auf Paper und
Folia und sind deshalb kein Erkennungsmerkmal. Die Klasse
`RegionizedServerInitEvent` existiert ebenfalls in beiden API-Artefakten und ist
nur ein einmaliger Initialisierungshook. Sie liefert weder Regionsidentität noch
Create-, Split-, Merge- oder Destroy-Callbacks.

Die öffentliche API bietet insbesondere nicht:

- eine stabile Regions-ID,
- eine vollständige Auflistung aktiver Regionen,
- Lifecycle-Events für einzelne Regionen,
- regionale Tickdauer, Tickverzögerung oder Überlastungszustände,
- eine regionale Entity- oder Spielerliste.

## Klassenlade- und Buildgrenze

Allgemeiner Code wird weiterhin ausschließlich gegen die gepinnte Paper-API
kompiliert. Der konkrete Provider liegt unter
`de.minecraftgilde.prometheus.folia.provider` im getrennten Source-Set
`src/folia/java`. Nur dieses Source-Set kompiliert gegen die Folia-API; sie ist
`compileOnly` und wird nicht in das Shadow-JAR eingebettet. Die Klassen beider
Source-Sets werden zu genau einem Plugin-JAR zusammengeführt.

Der gemeinsame Bootstrap prüft reflektiv nur die öffentliche Signatur
`Server#getRegionTPS(World,int,int)` und deren Rückgabetyp `double[]`. Erst nach
Erfolg lädt eine reflektive Factory die eigene Providerklasse anhand ihres
Klassennamens. Es gibt keine statische Referenz des Plugin-Bootstraps, der
`PhaseSixRuntime` oder des `FoliaCollector` auf diese Klasse. Reflection wird
nicht für Minecraft-Daten und nicht für interne Serverklassen verwendet.

Auf Paper gilt bei `collectors.folia: true`: genau eine Capability-Warnung,
Collectorstatus `unsupported`, kein Providerstart und keine
`minecraft_folia_*`-Familie. Health, Readiness und der übrige Pluginstart bleiben
erfolgreich. Bei `collectors.folia: false` bleibt der Status `disabled`; es gibt
weder Prüfung noch Warnung oder Metrikfamilie.

## Beobachtungsquelle und Regionsdeduplizierung

Da keine öffentliche Auflistung existiert, beobachtet der Provider nur Regionen,
die durch konfigurierte öffentliche Anker erreichbar sind:

- Online-Spielerpositionen, kurz auf dem jeweiligen Entity Scheduler gelesen,
- Weltspawns, auf dem Global Region Scheduler gelesen,
- optional force-loaded Chunks, ebenfalls nur während des globalen Capture
  ausgelesen.

Force-loaded Chunks sind standardmäßig deaktiviert, um große zusätzliche
Ankermengen nicht ungefragt periodisch zu planen. Freie konfigurierte Positionen
sind in Phase 6 nicht implementiert und werden bei nichtleerer Konfiguration
abgelehnt.

Nach dem Lesen bestehen Anker nur aus validiertem Weltlabel, Weltreferenz für den
noch laufenden Capture sowie Chunk-X/Z. Für jeden Anker wird eine kurze Aufgabe
auf dessen Region Scheduler geplant. Dort lässt sich mit der öffentlichen
`isOwnedByCurrentRegion`-Methode feststellen, welche anderen Anker aktuell
derselben Region gehören. Der lexikografisch erste Anker je Welt und aktuell
besitzender Region wird gewählt; spätere Anker derselben Region werden
verworfen. Dadurch wird jede in diesem Lauf beobachtete Region höchstens einmal
aggregiert, ohne eine interne Regions-ID zu erfinden.

Die Spielerzahl ist belastbar aggregierbar: Spielerpositionen werden bereits auf
ihrem Entity Scheduler auf neutrale Anker reduziert. Auf dem Region-Thread wird
nur gezählt, wie viele dieser Anker von der aktuellen Region besessen werden.
Player, Name und UUID gelangen weder in Registry noch Snapshot, Label oder Log.

`observed_regions` bedeutet ausdrücklich nicht alle aktiven Regionen. Regionen
ohne einen konfigurierten Anker sind unsichtbar. Splits und Merges werden bei
jedem vollständigen Lauf anhand der aktuellen Ownership neu dedupliziert. Weil
Callbacks verschiedener Regionen nicht atomar gleichzeitig laufen können, ist
ein Snapshot eine vollständige Sammlung eines begrenzten Capture-Laufs und kein
serverweiter Zeitpunkt mit globaler Regionsatomizität.

## Threading

- Start des periodischen Laufs, Weltenliste, Spawn- und Force-Load-Anker:
  Global Region Scheduler.
- Spielerposition: Entity Scheduler des jeweiligen Spielers.
- Deduplizierung, aggregierte Spielerzählung und `getRegionTPS`:
  Region Scheduler des Ankers.
- Timeout: Async Scheduler.
- Prometheus-Scrape: ausschließlich ein bereits publizierter immutable Snapshot.

Region-Threads werden niemals blockierend abgewartet. Tasks melden Ergebnisse
asynchron an den Capture. Minecraft-Liveobjekte existieren nur in diesem
begrenzten Lauf und werden nicht in der `RegionObservationRegistry` oder im
Prometheus-Snapshot gespeichert.

## Registry-, Snapshot- und Stale-Semantik

Die threadsichere `RegionObservationRegistry` arbeitet transaktional mit einer
monotonen Laufidentität. Updates gelten nur für den aktiven Lauf; Ergebnisse
alter, abgebrochener oder nach `stop()` eintreffender Läufe werden verworfen.
Innerhalb eines Laufs gewinnt bei demselben internen Anker nur der zeitlich
neuere Wert.

Ein erfolgreicher Lauf ersetzt die gesamte vorherige Beobachtungsmenge. Damit
verschwinden entladene Welten, entfernte Anker und ehemalige Regionen ohne
unbegrenzten Cache. Fehler beim Lesen oder Einplanen eines einzelnen
Spielerankers sowie Ownership-, Scheduling-, API- und Validierungsfehler einer
einzelnen Regionsbeobachtung werden lokal isoliert. Der betroffene Anker wird
genau einmal abgeschlossen und übersprungen; alle übrigen Aufgaben laufen weiter.
Der erfolgreiche Lauf publiziert anschließend einen Teilsnapshot aus genau den
gültigen Observationen.

Ein erfolgreicher Lauf darf null gültige Regionen enthalten. In diesem Fall
ersetzt eine leere immutable Liste den vorherigen Registry- und Metrikstand,
sodass alte dynamische Folia-Reihen verschwinden. Es werden weder `null`-Samples
noch nullwertige Ersatzreihen erzeugt. Der Collector bleibt `running`; Health und
Readiness ändern sich nicht. Der Prometheus Java Client 1.8.0 rendert leere
dynamische Gauge-Snapshots im Textformat vollständig ohne `HELP`, `TYPE` oder
Sample-Zeilen. Die Familienregistrierung wird deshalb unabhängig durch
Provider- und Descriptor-Integrationstests geprüft.

Systemisch sind dagegen eine nicht startbare Registry-Generation, das Ende der
Completion-Annahme durch Stop oder Timeout, ein Fehler der globalen Weltliste
und verletzte interne Zustandsinvarianten. Nur solche Fehler brechen den gesamten
Lauf ab und erhalten den letzten gültigen Snapshot. Der Commit der Registry wird
erst innerhalb der atomaren Erfolgsannahme des periodischen Collectors
materialisiert; dadurch können Timeout und Erfolg sich weder überholen noch
einen verspäteten Registry-Stand publizieren. Beim systemischen Abbruch werden
ausstehende Tasks kontrolliert storniert und spätere Callbacks ignoriert.
Andere Collector bleiben davon unberührt.

Lokale Beobachtungsfehler und systemische Laufzeitfehler werden über den
vorhandenen Reporter mit getrennten festen Schlüsseln rate-limitiert. Der
Provider übergibt dabei nur neutrale Fehlermeldungen und den ursprünglichen Stack
ohne ursprünglichen Nachrichtentext, damit API-Exceptions keine Spieler-, UUID-,
Chunk-, Regions- oder internen Beobachtungsdetails in das Log tragen können.
Auch eine Exception des Reporters wird an der Capture-Grenze abgefangen und
verlässt keinen Entity- oder Region-Thread.

Beobachtungen sind standardmäßig 60 Sekunden gültig. Der Wert muss mindestens
`collection.folia-interval` betragen und ohne Überlauf als Millisekunden
darstellbar sein. Der Metrics-Callback filtert den einmal geladenen immutable
Snapshot anhand dieser TTL und eines einmal gelesenen Zeitpunkts. Abgelaufene
Reihen verschwinden deshalb auch dann, wenn spätere Captures fehlschlagen.

`minecraft_folia_region_snapshot_age_seconds{world}` ist das Alter der ältesten
noch gültigen Beobachtung dieser Welt. Damit zeigt die Metrik den konservativsten
Freshness-Wert. Existiert keine gültige Beobachtung, fehlen alle dynamischen
Folia-Samples; es wird kein Nullwert erfunden.

## TPS, Quantile und Schwellenwerte

Konfigurierbar ist nur eine Teilmenge der fünf API-Fenster. Unbekannte oder
doppelte Fenster werden abgelehnt. Intern und in der Ausgabe gilt stets die feste
API-Reihenfolge. TPS-Werte müssen endlich und nichtnegativ sein. Als obere
Schutzgrenze gilt `10000`, die öffentliche Maximalrate des
`ServerTickManager`; Werte werden nicht gekappt oder gerundet.

Für `min`, `p05`, `p50`, `p95`, `max` und `average` werden Werte aufsteigend
sortiert. Quantile verwenden exakte lineare Typ-7-Interpolation mit
`h=(n-1)q`. Ein Einzelwert ergibt für jede Statistik denselben Wert. Nichtfinite
und negative Eingaben werden ausgeschlossen; nach der API-Validierung darf ein
solcher Wert den Snapshot ohnehin nicht erreichen. Es gibt keine Rundung.
Leere Eingaben erzeugen keine Sample-Reihe.

TPS-Schwellenwerte müssen endlich und in `(0,20]` liegen. Duplikate werden als
Konfigurationsfehler abgelehnt. Die Ausgabe sortiert absteigend und formatiert
kanonisch sowie locale-unabhängig, zum Beispiel `19`, `18` und `15`.
`regions_below_tps` verwendet einen strikt kleineren Vergleich.

## Implementierte Metriken

- `minecraft_folia_observed_regions{world}`
- `minecraft_folia_region_tps{world,window,stat}`
- `minecraft_folia_regions_below_tps{world,window,threshold}`
- `minecraft_folia_regions_with_players{world}`
- `minecraft_folia_players_per_region{world,stat}`
- `minecraft_folia_region_snapshot_age_seconds{world}`

Alle Familien hängen ausschließlich an der privaten Registry des jeweiligen
`MetricsCore`. Ein Scrape lädt genau einen Snapshot für die gesamte Gruppe.
Interne Ankerkoordinaten und Beobachtungsidentitäten sind keine Labels.

## Bewusst nicht implementiert

- `minecraft_folia_active_regions`: keine vollständige öffentliche Auflistung.
- `minecraft_folia_region_tick_duration_seconds`: keine regionale Messquelle.
- `minecraft_folia_overloaded_regions`: kein öffentlicher regionaler
  Überlastungszustand; TPS wird nicht in Tickdauer umgedeutet.
- `minecraft_folia_region_tick_delay_seconds`: keine öffentliche Messquelle.

Insbesondere wird keine Tickdauer als `20 / TPS` abgeleitet. Interne
`RegionizedServer`-Klassen, NMS, Servernamen, Versionsstrings, Threadnamen und
künstliche Nullwerte sind keine Ersatzquellen.

## Datenschutz, Kardinalität und Einschränkungen

Prometheus-Labels enthalten nur validierte Weltlabels und die festen Mengen
`window`, `stat` und `threshold`. Spielername, UUID, IP-Adresse, Spielerposition,
Chunkkoordinate, Regionskoordinate und interne Beobachtungsidentität werden nicht
exportiert oder geloggt.

Die Zahl der beobachteten Regionen hängt von den Ankern ab und kann kleiner als
die Zahl aktiver Regionen sein. Zwischen Ankererfassung und Regionscallback kann
sich Ownership ändern; der Provider verwendet dann bewusst die im Callback
aktuelle öffentliche Ownership. Ohne öffentliches Regions-Lifecycle- oder
Enumerationsmodell ist eine stärkere Garantie nicht möglich. Diese Grenze ist
der Grund gegen interne APIs, geschätzte Gesamtzahlen und künstliche Nullreihen.
