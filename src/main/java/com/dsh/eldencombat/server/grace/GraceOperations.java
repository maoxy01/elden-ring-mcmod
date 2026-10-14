package com.dsh.eldencombat.server.grace;

import net.minecraft.server.level.ServerPlayer;
import java.util.Objects;
import java.util.UUID;

/** Common-side boundary. Menus and packets must not depend on Waystones classes. */
public final class GraceOperations {
    public enum Status { APPLIED, REJECTED, UNAVAILABLE }
    public record Result(Status status, String reason) {
        public boolean accepted() { return status == Status.APPLIED; }
    }

    /** A backend resolves UUIDs and revalidates live access for every operation. */
    public interface Backend {
        String rejectLocalUse(ServerPlayer player, UUID graceId);
        String rest(ServerPlayer player, UUID graceId);
        /** Acceptance/completion remains asynchronous; never report success on dispatch. */
        void travel(ServerPlayer player, UUID graceId, int sequence);
    }

    private static Backend backend;
    private GraceOperations() {}

    /** Called only from dependency-gated common setup; never from client packets. */
    public static void install(Backend value) {
        if (backend != null) throw new IllegalStateException("Grace backend already installed");
        backend = Objects.requireNonNull(value);
    }

    /** true = 适配器已装载。菜单与包处理器用它决定"赐福入口是否可用"。 */
    public static boolean available() { return backend != null; }

    public static Result localAccess(ServerPlayer player, UUID graceId) {
        requireServerThread(player);
        if (backend == null) return unavailable();
        return result(backend.rejectLocalUse(player, graceId));
    }

    public static Result rest(ServerPlayer player, UUID graceId) {
        Result access = localAccess(player, graceId);
        return access.accepted() ? result(backend.rest(player, graceId)) : access;
    }

    public static void travel(ServerPlayer player, UUID graceId, int sequence) {
        requireServerThread(player);
        if (backend == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("赐福系统尚未启用"), true);
            return;
        }
        backend.travel(player, graceId, sequence);
    }

    private static Result result(String reason) {
        return new Result(reason == null ? Status.APPLIED : Status.REJECTED, reason == null ? "" : reason);
    }

    private static Result unavailable() { return new Result(Status.UNAVAILABLE, "赐福系统尚未启用"); }
    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Grace operations require server thread");
    }
}
