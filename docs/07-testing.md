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

## 7.4 Threading-Prüfungen

- keine Weltzugriffe aus HTTP-Threads
- keine Entityzugriffe aus Common Pool
- regionsgebundene Aufrufe über Region Scheduler
- Entity-Aufrufe über Entity Scheduler
- Dateisystemoperationen nicht auf Tickthreads
- kein Fallback auf den klassischen BukkitScheduler

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
