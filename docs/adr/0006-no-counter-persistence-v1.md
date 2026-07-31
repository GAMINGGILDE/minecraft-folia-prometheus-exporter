# ADR 0006: Keine Counter-Persistenz in Version 1

## Entscheidung

Event-Counter werden in Version 1 nicht lokal persistiert.

## Gründe

- Prometheus erkennt Counter-Resets
- weniger Dateisystemzugriffe
- einfacherer und robusterer Plugin-Lifecycle
- keine Inkonsistenzen zwischen lokaler Persistenz und Prometheus
