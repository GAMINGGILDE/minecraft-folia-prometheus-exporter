# ADR 0014: Aggregierte Event-Counter in Phase 5

## Entscheidung

Phase 5 implementiert genau einen verwalteten Collector `events`. Er registriert
zehn threadsichere Prometheus-Counter und einen Bukkit-Listener in der privaten
Registry beziehungsweise am Plugin. Der Schalter `collectors.events` ist
standardmäßig `true` und aktiviert oder deaktiviert die Gruppe vollständig.

Der Collector besitzt keinen periodischen Scheduler, kein Snapshot-Repository,
keinen statischen Countercache und keine globale Registry. Eventhandler erhöhen
den bereits registrierten Counter unmittelbar auf dem Thread, der das öffentliche
Paper-/Bukkit-Event ausliefert.

## Loginquelle und Doppelzählung

`PlayerLoginEvent` ist die einzige Loginquelle. Es repräsentiert die finale
öffentliche Loginentscheidung genau einmal und bietet die strukturierten Werte
`ALLOWED`, `KICK_FULL`, `KICK_BANNED`, `KICK_WHITELIST` und `KICK_OTHER`.
`minecraft_login_attempts_total` steigt einmal für jedes Event; bei allen Werten
außer `ALLOWED` steigt zusätzlich genau eine Denial-Reihe.

Die tatsächlich aufgelösten öffentlichen Sources von
`paper-api:26.1.2.build.74-stable` und
`folia-api:26.1.2.build.8-stable` enthalten für die geprüften Quellen denselben
API-Vertrag:

| Quelle | Einmaligkeit und Phase | Strukturierte Gründe / `server_full` | Stabilität und Threading | Datenschutz / Entscheidung |
|---|---|---|---|---|
| `PlayerLoginEvent` | Eine einzelne finale Eventquelle für jeden Versuch, der diese Serverphase erreicht | `Result` enthält `ALLOWED`, `KICK_FULL`, `KICK_BANNED`, `KICK_WHITELIST`, `KICK_OTHER`; `server_full` ist eindeutig | Öffentlich, aber seit 1.21.6 deprecated und mit `@Warning` versehen; direkter kurzer Eventhandler, kein Schedulerwechsel | Host, Adressen und Player werden nicht gelesen; gewählt |
| `AsyncPlayerPreLoginEvent` | Frühe asynchrone Phase nach erfolgreicher Mojang-Authentifizierung; spätere Validierungen können das Ergebnis noch ändern | Gleichnamige strukturierte Resultwerte, aber keine vollständige finale Entscheidung | Öffentlich und nicht als Event deprecated; asynchron, kein Schedulerwechsel für ein Counterupdate | Name, UUID, IP, Host und Connection sind verfügbar, werden aber nicht benötigt; als alleinige finale Quelle ungeeignet |
| `PlayerConnectionValidateLoginEvent` | Laut Javadoc beim ersten Login und erneut am Ende einer Konfigurationsphase möglich; daher bis zu zwei Aufrufe derselben Verbindung | Nur `isAllowed()` und eine freie `Component`; kein strukturierter Cause und kein eindeutig klassifizierbares `server_full` | Öffentlich, aber das gesamte Connection-Package ist `@ApiStatus.Experimental`; synchroner Eventcallback | Eine Kombination bräuchte Connection- oder Spieleridentität zur Deduplizierung; nicht verwendet |
| `PlayerServerFullCheckEvent` | Nur ein einzelner Teilcheck, keine vollständige Attemptquelle | `isAllowed() == false` ist strukturell ein Full-Check | Öffentlich und nicht deprecated; direkter Eventcallback | Exponiert ein Profil, das nicht benötigt wird; nicht mit anderen Phasen kombiniert |
| `ProfileWhitelistVerifyEvent` | Nur Whitelist-Teilcheck, keine vollständige Attemptquelle | Strukturiert nur Whiteliststatus | Öffentlich und nicht deprecated; direkter Eventcallback | Exponiert ein Profil; nicht kombiniert |
| `PlayerHandshakeEvent` | Zu frühe Handshakephase, nicht gleichbedeutend mit einem verarbeiteten Login | Nur Handshakefehler, keine finalen Loginresultate oder `server_full` | Öffentlich; asynchroner Low-Level-Callback | Enthält Host-, UUID- und Profildaten; nicht verwendet |
| `PlayerConnectionCloseEvent` | Disconnectquelle nach Pre-Login und auch nach erfolgreichem Join, daher keine Attempt- oder Denialquelle | Kein strukturierter Login-Ablehnungsgrund | Öffentlich; kann synchron oder asynchron feuern | Enthält Name, UUID und IP; nicht verwendet |
| Initial-/Async-Konfiguration und `PlayerJoinEvent` | Spätere Konfigurations- beziehungsweise reine Erfolgspunkte | Keine vollständigen strukturierten Denialgründe | Connection-Konfigurationspackage experimentell; Join stabil | Können abgelehnte Versuche nicht vollständig abbilden; nicht verwendet |

