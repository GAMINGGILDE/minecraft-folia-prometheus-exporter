# ADR 0003: Snapshot-Architektur

## Entscheidung

Prometheus-Scrapes greifen ausschließlich auf gecachte, unveränderliche Snapshots zu.

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
