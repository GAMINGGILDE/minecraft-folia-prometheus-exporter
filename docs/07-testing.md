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
- unabhängige Server-, Welt-, Chunk- und Weltgrößenschalter, idempotente Registrierung und mehrere private
  Registries
- Prometheus-`HELP`-, `TYPE`-, Sample- und Labelausgabe der Server-, Welt- und Chunk-Familien
- parallele Scrapes, die keine neue Minecraft-Erfassung anstoßen
- Event-Collector standardmäßig aktiv und über `collectors.events` vollständig
  deaktivierbar; deaktiviert weder Listener noch Eventfamilien
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
- Event-`HELP`-, `TYPE`-, Counter-Suffix-, Sample- und Labelausgabe über den
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
- vollständige zentrale Entityklassifizierung aller Ziel-API-Typen, exakt eine
  der zehn Gruppen, feste Priorität, unbekannte Typen als `other` und vollständiger
  Spielerausschluss
- immutable Entity-Weltsnapshots mit zehn Gruppen, konsistenten Summen,
  optionalen Typen sowie Entfernung alter Welt- und Typreihen
- Entity-Journalrennen für Event vor/nach Observation und Commit, Welt-Unload,
  Abbruch, Stop, erfolgreichen Leersnapshot, lokale Teilerfolge und
  Driftkorrekturen ohne verlorene oder doppelte Deltas
- explizite Weltzustände: initiales `UNAVAILABLE`/`PARTIAL` ohne Nullreihen,
  späterer Erhalt eines gültigen Weltwerts, gemischter Lauf mit erfolgreicher und
  fehlgeschlagener Welt, tatsächlich leere Welt mit zehn Nullgruppen sowie echte
  leere Weltenliste mit Entfernung alter Reihen
- Region-Scheduler je geladenem Chunk und Entity Scheduler je Beobachtung;
  lokale Welt-, Chunk-, Entity- und Reporterfehler bleiben isoliert
- deterministische Timeouts bei ausstehender Chunk- und Entityarbeit,
  Entity-Scheduler-Retire, Stop während eines aktiven Laufs und verspäteter alter
  Callback nach einem neueren Erfolg ohne `Thread.sleep()` als Synchronisation
- neutrale Entity-Fehlermeldungen mit ursprünglichem Exceptiontyp als Cause,
  abgefangenen Reporterfehlern und ohne UUID- oder Koordinatentext außen
- Registryausgabe aller Standardfamilien und drei begrenzter
  Reconciliation-Metriken; optionale Projektil- und Typfamilien erscheinen nur
  mit ihrem jeweiligen Schalter

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

Der Smoke-Test ruft nach der eindeutigen Aktivierung alle drei
HTTP-Endpunkte tatsächlich auf, prüft die stabilen Health-/Ready-Antworten und
erwartet die zentralen Exporter-Eigenmetriken. Nach dem kontrollierten
Server-Shutdown darf der HTTP-Listener nicht mehr antworten.
In GitHub Actions muss `minecraft_exporter_build_info` außerdem exakt den
ausgecheckten `${{ github.sha }}` enthalten.

Zusätzlich validiert derselbe gepinnte Smoke-Test die stabilen
Familien `jvm_memory_used_bytes`, `jvm_threads_current`,
`jvm_classes_currently_loaded` und `process_start_time_seconds` einschließlich
`HELP`-, `TYPE`- und Sample-Zeilen. Betriebssystemabhängige Familien wie
`process_open_fds` sind bewusst keine plattformübergreifende Assertion.

Der Smoke-Test wartet begrenzt auf den ersten Server-, Welt-, Chunk- und
Weltgrößensnapshot. Er prüft repräsentative Familien samt `HELP`, `TYPE` und
Samples, die drei kontrollierten Server-Info-Labels sowie die Abwesenheit des
standardmäßig deaktivierten `minecraft_plugin_info`. Output und
Start-/Shutdown-Log werden auf
Spielernamen-/UUID-Indikatoren beziehungsweise Threading- und Schedulerfehler
geprüft.

Der Smoke-Test setzt für seine kleine Testwelt explizit
`collection.filesystem-interval: "5s"`,
`collection.filesystem-timeout: "2m"` und
`filesystem.world-size-scan-concurrency: 1`. Seine Wartezeit bleibt unabhängig
davon auf 90 Sekunden begrenzt.

Der gepinnte Smoke-Test prüft alle zehn Eventfamilien einschließlich
`HELP` und `TYPE counter` sowie den laufenden Collectorstatus `events`. Echte
Spielerlogins werden nicht künstlich erzeugt; damit validiert der Test bewusst
Registrierung, Defaultkonfiguration und den fehlerfreien Listener-Lifecycle. Die
zuvor erwartete Abwesenheit der Eventfamilien wurde entfernt. Commands und
Chunk-Load-Failures bleiben weiterhin ausgeschlossen. Das Start-/Shutdown-Log
wird zusätzlich auf Eventregistrierungsfehler, Eventhandler-Exceptions,
Hinweise auf doppelte Listener sowie Deprecation-bedingte Laufzeitfehler geprüft.
Die bekannte dokumentierte `PlayerLoginEvent`-Deprecation allein ist kein
Laufzeitfehler; ihre Einmaligkeit wird ergänzend im Unit-Test erzwungen.

Für Folia-Metriken unterscheidet der Smoke-Test die Plattformen. Paper erwartet den
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

Für Entity-Metriken wartet der Smoke-Test auf einen erfolgreichen initialen
Entity-Abgleich. Paper und Folia müssen für jede erfasste geladene Welt genau
zehn `minecraft_entity_group_count`-Samples, die vier standardmäßigen
Weltaggregate, die drei begrenzten Reconciliation-Familien und den laufenden
Collectorstatus `entities` liefern. Anschließend lädt der Test kontrolliert den
Chunk `0/0`, erzeugt in der Standardwelt eine kurzlebige, markierte Area-Effect-
Cloud und verlangt einen Anstieg der Gruppe `other` um mindestens eins. Nach der
natürlichen Entfernung muss der Bestand wieder sinken. Damit kann ein fälschlich
leerer Initialsnapshot nicht mehr bestehen. Die standardmäßig deaktivierten
Familien `minecraft_entities` und `minecraft_world_projectiles` sowie Entity-
Lifecycle-Counter müssen fehlen. Die vollständige Paper-/Folia-Logdatei wird
präzise auf `Thread failed main thread check`,
`Cannot getEntities asynchronously`, `Cannot getLoadedChunks asynchronously`,
`not owned by current region`, TickThread-Stackframes sowie fehlerhafte Region-
oder Entity-Scheduler-Callbacks geprüft. Eine beliebige
`IllegalStateException` allein ist kein Fehlerkriterium.

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
- Entity-Welten- und Chunkanker entstehen auf dem Global Region Scheduler,
  Chunklisten werden nur über den zuständigen Region Scheduler ausgewertet und
  Entitytyp, Welt und lauflokale Identität nur auf dem Entity Scheduler gelesen
- Entity-Region- und Entitytasks warten nie blockierend aufeinander; Timeout,
  Commit und Scrape akzeptieren ausschließlich zurückgeführte immutable Werte
- `World#getLoadedChunks()` ist gegen die zu Folia Build 8 gepinnte
  `CraftWorld`-/concurrent-Chunk-Tabellen-Implementierung geprüft; der echte
  Entity-Smoke-Test sichert diese Annahme zusätzlich gegen Threadchecks ab

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
