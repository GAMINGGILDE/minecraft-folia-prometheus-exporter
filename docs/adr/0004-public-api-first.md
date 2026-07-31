# ADR 0004: Ausschließlich öffentliche APIs

## Entscheidung

Version 1 verwendet ausschließlich öffentliche Folia-, Bukkit- und Java-APIs.

## Konsequenzen

- keine NMS-Abhängigkeit
- keine direkte oder reflektive Nutzung interner Folia-Klassen
- kein experimenteller Internal Provider in Version 1
- regionale MSPT- und Tick-Delay-Metriken werden nur implementiert, wenn Folia
  dafür eine belastbare öffentliche API bereitstellt
- Update-Kompatibilität hat Vorrang vor maximaler Metrikabdeckung
