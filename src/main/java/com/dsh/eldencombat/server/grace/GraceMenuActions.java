package com.dsh.eldencombat.server.grace;

import com.dsh.eldencombat.network.GraceMenuActionPayload;
import com.dsh.eldencombat.network.GraceMenuStatePayload;
import com.dsh.eldencombat.server.skills.AshBindings;
import com.dsh.eldencombat.server.skills.AshWorkshop;
import com.dsh.eldencombat.server.skills.SkillServerEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 赐福菜单的服务端执行口。四条铁律：
 * <ol>
 *   <li>赐福 UUID 取**服务端菜单实例**里的那份，不信客户端；</li>
 *   <li>每次执行重新校验（节点有效性、发现、权限、距离、维度、战斗状态），不认"打开过菜单"；</li>
 *   <li>{@code containerId} 只用来挡过期/伪造包，换过或关过菜单的旧请求一律丢弃；</li>
 *   <li>改主手武器的操作（绑定/替换/卸除）要核对界面展示的武器仍是当前主手——
 *       不允许把战灰装到玩家"后来拿起的那把"上；不一致就拒绝并刷新，让玩家重试。</li>
 * </ol>
 * 所有失败原因都翻译成玩家能看懂的一句话（§1.6 可读失败反馈），而不是把枚举名甩到界面上。
 */
public final class GraceMenuActions {

    /** 菜单一次最多下发的节点数（地图那份走 {@code settings().snapshotLimit}）。 */
    private static final int NODE_LIMIT = 256;
    /** 打开菜单时的刷新节奏（tick）：换手/换槽后界面不会撒谎太久，也不至于每 tick 发包。 */
    public static final int REFRESH_TICKS = 10;

    private GraceMenuActions() {}

