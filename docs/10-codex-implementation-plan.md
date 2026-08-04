# 10. Codex-Umsetzungsplan

Codex soll nicht das gesamte Plugin in einem einzigen Auftrag erzeugen.

## Phase 1 – Projektgerüst

Status: abgeschlossen.

- Gradle Wrapper 9.6.1 mit Distribution-Checksumme
- Gradle Kotlin DSL
- Java Toolchain 25 und `JavaCompile.options.release = 25`
- öffentliche `paper-api` als `compileOnly`
- genau ein gemeinsames Plugin-JAR
- klassische `plugin.yml` mit `folia-supported: true`
- verifiziertes `api-version: '26.1.2'`
- Entwicklungsversion `0.1.0-SNAPSHOT`
- Ressourcenexpansion der Projektversion
- Hauptklasse
- immutable Konfigurationsmodelle
- getrennte Loader- und Validator-Komponenten
- JUnit 5
- GitHub-Actions-Build
- separater verpflichtender GitHub-Actions-Server-Smoke-Test
- fest gepinnter Paper-Starttest: 26.1.2 Build 74
- fest gepinnter Folia-Starttest: 26.1.2 Build 8
- Download über den offiziellen PaperMC Downloads Service mit identifizierendem
  User-Agent und SHA-256-Prüfung
- kontrollierter Serverstart, Aktivierungsprüfung und Shutdown
- Tests für Standardwerte, ungültige Konfigurationen, Plugin-Metadaten und
  expandierte Pluginversion

Nicht Bestandteil von Phase 1:

- Collector
- HTTP-Endpunkte
- konkrete Metriken
- Folia-spezifischer Provider
- vorsorglicher PlatformDetector oder Feature-Detector

Abnahme: `./gradlew clean build`; der erzeugte Descriptor enthält die konkrete
Version und keinen nicht expandierten Platzhalter. Der getrennte Smoke-Workflow
bestätigt die eindeutige Pluginaktivierung auf beiden gepinnten Zielservern und
beendet sie anschließend kontrolliert.

## Phase 2 – Metrics Core

Status: abgeschlossen.

- offizieller Prometheus Java Client 1.8.0 über
  `io.prometheus:prometheus-metrics-bom`
- Module `prometheus-metrics-core`,
  `prometheus-metrics-instrumentation-jvm` und
  `prometheus-metrics-exporter-httpserver`
- genau ein schattiertes Plugin-JAR
- Relocation von `io.prometheus` nach
  `de.minecraftgilde.prometheus.internal.prometheus`
- Collector-Interface
- threadsichere Zustandsmaschine mit isolierter Initialisierung und idempotentem
  Stop
- Snapshot-Modell
- lockfreies `SnapshotRepository` auf Basis einer `AtomicReference`
- `CollectorCoordinator` mit deterministischem Start, umgekehrtem Stop und
  Fehlerisolation
- offizieller Prometheus-`MetricsHandler` auf dem vorgesehenen JDK-HTTP-Server;
  kein eigener Text-Renderer und kein zusätzliches HTTP-Framework
- Bindung an konfigurierte Host-, Port-, Pfad- und Workerwerte mit Standardhost
  `127.0.0.1`
- Standardpfade `/metrics`, `/health` und `/ready`
- sauberer HTTP-Shutdown beim Plugin-Disable
- HTTP-Threads lesen ausschließlich immutable Snapshots und kontrollierten
  Exporterstatus
- Exporter-Eigenmetriken
- Git-Commit aus einer beim Build expandierten Resource mit `unknown`-Fallback
- genau ein instanzgebundener Eigenmetrik-Satz je privater Metrics-Core-Registry
- Unit-, Parallelitäts- und HTTP-Integrationstests
- erweiterter Paper-/Folia-Smoke-Test für alle drei HTTP-Endpunkte und Shutdown
- automatische Inhaltsprüfung des auslieferbaren JARs

Nicht Bestandteil von Phase 2:

