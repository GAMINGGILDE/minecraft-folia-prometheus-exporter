# FoliaPrometheusExporter

Repository: `minecraft-folia-prometheus-exporter`

FoliaPrometheusExporter ist ein für Paper und Folia entwickelter
Prometheus-Exporter. Das Plugin stellt umfangreiche Server-, Welt-, Entity-,
Folia-, JVM-, Prozess- und Exporter-Metriken bereit.

## Verbindliche Eckdaten

- Pluginname: `FoliaPrometheusExporter`
- Repository: `minecraft-folia-prometheus-exporter`
- Package: `de.minecraftgilde.prometheus`
- Java: 25 oder neuer
- Plattformen: Paper und Folia
- Startversion: API-Linie 26.1.2
- Auslieferung: ein gemeinsames Plugin-JAR
- Lizenz: MIT
- Build: Gradle Kotlin DSL

## Datenschutz

Das Plugin implementiert niemals individuelle Spielermetriken oder
Spielerstatistiken. Spielernamen und UUIDs werden nicht als Labels exportiert.

## Stabilitätsprinzip

Version 1 verwendet keine internen Paper- oder Folia-Klassen, keine
NMS-Abhängigkeiten und keinen experimentellen Internal Provider. Metriken ohne
belastbare öffentliche API werden nicht vorgetäuscht.

## Dokumentation

- [Projektumfang](docs/01-project-scope.md)
- [Metrikkatalog](docs/02-metrics-catalog.md)
- [Architektur](docs/03-architecture.md)
- [Folia-Threading](docs/04-folia-threading.md)
- [Konfiguration](docs/05-configuration.md)
- [Tests und Abnahme](docs/07-testing.md)
- [Entscheidungen](docs/09-open-decisions.md)
- [Codex-Plan](docs/10-codex-implementation-plan.md)

## Build und Tests

Voraussetzung ist ein installiertes JDK 25. Der Build verwendet den mitgelieferten
Gradle Wrapper:

```bash
./gradlew clean build
./gradlew test
```

Das Plugin-JAR wird unter `build/libs/` erzeugt.

## Status

Dieses Repository ist derzeit ein Spezifikations- und Projektgerüst. Die eigentliche
Pluginimplementierung wird schrittweise anhand der Codex-Aufgaben erstellt.
