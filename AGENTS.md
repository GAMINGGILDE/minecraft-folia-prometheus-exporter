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

- Das Plugin unterstützt ausschließlich Folia.
- Mindestversion ist Java 25.
- Keine Paper-, Spigot- oder Bukkit-Kompatibilitätsschicht.
- Keine internen Folia-Klassen.
- Keine NMS-Nutzung.
- Keine Reflection auf Folia-Interna.
- Keine individuellen Spielermetriken.
- Keine Spielernamen oder UUIDs in Metriken.
- Der HTTP-Thread darf niemals Minecraft-Daten live abfragen.
- Scrapes lesen ausschließlich immutable Snapshots.
- Alle Minecraft-Zugriffe müssen Folias Ownership- und Scheduler-Regeln beachten.

## Build

./gradlew clean build

## Arbeitsweise

- Bearbeite nur die ausdrücklich genannte Codex-Phase.
- Erweitere den Projektumfang nicht eigenständig.
- Ergänze Tests für neue Logik.
- Aktualisiere den Metrikkatalog bei jeder neuen Metrik.
- Melde fehlende öffentliche Folia-APIs, statt interne APIs zu verwenden.