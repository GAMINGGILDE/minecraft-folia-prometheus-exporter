# 7. Tests und Abnahmekriterien

## 7.1 Build

```bash
./gradlew clean build
```

muss erfolgreich sein.

## 7.2 Unit-Tests

- Standardwerte des Konfigurationsmodells
- ungültige Konfigurationen
- Plugin-Metadaten aus `plugin.yml`
- expandierte Pluginversion ohne verbliebenen Platzhalter
- Quantile und Statistikberechnung
- TPS-Schwellenwerte
- Dauer- und Größenparser
- Konfigurationsvalidierung
- Prometheus-Namensprüfung
- Label-Normalisierung
- Kick-/Login-Grundklassifikation
- Snapshot-Austausch
- Ablauf von Regionsbeobachtungen
- Registrierung repräsentativer JVM-/Prozessfamilien in der privaten Registry
- idempotente Registrierung und getrennte JVM-/Prozessschaltung
- zwei unabhängige Metrics Cores ohne Duplicate-Registration
- feste GameMode-, Wetter-, Schwierigkeits- und Umgebungslabels
- Server-, Plugin-, Spieler-, Welt- und Chunk-Snapshotabbildung ohne
  Spieleridentitäten
- dynamisches Hinzufügen und Entfernen von Welten ohne veraltete Labelreihen
- Weltfehler mit Aktualisierung der übrigen Welten und Erhalt des letzten
  gültigen Einzelwerts
- rekursive Weltgrößen aus regulären Dateien ohne Folgen symbolischer Links
- Standard-, Alt-Konfigurations-, Typ-, Bereichs- und Überlauftests für
  `collection.filesystem-timeout` und
  `filesystem.world-size-scan-concurrency`
- begrenzte Weltgrößenparallelität für `1` und `2`, wartende dritte Scans,
  deterministische Weltreihenfolge und niemals doppelt aktive Weltpfade
- Slotfreigabe nach Erfolg, Berechnungs- und Scheduling-Fehler sowie nach Timeout
- Timeout während wartender Scans, später erfolgreicher Folgelauf und keine
  Publikation oder neue Arbeit nach Stop
- unabhängige Weltgrößen-Captures ohne statisch geteilte Queue- oder Slotzustände
- periodische Collector: idempotenter Lifecycle, Überlappungsschutz, Timeout,
  verspätete Callback-Unterdrückung und keine Publikation nach Stop
- unabhängige Phase-4-Schalter, idempotente Registrierung und mehrere private
  Registries
- Prometheus-`HELP`-, `TYPE`-, Sample- und Labelausgabe der Phase-4-Familien
- parallele Scrapes, die keine neue Minecraft-Erfassung anstoßen
- Event-Collector standardmäßig aktiv und über `collectors.events` vollständig
  deaktivierbar; deaktiviert weder Listener noch Phase-5-Familien
- idempotenter Event-Start/-Stop, genau eine Listenerregistrierung, Abmeldung
  beim Stop und keine Inkremente nach Rückkehr von Stop
- vollständige strukturierte Login-Result- und Kick-Cause-Klassifikation,
  insbesondere `IDLING → idle`, `TIMEOUT → connection_lost`, vorsorgliche
  `CONNECTION_LOST`/`NETWORK_ERROR → connection_lost`, unbekannte Werte als
  `unknown` und exakt neun erlaubte Reasonwerte
- Login-Attempt-/Denial-, Join-, Quit-, Kick-, Ping- und nicht abgebrochene
  Chatsemantik ohne Commands oder freie Eventtexte
- genau eine registrierte Loginquelle ohne Handler für parallele Loginphasen;
  erlaubtes Event genau ein Attempt, abgelehntes Event genau ein Attempt und
  eine Denial-Reihe
- Fehlerweitergabe mit ursprünglicher `RuntimeException` als Cause, abgefangener
  Exception eines fehlschlagenden Failure-Listeners, weiter funktionsfähigem
  Collector und keinen Inkrementen nach Stop