- Folia-Metrikprovider
- Folia-Plattformerkennung
- Registrierung der JVM- und Prozessmetriken aus Phase 3
- zusätzliche Metriken außerhalb der für Phase 2 festgelegten
  Exporter-Eigenmetriken

Abnahme: HTTP-Ausgabe ohne Minecraft-Livezugriffe; `/metrics`, `/health` und
`/ready` funktionieren; der HTTP-Server stoppt beim Disable; das einzige
Plugin-JAR enthält die relocateten Prometheus-Bibliotheken. Diese Abnahme ist mit
Phase 2 erfüllt.

## Phase 3 – JVM und Prozess

Status: abgeschlossen.

- offizielle Prometheus-Java-Client-1.8.0-Instrumentierungen für JVM-Speicher,
  GC, Threads, Klassen und Buffer Pools
- offizielle Prozessinstrumentierung für CPU-Zeit und Prozessstart
- betriebssystem-/MXBean-abhängige Dateideskriptoren
- Linux-abhängiger residenter und virtueller Speicher
- direkte, idempotente Registrierung in der privaten Registry jedes Metrics Core
- unabhängige, standardmäßig aktive Schalter `collectors.jvm` und
  `collectors.process`
- keine Minecraft-Liveobjekte, Scheduler oder Snapshots für JDK-Laufzeitdaten
- keine Nachimplementierung der in Client 1.8.0 fehlenden CPU-Usage-,
  Prozess-Uptime- oder `system_*`-Metriken
- Unit-, HTTP-, Shadow-JAR- und gepinnte Paper-/Folia-Smoke-Prüfungen
- Architekturentscheidung in ADR 0012

Abnahme: Phase 3 ist abgeschlossen. Die standardisierten JVM-/Prozessmetriken
werden vom bestehenden `/metrics`-Endpunkt ausgegeben.

## Phase 4 – Server und Welten

Status: abgeschlossen.

- Server-Info, Online-Mode, Hardcore, View- und Simulation Distance
- Serverstart als Plugin-Aktivierungszeit und daraus berechnete Uptime
- aggregierte Online-, Maximal-, bekannte, Whitelist-, Ban- und Operatorzahlen
- feste aggregierte Spielmodusgruppen über Entity Scheduler, ohne Identitäten
- Plugin-Summen und standardmäßig deaktivierte Plugin-Info
- dynamische Weltinformationen mit Spielern, Zeit, Border, Wetter,
  Schwierigkeit, Umgebung und PVP
- geladene Chunks über `World#getChunkCount()` ohne Chunkobjekte
- Weltgröße asynchron als Summe regulärer Dateien, ohne Symlink-Folgen
- eigener Weltgrößen-Timeout `collection.filesystem-timeout` mit Standard `15m`;
  der allgemeine `collection.timeout` bleibt Server, Welten und Chunks vorbehalten
- deterministische interne Scan-Warteschlange mit konfigurierbarer Parallelität
  `filesystem.world-size-scan-concurrency` von `1` bis `8` und Standard `1`
- aktive Invalidierung bei Timeout und Stop: keine neue Queue-Arbeit und keine
  Publikation verspäteter Ergebnisse; bereits laufende Java-Dateisystemaufrufe
  müssen nicht physisch unterbrechbar sein
- `PaperCollectionScheduler` ausschließlich auf öffentlichen gemeinsamen
  Global-, Region-, Entity- und Async-Schedulern
- vier unabhängige verwaltete Snapshot-Collector mit Überlappungsschutz,
  Timeout, Laufidentität und Erhalt des letzten gültigen Snapshots
- private, idempotente Prometheus-Registrierung ohne globale Registry
- dynamische Weltlabels ohne veraltete Reihen
- Konfigurationsvalidierung für Tick-/Millisekundenintervalle und Überläufe
- Unit-, HTTP-, Build- und gepinnte Paper-/Folia-Smoke-Prüfungen
- Architekturentscheidung in ADR 0013

Nicht Bestandteil von Phase 4:

- Events und Chunk-Lifecycle-Counter
- Entityzählungen
- Folia-Regionsmetriken oder ein Folia-Provider
- optionale Weltwerte Full Time und Autosave
- allgemeine Dateisystem-, Log- oder Pluginverzeichnisgrößen

