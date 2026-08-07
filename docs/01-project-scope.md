# 1. Projektumfang

## 1.1 Ziel

FoliaPrometheusExporter ist ein langlebiges Prometheus-Exporter-Plugin für Paper
und Folia. Die offizielle Unterstützung beginnt mit der API-Linie 26.1.2, setzt
Java 25 voraus und verwendet genau ein gemeinsames Plugin-JAR.

## 1.2 Kernanforderungen

- Prometheus-Endpunkt unter `/metrics`
- optionale Endpunkte `/health` und `/ready`
- standardmäßig Bindung an `127.0.0.1`
- Paper- und Folia-konform sowie threadsicher
- Snapshot-basierte Datenerfassung
- keine Live-Abfragen von Minecraft-Daten während eines Scrapes
- ausschließlich öffentliche Paper-, Folia-, Bukkit- und Java-APIs
- einzelne Collector getrennt aktivierbar
- konfigurierbare Erfassungsintervalle
- kontrollierte Label-Kardinalität
- Gradle-Build mit Kotlin DSL
- reproduzierbare Builds über Gradle Wrapper
- Fehler eines Collectors dürfen andere Collector nicht beeinträchtigen

## 1.3 Unterstützte Metrikbereiche

- Serverinformationen
- aggregierte Spielerzahlen
- Verbindungs- und Serverereignisse
- Welten und Chunks
- aggregierte Entities
- Folia-Regions-TPS
- abgeleitete Folia-Leistungswerte
- JVM
- Garbage Collection
- Threads und Klassen
- Prozess und CPU
- begrenzte Dateisystemmetriken
- Eigenüberwachung des Exporters

## 1.4 Harte Nicht-Ziele

- keine individuellen Spielermetriken
- keine individuellen Spielerstatistiken
- keine Spielernamen oder UUIDs in Metriklabels
- keine Spielerpositionen
- keine Inventarinhalte
- keine Chat-Inhalte
- keine Befehlsargumente
- keine freien Kick- oder Fehlermeldungen als Label
- kein vollständiger Ersatz für node_exporter
- keine NMS- oder internen Paper-/Folia-Abhängigkeiten
- kein experimenteller oder interner Provider in Version 1
- kein Fallback auf den klassischen BukkitScheduler
- kein Zugriff auf Minecraft-Weltdaten vom HTTP-Thread
- keine unbeschränkte Auflistung einzelner Chunks als Zeitreihen
- keine dynamischen internen Folia-Regions-IDs als langlebige Labels
- keine Gameplay-Counter
- keine Spawn-, Removal-, Kill- oder Item-Despawn-Counter für Entities

## 1.5 Stabilitätsklassen

| Klasse | Bedeutung |
|---|---|
| Stabil | Öffentliche API, für Standardbetrieb vorgesehen |
| Snapshot | Ownership-konform gesammelt und zwischengespeichert |
| Eventbasiert | Fortlaufend über Events gezählt |
| Abgeleitet | Aus stabilen Metriken berechnet |
| Optional | Standardmäßig deaktiviert |
| Experimentell | Kann versionsabhängig sein |
| Nicht verfügbar | Derzeit keine belastbare öffentliche API |

## 1.6 Qualitätsziele

- Scrape-Latenz möglichst unter 10 ms
- keine Welt- oder Entity-Scans während eines Scrapes
- Entity-Abgleichslaufzeit und letzter erfolgreicher Abgleich sichtbar
- Alter gültiger Folia-Regionsbeobachtungen sichtbar
- sauberer Shutdown des HTTP-Servers
- keine Blockierung von Region-Tickthreads durch Dateisystemoperationen
- definierte Timeouts und Fehlerbehandlung

## 1.7 Verbindliche Projektidentität

```text
Pluginname: FoliaPrometheusExporter
Repository: minecraft-folia-prometheus-exporter
Group: de.minecraftgilde
Artifact: minecraft-folia-prometheus-exporter
Package: de.minecraftgilde.prometheus
Java: 25+
Plattformen: Paper und Folia ab API-Linie 26.1.2
Auslieferung: ein gemeinsames Plugin-JAR
Lizenz: MIT
```

## 1.8 Plattformunterstützung

- Offiziell getestet und unterstützt werden Paper und Folia.
- Andere Serverimplementierungen und Forks werden nicht aktiv blockiert.
- Für andere Serverimplementierungen und Forks besteht kein offizieller Supportanspruch.
- Es werden keine Scheduler-Fallbacks für Spigot oder CraftBukkit entwickelt.

Allgemeiner Code kompiliert gegen die öffentliche `paper-api`. Die
Folia-spezifische Regions-TPS-Erfassung ist in einem isolierten Provider
gekapselt, der erst nach Prüfung der benötigten öffentlichen Capability geladen
wird. Gemeinsame Server-, Welt-, Event- und Entity-Funktionen verwenden die
öffentlichen Paper-APIs auf beiden Plattformen.

Die unterstützten Zielplattformen werden in einem separaten CI-Smoke-Test gegen
fest gepinnte Paper- und Folia-Serverbuilds der API-Linie 26.1.2 gestartet. Der
Test ist vom normalen Build getrennt und verwendet keine dynamische Latest-Auswahl.