- keine Spieleridentität, UUID, IP-Adresse, Host-, Kick-, Login- oder Chattexte
  in Eventlabels oder Exposition
- bestehender und neuer Chunkload, Generierung und Unload mit ausschließlich
  gemeinsam validiertem Weltlabel und ohne Koordinaten
- parallele Eventinkremente aus mehreren Threads und Welten mit exakt erwartetem
  Endstand aller zehn Counterfamilien
- Phase-5-`HELP`-, `TYPE`-, Counter-Suffix-, Sample- und Labelausgabe über den
  realen lokalen HTTP-Endpunkt, einschließlich paralleler Scrapes und Events
- Capability-Erkennung anhand der exakten öffentlichen
  `Server#getRegionTPS(World,int,int)`-Signatur; Paper ist `unsupported`, warnt
  genau einmal, lädt die Providerklasse nicht und registriert keine
  `minecraft_folia_*`-Familie
- deaktivierter Folia-Collector ohne Capability-Prüfung, Warnung, Providerload
  oder Familie; unterstützter Provider mit idempotentem Start und Stop
- transaktionale `RegionObservationRegistry`: parallele Updates, deterministische
  Ordnung, Laufidentität, ältere und verspätete Ergebnisse, Ablauf, vollständiger
  Ersatz einschließlich leerer erfolgreicher Generation, systemischer
  Fehlererhalt und Annahmestopp
- öffentliche Ankerdeduplizierung über aktuelle Regionsownership sowie
  aggregierte Spielerzahl ohne Identität
- lokale Spielerfehler bei Positionszugriff, Scheduling und konkurrierendem
  Retire-Callback: genau einmaliger Abschluss, kein Hängen und Fortsetzung mit
  allen gültigen Spieler-, Spawn- und Force-Load-Ankern
- lokale Regionsfehler für `null`, zu kurze Arrays, nichtfinite oder negative
  TPS-Werte, API-Exceptions, fehlende Ownership und Scheduling-Ablehnung:
  erfolgreiche übrige Observationen werden als Teilsnapshot publiziert und
  andere Regiontasks nicht storniert
- erfolgreicher leerer Folia-Snapshot ersetzt einen vorherigen Stand und entfernt
  alte Prometheus-Reihen ohne künstliche Nullwerte
- systemische Weltlisten-, Stop- und Timeoutfehler erhalten den vorherigen
  Snapshot, stornieren ausstehende Tasks und unterdrücken verspätete Publikation
- Parallelitätsrennen zwischen lokalem Regionsfehler, letztem Erfolg und Abbruch:
  kein negativer Restzähler, kein doppelter Completion-Aufruf und keine Updates
  nach Abbruch
- lokale Reporteraufrufe sind neutral, enthalten keine Identitäten oder
  Koordinaten und lassen auch bei eigener Exception keinen Schedulerthread werfen
- exakte Typ-7-Quantile für leere, einzelne, gerade und ungerade Datenmengen,
  Ausschluss ungültiger Werte sowie kanonische und deterministische Thresholds
- alle sechs Folia-Familien mit `HELP`, `TYPE`, erlaubten Labels, dynamischem
  Ablauf, ältester Observation als Alterssemantik und fehlenden Samples statt
  künstlicher Nullwerte
- getrennte allgemeine und Folia-Compile-Classpaths, Folia-Provider-Suite gegen
  die gepinnte Folia-API und Bytecode-/JAR-Prüfungen ohne interne APIs

## 7.3 Integrationsprüfungen

- Ein separater, verpflichtender GitHub-Actions-Workflow baut das Plugin mit
  Java 25 und startet es auf Paper `26.1.2` Build `74` sowie Folia `26.1.2`
  Build `8`.
- Beide Serverartefakte werden über den offiziellen PaperMC Downloads Service mit
  einem identifizierenden User-Agent geladen; Buildnummer und SHA-256 sind fest
  gepinnt.
- Der Test setzt `eula=true`, kopiert das einzige erzeugte Plugin-JAR nach
  `plugins/` und startet mit `--nogui`.
