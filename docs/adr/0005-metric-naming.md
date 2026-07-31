# ADR 0005: Prometheus-konforme Metriknamen

## Entscheidung

Neue Metriken folgen Prometheus-Konventionen.

## Regeln

- Präfix `minecraft_`
- Folia-Präfix `minecraft_folia_`
- Sekunden statt Millisekunden oder Nanosekunden
- Bytes statt uneindeutiger Größen
- Counter enden auf `_total`
- keine freien Texte oder Spieleridentitäten als Labels
