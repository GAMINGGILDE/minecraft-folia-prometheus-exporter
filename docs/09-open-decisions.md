# 9. Verbindliche Entscheidungen und verbleibende Detailfragen

## 9.1 Projekt- und Buildgrundlagen

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
Folia-Provider-Compile-API: dev.folia:folia-api:26.1.2.build.8-stable (compileOnly)
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

## 9.2 Server-Smoke-Tests

Der normale Build und der verpflichtende Server-Smoke-Test bleiben getrennte
GitHub-Actions-Workflows. Jeder Matrixlauf baut das Plugin mit Java 25. Folgende
Serverartefakte sind fest gepinnt:

| Plattform | Version | Build | Kanal | SHA-256 |
|---|---:|---:|---|---|
| Paper | 26.1.2 | 74 | stable | `1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7` |
| Folia | 26.1.2 | 8 | stable | `607afd1c3320008e1ffd2eaee6780ace4419d5f8c527b75e79f259be79ebf57b` |

Die Artefakte werden über den offiziellen PaperMC Downloads Service bezogen. Alle
Anfragen senden den eindeutigen User-Agent
`minecraft-folia-prometheus-exporter-ci/0.1.0` samt Repository-URL. Der Workflow
wählt niemals dynamisch einen Latest-Build aus und verifiziert die gepinnte
Prüfsumme.

Der Test setzt `eula=true`, kopiert das einzige erzeugte Plugin-JAR nach
`plugins/`, startet den Server mit `--nogui` und wartet höchstens 240 Sekunden auf
`FoliaPrometheusExporter started.`. Plugin-Ladefehler, Plugin-Exceptions,
vorzeitiges Serverende oder Timeout sind Fehler. Nach erfolgreicher Aktivierung
wird `stop` über die Serverkonsole gesendet und der Prozess kontrolliert beendet.
Details stehen in ADR 0009.

## 9.3 Festgelegt für Phase 2

- Allgemeine Scheduler-Funktionen verwenden die Global-, Region-, Entity- und
  Async-Scheduler der gemeinsamen öffentlichen Paper-API auf Paper und Folia.
- Phase 2 enthält keinen Folia-Metrikprovider und keine vorsorgliche
  Plattform-Erkennung.
- Der offizielle Prometheus Java Client wird über
  `io.prometheus:prometheus-metrics-bom:1.8.0` eingebunden.
- Benötigte Module sind `prometheus-metrics-core`,
  `prometheus-metrics-instrumentation-jvm` und
  `prometheus-metrics-exporter-httpserver`.
- Diese Bibliotheken werden in das einzige Plugin-JAR eingebunden; das Package
  `io.prometheus` wird nach
  `de.minecraftgilde.prometheus.internal.prometheus` relocatet.
- Es gibt weder einen eigenen Prometheus-Text-Renderer noch ein zusätzliches
  HTTP-Framework.
- Der offizielle Prometheus-HTTP-Server übernimmt den konfigurierten Metrikpfad.
  Kontrollierte Handler desselben Servers stellen die konfigurierten Health- und
  Readiness-Pfade bereit. Standardwerte sind `/metrics`, `/health` und `/ready`.
- HTTP-Host, -Port, -Pfade und Workerzahl stammen aus der bestehenden
  Konfiguration. Die Standardbindung ist `127.0.0.1`.
- Der HTTP-Server wird beim Plugin-Disable über seine `close()`-/`stop()`-API
  geschlossen.
- HTTP-Threads und Prometheus-Callbacks lesen ausschließlich immutable Snapshots
  und kontrollierten Exporterstatus, niemals Minecraft-Livedaten.
- Die Registrierung konkreter JVM- und Prozessmetriken bleibt Phase 3 vorbehalten.

Details stehen in ADR 0011.

## 9.4 Festgelegt für den späteren Folia-Provider

- Der Provider wird erst in Phase 6 in einem isolierten Package implementiert.
- Seine Compile-API ist
  `dev.folia:folia-api:26.1.2.build.8-stable` als `compileOnly`.
- NMS, interne Klassen und insbesondere
  `io.papermc.paper.threadedregions.RegionizedServer` sind verboten.
- Aktivierung erfolgt ausschließlich anhand genau der öffentlichen Folia-API, die
  für die konkrete Messung benötigt wird.
- Servername, Versionsstring und die auf beiden Plattformen verfügbaren Scheduler
  sind keine Erkennungsmerkmale.
- Allgemeiner Code darf die konkrete Providerklasse vor erfolgreicher
  Capability-Prüfung nicht statisch referenzieren oder laden.
- Fehlt die Capability auf Paper, wird ein konfigurierter Folia-Collector nicht
  gestartet, einmalig verständlich gewarnt und intern als `unsupported` markiert.
  Der Pluginstart läuft weiter; Folia-Metriken und künstliche Nullwerte werden
  nicht exportiert.
- Eine zusätzliche Statusmetrik ist derzeit nicht beschlossen. Wird sie später
  eingeführt, verwendet sie ausschließlich feste Zustände wie `enabled`,
  `disabled`, `unsupported` und `failed` und wird zuvor im Metrikkatalog ergänzt.

Details stehen in ADR 0010.

## 9.5 Noch offen

- endgültige Collector-Standardintervalle
- genaue Strategie zur Regionsbeobachtung über öffentliche APIs
- konkrete öffentliche Folia-API für die in Phase 6 implementierbaren
  Regionsmetriken
- genaue Entity-Abgleichstrategie
- gewünschte Standard-Buckets für Histogramme
- Release- und Changelog-Format

## 9.6 Nicht mehr offen

- Automatisierbarkeit eines verpflichtenden Paper- und Folia-Starttests
- feste Paper- und Folia-Serverbuilds für den Smoke-Test
- offizieller Support für Paper und Folia
- genau ein gemeinsames Plugin-JAR
- keine aktive Blockierung anderer Forks, aber kein offizieller Support
- kein klassischer BukkitScheduler-Fallback
- kein Folia-Provider und kein PlatformDetector in Phase 1 oder Phase 2
- Compile-API und Ladegrenze des späteren Folia-Providers
- Verhalten eines aktivierten Folia-Collectors auf Paper
- Prometheus-Java-Client-Version, BOM und Module
- eingebetteter HTTP-Server und Ausschluss zusätzlicher HTTP-Frameworks
- Shading und Relocation der Prometheus-Abhängigkeiten
- klassische `plugin.yml` statt `paper-plugin.yml`
- fest gepinnte `paper-api`-Koordinate und JUnit-5-Version
- Fehlerverhalten des Konfigurationsloaders bei ungültigen Werten
- keine individuellen Spielerstatistiken
