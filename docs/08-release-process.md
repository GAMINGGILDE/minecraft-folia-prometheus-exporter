# 8. Release-Prozess

## Versionierung

Semantische Versionierung:

- MAJOR: inkompatible Konfigurations- oder Metrikänderung
- MINOR: neue rückwärtskompatible Collector oder Metriken
- PATCH: Fehlerbehebung

## Release-Artefakte

- genau ein schattiertes Plugin-JAR; Prometheus-Abhängigkeiten sind enthalten und
  `io.prometheus` ist nach `de.minecraftgilde.prometheus.internal.prometheus`
  relocatet
- Prüfsumme
- Changelog
- dokumentierte Zielversionen von Paper und Folia
- dokumentierte plattformspezifische Provider

## Kompatibilität

Der stabile Kern kompiliert gegen die öffentliche `paper-api` und darf keine
direkte Compile-Time-Abhängigkeit auf interne Paper- oder Folia-Klassen besitzen.
Folia-spezifische Funktionen werden in einem isolierten Provider gekapselt.
Kompatibilitätsprüfungen erfolgen ausschließlich über öffentliche APIs und
Capabilities.
