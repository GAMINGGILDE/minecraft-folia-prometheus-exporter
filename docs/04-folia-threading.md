# 4. Paper-/Folia-Threading und Scheduler-Regeln

Die Regeln gelten für beide offiziell unterstützten Plattformen. Das Plugin nutzt
auf Paper und Folia ausschließlich Global-, Region-, Entity- und Async-Scheduler.
Es gibt keinen Fallback auf den klassischen `BukkitScheduler`.

Diese vier Scheduler sind Teil der gemeinsamen öffentlichen Paper-API und werden
nicht als Folia-Erkennungsmerkmal verwendet. Phase 4 setzt diese Grenze mit
`PaperCollectionScheduler` um und benötigt weiterhin weder einen Folia-Provider
noch Plattform- oder Feature-Erkennung.

## 4.1 Scheduler-Zuordnung

| Aufgabe | Scheduler |
|---|---|
| Globale Serverzustände | Global Region Scheduler |
| Position-/Chunkgebundene Daten | Region Scheduler |
| Entitygebundene Daten | Entity Scheduler |
| JVM-MBeans | Async oder HTTP-unabhängig |
| Dateisystemoperationen | Async Scheduler |
| HTTP-Ausgabe | eigener HTTP-Thread, nur Snapshots |

Für Phase 4 gilt konkret:

| Öffentlicher API-Zugriff | Ausführung |
|---|---|
| Servername, Minecraft-Version, Online-Mode, Hardcore, Distanzen | Global Region Scheduler |
| Online-/Maximal-/bekannte Spieler, Whitelist, Bans und Operatoren | Global Region Scheduler |
| Pluginliste, Plugin-Metadaten und Aktivierungszustand | Global Region Scheduler |
| Liste geladener Welten und deren Name, Spielerzahl, Zeit, Wetter, Schwierigkeit, Umgebung, World Border und PVP | Global Region Scheduler |
| `World#getChunkCount()` | Global Region Scheduler |
| `World#getWorldPath()` | Global Region Scheduler; nur der immutable Pfad wird weitergereicht |
| `Player#getGameMode()` | Entity Scheduler des jeweiligen Spielers |
| Rekursives Lesen der Weltverzeichnisse | Async Scheduler |
| Timeout-Wächter | Async Scheduler |
| Prometheus-Scrape | HTTP-Thread; nur Repositories und Registry |

Phase 4 braucht keinen Region-Scheduler-Aufruf: Der öffentliche aggregierte
Chunkzähler vermeidet Positions- und Chunkobjektzugriffe. Das Interface behält
die Region-Methode für spätere, tatsächlich positionsgebundene Collector.

## 4.2 Verbotene Muster

Nicht verwenden:

```java
Bukkit.getScheduler().runTask(plugin, task);
```

Keine Minecraft-API-Aufrufe aus:

```java
CompletableFuture.runAsync(...)
```

wenn Regions- oder Entity-Ownership erforderlich ist.

Keine Live-Scans vom HTTP-Thread:

```java
world.getEntities();
world.getLoadedChunks();
player.getLocation();
```

## 4.3 Scheduler-Abstraktion

Implementiertes Interface, gekürzt um Rückgabetypdetails:

```java
public interface CollectionScheduler {
    CollectionTask scheduleGlobalAtFixedRate(Duration interval, Runnable task);

    CollectionTask executeAt(
        org.bukkit.World world,
        int chunkX,
        int chunkZ,
        Runnable task
    );

    Optional<CollectionTask> executeFor(
        org.bukkit.entity.Entity entity,
        Runnable task,
        Runnable retired
    );

    CollectionTask executeAsync(Runnable task);
    CollectionTask executeAsyncAfter(Duration delay, Runnable task);
    void cancelAll();
}
```

Die Abstraktion enthält keine klassische Scheduler-Alternative. Globale
Intervalle werden auf mindestens einen Tick aufgerundet; ungültige Intervalle
werden bereits durch die Konfigurationsvalidierung abgelehnt. Beim Disable
bricht `cancelAll()` alle durch das Plugin geplanten Aufgaben ab.

Die spätere Capability-Prüfung des isolierten Folia-Metrikproviders bezieht sich
ausschließlich auf die konkrete öffentliche Folia-API, die seine Messung benötigt.
Sie prüft weder Servername noch Versionsstring und lädt die Providerklasse auf
Paper nicht vorzeitig.

## 4.4 Snapshot-Regel

Jeder Scheduler-Task erzeugt nur lokale Werte. Erst nach vollständiger Erfassung wird
ein unveränderlicher Snapshot veröffentlicht.

Unvollständige Zwischenstände dürfen nicht sichtbar werden. Pro Collector ist
nur ein Lauf aktiv. Ein Timeout entfernt diesen Lauf atomar; ein später Callback
kann wegen der abweichenden Laufidentität nicht mehr publizieren. Nach `stop()`
werden überhaupt keine Ergebnisse mehr angenommen.

Der Metrics Core setzt diese Grenze mit `ImmutableSnapshot<T>` und
`SnapshotRepository<T>` um. Der Snapshot kopiert seine Werteliste defensiv und
trägt einen `Instant` als Erfassungszeitpunkt. Das Repository tauscht ausschließlich
vollständig konstruierte Instanzen über eine `AtomicReference` aus. Der enthaltene
Werttyp muss selbst immutable sein und darf keine veränderlichen Bukkit-, Paper-,
Folia- oder Minecraft-Objekte enthalten.

## 4.5 HTTP-Grenze

Der benannte HTTP-Workerpool arbeitet unabhängig von Minecraft-Tickthreads. Seine
Handler dürfen ausschließlich:

1. die Prometheus-Registry serialisieren,
2. atomar veröffentlichte immutable Snapshots lesen,
3. den atomaren Health-/Readiness-Zustand lesen und
4. HTTP-Antworten und niedrig-kardinale Eigenmetriken aktualisieren.

Im Metrics Core existiert keine Referenz von einem HTTP-Handler auf Bukkit-,
Paper-, Folia- oder Minecraft-Objekte. Parallele Scrapes werden vom offiziellen
Prometheus-Handler und threadsicheren Client-Metriken verarbeitet. Direkte
Callbacks der JVM-/Prozessinstrumentierung sind zulässig, weil sie ausschließlich
JDK- und Betriebssystemdaten lesen und keine Ownership-Regel von Paper oder Folia
berühren.

Die Phase-4-Prometheus-Callbacks lesen ebenfalls keine Minecraft-Objekte. Sie
wandeln den jeweils einmal geladenen immutable Snapshot einer Gruppe in
Prometheus-Snapshots um. Insbesondere lösen parallele Scrapes keine zusätzliche
Minecraft-Erfassung oder Weltgrößenberechnung aus.

## 4.6 Regionsbeobachtung

Dieser Abschnitt betrifft den späteren isolierten Folia-Provider und ist nicht
Bestandteil von Phase 4.

Spielerpositionen dürfen intern als temporäre Beobachtungsquelle dienen. Dabei gilt:

- keine Speicherung von Spielername oder UUID im Metriksnapshot
- keine Ausgabe einer Beobachtung pro Spieler
- nur aggregierte TPS-Verteilungen pro Welt
- Messpunkte nach Ablauf entfernen
- dynamische Regionsänderungen berücksichtigen
