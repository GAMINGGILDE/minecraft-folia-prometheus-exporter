# AGENTS.md

## Verbindliche Dokumente

Vor jeder Änderung lesen:

- docs/01-project-scope.md
- docs/02-metrics-catalog.md
- docs/03-architecture.md
- docs/04-folia-threading.md
- docs/07-testing.md
- docs/adr/

## Harte Regeln

- Das Plugin unterstützt offiziell Paper und Folia ab der API-Linie 26.1.2.
- Es wird genau ein gemeinsames Plugin-JAR erzeugt.
- Mindestversion ist Java 25.
- Allgemeiner Code kompiliert gegen die öffentliche Paper-API.
- Folia-spezifischer Code wird in einem isolierten Provider gekapselt.
- Andere Serverimplementierungen und Forks werden nicht aktiv blockiert, sind aber nicht offiziell unterstützt.
- Kein Fallback auf den klassischen BukkitScheduler.
- Keine internen Paper- oder Folia-Klassen.
- Keine NMS-Nutzung.
- Keine Reflection auf Paper- oder Folia-Interna.
- Keine experimentellen oder internen Provider in Version 1.
- Keine individuellen Spielermetriken.
- Keine Spielernamen oder UUIDs in Metriken.
- Der HTTP-Thread darf niemals Minecraft-Daten live abfragen.
- Scrapes lesen ausschließlich immutable Snapshots.
- Alle Minecraft-Zugriffe müssen die Ownership- und Scheduler-Regeln von Paper und Folia beachten.

## Build

./gradlew clean build

## Arbeitsweise

- Bearbeite nur den ausdrücklich angeforderten Umfang und erweitere den
  Projektumfang nicht eigenständig.
- Ergänze Tests für neue Logik.
- Aktualisiere den Metrikkatalog bei jeder neuen Metrik.
- Melde fehlende öffentliche Paper- oder Folia-APIs, statt interne APIs zu verwenden.
