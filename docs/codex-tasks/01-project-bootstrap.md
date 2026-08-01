# Codex-Aufgabe 1: Projektgerüst

Erzeuge das Gradle-Projekt anhand der Spezifikation.

## Anforderungen

- Gradle Kotlin DSL
- Gradle Wrapper 9.6.1
- `distributionSha256Sum=9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14`
- Java Toolchain 25
- `JavaCompile.options.release = 25`
- JUnit 5
- öffentliche Paper API `compileOnly`
- exakt gepinnte API-Koordinate aus der Linie 26.1.2
- genau ein gemeinsames Plugin-JAR
- Plugin-Hauptklasse
- klassische `plugin.yml`
- `folia-supported: true`
- anhand der öffentlichen Paper-API verifiziertes `api-version: '26.1.2'`
- Entwicklungsversion `0.1.0-SNAPSHOT`
- Expansion der Projektversion durch `processResources`
- Konfigurationsdatei und immutable Konfigurationsmodelle
- getrennte Loader- und Validator-Komponenten
- GitHub Actions Build
- Tests für Standardwerte, ungültige Konfigurationen, Plugin-Metadaten und die
  expandierte Pluginversion
- separater automatisierter Starttest auf fest gepinntem Paper 26.1.2 Build 74
  und Folia 26.1.2 Build 8

## Verbote

- keine individuellen Spielermetriken
- keine internen Paper- oder Folia-Klassen
- keine NMS-Nutzung
- keine Reflection auf Plattforminternas
- kein klassischer BukkitScheduler-Fallback
- kein Folia-spezifischer Provider
- kein PlatformDetector oder Feature-Detector auf Vorrat
- keine Collector
- keine HTTP-Endpunkte
- keine konkreten Metriken
- keine experimentellen oder internen Provider
- keine Versionsnummern erfinden; zentrale Versionseigenschaften verwenden

## Abnahme

`./gradlew clean build` ist erfolgreich.

Zusätzlich gilt:

- Das Build erzeugt genau ein Plugin-JAR.
- Das JAR enthält `plugin.yml` und keine `paper-plugin.yml`.
- Der erzeugte Descriptor enthält `version: '0.1.0-SNAPSHOT'` und keinen
  nicht expandierten Platzhalter.
- Die Tests für Konfiguration und Plugin-Metadaten sind erfolgreich.
- Ein verpflichtender, vom normalen Build getrennter Starttest verifiziert
  `api-version` auf den fest gepinnten Paper-/Folia-Artefakten. Er verwendet den
  offiziellen PaperMC Downloads Service mit identifizierendem User-Agent,
  verifiziert die SHA-256-Prüfsummen und beendet beide Server kontrolliert.
