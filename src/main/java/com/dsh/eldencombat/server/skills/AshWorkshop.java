package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.server.grace.GraceOperations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Grace workshop boundary: server-owned main hand, access, ownership, then binding. */
public final class AshWorkshop {
    public enum Action { BIND, REPLACE, REMOVE }
    public enum Status { APPLIED, GRACE_REJECTED, OWNERSHIP_UNAVAILABLE, NOT_OWNED, BINDING_REJECTED }
    public record Result(Status status, String reason, AshBindings.Result bindingResult) {}
    public record Entry(ResourceLocation id, boolean implemented, boolean owned) {}

    /** DSH supplies a persistent per-player unlock store; no client boolean may implement this. */
    @FunctionalInterface
    public interface Ownership { boolean owns(ServerPlayer player, ResourceLocation ashId); }
    private static Ownership ownership;
    private AshWorkshop() {}

    public static void installOwnership(Ownership value) {
        if (ownership != null) throw new IllegalStateException("Ash ownership already installed");
        ownership = Objects.requireNonNull(value);
    }

    public static List<Entry> catalog(ServerPlayer player) {
        requireServerThread(player);
        return AshBindings.definitions().values().stream()
                .map(value -> new Entry(value.id(), value.implemented(),
                        ownership != null && ownership.owns(player, value.id())))
                .toList();
    }

    /** Called by a future server menu handler. UUID/ash ID are selectors, never authorization. */
    public static Result apply(ServerPlayer player, UUID graceId, Action action, ResourceLocation ashId) {
        requireServerThread(player);
        Objects.requireNonNull(action);
        var access = GraceOperations.localAccess(player, graceId);
        if (!access.accepted()) return new Result(Status.GRACE_REJECTED, access.reason(), null);
        if (action != Action.REMOVE) {
            if (ashId == null) return new Result(Status.BINDING_REJECTED, "战灰 ID 无效", AshBindings.Result.INVALID_ID);
            if (ownership == null) return new Result(Status.OWNERSHIP_UNAVAILABLE, "战灰解锁存储尚未接入", null);
            if (!ownership.owns(player, ashId)) return new Result(Status.NOT_OWNED, "尚未获得此战灰", null);
        }
        var result = switch (action) {
            case BIND -> AshBindings.bind(player, ashId.toString());
            case REPLACE -> AshBindings.replace(player, ashId.toString());
            case REMOVE -> AshBindings.unbind(player);
        };
        return new Result(result == AshBindings.Result.APPLIED ? Status.APPLIED : Status.BINDING_REJECTED,
                result.name(), result);
    }

    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Ash workshop requires server thread");
    }
}
