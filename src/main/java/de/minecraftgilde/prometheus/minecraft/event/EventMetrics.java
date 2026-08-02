package de.minecraftgilde.prometheus.minecraft.event;

import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;

/** Thread-safe Phase-5 counters owned by one private Prometheus registry. */
@SuppressWarnings("deprecation")
public final class EventMetrics {

    private final Counter loginAttempts;
    private final Counter loginDenied;
    private final Counter playerJoins;
    private final Counter playerQuits;
    private final Counter playerKicks;
    private final Counter serverListPings;
    private final Counter chatMessages;
    private final Counter chunksLoaded;
    private final Counter chunksUnloaded;
    private final Counter chunksGenerated;

    public EventMetrics(PrometheusRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        List<Counter> counters = new ArrayList<>();
        try {
            loginAttempts = register(
                registry,
                counters,
                "minecraft_login_attempts_total",
                "Total login attempts processed by the server."
            );
            loginDenied = register(
                registry,
                counters,
                "minecraft_login_denied_total",
                "Total denied logins by bounded structured reason.",
                "reason"
            );
            playerJoins = register(
                registry,
                counters,
                "minecraft_player_joins_total",
                "Total completed player joins."
            );
            playerQuits = register(
                registry,
                counters,
                "minecraft_player_quits_total",
                "Total observed player session ends."
            );
            playerKicks = register(
                registry,
                counters,
                "minecraft_player_kicks_total",
                "Total completed player kicks by bounded structured reason.",
                "reason"
            );
            serverListPings = register(
                registry,
                counters,
                "minecraft_server_list_pings_total",
                "Total observed server list pings."
            );
            chatMessages = register(
                registry,
                counters,
                "minecraft_chat_messages_total",
                "Total non-cancelled player chat messages."
            );
            chunksLoaded = register(
                registry,
                counters,
                "minecraft_chunks_loaded_total",
                "Total chunk load events by world.",
                "world"
            );
            chunksUnloaded = register(
                registry,
                counters,
                "minecraft_chunks_unloaded_total",
                "Total chunk unload events by world.",
                "world"
            );
            chunksGenerated = register(
                registry,
                counters,
                "minecraft_chunks_generated_total",
                "Total newly generated chunk load events by world.",
                "world"
            );
        } catch (RuntimeException failure) {
            List<Counter> reverse = new ArrayList<>(counters);
            Collections.reverse(reverse);
            reverse.forEach(registry::unregister);
            throw failure;
        }
        loginDenied.labelValues(EventReason.UNKNOWN.metricValue()).inc(0);
        playerKicks.labelValues(EventReason.UNKNOWN.metricValue()).inc(0);
    }

    public void recordLogin(PlayerLoginEvent.Result result) {
        loginAttempts.inc();
        EventReasonMapper.loginDenial(result).ifPresent(
            reason -> loginDenied.labelValues(reason.metricValue()).inc()
        );
    }

    public void recordJoin() {
        playerJoins.inc();
    }

    public void recordQuit() {
        playerQuits.inc();
    }

    public void recordKick(PlayerKickEvent.Cause cause) {
        playerKicks
            .labelValues(EventReasonMapper.kick(cause).metricValue())
            .inc();
    }

    public void recordServerListPing() {
        serverListPings.inc();
    }

    public void recordChatMessage() {
        chatMessages.inc();
    }

    public void recordChunkLoad(String world, boolean newlyGenerated) {
        String worldLabel = WorldLabel.normalize(world);
        chunksLoaded.labelValues(worldLabel).inc();
        if (newlyGenerated) {
            chunksGenerated.labelValues(worldLabel).inc();
        }
    }

    public void recordChunkUnload(String world) {
        chunksUnloaded.labelValues(WorldLabel.normalize(world)).inc();
    }

    /** Creates bounded zero-valued chunk series for one currently loaded world. */
    public void initializeWorld(String world) {
        String worldLabel = WorldLabel.normalize(world);
        chunksLoaded.labelValues(worldLabel).inc(0);
        chunksUnloaded.labelValues(worldLabel).inc(0);
        chunksGenerated.labelValues(worldLabel).inc(0);
    }

    private static Counter register(
        PrometheusRegistry registry,
        List<Counter> counters,
        String name,
        String help,
        String... labelNames
    ) {
        Counter counter = Counter.builder()
            .name(name)
            .help(help)
            .labelNames(labelNames)
            .register(registry);
        counters.add(counter);
        return counter;
    }
}
