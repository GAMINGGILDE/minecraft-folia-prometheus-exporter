# ADR 0003: Snapshot-Architektur

## Entscheidung

Prometheus-Scrapes greifen ausschließlich auf gecachte, unveränderliche Snapshots zu.

Für die ab Phase 5 fortlaufend akkumulierten Event-Counter gilt die eng begrenzte
Ergänzung aus [ADR 0014](0014-phase-5-event-counters.md): Eventhandler erhöhen
threadsichere Prometheus-Counter direkt, der Scrape liest nur deren bereits
akkumulierten Zustand und löst keine Minecraft-Abfrage aus. Minecraft-Livezustand
bleibt snapshotbasiert; Eventobjekte werden weder gespeichert noch beim Scrape
erreichbar gemacht.

Für die ausschließlich JDK-/Betriebssystemdaten lesende offizielle JVM- und
Prozessinstrumentierung gilt ab Phase 3 die eng begrenzte Ausnahme aus
[ADR 0012](0012-direct-jvm-process-instrumentation.md). Minecraft-Daten bleiben
ohne Ausnahme snapshotbasiert.

Phase 2 konkretisiert dies durch `ImmutableSnapshot<T>` mit einem eindeutigen
`Instant` und einer defensiv kopierten, unveränderlichen Werteliste. Der Werttyp
muss selbst immutable sein und darf keine Bukkit-, Paper-, Folia- oder
Minecraft-Liveobjekte enthalten. `SnapshotRepository<T>` veröffentlicht und
ersetzt den neuesten vollständig konstruierten Snapshot atomar über eine
`AtomicReference`; Leser benötigen keinen großen Lockbereich.

## Konsequenzen

- Scrapes blockieren keine Regionen
- Daten können abhängig vom Collector einige Sekunden oder Minuten alt sein
- Snapshot-Alter wird exportiert
- Collector und HTTP-Ausgabe sind entkoppelt
- Erfassungszeitpunkt, Alter, atomisches Ersetzen und kontrolliertes Entfernen
  sind unabhängig vom Minecraft-Runtime testbar
