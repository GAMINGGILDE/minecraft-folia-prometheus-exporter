# ADR 0012: JVM-/Prozessinstrumentierung direkt an der privaten Registry

## Entscheidung

Phase 3 registriert die offiziellen Instrumentierungen `JvmMemoryMetrics`,
`JvmGarbageCollectorMetrics`, `JvmThreadsMetrics`, `JvmClassLoadingMetrics`,
`JvmBufferPoolMetrics` und `ProcessMetrics` aus dem Prometheus Java Client 1.8.0
direkt in der privaten `PrometheusRegistry` jeder `MetricsCore`-Instanz.

`JvmMetricsRegistrar` registriert die Instrumentierungen einzeln. Dadurch bleiben
die bestehenden Schalter `collectors.jvm` und `collectors.process` unabhängig,
und es wird weder die globale Default-Registry noch der statische
Registry-Guard des aggregierenden `JvmMetrics`-Builders verwendet. Jede
Registrar-Instanz führt die Registrierung höchstens einmal aus.

## Kein Snapshot-Collector

Die offiziellen Instrumentierungen sind threadsichere Registry-Callbacks. Sie
lesen ausschließlich JDK-Managementdaten und, soweit unterstützt,
Prozessinformationen des Betriebssystems. Sie greifen nicht auf Bukkit-, Paper-,
Folia- oder Minecraft-Liveobjekte zu. Ein vorgeschalteter periodischer Collector
würde nur zusätzliche veraltete Kopien und Scheduleraufwand erzeugen, ohne die
Minecraft-Ownership-Grenze zu verbessern.

Das Snapshot-Modell aus ADR 0003 bleibt für alle Minecraft-Daten verbindlich.
Die direkte JVM-/Prozessinstrumentierung ist eine eng begrenzte Ausnahme für
serverunabhängige Laufzeitdaten.

## Lifecycle und Konsequenzen

- Die Registrierung ist Teil der `MetricsCore`-Konstruktion und damit vor
  HTTP-Start und Readiness abgeschlossen.
- Registrierungsfehler propagieren und lassen die Plugininitialisierung
  kontrolliert fehlschlagen; der Registrar leert die noch nicht veröffentlichte
  private Registry und wiederholt einen fehlgeschlagenen Versuch nicht.
- Die verwendeten Instrumentierungen starten keine eigenen Hintergrundthreads
  und besitzen keine Stop- oder Unregister-Operation.
- `PrometheusRegistry.clear()` entfernt beim Core-Shutdown die registrierten
  Callbacks.
- Mehrere `MetricsCore`-Instanzen teilen keine Registry und verursachen keine
  Duplicate-Registration.
- Betriebssystemabhängige Samples dürfen fehlen; nicht angebotene `system_*`-
  oder CPU-Usage-Metriken werden nicht nachimplementiert.
