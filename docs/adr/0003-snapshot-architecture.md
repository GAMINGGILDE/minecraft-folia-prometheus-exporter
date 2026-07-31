# ADR 0003: Snapshot-Architektur

## Entscheidung

Prometheus-Scrapes greifen ausschließlich auf gecachte, unveränderliche Snapshots zu.

## Konsequenzen

- Scrapes blockieren keine Regionen
- Daten können abhängig vom Collector einige Sekunden oder Minuten alt sein
- Snapshot-Alter wird exportiert
- Collector und HTTP-Ausgabe sind entkoppelt
