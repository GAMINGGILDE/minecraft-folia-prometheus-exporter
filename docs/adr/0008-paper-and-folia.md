# ADR 0008: Paper und Folia

## Entscheidung

Das Plugin unterstützt offiziell Paper und Folia ab der API-Linie 26.1.2 und
wird als genau ein gemeinsames Plugin-JAR ausgeliefert.

Allgemeiner Code kompiliert gegen die öffentliche `paper-api`. Folia-spezifische
Funktionen werden erst bei tatsächlichem Bedarf in einem isolierten Provider
implementiert. Phase 1 enthält weder diesen Provider noch eine vorsorgliche
Plattform- oder Feature-Erkennung.

## Scheduler

Auf Paper und Folia werden ausschließlich Global-, Region-, Entity- und
Async-Scheduler verwendet. Ein Fallback auf den klassischen `BukkitScheduler` ist
ausgeschlossen.

## Plattformumfang

- Paper und Folia werden offiziell getestet und unterstützt.
- Andere Serverimplementierungen und Forks werden nicht aktiv blockiert.
- Für andere Serverimplementierungen und Forks besteht kein offizieller
  Supportanspruch.
- Interne Paper-/Folia-Klassen, NMS und Reflection auf Plattforminternas bleiben
  ausgeschlossen.

## Plugin-Descriptor

Das gemeinsame JAR verwendet die klassische `plugin.yml` mit
`folia-supported: true`. Für die API-Linie 26.1.2 ist der öffentlich bestätigte
Wert `api-version: '26.1.2'` zu verwenden und nach Möglichkeit durch einen
Starttest zu verifizieren.

## Konsequenzen

- Gemeinsame Funktionalität darf keine Folia-spezifischen API-Aufrufe enthalten.
- Eine Plattform- oder Feature-Erkennung wird erst zusammen mit dem späteren
  Folia-Provider eingeführt.
- Tests und Dokumentation berücksichtigen Paper und Folia getrennt.
- Folia-spezifische Metriken bleiben in einem eigenen Provider isoliert.
