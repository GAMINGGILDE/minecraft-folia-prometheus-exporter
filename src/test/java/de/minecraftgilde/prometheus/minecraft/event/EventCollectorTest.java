package de.minecraftgilde.prometheus.minecraft.event;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.prometheus.metrics.config.EscapingScheme;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.Test;

class EventCollectorTest {

    @Test
    void startAndStopAreIdempotentAndNoEventsCountAfterStop() throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        FakeRegistration registration = new FakeRegistration();
        EventCollector collector = new EventCollector(
            registry,
            true,
            registration,
            failure -> {}
        );

        collector.start();
        collector.start();
        collector.onJoin(new PlayerJoinEvent(player(), Component.text("private join")));
        assertEquals(1.0, value(registry, "minecraft_player_joins_total"));
        assertEquals(1, registration.registrations);

        collector.stop();
        collector.stop();
        collector.onJoin(new PlayerJoinEvent(player(), Component.text("private join")));

        assertEquals(1.0, value(registry, "minecraft_player_joins_total"));
        assertEquals(1, registration.unregistrations);
        assertFalse(registration.registered);
    }

    @Test
    void disabledCollectorRegistersNeitherListenerNorMetricFamilies()
        throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        FakeRegistration registration = new FakeRegistration();
        EventCollector collector = new EventCollector(
            registry,
            false,
            registration,
            failure -> {}
        );

        collector.start();

        assertTrue(registry.scrape().stream().findAny().isEmpty());
        assertEquals(0, registration.registrations);
        assertEquals(0, registration.unregistrations);
    }

    @Test
    void oneChosenLoginEventCountsOneAttemptAndAtMostOneDenial()
        throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        EventCollector collector = new EventCollector(
            registry,
            true,
            new FakeRegistration(),
            failure -> {}
        );
        collector.start();
        Player player = player();
        InetAddress address = InetAddress.getLoopbackAddress();

        collector.onLogin(loginEvent(
            player,
            address,
            "ALLOWED",
            "private allowed message"
        ));
        assertEquals(1.0, value(registry, "minecraft_login_attempts_total"));
        assertEquals(
            0.0,
            counter(registry, "minecraft_login_denied_total")
                .getDataPoints()
                .stream()
                .mapToDouble(point -> point.getValue())
                .sum()
        );

        collector.onLogin(loginEvent(
            player,
            address,
            "KICK_FULL",
            "private full message"
        ));
        assertEquals(2.0, value(registry, "minecraft_login_attempts_total"));
        assertEquals(1.0, value(
            registry,
            "minecraft_login_denied_total",
            "reason",
            "server_full"
        ));
        assertEquals(
            1.0,
            counter(registry, "minecraft_login_denied_total")
                .getDataPoints()
                .stream()
                .mapToDouble(point -> point.getValue())
                .sum()
        );
        assertFalse(exposition(registry).contains("private full message"));
    }

    @Test
    void handlersImplementLoginSessionChatKickPingAndChunkSemantics()
        throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        EventCollector collector = new EventCollector(
            registry,
            true,
            new FakeRegistration(),
            failure -> {}
        );
        collector.start();
        java.util.UUID privateUuid = java.util.UUID.fromString(
            "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        );
        Player player = player("private-player-name", privateUuid);
        InetAddress address = InetAddress.getByAddress(
            new byte[] { (byte) 203, 0, 113, 17 }
        );

        collector.onLogin(loginEvent(
            player,
            address,
            "ALLOWED",
            "private login message"
        ));
        collector.onLogin(loginEvent(
            player,
            address,
            "KICK_BANNED",
            "private denial message"
        ));
        collector.onJoin(new PlayerJoinEvent(player, Component.text("private join")));
        collector.onQuit(new PlayerQuitEvent(
            player,
            Component.text("private quit"),
            PlayerQuitEvent.QuitReason.DISCONNECTED
        ));

        PlayerKickEvent kick = new PlayerKickEvent(
            player,
            Component.text("private kick message"),
            Component.text("private leave message"),
            PlayerKickEvent.Cause.PLUGIN
        );
        collector.onKick(kick);
        collector.onKick(new PlayerKickEvent(
            player,
            Component.text("private timeout message"),
            null,
            PlayerKickEvent.Cause.TIMEOUT
        ));
        PlayerKickEvent cancelledKick = new PlayerKickEvent(
            player,
            Component.text("cancelled private kick"),
            null,
            PlayerKickEvent.Cause.UNKNOWN
        );
        cancelledKick.setCancelled(true);
        collector.onKick(cancelledKick);

        collector.onServerListPing(new ServerListPingEvent(
            "private-client-host",
            address,
            Component.text("private motd"),
            0,
            20
        ));
        AsyncChatEvent chat = chat(player, "private chat content");
        collector.onChat(chat);
        AsyncChatEvent cancelledChat = chat(player, "cancelled private content");
        cancelledChat.setCancelled(true);
        collector.onChat(cancelledChat);

        World world = proxy(World.class, java.util.Map.of("getName", "world"));
        Chunk chunk = proxy(
            Chunk.class,
            java.util.Map.of(
                "getWorld", world,
                "getX", 424_242,
                "getZ", -313_131
            )
        );
        collector.onChunkLoad(new ChunkLoadEvent(chunk, false));
        collector.onChunkLoad(new ChunkLoadEvent(chunk, true));
        collector.onChunkUnload(new ChunkUnloadEvent(chunk));

        assertEquals(2.0, value(registry, "minecraft_login_attempts_total"));
        assertEquals(1.0, value(registry, "minecraft_login_denied_total", "reason", "banned"));
        assertEquals(1.0, value(registry, "minecraft_player_joins_total"));
        assertEquals(1.0, value(registry, "minecraft_player_quits_total"));
        assertEquals(1.0, value(registry, "minecraft_player_kicks_total", "reason", "plugin"));
        assertEquals(1.0, value(
            registry,
            "minecraft_player_kicks_total",
            "reason",
            "connection_lost"
        ));
        assertEquals(1.0, value(registry, "minecraft_server_list_pings_total"));
        assertEquals(1.0, value(registry, "minecraft_chat_messages_total"));
        assertEquals(2.0, value(registry, "minecraft_chunks_loaded_total", "world", "world"));
        assertEquals(1.0, value(registry, "minecraft_chunks_generated_total", "world", "world"));
        assertEquals(1.0, value(registry, "minecraft_chunks_unloaded_total", "world", "world"));
        assertFalse(hasLabelValue(registry, "private"));
        assertEquals(
            1,
            counter(registry, "minecraft_chunks_loaded_total")
                .getDataPoints()
                .getFirst()
                .getLabels()
                .size()
        );

        String exposition = exposition(registry);
        for (String privateValue : java.util.List.of(
            "private-client-host",
            "private-player-name",
            privateUuid.toString(),
            "203.0.113.17",
            "private login message",
            "private denial message",
            "private kick message",
            "private timeout message",
            "private chat content",
            "424242",
            "313131"
        )) {
            assertFalse(exposition.contains(privateValue), privateValue);
        }
    }

    @Test
    void cancellableHandlersRunAtMonitorAndIgnoreCancelledEvents()
        throws Exception {
        EventHandler chat = EventCollector.class
            .getMethod("onChat", AsyncChatEvent.class)
            .getAnnotation(EventHandler.class);
        EventHandler kick = EventCollector.class
            .getMethod("onKick", PlayerKickEvent.class)
            .getAnnotation(EventHandler.class);
        EventHandler login = EventCollector.class
            .getMethod(
                "onLogin",
                Class.forName("org.bukkit.event.player.PlayerLoginEvent")
            )
            .getAnnotation(EventHandler.class);

        assertEquals(EventPriority.MONITOR, chat.priority());
        assertTrue(chat.ignoreCancelled());
        assertEquals(EventPriority.MONITOR, kick.priority());
        assertTrue(kick.ignoreCancelled());
        assertEquals(EventPriority.MONITOR, login.priority());
        assertFalse(login.ignoreCancelled());
        Set<String> possibleLoginSources = Set.of(
            "org.bukkit.event.player.PlayerLoginEvent",
            "org.bukkit.event.player.AsyncPlayerPreLoginEvent",
            "io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent",
            "io.papermc.paper.event.player.PlayerServerFullCheckEvent"
        );
        Set<String> registeredLoginSources = java.util.Arrays.stream(
            EventCollector.class.getMethods()
        )
            .filter(method -> method.getAnnotation(EventHandler.class) != null)
            .flatMap(method -> java.util.Arrays.stream(method.getParameterTypes()))
            .map(Class::getName)
            .filter(possibleLoginSources::contains)
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(
            Set.of("org.bukkit.event.player.PlayerLoginEvent"),
            registeredLoginSources
        );
        assertTrue(java.util.Arrays.stream(EventCollector.class.getMethods())
            .flatMap(method -> java.util.Arrays.stream(method.getParameterTypes()))
            .noneMatch(PlayerCommandPreprocessEvent.class::equals));
    }

    @Test
    void reportsTheOriginalFailureAsCauseAndKeepsProcessingEvents()
        throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        AtomicReference<Throwable> reportedFailure = new AtomicReference<>();
        EventCollector collector = new EventCollector(
            registry,
            true,
            new FakeRegistration(),
            reportedFailure::set
        );
        collector.start();
        RuntimeException originalFailure = new IllegalStateException(
            "private third-party detail"
        );
        World brokenWorld = proxy(
            World.class,
            java.util.Map.of(
                "getName",
                (java.util.function.Function<Object[], Object>) arguments -> {
                    throw originalFailure;
                }
            )
        );
        Chunk brokenChunk = proxy(
            Chunk.class,
            java.util.Map.of("getWorld", brokenWorld)
        );

        assertDoesNotThrow(
            () -> collector.onChunkLoad(new ChunkLoadEvent(brokenChunk, false))
        );
        assertDoesNotThrow(
            () -> collector.onJoin(new PlayerJoinEvent(player(), Component.empty()))
        );

        IllegalStateException wrapper = assertInstanceOf(
            IllegalStateException.class,
            reportedFailure.get()
        );
        assertEquals("An event metric could not be updated", wrapper.getMessage());
        assertSame(originalFailure, wrapper.getCause());
        assertEquals(1.0, value(registry, "minecraft_player_joins_total"));
        assertFalse(exposition(registry).contains("private third-party detail"));
    }

    @Test
    void aFailingFailureListenerCannotEscapeAndStopStillEndsCounting()
        throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        AtomicInteger failureReports = new AtomicInteger();
        EventCollector collector = new EventCollector(
            registry,
            true,
            new FakeRegistration(),
            failure -> {
                failureReports.incrementAndGet();
                throw new IllegalStateException("observer failed");
            }
        );
        collector.start();
        World brokenWorld = proxy(
            World.class,
            java.util.Map.of(
                "getName",
                (java.util.function.Function<Object[], Object>) arguments -> {
                    throw new IllegalArgumentException("counter input failed");
                }
            )
        );
        Chunk brokenChunk = proxy(
            Chunk.class,
            java.util.Map.of("getWorld", brokenWorld)
        );

        assertDoesNotThrow(
            () -> collector.onChunkLoad(new ChunkLoadEvent(brokenChunk, false))
        );
        assertDoesNotThrow(
            () -> collector.onJoin(new PlayerJoinEvent(player(), Component.empty()))
        );
        collector.stop();
        assertDoesNotThrow(
            () -> collector.onJoin(new PlayerJoinEvent(player(), Component.empty()))
        );

        assertEquals(1, failureReports.get());
        assertEquals(1.0, value(registry, "minecraft_player_joins_total"));
    }

    @Test
    void serverLoadInitializesZeroChunkSeriesForLoadedWorlds() throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        EventCollector collector = new EventCollector(
            registry,
            true,
            new FakeRegistration(),
            failure -> {},
            () -> java.util.List.of("world", "world_nether")
        );
        collector.start();

        collector.initializeLoadedWorlds();

        for (String family : java.util.List.of(
            "minecraft_chunks_loaded_total",
            "minecraft_chunks_unloaded_total",
            "minecraft_chunks_generated_total"
        )) {
            CounterSnapshot snapshot = counter(registry, family);
            assertEquals(2, snapshot.getDataPoints().size());
            assertTrue(snapshot.getDataPoints().stream().allMatch(
                point -> point.getValue() == 0.0
                    && point.getLabels().size() == 1
                    && point.getLabels().contains("world")
            ));
        }
    }

    private static AsyncChatEvent chat(Player player, String content) {
        Component message = Component.text(content);
        return new AsyncChatEvent(
            true,
            player,
            Set.of(),
            null,
            message,
            message,
            null
        );
    }

    private static Player player() {
        return proxy(Player.class, java.util.Map.of());
    }

    private static Player player(String name, java.util.UUID uniqueId) {
        return proxy(
            Player.class,
            java.util.Map.of("getName", name, "getUniqueId", uniqueId)
        );
    }

    @SuppressWarnings("deprecation")
    private static PlayerLoginEvent loginEvent(
        Player player,
        InetAddress address,
        String resultName,
        String message
    ) {
        return new PlayerLoginEvent(
            player,
            "private-client-host",
            address,
            PlayerLoginEvent.Result.valueOf(resultName),
            Component.text(message),
            address
        );
    }

    private static boolean hasLabelValue(
        PrometheusRegistry registry,
        String fragment
    ) {
        return registry.scrape().stream()
            .flatMap(metric -> metric.getDataPoints().stream())
            .flatMap(point -> point.getLabels().stream())
            .anyMatch(label -> label.getValue().contains(fragment));
    }

    private static double value(PrometheusRegistry registry, String family) {
        return value(registry, family, null, null);
    }

    private static double value(
        PrometheusRegistry registry,
        String family,
        String labelName,
        String labelValue
    ) {
        CounterSnapshot snapshot = counter(registry, family);
        return snapshot.getDataPoints().stream()
            .filter(point -> labelName == null
                ? point.getLabels().isEmpty()
                : labelValue.equals(point.getLabels().get(labelName)))
            .findFirst()
            .orElseThrow()
            .getValue();
    }

    private static CounterSnapshot counter(
        PrometheusRegistry registry,
        String family
    ) {
        return registry.scrape().stream()
            .filter(metric -> metric.getMetadata()
                .getExpositionBasePrometheusName()
                .equals(family))
            .map(CounterSnapshot.class::cast)
            .findFirst()
            .orElseThrow();
    }

    private static String exposition(PrometheusRegistry registry)
        throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrometheusTextFormatWriter.create().write(
            output,
            registry.scrape(),
            EscapingScheme.DEFAULT
        );
        return output.toString(StandardCharsets.UTF_8);
    }

    private static final class FakeRegistration implements EventRegistration {

        private int registrations;
        private int unregistrations;
        private boolean registered;

        @Override
        public void register(Listener listener) {
            registrations++;
            registered = true;
        }

        @Override
        public void unregister(Listener listener) {
            unregistrations++;
            registered = false;
        }
    }
}
