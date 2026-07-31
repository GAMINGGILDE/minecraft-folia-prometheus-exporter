# 9. Verbindliche Entscheidungen und verbleibende Detailfragen

## 9.1 Festgelegt

```text
Pluginname: FoliaPrometheusExporter
Repository: minecraft-folia-prometheus-exporter
Group: de.minecraftgilde
Artifact: minecraft-folia-prometheus-exporter
Package: de.minecraftgilde.prometheus
Java-Mindestversion: 25
Plattform: ausschließlich Folia
Lizenz: MIT
Interne Folia-APIs in Version 1: ausgeschlossen
Individuelle Spielermetriken: ausgeschlossen
Counter-Persistenz in Version 1: nein
```

## 9.2 Noch vor der Implementierung zu prüfen

- exakte veröffentlichte Folia-API-Koordinate für 26.1.2
- aktuelle stabile Prometheus-Java-Client-Version und benötigte Module
- Wahl des eingebetteten HTTP-Servers
- endgültige Collector-Standardintervalle
- genaue Strategie zur Regionsbeobachtung über öffentliche APIs
- genaue Entity-Abgleichstrategie
- gewünschte Standard-Buckets für Histogramme
- Release- und Changelog-Format

## 9.3 Nicht mehr offen

- keine Paper-Kompatibilität
- keine Spigot-/Bukkit-Kompatibilität
- kein experimenteller Internal Provider in Version 1
- keine individuellen Spielerstatistiken