- Nur die eindeutige Meldung `FoliaPrometheusExporter started.` bestätigt die
  Aktivierung.
- Plugin-Exceptions, Ladefehler, vorzeitiges Serverende und Timeout führen zum
  Fehlschlag; danach wird der Server über `stop` kontrolliert beendet.
- `/metrics` antwortet
- `/health` und `/ready` funktionieren
- HTTP-Server stoppt beim Disable
- Collector-Fehler stoppen andere Collector nicht
- deaktivierte Collector erzeugen keine Metriken
- Snapshot-Alter steigt bei Collector-Ausfall
- keine Spieleridentitäten im Output

Seit Phase 2 ruft der Smoke-Test nach der eindeutigen Aktivierung alle drei
HTTP-Endpunkte tatsächlich auf, prüft die stabilen Health-/Ready-Antworten und
erwartet die zentralen Exporter-Eigenmetriken. Nach dem kontrollierten
Server-Shutdown darf der HTTP-Listener nicht mehr antworten.
In GitHub Actions muss `minecraft_exporter_build_info` außerdem exakt den
ausgecheckten `${{ github.sha }}` enthalten.

Seit Phase 3 validiert derselbe gepinnte Smoke-Test zusätzlich die stabilen
Familien `jvm_memory_used_bytes`, `jvm_threads_current`,
`jvm_classes_currently_loaded` und `process_start_time_seconds` einschließlich
`HELP`-, `TYPE`- und Sample-Zeilen. Betriebssystemabhängige Familien wie
`process_open_fds` sind bewusst keine plattformübergreifende Assertion.

Seit Phase 4 wartet der Smoke-Test begrenzt auf den ersten Server-, Welt-, Chunk-
und Weltgrößensnapshot. Er prüft repräsentative Phase-4-Familien samt `HELP`,
`TYPE` und Samples, die drei kontrollierten Server-Info-Labels sowie die
Abwesenheit des standardmäßig deaktivierten `minecraft_plugin_info`. Im
Phase-4-Stand durften zusätzlich noch keine Event-, Entity- oder Folia-Familien
erscheinen. Output und Start-/Shutdown-Log werden auf
Spielernamen-/UUID-Indikatoren beziehungsweise Threading- und Schedulerfehler
geprüft.

Der Smoke-Test setzt für seine kleine Testwelt explizit
`collection.filesystem-interval: "5s"`,
`collection.filesystem-timeout: "2m"` und
`filesystem.world-size-scan-concurrency: 1`. Seine Wartezeit bleibt unabhängig
davon auf 90 Sekunden begrenzt.

Seit Phase 5 prüft der gepinnte Smoke-Test alle zehn Eventfamilien einschließlich
`HELP` und `TYPE counter` sowie den laufenden Collectorstatus `events`. Echte
Spielerlogins werden nicht künstlich erzeugt; damit validiert der Test bewusst
Registrierung, Defaultkonfiguration und den fehlerfreien Listener-Lifecycle. Die
zuvor erwartete Abwesenheit der Eventfamilien wurde entfernt. Commands und
Chunk-Load-Failures bleiben weiterhin ausgeschlossen. Das Start-/Shutdown-Log
wird zusätzlich auf Eventregistrierungsfehler, Eventhandler-Exceptions,
Hinweise auf doppelte Listener sowie Deprecation-bedingte Laufzeitfehler geprüft.
Die bekannte dokumentierte `PlayerLoginEvent`-Deprecation allein ist kein
Laufzeitfehler; ihre Einmaligkeit wird ergänzend im Unit-Test erzwungen.