Abnahme: Phase 4 ist abgeschlossen. Der `/metrics`-Endpunkt serialisiert nur
immutable Snapshots; sämtliche Minecraft-Zugriffe beachten die gemeinsame
öffentliche Paper-/Folia-Scheduler-API. Phase 5 „Events“ ist der nächste Umfang.

## Phase 5 – Events

Status: abgeschlossen.

- einzelner standardmäßig aktiver `events`-Collector ohne periodische Tasks
- finaler Loginversuch und strukturierte Ablehnung über genau eine
  `PlayerLoginEvent`-Quelle ohne phasenübergreifende Doppelzählung
- Join, Quit und nicht abgebrochene Kicks; Kick und nachfolgendes Quit zählen
  unabhängig
- Serverlisten-Ping ohne Client- oder Antwortdaten
- moderner `AsyncChatEvent` bei `MONITOR`, nur nicht abgebrochene Spielerchats;
  keine Commands, Systemnachrichten oder Inhalte
- Chunk-Load, -Unload und -Generated; ein neuer Chunk erhöht Loaded und Generated
- ausschließlich gemeinsam validierte Weltlabels, keine Koordinaten
- neun feste Reason-Kategorien aus strukturierten Result-/Cause-Enumnamen;
  unbekannte Werte konservativ `unknown`, keine Nachrichtenfallbacks
- direkte threadsichere Counterinkremente auf Eventthreads ohne Schedulerwechsel
- idempotente Listenerregistrierung und -abmeldung, keine Inkremente nach Stop
- `collectors.events: false` entfernt Listener und sämtliche Phase-5-Familien
- nicht persistente Counter mit dokumentierter `rate()`-/`increase()`-Semantik
- Unit-, Parallelitäts-, HTTP-, Build- und gepinnte Paper-/Folia-Smoke-Prüfungen
- Architekturentscheidung in ADR 0014

Nicht Bestandteil von Phase 5:

- Command-Metriken
- Chunk-Load-Failures
- Spieler-, Connection- oder Chunkidentitäten
- freie Reason-Texte
- Counter-Persistenz

Abnahme: Phase 5 ist abgeschlossen.

## Phase 6 – Folia Regions-TPS

Status: abgeschlossen.

- tatsächliche Untersuchung der gepinnten Paper-/Folia-API-Artefakte und
  Dokumentation in ADR 0015
- exakte Capability `Server#getRegionTPS(World,int,int)` mit den festen Fenstern
  `5s`, `15s`, `1m`, `5m`, `15m`
- isolierter Provider unter `de.minecraftgilde.prometheus.folia.provider` in
  einem getrennten Source-Set
- `dev.folia:folia-api:26.1.2.build.8-stable` ausschließlich als `compileOnly`
  für dieses Source-Set; allgemeiner Code bleibt auf der Paper-API
- ein gemeinsames Shadow-JAR ohne eingebettete Folia-API, NMS oder interne
  `RegionizedServer`-Klassen
- reflektive Capability-/Factory-Grenze ohne statische Providerreferenz im
  gemeinsamen Bootstrap
- Paper: genau eine Warnung, Status `unsupported`, keine Providerladung oder
  Folia-Familie; deaktiviert warnungsfrei `disabled`
- öffentliche Spieler-, Spawn- und optionale Force-Load-Beobachtungsanker mit
  Ownership-Deduplizierung auf Region-Threads
- transaktionale `RegionObservationRegistry` mit Laufidentität, Parallelität,
  Ablauf, Stop-Grenze und Erhalt des letzten vollständigen Snapshots
- lokale Fehlerisolation je Spieleranker und Regionsbeobachtung mit genau
  einmaligem Abschluss, neutraler rate-limitierter Meldung und fortgesetzten
  übrigen Scheduler-Tasks
- erfolgreiche Teilsnapshots aus allen gültigen Observationen sowie erfolgreiche
  leere Snapshots, die den vorherigen Stand ersetzen und alte Reihen entfernen;
  nur systemische Laufabbrüche erhalten den letzten gültigen Snapshot
