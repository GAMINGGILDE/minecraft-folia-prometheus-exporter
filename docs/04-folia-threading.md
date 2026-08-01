# 4. Paper-/Folia-Threading und Scheduler-Regeln

Die Regeln gelten für beide offiziell unterstützten Plattformen. Das Plugin nutzt
auf Paper und Folia ausschließlich Global-, Region-, Entity- und Async-Scheduler.
Es gibt keinen Fallback auf den klassischen `BukkitScheduler`.

Diese vier Scheduler sind Teil der gemeinsamen öffentlichen Paper-API und werden
nicht als Folia-Erkennungsmerkmal verwendet. Phase 2 benötigt deshalb weder einen
Folia-Provider noch eine Plattform- oder Feature-Erkennung.

## 4.1 Scheduler-Zuordnung

| Aufgabe | Scheduler |
|---|---|
| Globale Serverzustände | Global Region Scheduler |
| Position-/Chunkgebundene Daten | Region Scheduler |
| Entitygebundene Daten | Entity Scheduler |
| JVM-MBeans | Async oder HTTP-unabhängig |
| Dateisystemoperationen | Async Scheduler |
| HTTP-Ausgabe | eigener HTTP-Thread, nur Snapshots |

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

Empfohlenes Interface:

```java
public interface CollectionScheduler {
    void executeGlobal(Runnable task);

    void executeAt(
        org.bukkit.World world,
        int chunkX,
        int chunkZ,
        Runnable task
    );

    void executeFor(
        org.bukkit.entity.Entity entity,
        Runnable task
    );

    void executeAsync(Runnable task);
}
```

Die Abstraktion darf keine klassische Scheduler-Alternative enthalten. In Phase 1
wird noch keine Collector-Scheduling-Logik implementiert.

Die spätere Capability-Prüfung des isolierten Folia-Metrikproviders bezieht sich
ausschließlich auf die konkrete öffentliche Folia-API, die seine Messung benötigt.
Sie prüft weder Servername noch Versionsstring und lädt die Providerklasse auf
Paper nicht vorzeitig.

## 4.4 Snapshot-Regel

Jeder Scheduler-Task erzeugt nur lokale Werte. Erst nach vollständiger Erfassung wird
ein unveränderlicher Snapshot veröffentlicht.

Unvollständige Zwischenstände dürfen nicht sichtbar werden.

## 4.5 Regionsbeobachtung

Dieser Abschnitt betrifft den späteren isolierten Folia-Provider und ist nicht
Bestandteil von Phase 1.

Spielerpositionen dürfen intern als temporäre Beobachtungsquelle dienen. Dabei gilt:

- keine Speicherung von Spielername oder UUID im Metriksnapshot
- keine Ausgabe einer Beobachtung pro Spieler
- nur aggregierte TPS-Verteilungen pro Welt
- Messpunkte nach Ablauf entfernen
- dynamische Regionsänderungen berücksichtigen
