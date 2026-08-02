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

- Login
- Join
- Quit
- Kick
- Serverlisten-Ping
- Chat-Zähler
- Chunk-Events
- feste Reason-Kategorien

## Phase 6 – Folia Regions-TPS

- isolierter Folia-Provider im eigenen Package
- `dev.folia:folia-api:26.1.2.build.8-stable` als `compileOnly`
- Capability-Erkennung ausschließlich über die konkret benötigte öffentliche
  Folia-API; keine Servernamen oder Versionsstrings
- keine Verwendung von NMS, internen Klassen oder
  `io.papermc.paper.threadedregions.RegionizedServer`
- Providerklasse wird auf Paper vor erfolgreicher Capability-Prüfung nicht geladen
- aktivierter Folia-Collector auf Paper: einmalige Warnung, Status `unsupported`,
  kein Start des Collectors, fortgesetzter Pluginstart und keine Folia-Nullwerte
- RegionObservationRegistry
- Beobachtungsquellen
- TPS-Abfrage
- Fenster
- Quantile
- Schwellenwerte
- Snapshot-Alter
- keine Spieleridentitäten im Output

## Phase 7 – Entities

- Gruppen
- eventbasierte Aktualisierung
- periodischer Abgleich
- optionale genaue Typen
- Laufzeitmessung

## Phase 8 – Optionales Gameplay

- nur aggregiert
- standardmäßig aus
- keine Spielerzuordnung
- Kardinalitätsgrenzen

## Phase 9 – Dokumentation und Dashboard

- Installationsanleitung
- Alloy-Beispiel
- Prometheus-Beispiel
- Grafana-Dashboard
- Alert-Regeln
- Releaseworkflow