Seit Phase 6 unterscheidet der Smoke-Test die Plattformen. Paper erwartet den
Collectorstatus `unsupported`, genau eine Capability-Warnung, erfolgreiche
Health/Readiness, keine Provider-Linkagefehler und keine
`minecraft_folia_*`-Familie. Folia erwartet `running`. Existiert mindestens eine
beobachtbare Region, werden alle sechs Familien samt `HELP`, `TYPE` und gültigen
Samples geprüft. Ohne beobachtbare Region dürfen die dynamischen Familien im
Textformat vollständig fehlen. Dies schließt beim Prometheus Java Client 1.8.0
auch `HELP` und `TYPE` ein; die Registrierung aller sechs Descriptoren wird
stattdessen im Provider-/Prometheus-Integrationstest erzwungen. Der Smoke-Test
verlangt ausdrücklich keinen Wert `minecraft_folia_observed_regions > 0`.
Laufender Collector, fehlerfreier Start, Health/Readiness, erlaubte Labels,
fehlende experimentelle Familien sowie das Fehlen nichtfiniter oder negativer
Samples bleiben auch beim leeren Snapshot die Abnahme.

## 7.4 Threading-Prüfungen

- keine Weltzugriffe aus HTTP-Threads
- keine Entityzugriffe aus Common Pool
- regionsgebundene Aufrufe über Region Scheduler
- Entity-Aufrufe über Entity Scheduler
- Dateisystemoperationen nicht auf Tickthreads
- kein Fallback auf den klassischen BukkitScheduler
- direkte threadsichere Eventinkremente ohne Schedulerwechsel
- Chat-, Login-, Ping- und Chunkhandler dürfen auf unterschiedlichen Threads
  parallel laufen; Stop wartet kurze laufende Inkremente ab
- Folia-Ankerlisten und Spawn-/Force-Load-Werte nur auf dem Global Region
  Scheduler, Spielerpositionen nur auf dem Entity Scheduler und Ownership/TPS
  nur auf dem Region Scheduler
- Folia-Regionthreads warten niemals blockierend auf andere Anker; Timeout und
  Scrape laufen außerhalb der Regionthreads

## 7.5 Performance-Ziele

- Scrape serialisiert ausschließlich Snapshots
- keine vollständigen Entity-Scans pro Scrape
- keine Weltgrößenberechnung pro Scrape
- Collector-Laufzeiten werden gemessen
- konfigurierbare Intervalle und Timeouts

## 7.6 Definition of Done

- Code formatiert und dokumentiert
- Tests erfolgreich
- Metrikkatalog aktualisiert
- keine neue unkontrollierte Label-Kardinalität
- keine individuellen Spielermetriken
- öffentliche API im stabilen Kern
- Fehlermeldungen verständlich

Die Tests dürfen Symlink-Fälle nur dann überspringen, wenn das ausführende
Betriebssystem die für den Test nötige Linkerzeugung nicht erlaubt. Das
Produktionsverhalten bleibt unabhängig davon: Symlinks werden nie verfolgt.

## 7.7 Abnahme Phase 1

- `./gradlew clean build` ist erfolgreich.
- Der Gradle Wrapper verwendet Gradle 9.6.1 und prüft die hinterlegte
  Distribution-Checksumme.
- Java Toolchain und `JavaCompile.options.release` stehen auf 25.
- Allgemeiner Code kompiliert gegen die öffentliche `paper-api`.
- Das Build erzeugt genau ein Plugin-JAR.
- Das JAR enthält eine klassische `plugin.yml`, keine `paper-plugin.yml`.
- `folia-supported: true` ist gesetzt.
- `api-version` ist anhand der öffentlichen Paper-API verifiziert; für die
  API-Linie 26.1.2 ist der bestätigte Wert `26.1.2`.
- Der Descriptor wird zusätzlich durch den separaten, verpflichtenden Starttest
  auf den fest gepinnten Paper- und Folia-Builds geprüft.
- Die Descriptor-Version ist `0.1.0-SNAPSHOT` und enthält keinen Platzhalter.
- Tests decken Standardwerte, ungültige Konfigurationen, Plugin-Metadaten und die
  expandierte Pluginversion ab.
- Es existieren keine Collector, HTTP-Endpunkte, konkreten Metriken,
  Folia-Provider oder vorsorglichen PlatformDetector.

## 7.8 Abnahme Architektur vor Phase 2

- Normaler Build und Server-Smoke-Test sind getrennte GitHub-Actions-Workflows.
- Der Smoke-Test verwendet keine dynamische Latest-Version.
- Prometheus-Client, HTTP-Lifecycle, Shading und Relocation sind durch ADR 0011
  verbindlich festgelegt.
