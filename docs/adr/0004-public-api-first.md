# ADR 0004: Ausschließlich öffentliche APIs

## Entscheidung

Version 1 verwendet ausschließlich öffentliche Paper-, Folia-, Bukkit- und
Java-APIs. Allgemeiner Code kompiliert gegen die öffentliche `paper-api`.
Folia-spezifische Funktionen werden bei tatsächlichem Bedarf in einem isolierten
Provider implementiert.

## Konsequenzen

- keine NMS-Abhängigkeit
- keine direkte oder reflektive Nutzung interner Paper- oder Folia-Klassen
- kein experimenteller Internal Provider in Version 1
- kein Folia-spezifischer Provider auf Vorrat in Phase 1
- kein Folia-spezifischer Provider oder PlatformDetector in Phase 2
- regionale MSPT- und Tick-Delay-Metriken werden nur implementiert, wenn Folia
  dafür eine belastbare öffentliche API bereitstellt
- Update-Kompatibilität hat Vorrang vor maximaler Metrikabdeckung

Die Capability-basierte Aktivierung und die Ladegrenze des späteren Providers sind
in [ADR 0010](0010-folia-provider-capability-detection.md) konkretisiert.
