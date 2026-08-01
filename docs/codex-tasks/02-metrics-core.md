# Codex-Aufgabe 2: Metrics Core

Implementiere Collector-Lifecycle, immutable Snapshots, Repository, Coordinator und
HTTP-Endpunkte.

Der HTTP-Endpunkt darf niemals Bukkit-, Paper- oder Folia-Livezugriffe ausführen.

## Verbindlicher Prometheus- und HTTP-Stack

- offizieller Prometheus Java Client 1.8.0 über dessen BOM
- `prometheus-metrics-core`
- `prometheus-metrics-instrumentation-jvm`
- `prometheus-metrics-exporter-httpserver`
- kein eigener Prometheus-Text-Renderer
- kein zusätzliches HTTP-Framework
- Host, Port, Pfade und Workerzahl aus der Konfiguration; Standardhost
  `127.0.0.1`
- Standardpfade `/metrics`, `/health` und `/ready`
- sauberer Shutdown beim Plugin-Disable
- genau ein Plugin-JAR mit eingebundenen Bibliotheken
- Relocation von `io.prometheus` nach
  `de.minecraftgilde.prometheus.internal.prometheus`

Implementiere die für Phase 2 festgelegten Exporter-Eigenmetriken. Die
JVM-Instrumentierungsbibliothek wird gebündelt, ihre konkreten JVM- und
Prozessmetriken werden aber erst in Phase 3 registriert.

## Plattformgrenze

Phase 2 implementiert keinen Folia-Metrikprovider und keine vorsorgliche
Plattformerkennung. Gemeinsame Scheduler-Funktionen verwenden die öffentlichen
Global-, Region-, Entity- und Async-Scheduler der Paper-API auf Paper und Folia.

## Abnahme

- HTTP-Callbacks lesen nur immutable Snapshots und kontrollierten Exporterstatus.
- `/metrics`, `/health` und `/ready` funktionieren.
- Der HTTP-Server stoppt beim Disable.
- Das ausgelieferte JAR enthält die relocateten Prometheus-Abhängigkeiten.
- Es wurden keine Metriken außerhalb des Phase-2-Umfangs ergänzt.
