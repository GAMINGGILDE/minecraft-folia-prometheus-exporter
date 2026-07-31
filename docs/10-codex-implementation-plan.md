# 10. Codex-Umsetzungsplan

Codex soll nicht das gesamte Plugin in einem einzigen Auftrag erzeugen.

## Phase 1 – Projektgerüst

- Gradle Wrapper
- Gradle Kotlin DSL
- Java Toolchain
- Plugin-Metadaten
- Hauptklasse
- Konfigurationsmodell
- JUnit 5
- GitHub-Actions-Build

Abnahme: `./gradlew clean build`.

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
