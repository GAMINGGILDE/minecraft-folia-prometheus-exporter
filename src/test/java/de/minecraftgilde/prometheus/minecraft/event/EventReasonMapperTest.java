package de.minecraftgilde.prometheus.minecraft.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class EventReasonMapperTest {

    @Test
    void mapsEveryStructuredLoginResultWithoutReadingMessages() {
        assertTrue(
            EventReasonMapper.loginDenial(PlayerLoginEvent.Result.ALLOWED).isEmpty()
        );
        assertEquals(
            EventReason.SERVER_FULL,
            EventReasonMapper.loginDenial(PlayerLoginEvent.Result.KICK_FULL)
                .orElseThrow()
        );
        assertEquals(
            EventReason.BANNED,
            EventReasonMapper.loginDenial(PlayerLoginEvent.Result.KICK_BANNED)
                .orElseThrow()
        );
        assertEquals(
            EventReason.WHITELIST,
            EventReasonMapper.loginDenial(PlayerLoginEvent.Result.KICK_WHITELIST)
                .orElseThrow()
        );
        assertEquals(
            EventReason.UNKNOWN,
            EventReasonMapper.loginDenial(PlayerLoginEvent.Result.KICK_OTHER)
                .orElseThrow()
        );
        assertEquals(
            EventReason.UNKNOWN,
            EventReasonMapper.loginDenialName("FUTURE_RESULT").orElseThrow()
        );
        assertEquals(
            EventReason.UNKNOWN,
            EventReasonMapper.loginDenial(null).orElseThrow()
        );
    }

    @Test
    void mapsEveryCurrentStructuredKickCauseConservatively() {
        Map<PlayerKickEvent.Cause, EventReason> expected = Map.ofEntries(
            Map.entry(PlayerKickEvent.Cause.PLUGIN, EventReason.PLUGIN),
            Map.entry(PlayerKickEvent.Cause.WHITELIST, EventReason.WHITELIST),
            Map.entry(PlayerKickEvent.Cause.BANNED, EventReason.BANNED),
            Map.entry(PlayerKickEvent.Cause.IP_BANNED, EventReason.BANNED),
            Map.entry(PlayerKickEvent.Cause.TIMEOUT, EventReason.IDLE),
            Map.entry(PlayerKickEvent.Cause.IDLING, EventReason.IDLE),
            Map.entry(PlayerKickEvent.Cause.KICK_COMMAND, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.FLYING_PLAYER, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.FLYING_VEHICLE, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.INVALID_VEHICLE_MOVEMENT, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.INVALID_PLAYER_MOVEMENT, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.INVALID_ENTITY_ATTACKED, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.INVALID_PAYLOAD, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.INVALID_COOKIE, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.SPAM, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.ILLEGAL_ACTION, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.ILLEGAL_CHARACTERS, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.OUT_OF_ORDER_CHAT, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.UNSIGNED_CHAT, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.CHAT_VALIDATION_FAILED, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.EXPIRED_PROFILE_PUBLIC_KEY, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.INVALID_PUBLIC_KEY_SIGNATURE, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.TOO_MANY_PENDING_CHATS, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.SELF_INTERACTION, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.RESOURCE_PACK_REJECTION, EventReason.MODERATION),
            Map.entry(PlayerKickEvent.Cause.DUPLICATE_LOGIN, EventReason.UNKNOWN),
            Map.entry(PlayerKickEvent.Cause.RESTART_COMMAND, EventReason.UNKNOWN),
            Map.entry(PlayerKickEvent.Cause.UNKNOWN, EventReason.UNKNOWN)
        );

        for (PlayerKickEvent.Cause cause : PlayerKickEvent.Cause.values()) {
            assertEquals(expected.get(cause), EventReasonMapper.kick(cause), cause.name());
        }
        assertEquals(EventReason.UNKNOWN, EventReasonMapper.kickCauseName("FUTURE_CAUSE"));
        assertEquals(EventReason.UNKNOWN, EventReasonMapper.kick(null));
    }

    @Test
    void exposesExactlyTheDocumentedBoundedReasonValues() {
        assertEquals(
            java.util.Set.of(
                "banned",
                "whitelist",
                "server_full",
                "invalid_session",
                "idle",
                "connection_lost",
                "moderation",
                "plugin",
                "unknown"
            ),
            java.util.Arrays.stream(EventReason.values())
                .map(EventReason::metricValue)
                .collect(java.util.stream.Collectors.toSet())
        );
    }
}
