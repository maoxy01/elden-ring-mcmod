package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.ClientCombat;
import com.dsh.eldencombat.client.PresentationRoutes;
import com.dsh.eldencombat.client.grace.AshUi;
import com.dsh.eldencombat.client.grace.GraceClientActions;
import com.dsh.eldencombat.client.grace.GraceScreen;
import com.dsh.eldencombat.client.grace.GraceScreenState;
import com.dsh.eldencombat.network.GraceMenuActionPayload;
import com.dsh.eldencombat.network.GraceMenuStatePayload;
import com.dsh.eldencombat.registry.ModDataComponents;
import com.dsh.eldencombat.registry.ModItems;
import com.dsh.eldencombat.server.grace.GraceAccess;
import com.dsh.eldencombat.server.grace.GraceMenu;
import com.dsh.eldencombat.server.grace.GraceMenuActions;
import com.dsh.eldencombat.server.skills.AshBindings;
import com.dsh.eldencombat.server.skills.AshUnlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Engine mouse-dispatch -> real menu packet -> integrated server -> real refreshed snapshot.
 *
 * <p>每一次"拒绝"都必须等到**本轮自己的**应答：准备阶段先让服务端清空 notice 并推一次快照，
 * 客户端看到过"空 notice 的新快照"（{@link #roundCleared()}）后才发请求，再等一个
 * revision 前进且命中预期文案的快照（{@link #replied(String)}）。菜单每 10 tick 的周期刷新
 * 会沿用旧 notice，所以只看 notice 文本会把上一轮的话当成这一轮的结论——这里不这么做。
 *
 * <p>{@code Screen.mouseClicked} 是引擎层鼠标事件分发，证明真实控件与网络闭环；
 * 它与"人类物理鼠标输入"是两件事，不能据此声称真人点击已验收。
 */
public final class GraceArtAcceptanceHarness {
    private static final String STAMP = "eldencombat:stamp_upward_cut";
    private static final String THRUST = "eldencombat:heavy_thrust";
    private static final String SPINNING = "eldencombat:spinning_slash";
    /** 服务端对"界面展示的武器和当前主手不是同一把"的统一答复（无效 token 与过期 token 走同一条校验）。 */
    private static final String TOKEN_MISMATCH = "主手武器已变化";
    private static int phase, age;
    private static CompletableFuture<Void> work;
    private static ItemStack before;
    private static String staleToken;
    /** 本轮回执的客户端快照基线（{@link GraceScreenState#revision()}）。 */
    private static long baseline;
    private GraceArtAcceptanceHarness() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("GRACE_ART_FAIL: " + message);
    }
    private static void next() { phase++; age = 0; }
    private static ServerPlayer player(Minecraft mc) { return mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(); }
    private static void server(Minecraft mc, Runnable action) {
        work = CompletableFuture.runAsync(action, mc.getSingleplayerServer()::execute); next();
    }
    private static GraceMenuStatePayload state() { return GraceScreenState.latest(); }
    /** 界面上真实存在的按钮文案，用来在匹配失败时把现场写进日志，而不是只丢一句"找不到"。 */
    private static List<String> labels(Minecraft mc) {
        require(mc.screen instanceof GraceScreen, "no real GraceScreen");
        return mc.screen.children().stream().filter(e -> e instanceof AbstractButton)
                .map(e -> ((AbstractButton)e).getMessage().getString()).toList();
    }
    /** 按**精确**文案取真实控件：文案取自服务端快照算出的那一份，不假设夹具的界面语言。 */
    private static AbstractButton labeled(Minecraft mc, String text) {
        require(mc.screen instanceof GraceScreen, "no real GraceScreen");
        return mc.screen.children().stream().filter(e -> e instanceof AbstractButton).map(e -> (AbstractButton)e)
                .filter(e -> e.getMessage().getString().equals(text))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "GRACE_ART_FAIL: no button '" + text + "' among " + labels(mc)));
    }
    /** 战灰按钮的实际文案（名字 + 状态），与界面用的是同一份快照与同一套文案。 */
    private static String ashLabel(String ashId) {
        var ash = state().ashes().stream().filter(a -> a.id().equals(ashId)).findFirst().orElseThrow(() ->
                new IllegalStateException("GRACE_ART_FAIL: ash " + ashId + " missing from snapshot"));
        return AshUi.label(ash).getString();
    }
    private static void click(Minecraft mc, AbstractButton b) {
        require(b.active, "button inactive: " + b.getMessage().getString());
        require(mc.screen.mouseClicked(b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0, 0), "mouse dispatch not handled");
        EldenCombat.LOGGER.info("GRACE_ART_MOUSE_DISPATCH: {} active={} -> normal screen event/network", b.getMessage().getString(), b.active);
    }
    private static void disabled(Minecraft mc, AbstractButton b) {
        require(!b.active, "must be disabled: " + b.getMessage().getString());
        require(!b.mouseClicked(b.getX() + 3, b.getY() + 3, 0), "disabled widget accepted input");
        EldenCombat.LOGGER.info("GRACE_ART_DISABLED: {}", b.getMessage().getString());
    }
    private static void send(Minecraft mc, int action, String ash, String token) {
        GraceClientActions.send(((GraceScreen)mc.screen).getMenu().containerId, action, null, ash, token);
    }
    /** 发一次真实请求，并把回执基线钉在发送前一刻。 */
    private static void request(Minecraft mc, int action, String ash, String token) {
        baseline = GraceScreenState.revision();
        send(mc, action, ash, token);
        EldenCombat.LOGGER.info("GRACE_ART_REQUEST: action={} ash='{}' token='{}' revisionBaseline={} noticeBeforeSend='{}'",
                action, ash, token, baseline, state().notice());
        next();
    }
    /** 服务端清空 notice 并立刻推一次快照：此后第一个非空 notice 只可能是本轮的应答。 */
    private static void freshRound(Minecraft mc) {
        baseline = GraceScreenState.revision();
        work = CompletableFuture.runAsync(() -> {
            var p = player(mc);
            if (p.containerMenu instanceof GraceMenu menu) menu.notice("");
            GraceMenuActions.refresh(p);
            EldenCombat.LOGGER.info("GRACE_ART_ROUND_START: server notice cleared, round baseline revision={}", baseline);
        }, mc.getSingleplayerServer()::execute);
    }
    /** 客户端已收到本轮清空后的新快照（revision 前进且 notice 为空）。 */
    private static boolean roundCleared() {
        return GraceScreenState.revision() > baseline && state().notice().isEmpty();
    }
    /** 客户端已收到本轮请求的应答：发送基线之后的新快照，且文案命中。 */
    private static boolean replied(String needle) {
        return GraceScreenState.revision() > baseline && state().notice().contains(needle);
    }
    private static void unchanged(Minecraft mc, String reason) {
        require(ItemStack.isSameItemSameComponents(before, player(mc).getMainHandItem()), reason + " changed item data");
        EldenCombat.LOGGER.info("GRACE_ART_REJECTION_PASS: {} full item/components unchanged; notice='{}'",
                reason, state().notice());
    }
    private static void bound(Minecraft mc, String expected) {
        var value = AshBindings.read(player(mc).getMainHandItem());
        require(expected.equals(value == null ? "" : value.toString()), "server binding expected=" + expected + " actual=" + value);
    }
    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                msg -> EldenCombat.LOGGER.info("GRACE_ART_CAPTURE: {} {}", name, msg.getString()));
    }
    private static boolean owned(String id) { return state().ashes().stream().anyMatch(a -> a.id().equals(id) && a.owned()); }
    /**
     * 界面是否已经把最新快照变成控件。
     *
     * <p>{@code GraceScreen.apply()} 只置 {@code dirty}，真正的 {@code rebuildWidgets()} 发生在
     * **下一帧的 {@code render()}**；tick 事件里读控件列表可能正好卡在这中间，看到的还是上一份界面。
     * 夹具必须等这一帧，否则会把"界面还没重建"误判成"按钮不存在"。
     */
    private static boolean buttonsReady(Minecraft mc) {
        if (!(mc.screen instanceof GraceScreen) || state().ashes().isEmpty()) return false;
        List<String> present = labels(mc);
        for (var ash : state().ashes()) if (!present.contains(AshUi.label(ash).getString())) return false;
        return true;
    }
    /** 图标实际走的是 16 还是 32 像素源（{@code key|texture} 轨迹里的纹理名）。 */
    private static boolean iconSource(String id, int pixels) {
        return PresentationRoutes.tracedIcons().stream().anyMatch(key -> key.startsWith(id + "|")
                && (pixels == 32 ? key.endsWith("_32.png") : key.endsWith(".png") && !key.endsWith("_32.png")));
    }

    public static void tick(Minecraft mc) {
        try {
            if (work != null) { if (!work.isDone()) return; work.join(); work = null; }
            if (++age > 400) throw new IllegalStateException("GRACE_ART_FAIL: timeout phase=" + phase
                    + " revision=" + GraceScreenState.revision() + " snapshot=" + state());
            switch (phase) {
                case 0 -> {
                    mc.options.guiScale().set(1); mc.resizeDisplay();
                    mc.getLanguageManager().setSelected("zh_cn"); mc.options.languageCode = "zh_cn";
                    work = mc.reloadResourcePacks(); next();
                }
                case 1 -> server(mc, () -> {
                    var p = player(mc);
                    p.setHealth(p.getMaxHealth()); p.clearFire(); p.fallDistance = 0;
                    p.getData(com.dsh.eldencombat.registry.ModAttachments.COMBAT_STATE).combatMode = false;
                    com.dsh.eldencombat.server.ServerCombat.syncNow(p);
                    for (var entry : com.dsh.eldencombat.server.skills.OrdinaryAshCatalog.entries()) AshUnlocks.revoke(p, entry.id());
                    p.getInventory().clearContent(); p.getInventory().selected = 0;
                    p.getInventory().setItem(0, new ItemStack(ModItems.GREAT_SWORD.get()));
                    var node = GraceAccess.authorizedNodes(p, 256).getFirst();
                    p.connection.teleport(node.pos().getX() + .5, node.pos().getY(), node.pos().getZ() + 1.5, 0, 0);
                    com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(17);
                    com.dsh.eldencombat.server.skills.SkillServerEvents.sync(p);
                    GraceMenuActions.open(p, node.uid(), node.name());
                    before = p.getMainHandItem().copy();
                    EldenCombat.LOGGER.info("GRACE_ART_FIXTURE_SETUP: non-OP={} test weapon supplied, unlocks revoked; later grants are diagnostics, not gameplay rewards", !p.hasPermissions(2));
                });
                case 2 -> {
                    if (mc.screen instanceof GraceScreen && !state().heldToken().isEmpty()) { freshRound(mc); next(); }
                }
                // 1) 未拥有：三种战灰名字/图标可见且按钮禁用，原版控件的禁用鼠标事件不能成功。
                case 3 -> {
                    if (roundCleared() && buttonsReady(mc)) {
                        EldenCombat.LOGGER.info("GRACE_ART_LABELS: lang={} actual buttons={}",
                                mc.getLanguageManager().getSelected(), labels(mc));
                        disabled(mc, labeled(mc, ashLabel(SPINNING)));
                        disabled(mc, labeled(mc, ashLabel(THRUST)));
                        disabled(mc, labeled(mc, ashLabel(STAMP)));
                        for (var id : new String[]{SPINNING, THRUST, STAMP})
                            require(iconSource(id, 16), "menu icon did not go through the accepted 16px icon route: " + id);
                        capture(mc, "grace-art-unowned-scale1");
                        request(mc, GraceMenuActionPayload.BIND, STAMP, state().heldToken());
                    }
                }
                // 2) 故意发未拥有请求：真实网络被拒，整件物品及组件不变。
                case 4 -> {
                    if (replied("尚未获得")) server(mc, () -> {
                        unchanged(mc, "unowned real network request");
                        AshUnlocks.grant(player(mc), STAMP);
                        GraceMenuActions.refresh(player(mc));
                    });
                }
                // 3) 已拥有且兼容 -> 真实鼠标事件 -> BIND -> 服务器组件 -> 新快照 -> 已绑定禁用。
                case 5 -> {
                    if (age > 4 && owned(STAMP) && buttonsReady(mc) && labeled(mc, ashLabel(STAMP)).active) {
                        capture(mc, "grace-art-owned-scale1"); click(mc, labeled(mc, ashLabel(STAMP))); next();
                    }
                }
                case 6 -> {
                    if (age > 4 && STAMP.equals(state().boundAsh()) && buttonsReady(mc)) {
                        disabled(mc, labeled(mc, ashLabel(STAMP))); capture(mc, "grace-art-bound-scale1");
                        server(mc, () -> {
                            bound(mc, STAMP); EldenCombat.LOGGER.info("GRACE_ART_BIND_PASS: real UI/network/server/snapshot");
                            // 目录按组互斥：正常玩法里没有一把武器同时兼容两种战灰。
                            // 为了走通"替换"链路，这里显式种入一个旧的不兼容绑定，并如实标注它不是正常配对。
                            player(mc).getMainHandItem().set(ModDataComponents.ASH_BINDING.get(), ResourceLocation.parse(SPINNING));
                            GraceMenuActions.refresh(player(mc));
                            EldenCombat.LOGGER.info("GRACE_ART_REPLACE_FIXTURE: seed old spinning_slash on great_sword; not a normal compatible pairing");
                        });
                    }
                }
                case 7 -> {
                    if (age > 4 && SPINNING.equals(state().boundAsh()) && buttonsReady(mc)) { click(mc, labeled(mc, ashLabel(STAMP))); next(); }
                }
                case 8 -> {
                    if (age > 4 && STAMP.equals(state().boundAsh())) server(mc, () -> {
                        bound(mc, STAMP); EldenCombat.LOGGER.info("GRACE_ART_REPLACE_PASS: seeded old component replaced via real UI/network/server/snapshot");
                        AshUnlocks.grant(player(mc), THRUST); GraceMenuActions.refresh(player(mc));
                    });
                }
                case 9 -> {
                    if (owned(THRUST)) { mc.options.guiScale().set(2); mc.resizeDisplay(); next(); }
                }
                // 4) 已拥有但不兼容：服务端拒绝，物品与组件不变。
                case 10 -> { if (age >= 5) { freshRound(mc); next(); } }
                case 11 -> {
                    if (roundCleared() && buttonsReady(mc)) {
                        disabled(mc, labeled(mc, ashLabel(THRUST)));
                        for (var id : new String[]{SPINNING, THRUST, STAMP})
                            require(iconSource(id, 32), "GUI scale 2 did not select the 32px icon source: " + id);
                        capture(mc, "grace-art-incompatible-scale2");
                        server(mc, () -> before = player(mc).getMainHandItem().copy());
                    }
                }
                case 12 -> request(mc, GraceMenuActionPayload.REPLACE, THRUST, state().heldToken());
                case 13 -> {
                    if (replied("不兼容") || replied("不支持")) server(mc, () -> unchanged(mc, "incompatible real network request"));
                }
                // 5a) 无效 token：从未存在过的标记，服务端拒绝。
                case 14 -> { freshRound(mc); next(); }
                case 15 -> { if (roundCleared()) request(mc, GraceMenuActionPayload.REMOVE, "", "invalid-held-token"); }
                case 16 -> {
                    if (replied(TOKEN_MISMATCH)) server(mc, () -> {
                        unchanged(mc, "invalid held token");
                        staleToken = GraceMenuActions.heldToken(player(mc));
                        // 同槽换入另一件"相同类型/组件"的物品：真实物品引用变了，旧标记必须作废。
                        player(mc).getInventory().setItem(0, player(mc).getMainHandItem().copy());
                        require(!staleToken.equals(GraceMenuActions.heldToken(player(mc))), "same-slot same-type swap kept old token");
                        before = player(mc).getMainHandItem().copy();
                        GraceMenuActions.refresh(player(mc));
                        EldenCombat.LOGGER.info("GRACE_ART_TOKEN_ROTATION: identical item placed in the same slot got a new menu token");
                    });
                }
                // 5b) 过期 token：换过物品后拿旧标记提交，服务端拒绝且物品数据不变。
                case 17 -> {
                    if (!staleToken.equals(state().heldToken())) { freshRound(mc); next(); }
                }
                case 18 -> { if (roundCleared()) request(mc, GraceMenuActionPayload.REMOVE, "", staleToken); }
                case 19 -> {
                    if (replied(TOKEN_MISMATCH)) server(mc, () -> unchanged(mc, "stale token after identical item swap in the same slot"));
                }
                // 随后 REMOVE 同样闭环。
                case 20 -> { if (age > 5 && buttonsReady(mc)) { click(mc, labeled(mc, "卸除战灰")); next(); } }
                case 21 -> {
                    if (state().boundAsh().isEmpty() && state().notice().contains("已卸除")) server(mc, () -> {
                        bound(mc, ""); EldenCombat.LOGGER.info("GRACE_ART_REMOVE_PASS: real UI/network/server/snapshot");
                        player(mc).closeContainer();
                        player(mc).getData(com.dsh.eldencombat.registry.ModAttachments.COMBAT_STATE).combatMode = true;
                        com.dsh.eldencombat.server.ServerCombat.syncNow(player(mc));
                    });
                }
                // 6) GUI 缩放 1/2、真实服务端 FP 镜像的 HUD 对齐截图。
                case 22 -> {
                    if (age == 5) { mc.options.guiScale().set(1); mc.resizeDisplay(); }
                    if (age >= 25 && ClientCombat.combatMode()) {
                        require(iconSource(STAMP, 16), "GUI scale 1 did not fall back to the 16px icon source");
                        capture(mc, "grace-art-fp-hud-scale1");
                        EldenCombat.LOGGER.info("GRACE_ART_COMPLETE: bind/replace/remove snapshots, unowned/incompatible/invalid/stale token refusal; GUI scales 1 and 2; engine mouse dispatch, not physical mouse acceptance");
                        mc.stop(); next();
                    }
                }
            }
        } catch (Exception failure) { EldenCombat.LOGGER.error("GRACE_ART_FAIL", failure); mc.stop(); }
    }
}
