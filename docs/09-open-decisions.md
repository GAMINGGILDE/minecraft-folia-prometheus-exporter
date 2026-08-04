# 9. Verbindliche Entscheidungen und verbleibende Detailfragen

## 9.1 Projekt- und Buildgrundlagen

```text
Pluginname: FoliaPrometheusExporter
Repository: minecraft-folia-prometheus-exporter
Group: de.minecraftgilde
Artifact: minecraft-folia-prometheus-exporter
Package: de.minecraftgilde.prometheus
Java-Mindestversion: 25
Plattformen: Paper und Folia ab API-Linie 26.1.2
Auslieferung: genau ein gemeinsames Plugin-JAR
Allgemeine Compile-API: öffentliche paper-api
paper-api-Koordinate: io.papermc.paper:paper-api:26.1.2.build.74-stable
Folia-Provider-Compile-API: dev.folia:folia-api:26.1.2.build.8-stable (compileOnly)
Gradle Wrapper: 9.6.1
Gradle-Distribution-SHA-256: 9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
Java Toolchain: 25
JavaCompile.options.release: 25
Plugin-Descriptor: klassische plugin.yml
folia-supported: true
api-version für API-Linie 26.1.2: 26.1.2
Entwicklungsversion: 0.1.0-SNAPSHOT
JUnit 5: 5.14.4
Shadow-Plugin: com.gradleup.shadow 9.6.1
Prometheus Java Client: 1.8.0 über BOM
Lizenz: MIT
Interne Paper-/Folia-APIs in Version 1: ausgeschlossen
Experimentelle oder interne Provider in Version 1: ausgeschlossen
Individuelle Spielermetriken: ausgeschlossen
Counter-Persistenz in Version 1: nein
```

Andere Serverimplementierungen und Forks werden nicht aktiv blockiert, sind aber
nicht offiziell getestet oder unterstützt.

## 9.2 Server-Smoke-Tests

Der normale Build und der verpflichtende Server-Smoke-Test bleiben getrennte
GitHub-Actions-Workflows. Jeder Matrixlauf baut das Plugin mit Java 25. Folgende
Serverartefakte sind fest gepinnt:

| Plattform | Version | Build | Kanal | SHA-256 |
|---|---:|---:|---|---|
| Paper | 26.1.2 | 74 | stable | `1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7` |
| Folia | 26.1.2 | 8 | stable | `607afd1c3320008e1ffd2eaee6780ace4419d5f8c527b75e79f259be79ebf57b` |

Die Artefakte werden über den offiziellen PaperMC Downloads Service bezogen. Alle
Anfragen senden den eindeutigen User-Agent
`minecraft-folia-prometheus-exporter-ci/0.1.0` samt Repository-URL. Der Workflow
wählt niemals dynamisch einen Latest-Build aus und verifiziert die gepinnte
Prüfsumme.

Der Test setzt `eula=true`, kopiert das einzige erzeugte Plugin-JAR nach
`plugins/`, startet den Server mit `--nogui` und wartet höchstens 240 Sekunden auf
`FoliaPrometheusExporter started.`. Plugin-Ladefehler, Plugin-Exceptions,
vorzeitiges Serverende oder Timeout sind Fehler. Nach erfolgreicher Aktivierung
wird `stop` über die Serverkonsole gesendet und der Prozess kontrolliert beendet.
Seit Phase 3 prüft der Smoke-Test zusätzlich stabile JVM-/Prozessfamilien samt
Prometheus-`HELP`-, `TYPE`- und Sample-Zeilen. Seit Phase 4 wartet er begrenzt auf
Server-, Welt-, Chunk- und Weltgrößensnapshots, validiert repräsentative
Phase-4-Familien und kontrollierte Labels. Seit Phase 5 prüft er die zehn
Eventfamilien und den laufenden Listener-Collector; Entity- und Folia-Familien
bleiben im historischen Phase-5-Stand ausgeschlossen. Seit Phase 6 erwartet
Paper den Folia-Status
`unsupported`, genau eine Warnung und keine Folia-Familie. Folia erwartet
`running` und prüft bei vorhandenen Beobachtungen die sechs Phase-6-Familien;
ohne Beobachtung sind vollständig fehlende dynamische Textfamilien einschließlich
`HELP` und `TYPE` ausdrücklich zulässig und es wird keine Region erzwungen.
Seit Phase 7 verlangen beide Plattformen einen laufenden Entity-Collector, zehn
Gruppensamples je erfasster Welt, die Standardaggregate und die begrenzten
Abgleichsmetriken; optionale Typ- und Projektilfamilien bleiben im Standard aus.
Details stehen in ADR 0009.

