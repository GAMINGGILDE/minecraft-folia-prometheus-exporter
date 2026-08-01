# 6. Prometheus-Format und Kardinalität

## 6.0 Implementierung

Formatverhandlung und Serialisierung übernimmt der offizielle Prometheus Java
Client 1.8.0. Ein eigener Text-Renderer ist ausgeschlossen. Die benötigten
Clientmodule werden in das Plugin-JAR eingebunden und von `io.prometheus` nach
`de.minecraftgilde.prometheus.internal.prometheus` relocatet.

## 6.1 Typen

- Gauge: aktueller Zustand
- Counter: monoton wachsender Ereigniszähler
- Histogram: Verteilung von Laufzeiten
- Info: statische Versions- oder Buildinformation

## 6.2 Einheiten

- Tick- und Laufzeiten: Sekunden
- Speicher und Dateien: Bytes
- CPU: Verhältnis 0 bis 1
- Timestamps: Unix-Zeit in Sekunden

## 6.3 Labels

Erlaubte kontrollierte Labels:

- `world`
- `window`
- `stat`
- `threshold`
- `threshold_seconds`
- `collector`
- `reason` aus fester Liste
- `type` aus Minecraft-Registry, nur optional
- `group` aus fester Liste
- `command` nur Basisbefehl, optional

Verbotene Labels:

- Spielername
- UUID
- Chatinhalt
- Befehlsargumente
- freie Fehlertexte
- Koordinaten
- dynamische interne Regions-ID

## 6.4 Counter-Resets

Event-Counter werden in Version 1 nicht lokal persistiert. Prometheus erkennt
Counter-Resets nach Neustarts. Langzeitwerte werden in Prometheus berechnet.

## 6.5 Histogramme

Collector-Laufzeiten sollen als Histogramme exportiert werden. Regionale
Tickdauer-Histogramme dürfen nur angeboten werden, wenn die zugrunde liegenden
regionalen Tickdauern tatsächlich belastbar messbar sind.

## 6.6 Scrape-Datenquelle

Der Renderer und alle HTTP-Handler lesen ausschließlich immutable Snapshots und
kontrollierten Exporterstatus. Sie führen keine Live-Abfragen gegen Bukkit, Paper,
Folia oder Minecraft-Daten aus.
