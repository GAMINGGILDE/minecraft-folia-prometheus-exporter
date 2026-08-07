# ADR 0011: Prometheus Java Client und eingebetteter HTTP-Server

## Entscheidung

Das Projekt verwendet den offiziellen Prometheus Java Client 1.8.0. Die Version wird
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

Der offizielle Prometheus-`MetricsHandler` übernimmt Scrape-Protokoll,
Content-Negotiation und Serialisierung. Er wird auf dem schlanken JDK-`HttpServer`
des Exportermoduls betrieben. Diese Konkretisierung erlaubt dem Plugin, vor dem
offiziellen Handler ausschließlich `GET` zuzulassen, unbekannte Pfade exakt mit
`404` zu beantworten und alle kontrollierten Endpunkte konsistent zu
instrumentieren, ohne einen zweiten HTTP-Stack oder eigenen Renderer einzuführen.

Host und Port stammen aus der bestehenden Plugin-Konfiguration; der Standardhost
bleibt `127.0.0.1`. Die bestehenden Pfadwerte werden ebenfalls übernommen und
haben die Standardwerte `/metrics`, `/health` und `/ready`. Ein kontrollierter
Router desselben Listeners bedient Health und Readiness und liest nur atomaren
Plugin-Lifecycle. Der konfigurierte benannte Daemon-Workerpool wird an den
JDK-Server gebunden. Beim Deaktivieren des Plugins werden Listener und Executor
idempotent geschlossen.

## Snapshot-Grenze

Prometheus-Callbacks und HTTP-Handler dürfen keine Bukkit-, Paper-, Folia- oder
Minecraft-Daten live lesen. Collector veröffentlichen vollständig erzeugte,
immutable Snapshots atomar. Scrapes und Statushandler lesen ausschließlich diese
Snapshots und kontrollierten Exporterstatus.

Die gebündelte JVM-Instrumentierungsbibliothek registriert JVM- und
Prozessmetriken direkt und instanzgebunden gemäß
[ADR 0012](0012-direct-jvm-process-instrumentation.md).

Jede `MetricsCore` besitzt genau eine eigene `PrometheusRegistry` und einen
einmalig konstruierten, instanzgebundenen `ExporterMetrics`-Satz. Ein statischer
Registry-Cache ist weder für den Lifecycle noch gegen Duplicate-Registration
nötig und wird deshalb nicht verwendet.

Das Label `git_commit` der Build-Info stammt aus einer von Gradle expandierten
`build-info.properties`. Der Build versucht `git rev-parse HEAD`, akzeptiert nur
einen validierten Hex-Hash und verwendet ohne Git-Kontext `unknown`.

## Konsequenzen

- Content Negotiation und Prometheus-Serialisierung stammen aus dem offiziellen
  Client.
- Es gibt keinen zweiten HTTP-Stack im Plugin.
- Relocation verhindert Klassen- und Versionskonflikte mit anderen Plugins.
- Der HTTP-Lifecycle ist Bestandteil des Plugin-Lifecycles und wird getestet.
- Andere Methoden auf bekannten Endpunkten liefern `405`, unbekannte Pfade `404`.
- Wiederholte Plugin-Lifecycles teilen weder Registry noch Eigenmetriken.
- Das auslieferbare JAR trägt den Build-Commit, bleibt aber außerhalb eines
  Git-Checkouts baubar.
