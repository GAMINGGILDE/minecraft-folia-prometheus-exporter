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

- Plugin startet auf Paper der Ziel-API-Linie
- Plugin startet auf Folia der Ziel-API-Linie
- `/metrics` antwortet
- `/health` und `/ready` funktionieren
- HTTP-Server stoppt beim Disable
- Collector-Fehler stoppen andere Collector nicht
- deaktivierte Collector erzeugen keine Metriken
- Snapshot-Alter steigt bei Collector-Ausfall
- keine Spieleridentitäten im Output

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
- Wenn ein geeigneter Testserver automatisierbar verfügbar ist, wird der Descriptor
  zusätzlich durch einen Starttest auf Paper und nach Möglichkeit Folia geprüft.
- Die Descriptor-Version ist `0.1.0-SNAPSHOT` und enthält keinen Platzhalter.
- Tests decken Standardwerte, ungültige Konfigurationen, Plugin-Metadaten und die
  expandierte Pluginversion ab.
- Es existieren keine Collector, HTTP-Endpunkte, konkreten Metriken,
  Folia-Provider oder vorsorglichen PlatformDetector.
