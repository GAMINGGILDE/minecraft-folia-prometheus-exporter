# 8. Release-Prozess

Releases werden durch den Workflow
`.github/workflows/release.yml` aus einem vorhandenen Git-Tag erzeugt. Der
Workflow verwendet dieselbe Gradle-Buildlogik wie CI und veröffentlicht kein
zweites oder abweichend gebautes Artefakt.

## 8.1 Versionierung

Das Projekt verwendet semantische Versionierung:

- MAJOR: inkompatible Konfigurations- oder Metrikänderung
- MINOR: neue rückwärtskompatible Collector oder Metriken
- PATCH: Fehlerbehebung

Die Version stammt ausschließlich aus `projectVersion` in
`gradle.properties`. Ein Release-Tag muss exakt `v<projectVersion>` heißen, zum
Beispiel `v1.2.3` für `projectVersion=1.2.3`. Der Workflow lehnt abweichende Tags
und Versionen mit dem Suffix `-SNAPSHOT` ab.

Die aktuelle Entwicklungsversion bleibt bewusst `0.1.0-SNAPSHOT`. Vor dem ersten
Release ist daher eine ausdrückliche Projektentscheidung über die Releaseversion
und eine separate Änderung von `projectVersion` erforderlich. Der
Releaseworkflow erfindet oder überschreibt keine Versionsnummer.

## 8.2 Ablauf

1. Gewünschte Releaseversion festlegen und `projectVersion` bewusst anpassen.
2. `./gradlew clean build` lokal ausführen und die Änderung reviewen.
3. Einen annotierten Tag `v<projectVersion>` auf dem freizugebenden Commit
   erstellen und pushen.
4. Der Workflow validiert Gradle Wrapper und Java 25.
5. `./gradlew clean build` führt Unit-Tests, `foliaTest`, Architekturprüfungen
   und die Prüfung des einzelnen Shadow-JARs aus.
6. Der Workflow bestimmt genau das eine JAR unter `build/libs/`, kopiert es unter
   einen eindeutigen Release-Namen und erzeugt daneben eine SHA-256-Datei.
7. JAR und Prüfsumme werden als Workflow-Artefakt gespeichert und an ein GitHub
   Release für denselben vorhandenen Tag angehängt. Release Notes werden aus der
   GitHub-Historie generiert.

Der getrennte gepinnte Paper-/Folia-Server-Smoke-Test bleibt ein eigener
Workflow. Vor einem Release sollte dessen Ergebnis für den getaggten Commit
erfolgreich sein; der Releaseworkflow dupliziert den Serverdownload und
Starttest nicht.

## 8.3 Release-Artefakte

Ein Release enthält:

- `FoliaPrometheusExporter-<version>.jar`
- `FoliaPrometheusExporter-<version>.jar.sha256`
- generierte GitHub Release Notes

Das JAR ist das vom Build geprüfte Shadow-JAR. Es enthält die relocateten
Prometheus-Abhängigkeiten, aber keine Paper-, Folia-, Bukkit-, Minecraft- oder
NMS-API-Klassen. Nebenartefakte und ein ungeschattetes Standard-JAR werden nicht
veröffentlicht.

Die Prüfsumme kann unter Linux beispielsweise so geprüft werden:

```bash
sha256sum --check FoliaPrometheusExporter-<version>.jar.sha256
```

Unter PowerShell:

```powershell
Get-FileHash .\FoliaPrometheusExporter-<version>.jar -Algorithm SHA256
```

## 8.4 Kompatibilität

Der stabile Kern kompiliert gegen die öffentliche `paper-api`. Folia-spezifische
Regions-TPS-Funktionen sind in einem isolierten Provider gekapselt. Die
veröffentlichten und gepinnt getesteten Zielplattformen sind Paper 26.1.2 Build
74 und Folia 26.1.2 Build 8; andere Serverimplementierungen und Forks sind nicht
offiziell unterstützt.
