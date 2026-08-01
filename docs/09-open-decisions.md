# 9. Verbindliche Entscheidungen und verbleibende Detailfragen

## 9.1 Festgelegt

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
Folia-spezifische Funktionen: späterer isolierter Provider
Gradle Wrapper: 9.6.1
Gradle-Distribution-SHA-256: 9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
Java Toolchain: 25
JavaCompile.options.release: 25
Plugin-Descriptor: klassische plugin.yml
folia-supported: true
api-version für API-Linie 26.1.2: 26.1.2
Entwicklungsversion: 0.1.0-SNAPSHOT
JUnit 5: 5.14.4
Lizenz: MIT
Interne Paper-/Folia-APIs in Version 1: ausgeschlossen
Experimentelle oder interne Provider in Version 1: ausgeschlossen
Individuelle Spielermetriken: ausgeschlossen
Counter-Persistenz in Version 1: nein
```

Andere Serverimplementierungen und Forks werden nicht aktiv blockiert, sind aber
nicht offiziell getestet oder unterstützt.

## 9.2 Festgelegt für Phase 1

- Allgemeiner Code kompiliert gegen die öffentliche `paper-api`.
- Es wird noch kein Folia-spezifischer Provider erzeugt.
- Es wird kein `PlatformDetector` oder Feature-Detector auf Vorrat implementiert.
- Auf Paper und Folia werden ausschließlich Global-, Region-, Entity- und
  Async-Scheduler verwendet; ein Fallback auf den klassischen `BukkitScheduler`
  ist ausgeschlossen.
- Die Projektversion wird beim `processResources`-Task in `plugin.yml` eingesetzt.
- Ein Test weist nach, dass der erzeugte Descriptor die konkrete Projektversion
  und keinen nicht expandierten Platzhalter enthält.
- Das Konfigurationsmodell besteht aus immutable Java-Records oder
  unveränderlichen finalen Klassen. Laden und Validieren sind getrennte
  Komponenten.
- Ungültige Konfigurationswerte verhindern den Pluginstart und werden mit einer
  verständlichen Fehlermeldung protokolliert.
- Tests decken mindestens Standardwerte, ungültige Konfigurationen,
  Plugin-Metadaten und die expandierte Pluginversion ab.
- Phase 1 enthält keine Collector, HTTP-Endpunkte oder konkreten Metriken.

Der Wert `api-version: '26.1.2'` ist durch die öffentliche Paper-API-Linie 26.1.2
bestätigt. Die Phase-1-Abnahme prüft den Descriptor zusätzlich durch einen
Starttest, sofern dieser mit einem geeigneten öffentlichen Paper-/Folia-Artefakt
automatisierbar ist.

## 9.3 Noch für die Phase-1-Abnahme zu prüfen

- Automatisierbarkeit eines Paper- und Folia-Starttests in CI

## 9.4 Für spätere Phasen zu prüfen

- exakte veröffentlichte `folia-api`-Koordinate für den isolierten Provider
- Verhalten eines aktivierten Folia-Collectors auf Paper
- aktuelle stabile Prometheus-Java-Client-Version und benötigte Module
- Wahl des eingebetteten HTTP-Servers
- endgültige Collector-Standardintervalle
- genaue Strategie zur Regionsbeobachtung über öffentliche APIs
- genaue Entity-Abgleichstrategie
- gewünschte Standard-Buckets für Histogramme
- Release- und Changelog-Format

## 9.5 Nicht mehr offen

- offizieller Support für Paper und Folia
- genau ein gemeinsames Plugin-JAR
- keine aktive Blockierung anderer Forks, aber kein offizieller Support
- kein klassischer BukkitScheduler-Fallback
- kein Folia-Provider und kein PlatformDetector in Phase 1
- kein experimenteller oder interner Provider in Version 1
- klassische `plugin.yml` statt `paper-plugin.yml`
- fest gepinnte `paper-api`-Koordinate und JUnit-5-Version
- Fehlerverhalten des Konfigurationsloaders bei ungültigen Werten
- keine individuellen Spielerstatistiken
