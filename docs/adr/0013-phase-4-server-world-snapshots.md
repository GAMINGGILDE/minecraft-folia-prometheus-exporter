# ADR 0013: Server- und Welt-Snapshots in Phase 4

## Entscheidung

Phase 4 implementiert Server-, aggregierte Spieler-, Plugin-, Welt-, Chunk- und
Weltgrößenmetriken als vier getrennte verwaltete Collector: `server`, `worlds`,
`chunks` und `world-sizes`. Jeder Collector publiziert über ein eigenes
`SnapshotRepository` ausschließlich vollständig konstruierte immutable Werte.
Die Prometheus-Abbildung erfolgt durch instanzgebundene `MultiCollector` in der
privaten Registry des vorhandenen `MetricsCore`.

Es entsteht weder eine zweite Registry-, HTTP-, Status- noch
Lifecycle-Architektur. Event-, Entity- und Folia-Provider bleiben außerhalb von
Phase 4.

## Scheduler- und Ownership-Strategie

`PaperCollectionScheduler` verwendet ausschließlich die gemeinsamen öffentlichen
Scheduler der Paper-API-Linie 26.1.2:

- Der Global Region Scheduler startet periodische Erfassungen und liest globale
  Serverzustände, Pluginzustand, Weltlisten sowie öffentliche aggregierte
  Weltwerte.
- `Player#getGameMode()` ist der einzige entitygebundene Zugriff. Er wird je
  Online-Spieler über dessen Entity Scheduler ausgeführt und sofort in vier
  feste Zähler aggregiert. Spielerobjekte verlassen diesen Erfassungslauf nicht;
  Namen und UUIDs werden nicht gelesen.
- Weltpfade werden im globalen Kontext als normalisierte `Path`-Werte erfasst.
  Die anschließende Verzeichnistraversierung läuft nur auf dem Async Scheduler.
- Der Region Scheduler ist Teil der gemeinsamen Abstraktion, wird für Phase 4
  jedoch nicht benötigt. Es gibt keinen klassischen Bukkit-Scheduler-Fallback.

Prometheus-Callbacks lesen nur Snapshot-Repositories. Ein Scrape löst weder eine
Minecraft-Abfrage noch eine Dateisystemberechnung aus.

## Definition der Serverstartzeit

`minecraft_server_start_time_seconds` bezeichnet den Zeitpunkt unmittelbar zu
Beginn von `ExporterPlugin#onEnable()`. `minecraft_server_uptime_seconds` ist die
nichtnegative Differenz zwischen diesem fixierten Zeitpunkt und dem
Erfassungszeitpunkt.

Die öffentliche gemeinsame API bietet keine belastbare Quelle für einen früheren
Minecraft-Prozess- oder Server-Lifecycle-Start. Interne Serverfelder, NMS und
Reflection sind ausgeschlossen; deshalb wird kein scheinbar genauerer Wert
abgeleitet.

## Geladene Chunks

`minecraft_world_loaded_chunks` verwendet `World#getChunkCount()`. Diese
öffentliche aggregierte Paper-API liefert die Anzahl ohne `getLoadedChunks()`,
ohne Materialisierung von Chunkobjekten und ohne positionsgebundene
Einzelabfragen. Der Aufruf geschieht während der globalen Welterfassung.

## Asynchrone Weltgrößen

`minecraft_world_size_bytes` ist die rekursive Summe der Größen aller regulären
Dateien unterhalb des normalisierten Weltpfads. Damit zählen unter anderem
Region-Dateien, Playerdata, Datapacks und alle weiteren regulären Dateien im
Weltordner. Symbolischen Links wird nicht gefolgt; ein Weltpfad, der selbst ein
Symlink ist, wird abgelehnt. Die Traversierung prüft, dass besuchte Pfade unter
dem Weltwurzelpfad bleiben.

Während der Traversierung verschwindende oder nicht lesbare Einzeleinträge
werden übersprungen und niedrigfrequent gemeldet. Schlägt eine Weltberechnung
insgesamt fehl, bleibt ihr letzter erfolgreicher Wert erhalten. Für denselben
Weltpfad läuft höchstens eine Berechnung gleichzeitig. Entfernte Welten werden
beim nächsten vollständigen Snapshot nicht mehr übernommen.

Die Weltverzeichnisse werden vor dem Start deterministisch nach Weltname
sortiert. Eine instanzgebundene interne Warteschlange hält höchstens
`filesystem.world-size-scan-concurrency` Dateisystemscans gleichzeitig aktiv.
Der gültige Bereich ist `1` bis `8`; der Standard `1` verarbeitet die Welten
sequenziell und minimiert konkurrierende I/O-Last. Ein höherer Wert kann den
Gesamtlauf beschleunigen, erhöht aber die I/O-Last. Die Queue nutzt weiterhin
ausschließlich die bestehende Async-Scheduler-Abstraktion und keinen zusätzlichen
allgemeinen Executor.

