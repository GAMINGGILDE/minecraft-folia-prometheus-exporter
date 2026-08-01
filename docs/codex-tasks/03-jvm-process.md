# Codex-Aufgabe 3: JVM- und Prozessmetriken

Status: abgeschlossen.

Implementiere standardisierte JVM-, GC-, Thread-, Klassen-, Buffer-Pool-, CPU-,
Prozess- und Dateideskriptor-Metriken. Nutze etablierte Prometheus-Namen.

Umgesetzt mit den offiziellen Instrumentierungen des Prometheus Java Clients
1.8.0. Nicht angebotene `system_*`-, CPU-Usage- und Prozess-Uptime-Metriken werden
nicht nachgebaut. Details stehen im Metrikkatalog und in ADR 0012.