    /** 打开菜单。只在服务端调用（赐福方块交互），客户端没有任何入口能打开别人的赐福菜单。 */
    public static void open(ServerPlayer player, UUID graceId, String graceName) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Grace menu requires server thread");
        String safeName = graceName == null ? "" : graceName;
        player.openMenu(new SimpleMenuProvider(
                        (windowId, inventory, ignored) -> new GraceMenu(windowId, inventory, graceId, safeName),
                        Component.literal("赐福 · " + safeName)),
                buffer -> {
                    buffer.writeUUID(graceId);
                    buffer.writeUtf(safeName, 128);
                });
        if (player.containerMenu instanceof GraceMenu menu) pushState(player, menu, "");
    }

    /** 周期性刷新：只对"菜单正开着"的玩家发，换手/换槽后界面立刻说实话。 */
    public static void refresh(ServerPlayer player) {
        if (player.containerMenu instanceof GraceMenu menu) pushState(player, menu, menu.notice());
    }

    /** 按钮入口。客户端只给操作与选择 ID，其余全部现取。 */
    public static void apply(ServerPlayer player, int containerId, int action, UUID target, String ashId, int sequence,
                            String shownHeldToken) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Grace menu requires server thread");
        if (!(player.containerMenu instanceof GraceMenu menu) || menu.containerId != containerId) return;
        UUID graceId = menu.graceId();
        // 每次执行重新校验：打开过菜单不是授权，战斗/距离/权限在菜单开着的时候都可能变。
        var access = GraceOperations.localAccess(player, graceId);
        if (!access.accepted()) {
            pushState(player, menu, access.reason());
            return;
        }
        // 只对"会写主手物品"的操作核对武器身份；休息/旅行与手上拿什么无关。
        if (writesToHeldItem(action) && !heldToken(player).equals(shownHeldToken == null ? "" : shownHeldToken)) {
            pushState(player, menu, "主手武器已变化（换手或换槽），已刷新，请重试");
            return;
        }
        String notice = switch (action) {
            case GraceMenuActionPayload.REST -> rest(player, graceId);
            case GraceMenuActionPayload.TRAVEL -> travel(player, target, sequence);
            case GraceMenuActionPayload.BIND -> ash(player, graceId, AshWorkshop.Action.BIND, ashId);
            case GraceMenuActionPayload.REPLACE -> ash(player, graceId, AshWorkshop.Action.REPLACE, ashId);
            case GraceMenuActionPayload.REMOVE -> ash(player, graceId, AshWorkshop.Action.REMOVE, null);
            default -> "未知操作";
        };
        pushState(player, menu, notice);
    }

    private static boolean writesToHeldItem(int action) {
        return action == GraceMenuActionPayload.BIND || action == GraceMenuActionPayload.REPLACE
                || action == GraceMenuActionPayload.REMOVE;
    }

    /**
     * 界面展示的武器身份标记：槽位 + 物品 id。
     *
     * <p>刻意**不含**已绑战灰与强化等级：{@code heldToken} 只回答"是不是同一把武器"，
     * 不含"它现在绑了什么"。否则一次成功绑定就会让界面上的标记过期，
     * 玩家连点第二下反而被自己的上一次操作挡住。
     */
    public static String heldToken(ServerPlayer player) {
        int slot = player.getInventory().selected;
        var stack = player.getMainHandItem();
        if (stack.isEmpty()) return slot + ":none";
        return slot + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static String rest(ServerPlayer player, UUID graceId) {
        var result = GraceOperations.rest(player, graceId);
        if (!result.accepted()) return "无法休息：" + result.reason();
        var fp = SkillServerEvents.fp(player);
        return "已休息：FP " + Math.round(fp.fp()) + "/" + Math.round(fp.maxFp());
    }

    private static String travel(ServerPlayer player, UUID target, int sequence) {
        if (target == null) return "旅行目标无效";
        // 目标必须是"已授权披露"的节点之一；真正的授权判定仍在 Waystones 原生管线里再走一遍。
        boolean authorized = GraceAccess.authorizedNodes(player, NODE_LIMIT).stream()
                .anyMatch(node -> node.uid().equals(target));
        if (!authorized) return "该赐福未授权或已失效";
        GraceOperations.travel(player, target, sequence);
        return "已提交旅行请求";
    }

    private static String ash(ServerPlayer player, UUID graceId, AshWorkshop.Action action, String ashId) {
        ResourceLocation id = ashId == null || ashId.isEmpty() ? null : ResourceLocation.tryParse(ashId);
        if (action != AshWorkshop.Action.REMOVE && id == null) return "战灰 ID 无效";
        var result = AshWorkshop.apply(player, graceId, action, id);
        return switch (result.status()) {
            case APPLIED -> action == AshWorkshop.Action.REMOVE ? "已卸除战灰" : "已装配战灰：" + id;
            case GRACE_REJECTED -> "赐福不可用：" + result.reason();
            case OWNERSHIP_UNAVAILABLE -> "战灰解锁存储尚未接入，暂时无法装配";
            case NOT_OWNED -> "尚未获得此战灰";
            case BINDING_REJECTED -> "无法装配：" + bindingReason(result.bindingResult());
        };
    }

    /** 把 {@link AshBindings.Result} 翻译成人话（§1.6）。 */
    private static String bindingReason(AshBindings.Result result) {
        if (result == null) return "未知原因";
        return switch (result) {
            case APPLIED -> "已生效";
            case BUSY -> "战斗动作进行中，暂时不能改装战灰";
            case LOCKED -> "这件武器是追忆/锁定战技，不能覆盖";
            case INVALID_ID -> "战灰 ID 无效";
            case UNKNOWN_SKILL -> "没有这件战灰";
            case UNIMPLEMENTED -> "这件战灰尚未实现，不能装配";
            case UNSUPPORTED_ITEM -> "当前主手武器不支持战灰";
            case INCOMPATIBLE -> "该战灰与当前武器不兼容";
            case ALREADY_BOUND -> "这件武器已经绑定了战灰，请用替换";
            case NOT_BOUND -> "这件武器没有绑定战灰";
        };
    }

    private static void pushState(ServerPlayer player, GraceMenu menu, String notice) {
        menu.notice(notice);
        List<GraceMenuStatePayload.Node> nodes = new ArrayList<>();
        for (var node : GraceAccess.authorizedNodes(player, NODE_LIMIT)) {
            nodes.add(new GraceMenuStatePayload.Node(node.uid(), node.name(), node.dimension(), node.pos(), node.state()));
        }
        var held = player.getMainHandItem();
        ResourceLocation bound = AshBindings.read(held);
        String boundAsh = bound == null ? "" : bound.toString();
        List<GraceMenuStatePayload.Ash> ashes = new ArrayList<>();
        for (var entry : AshWorkshop.catalog(player)) {
            ashes.add(new GraceMenuStatePayload.Ash(entry.id().toString(), entry.implemented(), entry.owned(),
                    entry.id().toString().equals(boundAsh),
                    AshBindings.checkHeld(player, entry.id()) == null));
        }
        var rejection = AshBindings.editability(player);
        var fp = SkillServerEvents.fp(player);
        PacketDistributor.sendToPlayer(player, new GraceMenuStatePayload(menu.graceId(),
                menu.graceName() == null ? "" : menu.graceName(),
                List.copyOf(nodes), List.copyOf(ashes),
                held.isEmpty() ? "（空手）" : held.getHoverName().getString(),
                rejection == null ? "" : bindingReason(rejection), heldToken(player), boundAsh,
                Math.round(fp.fp()), Math.round(fp.maxFp()), notice == null ? "" : notice));
    }
}
