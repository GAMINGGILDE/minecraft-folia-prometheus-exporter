# 8. Release-Prozess

## Versionierung

Semantische Versionierung:

- MAJOR: inkompatible Konfigurations- oder Metrikänderung
- MINOR: neue rückwärtskompatible Collector oder Metriken
- PATCH: Fehlerbehebung

## Release-Artefakte

- schattiertes Plugin-JAR
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