- Phase 2 führt keinen Folia-Metrikprovider ein.
- Capability-Erkennung und Paper-Verhalten des späteren Folia-Collectors sind
  durch ADR 0010 verbindlich festgelegt.

## 7.9 Abnahme Phase 2

- Prometheus Java Client 1.8.0 wird über die BOM eingebunden.
- Core, JVM-Instrumentierungsmodul und HTTP-Exporter sind im einzigen Shadow-JAR
  enthalten; JVM- und Prozessmetriken sind noch nicht registriert.
- `io.prometheus` ist nach
  `de.minecraftgilde.prometheus.internal.prometheus` relocatet.
- Der Build prüft Descriptor, Hauptklasse, Relocation, ausgeschlossene
  Server-APIs, Signaturdateien und genau ein JAR.
- Collector-Zustandswechsel, Mehrfachstart, idempotenter Stop, doppelte Namen,
  Start-/Stoppreihenfolge und Fehlerisolation sind durch Unit-Tests abgedeckt.
- Snapshots kopieren Sammlungen defensiv; atomische Publikation, Alter, Entfernen
  und paralleles Lesen sind getestet.
- Jede Metrics-Core-Instanz besitzt eine private Registry und registriert ihren
  instanzgebundenen Eigenmetrik-Satz genau einmal; ein statischer Cache existiert
  nicht.
- Buildinformationstests decken expandierte Commit-Hashes sowie `unknown` bei
  fehlender, ungültiger oder nicht lesbarer Buildresource ab.
- Der Build funktioniert auch ohne Git-Kontext und bettet dann kontrolliert
  `git_commit="unknown"` ein.
- `/metrics`, `/health`, `/ready`, `404`, `405`, parallele Requests, Readiness vor
  und nach Initialisierung, belegte Ports und Portfreigabe nach Shutdown sind
  durch lokale Integrationstests abgedeckt.
- Der Plugin-Lifecycle setzt Readiness vor dem Shutdown zurück und räumt auch nach
  einem nur teilweise erfolgreichen Start auf.
- HTTP-Handler referenzieren keine Minecraft-Liveobjekte und lesen nur Registry
  und atomaren Exporterstatus.

## 7.10 Abnahme Phase 3

- Die offiziellen Instrumentierungen `JvmMemoryMetrics`,
  `JvmGarbageCollectorMetrics`, `JvmThreadsMetrics`, `JvmClassLoadingMetrics`,
  `JvmBufferPoolMetrics` und `ProcessMetrics` des Clients 1.8.0 hängen direkt an
  der privaten Registry jeder `MetricsCore`-Instanz.
- Die Registrierung ist vor Readiness abgeschlossen, idempotent und verwendet
  weder Default-Registry noch globale oder statische Registry-Zustände.
- `collectors.jvm` und `collectors.process` sind unabhängig schaltbar und
  standardmäßig aktiv.
- Unit-Tests decken Speicher, GC, Threads, Klassen, Buffer Pools und Prozess ab;
  der reale lokale `/metrics`-Test prüft die Prometheus-Textnamen einschließlich
  Counter-/Summary-Suffixen.
- Zwei unabhängige Core-Instanzen registrieren ohne Duplicate-Registration.
- Der Shadow-JAR-Test prüft die sechs benötigten relocateten
  Instrumentierungsklassen und weiterhin das Fehlen unrelocateter
  `io/prometheus/...`-Klassen.
- Der Paper-/Folia-Smoke-Test bleibt auf Paper 26.1.2 Build 74 und Folia 26.1.2
  Build 8 gepinnt und prüft vier stabile Phase-3-Familien.
- Betriebssystem- oder MXBean-abhängige Prozesssamples dürfen fehlen. Die in
  Client 1.8.0 nicht angebotenen CPU-Usage-, Prozess-Uptime- und
  `system_*`-Metriken werden nicht nachgebaut.