## 9.3 In Phase 2 umgesetzt

- Allgemeine Scheduler-Funktionen verwenden die Global-, Region-, Entity- und
  Async-Scheduler der gemeinsamen öffentlichen Paper-API auf Paper und Folia.
- Phase 2 enthält keinen Folia-Metrikprovider und keine vorsorgliche
  Plattform-Erkennung.
- Der offizielle Prometheus Java Client wird über
  `io.prometheus:prometheus-metrics-bom:1.8.0` eingebunden.
- Benötigte Module sind `prometheus-metrics-core`,
  `prometheus-metrics-instrumentation-jvm` und
  `prometheus-metrics-exporter-httpserver`.
- Diese Bibliotheken werden in das einzige Plugin-JAR eingebunden; das Package
  `io.prometheus` wird nach
  `de.minecraftgilde.prometheus.internal.prometheus` relocatet.
- Es gibt weder einen eigenen Prometheus-Text-Renderer noch ein zusätzliches
  HTTP-Framework.
- Der offizielle Prometheus-`MetricsHandler` übernimmt Scrape-Protokoll,
  Content-Negotiation und Serialisierung. Er läuft auf dem vom Exportermodul
  vorgesehenen schlanken JDK-`HttpServer`; ein kontrollierter Router stellt
  Metrics, Health, Readiness, `404` und `405` auf demselben Listener bereit.
  Standardwerte sind `/metrics`, `/health` und `/ready`.
- HTTP-Host, -Port, -Pfade und Workerzahl stammen aus der bestehenden
  Konfiguration. Die Standardbindung ist `127.0.0.1`.
- Der HTTP-Server und sein benannter Daemon-Workerpool werden beim Plugin-Disable
  idempotent geschlossen.
- HTTP-Threads und Prometheus-Callbacks lesen ausschließlich immutable Snapshots
  und kontrollierten Exporterstatus, niemals Minecraft-Livedaten.
- Die Registrierung konkreter JVM- und Prozessmetriken bleibt Phase 3 vorbehalten.
- Der Collector-Lifecycle verwendet `disabled`, `starting`, `running`,
  `unsupported`, `failed` und `stopped`. Startreihenfolge ist deterministisch,
  Stoppreihenfolge umgekehrt und Fehler sind isoliert.
- Snapshots bestehen aus Erfassungszeitpunkt und defensiv kopierter immutable
  Werteliste. Das Repository publiziert per `AtomicReference`.
- Readiness ist nur aktiv, wenn Registry, Metrics Core, HTTP-Server und
  Plugininitialisierung vollständig verfügbar sind. Ein optionaler
  Collectorfehler beeinflusst Health und Readiness nicht automatisch.
- Implementierte Eigenmetriken sind `build_info`, `health`, `ready`, Scrape- und
  Scrape-Fehler-Counter, kontrollierte HTTP-Request-Counter und der One-Hot-
  Collectorzustand unter dem Präfix `minecraft_exporter_`.
- Der Build erzeugt nur das Shadow-JAR und prüft dessen Inhalt automatisch.
- Der Build bettet `git rev-parse HEAD` über `build-info.properties` ein. Ohne
  Git-Kontext oder bei ungültigem Ergebnis wird `unknown` verwendet; ein
  `gitCommit`-Projektparameter kann den Wert kontrolliert vorgeben.
- Jede `MetricsCore` besitzt eine private Registry und genau eine
  instanzgebundene `ExporterMetrics`; ein statischer Cache ist nicht erforderlich.

Details stehen in ADR 0011.

## 9.4 In Phase 3 umgesetzt