- atomare Kopplung von Registry-Commit und Collector-Erfolgsannahme gegen Rennen
  mit Timeout, Stop und verspäteten Callbacks
- exakte Typ-7-Quantile, feste Statistik- und Fenstermengen sowie kanonische
  Schwellenwerte
- implementierte Familien für beobachtete Regionen, TPS-Verteilung, Regionen
  unter Schwellen, Regionen mit Spielern, Spieler je Region und Alter der
  ältesten gültigen Observation
- keine vollständige aktive Regionszahl, Tickdauer, Überlastung oder
  Tickverzögerung mangels öffentlicher API; keine künstlichen Nullwerte
- Unit-, Parallelitäts-, Konfigurations-, Prometheus-, Classpath-, Bytecode-,
  Shadow-JAR- und getrennte Paper-/Folia-Smoke-Prüfungen
- Folia-Smoke-Test ohne Pflicht zu einer existierenden beobachtbaren Region;
  fehlende Textfamilien bei leerem Client-Snapshot sind gültig
- keine Spieleridentitäten oder Chunk-/Regionskoordinaten im Output oder Log

Abnahme: Phase 6 ist abgeschlossen.

## Phase 7 – Entities

Status: abgeschlossen.

- zehn feste Gruppen `monster`, `animal`, `ambient`, `water`, `villager`,
  `item`, `projectile`, `vehicle`, `display` und `other`
- zentrale deterministische Klassifizierung über öffentliche Entity-Interfaces
  mit fest dokumentierter Priorität und vollständigem Spielerausschluss
- Standardgauge `minecraft_entity_group_count` mit zehn Samples je gültiger Welt
- Weltaggregate für Nichtspieler-Entities, Living-Entities, Villager und
  gedroppte Items
- getrennt schaltbare Projektilsumme und genaue vollständige Namespaced Typen,
  beide standardmäßig aus
- Listener vor Initialabgleich; ausschließlich Add-/Remove-to/from-World als
  symmetrische Entityquelle sowie Welt-Load/-Unload
- sofortiger Initialabgleich und periodischer Folgeabgleich mit eigenem
  Fünf-Minuten-Intervall, Ein-Minuten-Mindestwert und 60-Sekunden-Timeout
- globale Welten-/Chunkanker, Chunkauswertung auf Region Schedulern und
  Entitybeobachtung auf Entity Schedulern ohne blockierendes Warten
- immutable Weltaggregate, atomare Publikation, erfolgreiche Leersnapshots und
  Entfernung alter Welt-/Typreihen
- Run-ID, Überlappungsschutz, kurzlebige UUID-Deduplizierung und sequenziertes
  Eventjournal für race-sichere Scan-/Event-/Commit-Zusammenführung
- lokale Fehlerisolation sowie Erhalt des letzten Standes bei systemischem
  Fehler, Timeout oder Stop
- begrenzte Laufzeit-, Erfolgszeitpunkt- und Korrekturmetriken ohne Welt- oder
  Fehlerlabels
- Konfigurations-, Klassifizierungs-, Snapshot-, Event-, Parallelitäts-,
  Prometheus-, Lifecycle-, Build- und gepinnte Paper-/Folia-Smoke-Prüfungen
- Architekturentscheidung in ADR 0016

Nicht Bestandteil von Phase 7:

- Spawn-, Removal-, Kill- und Item-Despawn-Counter
- Spieler-, UUID-, Namens-, Koordinaten-, Besitzer-, Item- oder Plugindaten
- persistente Entitybestände
- NMS, interne APIs oder ein vorsorglicher zusätzlicher Folia-Provider

Abnahme: Phase 7 ist abgeschlossen. Phase 8 „Dokumentation und Dashboard“ ist
der nächste Umfang.

## Phase 8 – Dokumentation und Dashboard

- Installationsanleitung
- Alloy-Beispiel
- Prometheus-Beispiel
- Grafana-Dashboard
- Alert-Regeln
- Releaseworkflow