- Es existieren weiterhin keine Server-, Spieler-, Welt-, Event-, Entity- oder
  Folia-Regionsmetriken.

## 7.11 Abnahme Phase 4

- Die vier verwalteten Collector `server`, `worlds`, `chunks` und `world-sizes`
  sind konfigurationsabhängig registriert und nutzen getrennte immutable
  Snapshot-Repositories.
- Server- und Weltzugriffe laufen über den Global Region Scheduler;
  `Player#getGameMode()` läuft ausschließlich über den jeweiligen Entity
  Scheduler; Weltgrößen und Timeout-Wächter laufen asynchron.
- Der HTTP-Thread liest nur die private Prometheus-Registry und bereits
  publizierte Snapshots. Parallele Scrapes führen keine Liveabfragen aus.
- Laufende Erfassungen überlappen nicht. Timeout, verspätete Ergebnisse und Stop
  können keinen alten oder nachträglichen Snapshot publizieren; der letzte
  gültige Snapshot bleibt bei Laufzeitfehlern erhalten.
- Server-, Welt- und Chunk-Collector verwenden weiter `collection.timeout`.
  Ausschließlich `world-sizes` verwendet den eigenen Standard
  `collection.filesystem-timeout: "15m"` für Queue, Scans und Publikation.
- Die nach Weltname sortierte interne Queue hält höchstens die konfigurierte Zahl
  aktiver Dateisystemscans. Standard `1` scannt sequenziell; gültig sind `1` bis
  `8`. Fehler und Scheduling-Ablehnungen geben Slots frei und blockieren keine
  Folgewelt.
- Timeout und Stop verwerfen wartende Scans. Bereits laufende, nicht garantiert
  unterbrechbare Java-Dateisystemoperationen dürfen zurückkehren, publizieren
  aber nichts und starten für den ungültigen Lauf keine weitere Arbeit.
- Geladene Welten werden dynamisch abgebildet. Entladene Welten verschwinden aus
  allen Phase-4-Weltfamilien; ein Fehler einer Welt blockiert die übrigen nicht.
- `minecraft_world_loaded_chunks` verwendet nur `World#getChunkCount()` und
  materialisiert keine Chunkobjekte.
- Weltgrößen summieren reguläre Dateien rekursiv, folgen keinen Symlinks und
  werden nicht im Tick- oder HTTP-Thread berechnet.
- Alle in Phase 4 vereinbarten Server-, aggregierten Spieler-, Plugin-, Welt-,
  Chunk- und Weltgrößenfamilien sind im Metrikkatalog und in HTTP-Tests
  abgedeckt. Optionale oder spätere Familien werden nicht registriert.
- Spielername und UUID werden weder in Metriksnapshots noch in Fehlerlogs
  übernommen.
- Der gepinnte Paper-/Folia-Smoke-Test prüft die Phase-4-Familien und einen
  sauberen Start/Shutdown ohne Scheduler- oder Threadingfehler.

## 7.12 Abnahme Phase 5

- Genau die zehn vereinbarten Eventfamilien sind standardmäßig registriert;
  `minecraft_commands_total` und `minecraft_chunk_load_failures_total` fehlen.
- `collectors.events: false` registriert weder Listener noch Phase-5-Familien
  und beeinträchtigt die übrigen Collector nicht.
- `PlayerLoginEvent` ist die einzige Loginquelle. Attempt steigt einmal pro
  Event; ein Denial steigt zusätzlich einmal mit festem Reasonwert. Die seit
  1.21.6 deprecated Quelle ist nur an der Handler-Methode unterdrückt und besitzt
  einen dokumentierten Migrationspunkt zu einer künftigen stabilen, einzelnen
  finalen Quelle mit strukturierten Gründen.
- Kicks verwenden ausschließlich `PlayerKickEvent.Cause`. Ein nachfolgendes
  `PlayerQuitEvent` zählt unabhängig zusätzlich das tatsächliche Sitzungsende.
  `TIMEOUT` zählt als `connection_lost`, `IDLING` als `idle`.