Eine Kombination mehrerer Phasen würde Doppelzählungen erzeugen oder
Connection-/Spieleridentitäten zur Deduplizierung benötigen und ist
ausgeschlossen. Versuche, die vor `PlayerLoginEvent` beispielsweise bereits an
der Authentifizierung scheitern, sind mit dieser finalen Quelle nicht sichtbar;
sie werden nicht aus Texten oder anderen Phasen ergänzt.

`PlayerLoginEvent` ist seit 1.21.6 deprecated, weil ein Listener den Player früh
erzeugt und laut API-Hinweis einen der zwei Vanilla-Validierungspunkte überspringt.
Dieses Laufzeit- und Migrationsrisiko wird für 26.1.2 akzeptiert, weil keine
moderne stabile Einzelquelle dieselbe doppelfreie finale Semantik samt
strukturierten Gründen bietet. `@SuppressWarnings("deprecation")` steht nur an
der einen Handler-Methode; `EventMetrics` und `EventReasonMapper` erhalten allein
den strukturierten Enumnamen und hängen nicht vom deprecated Typ ab.

Migriert wird, sobald Paper und Folia in der unterstützten API-Linie eine stabile
öffentliche Quelle anbieten, die pro verarbeitetem Versuch genau einmal feuert,
die finale Entscheidung samt strukturierten Gründen einschließlich Full-Check
liefert und keine identitätsbasierte Deduplizierung verlangt. Interne APIs,
Reflection oder mehrere parallele Loginquellen sind kein Übergangsersatz.

## Feste Reason-Normalisierung

Zulässig sind ausschließlich:

```text
banned
whitelist
server_full
invalid_session
idle
connection_lost
moderation
plugin
unknown
```

Der zentrale `EventReasonMapper` ordnet ausschließlich Namen strukturierter
öffentlicher Enums zu. Er liest keine Kick-, Login- oder Chatnachrichten.

Login:

| `PlayerLoginEvent.Result` | Reason |
|---|---|
| `KICK_BANNED` | `banned` |
| `KICK_WHITELIST` | `whitelist` |
| `KICK_FULL` | `server_full` |
| `KICK_OTHER` oder unbekannt | `unknown` |

Die aktuelle finale Loginquelle besitzt keinen strukturierten
Invalid-Session-Wert. `invalid_session` bleibt im festen Vokabular für einen
zukünftigen eindeutigen Enumnamen, wird aber nicht aus Nachrichtentext geraten.
Eine explizite Plugin-Ablehnung ist im aktuellen Result ebenfalls nicht von
anderen `KICK_OTHER`-Fällen unterscheidbar und bleibt deshalb `unknown`.

Kick:

| `PlayerKickEvent.Cause` | Reason |
|---|---|
| `PLUGIN` | `plugin` |
| `WHITELIST` | `whitelist` |
| `BANNED`, `IP_BANNED` | `banned` |
| `TIMEOUT` | `connection_lost` |
| `IDLING` | `idle` |
| Kick-Command, Flying-, Movement-, Payload-, Cookie-, Spam-, Illegal- und Chatvalidierungsverstöße sowie Resource-Pack-Ablehnung | `moderation` |
| `DUPLICATE_LOGIN`, `RESTART_COMMAND`, `UNKNOWN` oder unbekannt | `unknown` |

Die aktuelle Cause-Menge besitzt mit `TIMEOUT` bereits einen strukturierten Wert,
der als Verbindungsverlust klassifiziert wird. Die vorsorglich unterstützten
Namen `CONNECTION_LOST` und `NETWORK_ERROR` werden ebenfalls
`connection_lost`, sobald eine spätere öffentliche API einen dieser Enumwerte
liefert. Unsichere Fälle bleiben `unknown`.

Mit den aktuellen 26.1.2-Enums können Login und Kick zusammen `banned`,
`whitelist`, `server_full`, `idle`, `connection_lost`, `moderation`, `plugin`
und `unknown` tatsächlich erzeugen. `invalid_session` bleibt für einen
zukünftigen eindeutigen strukturierten Loginwert reserviert. Der Mapper erkennt
vorsorglich `INVALID_SESSION`, `KICK_INVALID_SESSION`, `KICK_PLUGIN`,
`CONNECTION_LOST` und `NETWORK_ERROR`, wertet aber niemals Nachrichtentext aus.

## Join, Quit und Kick

`PlayerJoinEvent` und `PlayerQuitEvent` zählen je ein beobachtetes Event.
`PlayerKickEvent` zählt nur, wenn es bei `MONITOR` nicht abgebrochen ist. Paper
beziehungsweise Folia kann nach einem erfolgreichen Kick zusätzlich ein
`PlayerQuitEvent` auslösen. Dann steigen bewusst sowohl der Kick-Counter als auch
der Quit-Counter: Kick beschreibt den Auslöser, Quit das tatsächliche
Sitzungsende. Eine Korrelation über Spielername, UUID oder Playerobjekt findet
nicht statt.

