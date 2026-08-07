# 11. Installation

Diese Anleitung richtet sich an Administratoren eines Paper- oder
Folia-Servers.

## 11.1 Voraussetzungen

- Java 25 oder neuer
- Paper oder Folia ab der API-Linie 26.1.2
- Zugriff auf das Serververzeichnis und die Serverkonsole
- ein Prometheus-kompatibler Scraper, falls Metriken dauerhaft gesammelt werden
  sollen

Paper 26.1.2 Build 74 und Folia 26.1.2 Build 8 sind die fest gepinnten
CI-Zielbuilds. Andere Serverimplementierungen und Forks werden nicht aktiv
blockiert, aber nicht offiziell unterstützt.

## 11.2 Plugin installieren

1. Das Plugin-JAR und die zugehörige `.sha256`-Datei aus einem GitHub Release
   herunterladen.
2. Die SHA-256-Prüfsumme prüfen.
3. Das Plugin-JAR in das Verzeichnis `plugins/` des Minecraft-Servers kopieren.
4. Den Server mit Java 25 starten.
5. In der Konsole die Meldung `FoliaPrometheusExporter started.` prüfen.

Beim ersten Start wird die Konfiguration unter
`plugins/FoliaPrometheusExporter/config.yml` angelegt. Es wird genau ein
gemeinsames Plugin-JAR für Paper und Folia verwendet.

Unter Linux kann die heruntergeladene Prüfsumme so geprüft werden:

```bash
sha256sum --check FoliaPrometheusExporter-<version>.jar.sha256
```

Unter PowerShell:

```powershell
Get-FileHash .\FoliaPrometheusExporter-<version>.jar -Algorithm SHA256
```

Den ausgegebenen Hash mit dem Inhalt der `.sha256`-Datei vergleichen.

## 11.3 HTTP-Endpunkte prüfen

Der HTTP-Server bindet standardmäßig an `127.0.0.1:9940` und stellt folgende
Endpunkte bereit:

| Endpunkt | Zweck |
|---|---|
| `/metrics` | Prometheus-Metriken |
| `/health` | Liveness des HTTP-Dienstes |
| `/ready` | vollständige Initialisierung des Exporters |

Auf demselben Host wie der Minecraft-Server:

```bash
curl --fail http://127.0.0.1:9940/health
curl --fail http://127.0.0.1:9940/ready
curl --fail http://127.0.0.1:9940/metrics
```

Erwartet werden `ok`, `ready` und anschließend die Prometheus-Exposition. Direkt
während des Starts kann `/ready` vorübergehend `503` liefern. Schlägt bereits
`/health` fehl, sollten Serverlog, Bindeadresse und Portbelegung geprüft werden.

## 11.4 Bedeutung von `127.0.0.1`

`127.0.0.1` ist die IPv4-Loopback-Adresse. Mit der Standardbindung ist der
Metrics-Port nur vom Minecraft-Host erreichbar und nicht von anderen Rechnern
im Netzwerk. Das ist beabsichtigt, denn der Exporter besitzt keine eigene
Authentifizierung und keine TLS-Terminierung.

Läuft Prometheus oder Grafana Alloy auf demselben Host, sollte die lokale
Bindung beibehalten werden. Läuft der Scraper auf einem anderen Host, muss die
Bindeadresse bewusst geändert und der Zugriff durch Firewall, privates Netzwerk,
VPN oder einen abgesicherten Reverse Proxy begrenzt werden. Port `9940` sollte
nicht unnötig direkt aus dem Internet erreichbar sein.

Bei Containern und getrennten Network Namespaces bezeichnet `127.0.0.1` jeweils
den Container beziehungsweise Namespace selbst. In diesem Fall muss eine
erreichbare interne Adresse verwendet werden, ohne den Port öffentlich
freizugeben.

## 11.5 Konfiguration ändern

Bindeadresse, Port und Pfade stehen im Abschnitt `http`:

```yaml
http:
  bind-address: "127.0.0.1"
  port: 9940
  metrics-path: "/metrics"
  health-path: "/health"
  ready-path: "/ready"
  worker-threads: 2
```

Nach einer Änderung:

1. Den Server kontrolliert mit `stop` beenden.
2. Prüfen, dass der Java-Prozess vollständig beendet ist.
3. `config.yml` bearbeiten.
4. Den Server regulär neu starten.
5. `/health`, `/ready` und den konfigurierten Metrics-Pfad erneut prüfen.

Ein Hot-Reload oder der Serverbefehl `/reload` wird nicht empfohlen. Ein
Plugin-Reload baut Registry und Snapshots neu auf und kann Event-Counter auf null
zurücksetzen.

Die vollständige Konfigurationsreferenz steht in
[docs/05-configuration.md](05-configuration.md).

## 11.6 Prometheus oder Alloy anbinden

Direkt nutzbare Beispiele:

- [Prometheus](../examples/prometheus/prometheus.yml)
- [Grafana Alloy](../examples/grafana-alloy/config.alloy)
- [Prometheus-Alertregeln](../examples/prometheus/alerts.yml)

Weitere Hinweise enthält die [Monitoring-Dokumentation](12-monitoring.md).