- `JvmMemoryMetrics`, `JvmGarbageCollectorMetrics`, `JvmThreadsMetrics`,
  `JvmClassLoadingMetrics`, `JvmBufferPoolMetrics` und `ProcessMetrics` aus dem
  Prometheus Java Client 1.8.0 werden direkt in jeder privaten Core-Registry
  registriert.
- `JvmMetricsRegistrar` registriert jede konfigurierte Gruppe höchstens einmal
  und verwendet weder Default-Registry noch statische Registry-Zustände.
- `collectors.jvm` und `collectors.process` bleiben unabhängige, standardmäßig
  aktive Schalter.
- Die Instrumentierungen lesen keine Minecraft-Liveobjekte und benötigen weder
  Snapshot noch Scheduler oder eigene Hintergrundthreads.
- Betriebssystem- und MXBean-abhängige Prozesssamples dürfen fehlen.
- Die in 1.8.0 nicht angebotenen CPU-Usage-, Prozess-Uptime- und
  `system_*`-Metriken werden nicht nachgebaut oder umbenannt.

Details stehen in ADR 0012.

## 9.5 In Phase 4 umgesetzt

- `server`, `worlds`, `chunks` und `world-sizes` sind getrennte verwaltete
  Collector mit den bestehenden Standardintervallen `5s`, `10s`, `10s` und
  `30m`. Server, Welten und Chunks verwenden standardmäßig
  `collection.timeout: 10s`; ausschließlich `world-sizes` verwendet den eigenen
  `collection.filesystem-timeout: 15m`.
- Periodische Erfassungen beginnen auf dem Global Region Scheduler. Globale
  Server- und Weltwerte werden dort gelesen, Spielmodi jeweils auf dem Entity
  Scheduler und Weltverzeichnisse ausschließlich auf dem Async Scheduler.
- Pro Collector läuft höchstens eine Erfassung. Timeouts, Stop und die Identität
  des aktiven Laufs verhindern die Publikation verspäteter Ergebnisse. Fehler
  löschen den letzten gültigen Snapshot nicht.
- Der Serverstartzeitpunkt ist der zu Beginn von `onEnable()` fixierte
  Plugin-Aktivierungszeitpunkt. Es werden keine internen APIs verwendet, um
  einen früheren Prozess- oder Serverstart abzuleiten.
- Spielernamen und UUIDs werden weder abgefragt noch exportiert oder in
  Erfassungsfehler übernommen. Spielmodi werden direkt in vier feste Gruppen
  aggregiert.
- `minecraft_world_loaded_chunks` basiert auf dem öffentlichen
  `World#getChunkCount()` und erzeugt keine Chunkobjekte.
- Weltgrößen sind die rekursive Summe regulärer Dateien innerhalb des
  Weltverzeichnisses. Symlinks werden nicht verfolgt, Dateifehler isoliert und
  parallele Scans desselben Pfads verhindert. Eine nach Weltname sortierte
  interne Queue begrenzt die gleichzeitig aktiven Scans über
  `filesystem.world-size-scan-concurrency` auf `1` bis `8`; der Standard `1`
  scannt sequenziell. Höhere Werte können schneller sein, erhöhen aber die
  konkurrierende I/O-Last.
- Timeout und Stop verwerfen wartende Weltgrößenscans und verspätete Ergebnisse.
  Bereits laufende Java-Dateisystemoperationen sind nicht garantiert physisch
  unterbrechbar, geben Slot und Pfad aber bei ihrer Rückkehr frei und können
  keinen Snapshot mehr publizieren. Weltpfade werden nie als Labels exportiert.
- Phase-4-Metriken werden als Prometheus-`MultiCollector` an die private Registry
  gebunden. Eine Gruppe liest pro Scrape genau einen immutable Snapshot;
  entladene Welten hinterlassen keine veralteten Labelreihen.
- `minecraft_plugin_info` bleibt standardmäßig aus. Full Time, Autosave,
  allgemeine Dateisystemmetriken sowie Event-, Entity- und Folia-Familien werden
  nicht vorgezogen.

Details stehen in ADR 0013.

## 9.6 In Phase 5 umgesetzt

- `events` ist ein einzelner standardmäßig aktiver `ManagedCollector`; bei
  Deaktivierung werden weder Listener noch Phase-5-Familien registriert.
