# Codex-Aufgabe 2: Metrics Core

Status: abgeschlossen.

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
- sauberer, idempotenter Shutdown beim Plugin-Disable
- genau ein Plugin-JAR mit eingebundenen Bibliotheken
- Relocation von `io.prometheus` nach
  `de.minecraftgilde.prometheus.internal.prometheus`

Implementiere die für Phase 2 festgelegten Exporter-Eigenmetriken. Die
JVM-Instrumentierungsbibliothek wird gebündelt, ihre konkreten JVM- und
Prozessmetriken werden aber erst in Phase 3 registriert.

Umgesetzt sind Build-, Health-, Readiness-, Scrape-, Scrape-Fehler-,
HTTP-Request- und Collector-State-Metriken aus dem verbindlichen Metrikkatalog.
Ihre Labels verwenden ausschließlich dokumentierte kontrollierte Werte.
Der Build-Commit wird über eine expandierte Resource eingespeist und fällt ohne
Git-Kontext auf `unknown` zurück. Eigenmetriken sind ohne globalen Cache genau an
die private Registry ihrer `MetricsCore` gebunden.

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

## Umsetzungsnachweis

- Collector-Lifecycle und Coordinator sind threadsicher, deterministisch und
  fehlerisoliert getestet.
- `ImmutableSnapshot<T>` kopiert die Werteliste defensiv;
  `SnapshotRepository<T>` publiziert atomar und wird parallel gelesen.
- `/ready` wechselt erst nach vollständiger Core-Initialisierung auf `200`;
  `/health` bleibt von optionalen Collectorfehlern unabhängig.
- Der offizielle Prometheus-`MetricsHandler` übernimmt ausschließlich die
  Exposition; der kontrollierte JDK-HTTP-Router liefert `404` und `405`.
- HTTP-Integrationstests verwenden Port `0`, führen parallele Requests aus und
  prüfen die Portfreigabe nach Shutdown.
- Der Server-Smoke-Test prüft Paper und Folia auf den fest gepinnten Builds sowie
  `/metrics`, `/health`, `/ready` und den HTTP-Shutdown.
- `./gradlew clean build` prüft automatisiert genau ein JAR, Descriptor,
  Hauptklasse, Relocation, ausgeschlossene Server-APIs und Signaturdateien.
