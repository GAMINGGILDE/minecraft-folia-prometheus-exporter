# 7. Tests und Abnahmekriterien

## 7.1 Build

```bash
./gradlew clean build
```

muss erfolgreich sein.

## 7.2 Unit-Tests

- Quantile und Statistikberechnung
- TPS-Schwellenwerte
- Dauer- und Größenparser
- Konfigurationsvalidierung
- Prometheus-Namensprüfung
- Label-Normalisierung
- Kick-/Login-Grundklassifikation
- Snapshot-Austausch
- Ablauf von Regionsbeobachtungen

## 7.3 Integrationsprüfungen

- Plugin startet auf Ziel-Folia-Version
- `/metrics` antwortet
- `/health` und `/ready` funktionieren
- HTTP-Server stoppt beim Disable
- Collector-Fehler stoppen andere Collector nicht
- deaktivierte Collector erzeugen keine Metriken
- Snapshot-Alter steigt bei Collector-Ausfall
- keine Spieleridentitäten im Output

## 7.4 Threading-Prüfungen

- keine Weltzugriffe aus HTTP-Threads
- keine Entityzugriffe aus Common Pool
- regionsgebundene Aufrufe über Region Scheduler
- Entity-Aufrufe über Entity Scheduler
- Dateisystemoperationen nicht auf Tickthreads

## 7.5 Performance-Ziele

- Scrape serialisiert ausschließlich Snapshots
- keine vollständigen Entity-Scans pro Scrape
- keine Weltgrößenberechnung pro Scrape
- Collector-Laufzeiten werden gemessen
- konfigurierbare Intervalle und Timeouts

## 7.6 Definition of Done

- Code formatiert und dokumentiert
- Tests erfolgreich
- Metrikkatalog aktualisiert
- keine neue unkontrollierte Label-Kardinalität
- keine individuellen Spielermetriken
- öffentliche API im stabilen Kern
- Fehlermeldungen verständlich