- `PlayerLoginEvent` ist die einzige Loginquelle, weil es genau einmal an der
  finalen Loginentscheidung feuert und strukturierte `Result`-Werte bietet. Das
  stabile `AsyncPlayerPreLoginEvent` liegt vor späteren Serverprüfungen, das
  `PlayerServerFullCheckEvent` deckt nur den Full-Check ab und das experimentelle
  `PlayerConnectionValidateLoginEvent` kann in zwei Phasen feuern und besitzt
  nur eine freie Nachricht; sie werden deshalb weder einzeln als Ersatz noch in
  Kombination verwendet.
- Die Deprecation von `PlayerLoginEvent` seit 1.21.6 ist eine dokumentierte
  Einschränkung der Ziel-API. Sie ist auf die konkrete Handler-Methode begrenzt;
  der Metrikkern erhält nur den strukturierten Enumnamen. Freie Logintexte werden
  auch nicht als Fallback gelesen. Migriert wird, sobald eine stabile öffentliche
  API genau eine finale Auslieferung und strukturierte Ablehnungsgründe bietet.
- Join, Quit, Ping, modernes `AsyncChatEvent`, `PlayerKickEvent` sowie Chunk-Load
  und -Unload sind die übrigen Quellen. Chat und Kick zählen nur nicht
  abgebrochene Events bei `MONITOR`.
- Kick und ein danach ausgelöstes Quit zählen bewusst beide; es gibt keine
  nachträgliche Korrelation über Spieleridentitäten.
- Reasonwerte stammen ausschließlich aus strukturierten Enumnamen und sind auf
  neun feste Kategorien begrenzt. `TIMEOUT` erzeugt `connection_lost`, `IDLING`
  erzeugt `idle`; zukünftige strukturierte Namen `CONNECTION_LOST` und
  `NETWORK_ERROR` sind vorsorglich ebenfalls `connection_lost`. Nur
  `invalid_session` ist mit den aktuellen strukturierten Enums nicht erzeugbar
  und wird nicht über Nachrichtentexte erraten.
- Chunk-Load zählt immer Loaded und bei `isNewChunk()` zusätzlich Generated;
  Unload zählt Unloaded. Nur das gemeinsame validierte Weltlabel wird übernommen.
- Prometheus-Counter werden direkt und threadsicher auf Eventthreads erhöht. Es
  gibt keine Schedulerwechsel, Event-Snapshots oder gespeicherten
  Minecraft-Objekte.
- Listenerstart und -stop sind idempotent. Stop sperrt neue Inkremente, wartet
  kurze laufende Updates ab und meldet den Listener öffentlich über
  `HandlerList.unregisterAll` ab.
- Eventupdate-Fehler werden als `IllegalStateException` mit der ursprünglichen
  `RuntimeException` als Cause an den rate-limitierten Reporter übergeben. Auch
  dessen Fehler werden abgefangen und erreichen den Eventthread nicht.
- Counter werden nicht persistiert und können bei Serverstart oder Plugin-Reload
  zurückgesetzt werden.

Details stehen in ADR 0014.

## 9.7 In Phase 6 umgesetzt

- Die tatsächlichen gepinnten API-Artefakte bestätigen
  `Server#getRegionTPS(World,int,int)` als Folia-exklusive öffentliche
  Mess-Capability. Rückgabefenster sind `5s`, `15s`, `1m`, `5m`, `15m`.
- Es gibt keine öffentliche Regions-ID, Vollauflistung oder Lifecycle-Events.
  Der Provider misst daher ausschließlich über öffentliche Spieler-, Spawn- und
  optionale Force-Load-Anker und nennt das Ergebnis „beobachtete Regionen“.
- Anker werden auf dem besitzenden Region-Thread mit der öffentlichen
  `isOwnedByCurrentRegion`-Methode dedupliziert. Spielerzahlen entstehen aus
  neutralisierten Spielerankern ohne Name oder UUID.
- Allgemeiner Code kompiliert nur gegen Paper. Das getrennte `folia`-Source-Set
  kompiliert gegen
  `dev.folia:folia-api:26.1.2.build.8-stable` als `compileOnly`; die API wird
  nicht eingebettet und beide Ausgaben bilden ein gemeinsames JAR.
