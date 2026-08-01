# ADR 0010: Capability-basierte Aktivierung des Folia-Providers

## Entscheidung

Phase 2 implementiert keinen Folia-Metrikprovider. Allgemeine Scheduler-Funktionen
verwenden auf Paper und Folia die Global-, Region-, Entity- und Async-Scheduler
der gemeinsamen öffentlichen Paper-API.

Der Folia-Metrikprovider wird erst in Phase 6 eingeführt und kompiliert in einem
isolierten Package gegen
`dev.folia:folia-api:26.1.2.build.8-stable` als `compileOnly`. Er verwendet weder
NMS noch interne Klassen. Insbesondere ist
`io.papermc.paper.threadedregions.RegionizedServer` verboten.

Die spätere Aktivierung prüft ausschließlich das Vorhandensein genau der
öffentlichen Folia-API-Capability, die der Provider für seine Messung tatsächlich
benötigt. Servername, Versionsstring und allgemeine Scheduler-Verfügbarkeit sind
keine Plattformmerkmale. Der allgemeine Bootstrap referenziert die konkrete
Providerklasse nicht statisch und lädt oder instanziiert sie erst nach erfolgreicher
Capability-Prüfung. Damit wird die Providerklasse auf Paper nicht vorzeitig
verifiziert oder geladen.

Falls sich in Phase 6 keine belastbare öffentliche API für die vorgesehene Messung
bestätigen lässt, wird der Provider nicht mit internen APIs oder Ersatzwerten
implementiert.

## Verhalten auf Paper

Ist der Folia-Collector konfiguriert, die benötigte öffentliche Capability aber
nicht vorhanden, gilt:

- Der Provider wird nicht gestartet.
- Pro Pluginstart wird genau eine verständliche Warnung protokolliert.
- Der interne Collectorstatus lautet `unsupported`.
- Der übrige Pluginstart wird fortgesetzt.
- Es werden keine Folia-Metriken und insbesondere keine künstlichen Nullwerte
  exportiert.

Eine öffentliche Statusmetrik ist nicht Bestandteil dieser Entscheidung. Falls
sie später ergänzt wird, muss sie eine kontrollierte feste Zustandsmenge wie
`enabled`, `disabled`, `unsupported` und `failed` verwenden und im Metrikkatalog
dokumentiert werden.

## Konsequenzen

- Paper lädt keine Klassen, deren Verifikation die `folia-api` voraussetzt.
- Eine gemeinsame Scheduler-API wird nicht fälschlich zur Folia-Erkennung benutzt.
- Fehlende öffentliche APIs werden sichtbar gemeldet, ohne den stabilen Kern zu
  gefährden.