## Timeout, verspätete Ergebnisse und Fehlerzustand

`PeriodicSnapshotCollector` besitzt atomar genau einen aktiven Lauf. Server-,
Welt- und Chunk-Collector verwenden weiterhin `collection.timeout`. Nur
`world-sizes` verwendet `collection.filesystem-timeout` mit dem Standard `15m`,
weil rekursive Scans großer Weltverzeichnisse den allgemeinen Standard `10s`
berechtigt überschreiten können. Der Filesystem-Timeout umfasst die Wartezeit in
der internen Queue, alle gestarteten Scans, Zusammenführung und Publikation des
vollständigen Snapshots.

Der konfigurierte Timeout entfernt den Lauf und signalisiert dessen Ungültigkeit
an den Capture. Noch wartende oder nur eingeplante Scans werden verworfen und für
den abgelaufenen Lauf wird keine neue Arbeit gestartet. Ein bereits physisch in
`Files.walkFileTree` laufender Java-Dateisystemaufruf ist nicht garantiert hart
unterbrechbar. Er darf zurückkehren, gibt dann seinen Parallelitätsslot und den
Weltpfad zuverlässig frei; sein Ergebnis kann wegen Laufidentität und
Gültigkeitssignal nicht mehr publiziert werden. Ein späterer Lauf darf nach dem
Timeout beginnen, scannt denselben Pfad aber erst nach Freigabe und kann nicht von
einem verspäteten alten Ergebnis überschrieben werden.

Nach `stop()` werden keine Ergebnisse angenommen, die interne Warteschlange
startet keine Scans mehr und periodischer Task sowie Timeout-Wächter werden
abgebrochen. Ein ausgelassener Intervalltick startet keinen parallelen Lauf.

Ein Erfassungsfehler löscht den letzten gültigen Snapshot nicht. Fehler einer
einzelnen weiterhin geladenen Welt behalten nur deren letzten Einzelwert und
lassen andere Welten aktualisieren. Wiederkehrende Fehler werden je Collector
höchstens einmal in fünf Minuten geloggt, wenn
`logging.collection-errors: true` gilt. Temporäre Laufzeitfehler belassen den
vorhandenen Collector-Zustand `running`; `failed` bleibt strukturellen
Startfehlern vorbehalten. Es wird kein zweiter Statusmechanismus eingeführt.

## Dynamische Welten und Labels

Jeder Welt-, Chunk- und Weltgrößen-Snapshot wird aus der aktuell geladenen
Weltenliste neu aufgebaut. Prometheus erzeugt Labelreihen unmittelbar aus genau
diesem Snapshot. Neu geladene Welten erscheinen nach erfolgreicher Erfassung;
entladene Welten und ihre Serien verschwinden. Ein separater, unbegrenzt
wachsender Labelcache existiert nicht.

Wetter, Schwierigkeit, Umgebung und Spielmodus verwenden feste Enum-Mappings.
Pluginname und -version sind die einzige optionale dynamische Plugin-Labelmenge;
`minecraft_plugin_info` bleibt deshalb standardmäßig deaktiviert.
Normalisierte Weltpfade sind nur interne Arbeitswerte und werden niemals als
Prometheus-Labels exportiert; `minecraft_world_size_bytes{world="..."}` bleibt
unverändert.

## Konsequenzen

- Das gemeinsame Plugin-JAR läuft mit derselben Implementierung auf Paper und
  Folia und kompiliert allgemeinen Code nur gegen die öffentliche Paper-API.
- Snapshots enthalten keine Bukkit-, Paper-, Folia- oder Minecraft-Liveobjekte.
- Dateisystemdaten können bis zum `filesystem-interval` alt sein; Fehler und
  Timeouts bewahren bewusst den letzten konsistenten Stand.
- Alte Konfigurationen erhalten ohne Migration automatisch den Filesystem-Timeout
  `15m` und die sequenzielle Scanparallelität `1`.
- Der Aktivierungszeitpunkt ist semantisch enger als ein Prozessstart, dafür
  öffentlich, deterministisch und plattformübergreifend korrekt.
- Full Time, Autosave, Entityzahlen, Event-Counter, allgemeine
  Dateisystemkapazität und Folia-Regionsmetriken bleiben späteren Phasen
  vorbehalten.