- Reflection prüft nur die öffentliche Methodensignatur und lädt danach die
  eigene Providerklasse. Servernamen, Versionen, Scheduler und interne Klassen
  sind keine Capability oder Datenquelle.
- Paper bleibt bei aktivem Collector nach genau einer Warnung `unsupported` und
  registriert keine Folia-Familie. Deaktiviert bleibt der Collector ohne Warnung
  `disabled`. Health und Readiness sind von diesem optionalen Zustand unabhängig.
- Die Registry ersetzt erfolgreiche Generationen vollständig, auch durch eine
  leere Liste, verwirft verspätete Updates und akzeptiert nach Stop nichts.
  Spieler- und Regionsfehler werden lokal übersprungen; die übrigen gültigen
  Observationen werden als Teilsnapshot publiziert. Nur systemische Fehler,
  Timeout und Stop bewahren den letzten vollständigen Snapshot bis zur TTL.
- Implementiert sind beobachtete Regionen, TPS-Verteilung, Regionen unter
  Schwellen, Regionen mit Spielern, Spieler pro Region und Alter der ältesten
  gültigen Observation.
- Quantile verwenden lineare Typ-7-Interpolation ohne Rundung. Schwellen sind
  eindeutig, in `(0,20]`, absteigend und kanonisch formatiert. Leere und
  ungültige Daten erzeugen keine Samples.
- Aktive Regionsgesamtzahl, Tickdauer, Überlastung und Tickverzögerung bleiben
  mangels öffentlicher API nicht verfügbar.
- Eine zusätzliche Statusmetrik wurde nicht eingeführt; der vorhandene
  `minecraft_exporter_collector_state` bildet `folia` vollständig ab.

Details stehen in ADR 0010 und ADR 0015.

## 9.8 In Phase 7 umgesetzt

- Zehn feste, gegenseitig ausschließende Entitygruppen werden zentral über
  öffentliche Entity-Interfaces klassifiziert. Spieler sind vor jeder
  Klassifizierung ausgeschlossen; unbekannte Typen ergeben `other`.
- Bereits vorhandene Entities werden initial und danach standardmäßig alle fünf
  Minuten über geladene Chunks abgeglichen. Der Mindestwert beträgt eine Minute,
  der eigene Standardtimeout 60 Sekunden.
- `EntityAddToWorldEvent` und `EntityRemoveFromWorldEvent` sind die einzigen
  Entity-Zustandsquellen. Welt-Load und nicht abgebrochenes Welt-Unload behandeln
  dynamische Welten ohne parallele Spawn-, Death-, Transform- oder Chunkevents.
- Globale Topologie, Chunkzugriffe und Entityeigenschaften laufen getrennt auf
  Global-, Region- und Entity-Schedulern. Ein zusätzlicher Folia-Provider ist
  nicht erforderlich.
- Ein lauflokales UUID-Deduplizierungsset und sequenziertes Eventjournal lösen
  Scan-/Event-/Commit-Rennen. Identitäten überschreiten nie die Laufgrenze.
- Standardmäßig registriert sind die zehn Gruppengauges, Nichtspieler-Gesamt-,
  Living-, Villager- und Itemaggregate sowie Dauer, letzter Erfolg und
  Korrekturen des Abgleichs.
- Projektilsumme und vollständige Namespaced Entitytypen sind unabhängig und
  standardmäßig deaktiviert. Entladene Welten und verschwundene Typen hinterlassen
  keine Reihen.
- Lokale Fehler werden neutral und rate-limitiert isoliert; systemischer Abbruch,
  Timeout und Stop publizieren keinen Teilstand. Ein erfolgreicher leerer Lauf
  entfernt den alten Stand.
- Spawn-, Removal-, Kill-, Item-Despawn- und Error-Counter wurden bewusst nicht
  eingeführt.

Details stehen in ADR 0016.

## 9.9 Vorbereitung für Phase 8

