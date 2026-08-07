# 12. Monitoring-Beispiele

Die Dateien unter `examples/` sind bewusst klein gehalten. Sie enthalten keine
Metrikfilter und setzen den Standardendpunkt `127.0.0.1:9940/metrics` voraus.
Bei abweichendem `http.metrics-path` muss `metrics_path` im jeweiligen Beispiel
denselben Wert erhalten.

## 12.1 Prometheus

Die Datei [examples/prometheus/prometheus.yml](../examples/prometheus/prometheus.yml)
enthält einen statischen Job mit dem Namen `folia-prometheus-exporter`.

Sie kann in eine bestehende Prometheus-Konfiguration übernommen oder als kleine
eigenständige Konfiguration verwendet werden. Nach dem Reload von Prometheus
sollte das Target im Status `UP` erscheinen. Läuft Prometheus in einem Container,
bezeichnet `127.0.0.1` den Container selbst; dann ist als Target eine intern
erreichbare Adresse des Minecraft-Hosts zu verwenden.

## 12.2 Grafana Alloy und Remote Write

Die Datei [examples/grafana-alloy/config.alloy](../examples/grafana-alloy/config.alloy)
scraped denselben lokalen Endpunkt und leitet alle Metriken unverändert an einen
Prometheus-kompatiblen Remote-Write-Endpunkt weiter.

Zugangsdaten stehen nicht in der Datei. Vor dem Start von Alloy werden folgende
Umgebungsvariablen gesetzt:

```text
PROMETHEUS_REMOTE_WRITE_URL
PROMETHEUS_REMOTE_WRITE_USERNAME
PROMETHEUS_REMOTE_WRITE_PASSWORD
```

Der jeweilige Monitoring-Anbieter stellt URL, Benutzername und Passwort bereit.
Die Werte dürfen nicht in das Repository eingecheckt werden. Verwendet der
Remote-Write-Endpunkt keine Basic Authentication, ist der `basic_auth`-Block aus
der lokalen Kopie zu entfernen.

Alloy erzeugt für den Scrape wie Prometheus die Metrik `up` und setzt durch
`job_name` dasselbe Joblabel wie das Prometheus-Beispiel.

Syntaxreferenzen: [Grafana Alloy `prometheus.scrape`](https://grafana.com/docs/alloy/latest/reference/components/prometheus/prometheus.scrape/)
und [Grafana Alloy `prometheus.remote_write`](https://grafana.com/docs/alloy/latest/reference/components/prometheus/prometheus.remote_write/).

## 12.3 Alert-Regeln

Die Datei [examples/prometheus/alerts.yml](../examples/prometheus/alerts.yml)
enthält konservative Beispielregeln für:

- nicht erreichbaren Exporter
- fehlende Readiness oder Health
- interne Scrape-Fehler
- Collector im Zustand `failed`
- veralteten Entity-Vollabgleich
- niedrige TPS in beobachteten Folia-Regionen

Prometheus lädt sie über `rule_files`, zum Beispiel:

```yaml
rule_files:
  - /etc/prometheus/rules/folia-prometheus-exporter-alerts.yml
```

Die Datei muss an diesen Pfad kopiert und Prometheus anschließend neu geladen
werden. Bei einem Remote-Write-Backend werden die Regeln stattdessen in dessen
Regelverwaltung eingebunden.

`FoliaObservedRegionTpsLow` verwendet eine Folia-Metrik. Auf Paper existiert
diese Familie nicht, sodass die Regel dort keinen permanenten False Positive
erzeugt. Auch auf Folia werden nur tatsächlich über öffentliche Anker
beobachtete Regionen bewertet; fehlende Beobachtungen werden nicht als null TPS
interpretiert.

Die Beispielschwellen sind konservative Betriebswerte, aber keine universelle
Kapazitätsaussage. Vor produktiver Alarmierung sollten Dauer und TPS-Grenze an
das gewünschte Service-Level angepasst werden. Die Regeln setzen den Jobnamen
`folia-prometheus-exporter` aus den mitgelieferten Scrape-Beispielen voraus.

## 12.4 Datenschutz und Kardinalität

Die Beispiele ergänzen keine Labels und filtern keine Metriken. Der Exporter
liefert keine Spielernamen, UUIDs, IP-Adressen, Chat-Inhalte, Chunk- oder
Regionskoordinaten und keine freien Fehlertexte als Labels.

Zwei optionale Familien verdienen besondere Aufmerksamkeit:

- `minecraft_plugin_info` enthält Pluginname, Version und Aktivierungsstatus.
  Sie ist standardmäßig deaktiviert, weil sich installierte Plugins und
  Versionen zwischen Servern unterscheiden und zusätzliche Reihen erzeugen.
- `minecraft_entities{world,type}` enthält exakte öffentliche Entitytypen. Sie
  ist standardmäßig deaktiviert, weil die Reiheanzahl mit Typen und Welten
  wächst.

Die festen Entitygruppen, Folia-Fenster/-Statistiken und strukturierten
Reasonwerte besitzen dagegen kontrollierte Labelmengen. Individuelle
Spielermetriken werden nicht exportiert.
