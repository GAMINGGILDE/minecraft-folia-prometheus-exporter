# ADR 0011: Prometheus Java Client und eingebetteter HTTP-Server

## Entscheidung

Phase 2 verwendet den offiziellen Prometheus Java Client 1.8.0. Die Version wird
über `io.prometheus:prometheus-metrics-bom:1.8.0` zentral festgelegt. Folgende
Module werden in das gemeinsame Plugin-JAR eingebunden:

- `io.prometheus:prometheus-metrics-core`
- `io.prometheus:prometheus-metrics-instrumentation-jvm`
- `io.prometheus:prometheus-metrics-exporter-httpserver`

Die Pakete unter `io.prometheus` werden beim Shadow-Build nach
`de.minecraftgilde.prometheus.internal.prometheus` relocatet. Das Build erzeugt
weiterhin genau ein auslieferbares Plugin-JAR. Ein eigener Prometheus-Text-Renderer
und zusätzliche HTTP-Frameworks wie Jetty, Undertow, Netty oder Tomcat sind
ausgeschlossen.

Der HTTP-Server des Prometheus-Clients übernimmt Bindung, Scrape-Protokoll und
Serialisierung. Host und Port stammen aus der bestehenden Plugin-Konfiguration;
der Standardhost bleibt `127.0.0.1`. Die bestehenden Pfadwerte werden ebenfalls
übernommen und haben die Standardwerte `/metrics`, `/health` und `/ready`. Der
konfigurierte Metrikpfad wird über `HTTPServer.Builder.metricsHandlerPath(...)`
gesetzt. Ein kontrollierter Default-Handler desselben Servers bedient den
konfigurierten Health- und Readiness-Pfad und liest nur den Plugin-Lifecycle
beziehungsweise den Snapshotstatus. Der konfigurierte HTTP-Executor wird über
`executorService(...)` angebunden. Beim Deaktivieren des Plugins wird der Server
über seine `close()`-/`stop()`-API geschlossen.

## Snapshot-Grenze

Prometheus-Callbacks und HTTP-Handler dürfen keine Bukkit-, Paper-, Folia- oder
Minecraft-Daten live lesen. Collector veröffentlichen vollständig erzeugte,
immutable Snapshots atomar. Scrapes und Statushandler lesen ausschließlich diese
Snapshots und kontrollierten Exporterstatus.

Die JVM-Instrumentierungsbibliothek wird bereits als benötigtes Modul gebündelt;
die Registrierung der JVM- und Prozessmetriken bleibt Phase 3 vorbehalten.

## Konsequenzen

- Content Negotiation und Prometheus-Serialisierung stammen aus dem offiziellen
  Client.
- Es gibt keinen zweiten HTTP-Stack im Plugin.
- Relocation verhindert Klassen- und Versionskonflikte mit anderen Plugins.
- Der HTTP-Lifecycle ist Bestandteil des Plugin-Lifecycles und wird getestet.