Der Metrikkatalog enthält PromQL-Grundlagen für Gruppensummen, Gesamtbestände,
Gauge-Veränderungen, `topk`-Typauswertungen, Erfolgsalter und Korrekturraten.
Vorgesehen sind Welt-/Gruppen-Zeitreihen, Stat-Panels für Gesamt-, Item- und
Villagerbestand sowie Betriebs-Panels für Dauer, Staleness, Korrekturen und
Collectorstatus. Beispielschwellen sind ausdrücklich nur Ausgangswerte und
müssen in Phase 8 an Weltgröße, Mobcaps, Sichtweite und Hardware angepasst
werden. Die optionale Typfamilie erfordert begrenzte, aggregierte Abfragen.

## 9.10 Noch offen

- gewünschte Standard-Buckets für Histogramme
- Release- und Changelog-Format

## 9.11 Nicht mehr offen

- Automatisierbarkeit eines verpflichtenden Paper- und Folia-Starttests
- feste Paper- und Folia-Serverbuilds für den Smoke-Test
- offizieller Support für Paper und Folia
- genau ein gemeinsames Plugin-JAR
- keine aktive Blockierung anderer Forks, aber kein offizieller Support
- kein klassischer BukkitScheduler-Fallback
- kein Folia-Provider und kein PlatformDetector in Phase 1 oder Phase 2
- Compile-API und Ladegrenze des späteren Folia-Providers
- Verhalten eines aktivierten Folia-Collectors auf Paper
- Prometheus-Java-Client-Version, BOM und Module
- eingebetteter HTTP-Server und Ausschluss zusätzlicher HTTP-Frameworks
- Shading und Relocation der Prometheus-Abhängigkeiten
- Shadow-Plugin-Version und automatisierte JAR-Inhaltsprüfung
- Collector-Zustandsmaschine, Start-/Stoppreihenfolge und Fehlerisolation
- Snapshot-Darstellung und atomare Repository-Operationen
- Health- und Readiness-Semantik
- Phase-2-Eigenmetriken und ihre begrenzten Labelwerte
- HTTP-Methoden- und Unknown-Path-Semantik
- Quelle und Fallback des `git_commit`-Buildlabels
- instanzgebundene Eigenmetriken ohne globalen Registry-Cache
- direkte JVM-/Prozessregistrierung ohne globale Registry oder Snapshot-Collector
- exakte offizielle JVM-/Prozessnamen und 1.8.0-Verfügbarkeitsgrenzen
- Collector-Standardintervalle und Timeout-Semantik für Phase 4
- Scheduler-Zuordnung für Server-, Welt-, Chunk- und Weltgrößenerfassung
- Definition der Serverstartzeit und der Weltgröße
- Chunkzahl über `World#getChunkCount()` ohne Chunkmaterialisierung
- Überlappungs-, Timeout- und verspätete-Ergebnis-Strategie
- dynamische Weltlabels ohne veraltete Reihen
- klassische `plugin.yml` statt `paper-plugin.yml`
- fest gepinnte `paper-api`-Koordinate und JUnit-5-Version
- Fehlerverhalten des Konfigurationsloaders bei ungültigen Werten
- keine individuellen Spielerstatistiken
- eindeutige Login-Eventquelle und Vermeidung von Doppelzählungen
- feste Login-/Kick-Reason-Normalisierung ohne freie Texte
- Kick-/Quit- und Cancelled-Chat-Semantik
- direkte threadsichere Eventinkremente ohne Schedulerwechsel
- Chunk-Generated-Semantik und gemeinsame Weltlabelvalidierung
- nicht persistenter Lifecycle der Event-Counter
- öffentliche Folia-Regions-TPS-Quelle und feste Fenster
- Beobachtungsanker, Ownership-Deduplizierung und Grenze zu aktiven Regionen
- Klassenlade- und Source-Set-Isolation des Folia-Providers
- Folia-Registry-, TTL-, Quantil-, Schwellen- und Nullsample-Semantik
- unterstützte und nicht verfügbare Phase-6-Metriken
- öffentliche Entity-Abgleichs- und Eventstrategie
- feste Gruppen, Klassifizierungspriorität und Spielerausschluss
- Entity-Journal-, Deduplizierungs-, Timeout- und Commitsemantik
- genaue Typnamen, Kardinalitätsschalter und Projektilschalter
- Definition und Begrenzung der Entity-Abgleichsmetriken