## Ping und Chat

Jedes `ServerListPingEvent` erhöht genau den Ping-Counter. IP-Adresse,
Clienthostname, MOTD, Spielerprobe, Protokoll und Antwortinhalt werden nicht
gelesen.

Chat verwendet das moderne öffentliche `AsyncChatEvent` bei `MONITOR` mit
`ignoreCancelled = true` und einer zusätzlichen defensiven Cancelled-Prüfung.
Nur nicht abgebrochene, von dieser Spielerchatquelle ausgelieferte Events zählen.
Commands werden über `PlayerCommandPreprocessEvent` verarbeitet und besitzen
keinen Handler im Collector; Systemnachrichten lösen `AsyncChatEvent` ebenfalls
nicht aus. Der Nachrichteninhalt wird nicht gelesen, gespeichert oder geloggt.

## Chunksemantik und Weltlabels

`ChunkLoadEvent` erhöht immer `minecraft_chunks_loaded_total{world}`. Bei
`isNewChunk() == true` erhöht dasselbe Event zusätzlich
`minecraft_chunks_generated_total{world}`. `ChunkUnloadEvent` erhöht
`minecraft_chunks_unloaded_total{world}`.

Der Handler liest nur `event.getWorld().getName()` und `isNewChunk()`. Chunkobjekt
und Event verlassen den Callback nicht; X-/Z-Koordinaten werden nicht gelesen.
`WorldLabel` bewahrt den öffentlichen Weltname unverändert, validiert ihn als
nichtleer und wird nun gemeinsam von Phase-4-Werttypen und Phase-5-Countern
verwendet.

Weil der Prometheus Java Client gelabelte Counterfamilien ohne Datenpunkte im
Textformat nicht rendert, initialisiert der Collector bei `ServerLoadEvent` die
drei Chunkfamilien mit nullwertigen Reihen für alle tatsächlich geladenen Welten.
`WorldLoadEvent` übernimmt später neu geladene Welten. Beide Handler lesen nur
Weltname-Strings und erzeugen keine zusätzlichen Ereigniszählungen. Die beiden
Reasonfamilien erhalten je eine feste `unknown`-Nullreihe. Dadurch sind alle
zehn registrierten Familien bei Standardkonfiguration sichtbar, ohne erfundene
Weltlabels oder periodische Erfassung einzuführen.

## Threading und Lifecycle

Prometheus-Counter aus Client 1.8.0 sind threadsicher. Login, Ping, Chat und
Chunkereignisse dürfen parallel auf unterschiedlichen Threads inkrementieren.
Ein Schedulerwechsel würde nur Latenz, Queuezustand und neue Fehlerpfade erzeugen
und ist deshalb ausgeschlossen.

Ein Read-/Write-Lock schützt die Annahmegrenze. Eventhandler halten den Read-Lock
nur während Validierung und Counterinkrement. Stop hält den Write-Lock, sperrt
weitere Events und meldet den Listener über die öffentliche
`HandlerList.unregisterAll(Listener)`-API ab. Nach Rückkehr von Stop kann kein
bereits begonnenes Inkrement mehr nachlaufen. Der vorhandene
`AbstractCollector` verhindert doppelte Starts und macht Stop idempotent.

Ein fehlerhaftes Eventupdate wird im Eventbereich abgefangen und ohne
Eventpayload als `IllegalStateException` rate-limitiert gemeldet. Die
ursprüngliche `RuntimeException` bleibt als Cause erhalten. Wirft der
Failure-Listener selbst, wird auch diese Exception innerhalb des Collectors
abgefangen; weder Counter- noch Reporterfehler verlassen einen Minecraft-
Eventthread. Andere Eventtypen, Collector, Readiness und HTTP bleiben aktiv.

## Datenschutz und Counter-Lifecycle

Nicht gelesen oder exportiert werden Spielername, UUID, IP-Adresse,
Clienthostname, Chatinhalt, Kicknachricht, Loginfehlermeldung, freie Reasontexte
und Chunkkoordinaten. Die einzigen neuen Labels sind `reason` aus der festen
Neunermenge und `world` aus der gemeinsamen Weltlabelvalidierung.

Die Counter beginnen bei jedem Plugin- beziehungsweise Serverstart bei null und
werden nicht persistiert. Ein Plugin-Reload kann ebenfalls einen Reset erzeugen.
Prometheus erkennt Counter-Resets; Auswertungen verwenden `rate()` oder
`increase()`.

## Konsequenzen

- Paper und Folia 26.1.2 verwenden denselben allgemeinen öffentlichen Code.
- Es gibt keine Liveabfrage von Minecraft-Daten beim Scrape.
- Deaktivierung entfernt die gesamte Eventgruppe ohne Auswirkungen auf andere
  Collector.
- Commands, Chunk-Load-Failures, Entity-/Gameplayevents und Counter-Persistenz
  bleiben außerhalb von Phase 5.
