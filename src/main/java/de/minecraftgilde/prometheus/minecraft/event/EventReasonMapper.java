package de.minecraftgilde.prometheus.minecraft.event;

import java.util.Optional;
import org.bukkit.event.player.PlayerKickEvent;

/** Maps only structured public API values to the fixed reason vocabulary. */
public final class EventReasonMapper {

    private EventReasonMapper() {}

    static Optional<EventReason> loginDenialName(String resultName) {
        if ("ALLOWED".equals(resultName)) {
            return Optional.empty();
        }
        return Optional.of(
            switch (resultName == null ? "" : resultName) {
                case "KICK_BANNED" -> EventReason.BANNED;
                case "KICK_WHITELIST" -> EventReason.WHITELIST;
                case "KICK_FULL" -> EventReason.SERVER_FULL;
                case "INVALID_SESSION", "KICK_INVALID_SESSION" ->
                    EventReason.INVALID_SESSION;
                case "KICK_PLUGIN" -> EventReason.PLUGIN;
                default -> EventReason.UNKNOWN;
            }
        );
    }

    public static EventReason kick(PlayerKickEvent.Cause cause) {
        return kickCauseName(cause == null ? null : cause.name());
    }

    static EventReason kickCauseName(String causeName) {
        return switch (causeName == null ? "" : causeName) {
            case "PLUGIN" -> EventReason.PLUGIN;
            case "WHITELIST" -> EventReason.WHITELIST;
            case "BANNED", "IP_BANNED" -> EventReason.BANNED;
            case "IDLING" -> EventReason.IDLE;
            case "TIMEOUT", "CONNECTION_LOST", "NETWORK_ERROR" ->
                EventReason.CONNECTION_LOST;
            case "KICK_COMMAND",
                "FLYING_PLAYER",
                "FLYING_VEHICLE",
                "INVALID_VEHICLE_MOVEMENT",
                "INVALID_PLAYER_MOVEMENT",
                "INVALID_ENTITY_ATTACKED",
                "INVALID_PAYLOAD",
                "INVALID_COOKIE",
                "SPAM",
                "ILLEGAL_ACTION",
                "ILLEGAL_CHARACTERS",
                "OUT_OF_ORDER_CHAT",
                "UNSIGNED_CHAT",
                "CHAT_VALIDATION_FAILED",
                "EXPIRED_PROFILE_PUBLIC_KEY",
                "INVALID_PUBLIC_KEY_SIGNATURE",
                "TOO_MANY_PENDING_CHATS",
                "SELF_INTERACTION",
                "RESOURCE_PACK_REJECTION" -> EventReason.MODERATION;
            default -> EventReason.UNKNOWN;
        };
    }
}
