# 10. Codex-Umsetzungsplan

Codex soll nicht das gesamte Plugin in einem einzigen Auftrag erzeugen.

## Phase 1 – Projektgerüst

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
- Tests für Standardwerte, ungültige Konfigurationen, Plugin-Metadaten und
  expandierte Pluginversion
- Starttest auf Paper und nach Möglichkeit Folia, sofern in CI automatisierbar

Nicht Bestandteil von Phase 1:

- Collector
- HTTP-Endpunkte
- konkrete Metriken
- Folia-spezifischer Provider
- vorsorglicher PlatformDetector oder Feature-Detector

Abnahme: `./gradlew clean build`; der erzeugte Descriptor enthält die konkrete
Version und keinen nicht expandierten Platzhalter.

## Phase 2 – Metrics Core

- Collector-Interface
- Snapshot-Modell
- SnapshotRepository
- CollectorCoordinator
- HTTP-Endpunkte
- Health und Readiness
- Exporter-Eigenmetriken

Abnahme: HTTP-Ausgabe ohne Minecraft-Livezugriffe.

## Phase 3 – JVM und Prozess

- standardisierte JVM-Metriken
- GC
- Threads
- Klassen
- Buffer Pools
- CPU
- Prozessstart
- Dateideskriptoren

## Phase 4 – Server und Welten

- Server-Info
- Spielerzahlen aggregiert
- Plugins
- Whitelist
- Weltinformationen
- Weltgröße asynchron
- geladene Chunks

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

- isolierter Folia-Provider
- belastbare Plattform- oder Feature-Erkennung über öffentliche APIs
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
