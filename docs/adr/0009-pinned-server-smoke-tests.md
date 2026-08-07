# ADR 0009: Gepinnte Server-Smoke-Tests

## Entscheidung

Paper und Folia erhalten einen verpflichtenden, vom normalen Build getrennten
GitHub-Actions-Smoke-Test. Beide Matrixläufe bauen das Plugin mit Java 25 und
laufen auf `ubuntu-24.04`. Sie starten jeweils genau ein fest gepinntes
Serverartefakt der API-Linie 26.1.2:

| Plattform | Version | Build | SHA-256 |
|---|---:|---:|---|
| Paper | 26.1.2 | 74 | `1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7` |
| Folia | 26.1.2 | 8 | `607afd1c3320008e1ffd2eaee6780ace4419d5f8c527b75e79f259be79ebf57b` |

Die Download-URL wird über den offiziellen PaperMC Downloads Service für die
festgelegte Projekt-, Versions- und Build-Kombination ermittelt. Metadaten- und
Artefaktanfrage senden den User-Agent
`minecraft-folia-prometheus-exporter-ci/1.0.1`
mit der Repository-URL als Kontaktangabe. Die Prüfsumme wird vor dem Start
verifiziert. Eine dynamische Latest-Auswahl ist ausgeschlossen.

## Abnahmeverhalten

- `eula=true` wird vor dem Start gesetzt.
- Das Build muss genau ein Plugin-JAR erzeugen; dieses wird nach `plugins/`
  kopiert.
- Der Server wird mit `--nogui` gestartet.
- Erfolg ist ausschließlich die eindeutige Logmeldung
  `FoliaPrometheusExporter started.`.
- Plugin-Ladefehler, Plugin-Exceptions, ein vorzeitiges Serverende und ein
  Startup-Timeout lassen den Test fehlschlagen.
- Nach erfolgreicher Aktivierung wird der Server über den Konsolenbefehl `stop`
  kontrolliert beendet. Auch Fehlerpfade versuchen zuerst einen kontrollierten
  Shutdown.

## Konsequenzen

- Der normale Build bleibt schnell und unabhängig vom Serverstart.
- Änderungen an Zielbuilds und Prüfsummen sind bewusste, reviewbare Änderungen.
- Serverlogs werden auch bei einem Fehlschlag als CI-Artefakt bereitgestellt.
