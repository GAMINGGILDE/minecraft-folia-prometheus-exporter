# 6. Prometheus-Format und Kardinalität

## 6.0 Implementierung

Formatverhandlung und Serialisierung übernimmt der offizielle Prometheus Java
Client 1.8.0. Ein eigener Text-Renderer ist ausgeschlossen. Die benötigten
Clientmodule werden in das Plugin-JAR eingebunden und von `io.prometheus` nach
`de.minecraftgilde.prometheus.internal.prometheus` relocatet.

Der Metrics-Endpunkt verwendet direkt den offiziellen `MetricsHandler` auf dem
JDK-HTTP-Server. Health-, Ready-, Fehler- und Methodenantworten sind stabile kurze
UTF-8-Textantworten.

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
- `collector`
- `state` aus der festen Collector-Zustandsmenge
- `endpoint` aus `metrics`, `health`, `ready`, `not_found`
- `status_class` aus `2xx`, `4xx`, `5xx`, `other`
- `reason` aus fester Liste
- `type` aus Minecraft-Registry, nur optional
- `group` aus fester Liste

Verbotene Labels:

- Spielername
- UUID
- Chatinhalt
- Befehlsargumente
- freie Fehlertexte
- Koordinaten
- dynamische interne Regions-ID

`reason` ist exakt auf `banned`, `whitelist`, `server_full`,
`invalid_session`, `idle`, `connection_lost`, `moderation`, `plugin` und
`unknown` begrenzt. Chunk-Eventfamilien besitzen ausschließlich `world`; Namen
werden über dieselbe Validierung wie die übrigen Weltmetriken übernommen.

## 6.4 Counter-Resets

Event-Counter werden in Version 1 nicht lokal persistiert. Sie beginnen bei
Plugin- beziehungsweise Serverstart bei null und können auch bei einem
Plugin-Reload zurückgesetzt werden. Prometheus erkennt Counter-Resets nach
Neustarts. Zeiträume und Langzeitwerte werden mit `rate()` beziehungsweise
`increase()` in Prometheus berechnet.

## 6.5 Laufzeiten und Verteilungen

Der Exporter registriert keine allgemeinen Collector-Histogramme. Die Dauer des
letzten erfolgreichen Entity-Abgleichs wird als
`minecraft_entity_reconciliation_duration_seconds` ausgegeben. Regionale
Tickdauer-Histogramme existieren nicht, weil keine belastbare öffentliche
Messquelle für regionale Tickdauern verfügbar ist.

## 6.6 Scrape-Datenquelle

Der Renderer und alle HTTP-Handler lesen immutable Minecraft-Snapshots,
bereits akkumulierten Event-Counterzustand, kontrollierten Exporterstatus und die
Registry-Callbacks der offiziellen JVM-/Prozessinstrumentierung. Diese Callbacks
lesen ausschließlich JDK- und Betriebssystemdaten. Es gibt keine Live-Abfragen
gegen Bukkit, Paper, Folia oder Minecraft-Daten.

Die HTTP-Eigenmetrik enthält weder den konfigurierten oder angefragten Rohpfad noch
Methode, Client-IP, vollständige URL oder User-Agent. Fehlerursachen und
Stacktraces werden protokolliert, aber nie als Label übernommen.
