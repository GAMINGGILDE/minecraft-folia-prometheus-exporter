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
- dokumentierte Ziel-Folia-Version
- Liste experimenteller Provider

## Kompatibilität

Der stabile Kern darf keine direkte Compile-Time-Abhängigkeit auf interne
Folia-Klassen besitzen. Kompatibilitätsprüfungen erfolgen über öffentliche APIs und
Capabilities.
