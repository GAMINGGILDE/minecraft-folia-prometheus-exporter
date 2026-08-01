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
- Registry-Eigenmetriken werden pro Registry nur einmal registriert.
- `/metrics`, `/health`, `/ready`, `404`, `405`, parallele Requests, Readiness vor
  und nach Initialisierung, belegte Ports und Portfreigabe nach Shutdown sind
  durch lokale Integrationstests abgedeckt.
- Der Plugin-Lifecycle setzt Readiness vor dem Shutdown zurück und räumt auch nach
  einem nur teilweise erfolgreichen Start auf.
- HTTP-Handler referenzieren keine Minecraft-Liveobjekte und lesen nur Registry
  und atomaren Exporterstatus.
