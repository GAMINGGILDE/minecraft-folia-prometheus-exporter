# 1. Projektumfang

## 1.1 Ziel

Entwicklung eines langlebigen Prometheus-Exporter-Plugins ausschließlich für Folia. Das Plugin beginnt mit Folia 26.1.2, benötigt Java 25 und soll spätere Folia-Versionen ohne große Umbauten unterstützen.

## 1.2 Kernanforderungen

- Prometheus-Endpunkt unter `/metrics`
- optionale Endpunkte `/health` und `/ready`
- standardmäßig Bindung an `127.0.0.1`
- Folia-first und threadsicher
- Snapshot-basierte Datenerfassung
- keine Live-Abfragen von Minecraft-Daten während eines Scrapes
- ausschließlich öffentliche Folia-, Bukkit- und Java-APIs
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
- optionale, aggregierte Gameplay-Counter
- spätere Pluginintegrationen über getrennte Provider

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
- keine NMS- oder internen Folia-Abhängigkeiten
- kein Zugriff auf Minecraft-Weltdaten vom HTTP-Thread
- keine unbeschränkte Auflistung einzelner Chunks als Zeitreihen
- keine dynamischen internen Folia-Regions-IDs als langlebige Labels

## 1.5 Stabilitätsklassen

| Klasse | Bedeutung |
|---|---|
| Stabil | Öffentliche API, für Standardbetrieb vorgesehen |
| Snapshot | Folia-konform gesammelt und zwischengespeichert |
| Eventbasiert | Fortlaufend über Events gezählt |
| Abgeleitet | Aus stabilen Metriken berechnet |
| Optional | Standardmäßig deaktiviert |
| Experimentell | Kann versionsabhängig sein |
| Nicht verfügbar | Derzeit keine belastbare öffentliche API |

## 1.6 Qualitätsziele

- Scrape-Latenz möglichst unter 10 ms
- keine Welt- oder Entity-Scans während eines Scrapes
- Collector-Laufzeiten als eigene Metriken sichtbar
- Snapshot-Alter als eigene Metrik sichtbar
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
Plattform: ausschließlich Folia
Lizenz: MIT
```

## 1.8 Nicht unterstützte Plattformen

- Paper
- Spigot
- CraftBukkit
- Purpur
- andere Bukkit-/Paper-Forks

Es werden keine Fallbacks oder Kompatibilitätsschichten für diese Plattformen entwickelt.