- Nur nicht abgebrochene moderne `AsyncChatEvent`s zählen. Commands und
  Systemnachrichten besitzen keinen Handler im Event-Collector.
- Neue Chunks erhöhen Loaded und Generated, bestehende nur Loaded; Unload erhöht
  Unloaded. Ausschließlich `world` wird als Chunklabel exportiert.
- Der Event-Stop meldet den Listener ab und schließt über einen Lock aus, dass
  nach seiner Rückkehr weitere Counterinkremente erfolgen.
- Fehlgeschlagene Eventupdates bleiben auf dem Eventthread abgefangen und werden
  mit ihrer ursprünglichen Exception als Cause rate-limitiert gemeldet; auch ein
  fehlschlagender Fehlerbeobachter kann nicht nach außen werfen.
- Parallelitätstests verlieren keine Inkremente; parallele HTTP-Scrapes lösen
  weder Schedulerwechsel noch Minecraft-Livezugriffe aus.
- Counter sind nicht persistent, beginnen bei Start bei null und sind für
  `rate()` beziehungsweise `increase()` vorgesehen.
- Der gepinnte Paper-/Folia-Smoke-Test bestätigt Familienregistrierung,
  Collectorstatus und sauberen Listener-Lifecycle auf beiden Plattformen.

## 7.13 Abnahme Phase 6

- Das tatsächliche Folia-API-Artefakt 26.1.2 Build 8 ist untersucht und in
  ADR 0015 dokumentiert. Einzige Mess-Capability ist die öffentliche Signatur
  `Server#getRegionTPS(World,int,int)` mit fünf festen Fenstern.
- Allgemeiner Code kompiliert ohne Folia-API gegen Paper. Der konkrete Provider
  kompiliert in einem getrennten Source-Set gegen Folia als `compileOnly`; beide
  Ausgaben landen in genau einem Shadow-JAR, die Folia-API selbst nicht.
- Paper löst die Capability nicht auf, lädt den konkreten Provider nicht, setzt
  den Collector auf `unsupported`, warnt einmal und lässt Plugin, Health und
  Readiness weiterlaufen. Deaktivierung bleibt warnungsfrei `disabled`.
- Beobachtungsanker stammen nur aus öffentlichen Spieler-, Spawn- und optionalen
  Force-Load-Quellen. Aktuelle Ownership dedupliziert sie je Region; es wird
  keine vollständige aktive Regionszahl behauptet.
- Registry, periodischer Collector und Snapshot besitzen Überlappungsschutz,
  Timeout, Laufidentität, verspätete-Ergebnis-Unterdrückung, Stale-Ablauf und
  idempotenten Stop. Lokale Spieler- und Regionsfehler werden einzeln
  abgeschlossen und übersprungen; ein erfolgreicher Teil- oder Leersnapshot
  ersetzt den vorherigen Stand. Nur systemische Laufabbrüche behalten den
  letzten vollständigen Snapshot.
- Die sechs implementierten Familien verwenden nur `world`, `window`, `stat`
  und `threshold`. Spieleridentitäten sowie Chunk-/Regionskoordinaten fehlen in
  Labels, Samples und Logs.
- Quantile verwenden deterministische Typ-7-Interpolation ohne Rundung;
  Schwellenwerte sind eindeutig, absteigend und kanonisch formatiert. Leere oder
  ungültige Daten erzeugen keine erfundenen Samples.
- Ein erfolgreicher leerer Snapshot entfernt alte dynamische Folia-Reihen,
  während Collectorstatus `running`, Health und Readiness unverändert bleiben.
- Der Folia-Smoke-Test verlangt keine beobachtete Region; erst bei einem Wert
  größer null sind Samples aller sechs implementierten Familien verpflichtend.
- Aktive Regionsgesamtzahl, regionale Tickdauer, Überlastung und Tickverzögerung
  werden mangels öffentlicher API nicht registriert.
- `./gradlew clean build`, `./gradlew test`, `./gradlew foliaTest` sowie die
  Architektur-, Dependency-, Shadow-JAR- und gepinnten Smoke-Prüfungen sind die
  vollständige Abnahme.
