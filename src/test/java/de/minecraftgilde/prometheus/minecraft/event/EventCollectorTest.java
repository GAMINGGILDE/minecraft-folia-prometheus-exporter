package de.minecraftgilde.prometheus.minecraft.event;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.prometheus.metrics.config.EscapingScheme;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import java.net.InetAddress;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
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
        Player player = player();
        InetAddress address = InetAddress.getLoopbackAddress();

        collector.onLogin(new PlayerLoginEvent(
            player,
            "private-client-host",
            address,
            PlayerLoginEvent.Result.ALLOWED,
            Component.text("private login message"),
            address
        ));
        collector.onLogin(new PlayerLoginEvent(
            player,
            "private-client-host",
            address,
            PlayerLoginEvent.Result.KICK_BANNED,
            Component.text("private denial message"),
            address
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
            "private login message",
            "private denial message",
            "private kick message",
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
            .getMethod("onLogin", PlayerLoginEvent.class)
            .getAnnotation(EventHandler.class);

        assertEquals(EventPriority.MONITOR, chat.priority());
        assertTrue(chat.ignoreCancelled());
        assertEquals(EventPriority.MONITOR, kick.priority());
        assertTrue(kick.ignoreCancelled());
        assertEquals(EventPriority.MONITOR, login.priority());
        assertFalse(login.ignoreCancelled());
        assertTrue(java.util.Arrays.stream(EventCollector.class.getMethods())
            .flatMap(method -> java.util.Arrays.stream(method.getParameterTypes()))
            .noneMatch(PlayerCommandPreprocessEvent.class::equals));
    }

    @Test
    void oneMalformedEventDoesNotBreakLaterEventAreas() throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        AtomicInteger failures = new AtomicInteger();
        EventCollector collector = new EventCollector(
            registry,
            true,
            new FakeRegistration(),
            failure -> failures.incrementAndGet()
        );
        collector.start();
        World brokenWorld = proxy(
            World.class,
            java.util.Map.of(
                "getName",
                (java.util.function.Function<Object[], Object>) arguments -> {
                    throw new IllegalStateException("private third-party detail");
                }
            )
        );
        Chunk brokenChunk = proxy(
            Chunk.class,
            java.util.Map.of("getWorld", brokenWorld)
        );

        collector.onChunkLoad(new ChunkLoadEvent(brokenChunk, false));
        collector.onJoin(new PlayerJoinEvent(player(), Component.empty()));

        assertEquals(1, failures.get());
        assertEquals(1.0, value(registry, "minecraft_player_joins_total"));
        assertFalse(exposition(registry).contains("private third-party detail"));
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
