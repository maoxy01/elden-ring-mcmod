package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.IntentType;
import com.dsh.eldencombat.network.CombatIntentPayload;
import com.dsh.eldencombat.network.CombatStatePayload;
import com.dsh.eldencombat.network.LockOnPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端输入 / 视角 / 状态镜像。
 * 只提交动作意图、连续序号、可选目标与规范化方向；所有判定结果以服务端同步为准。
 *
 * <p>**必须挂在游戏事件总线上**（{@code @EventBusSubscriber} 默认就是 GAME 总线）：
 * 类里的两个 {@code @SubscribeEvent} 是"唯一消费 {@link EldenCombatClient} 按键的地方"，
 * 所以只要没有类级注解（或者别处又手动注册一次），按键就会全哑或者一次输入发两遍。
 * 历史上这里曾写着"不要补注解，会和 Epic Fight 冲突"——Epic Fight 已经移除，那条约束作废。
 */
@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)
public final class ClientCombat {

    // ---- 服务端同步下来的镜像 ----
    private static String actionId = "NONE";
    private static int actionSequence;
    private static long actionStartTick;
    private static final Map<Integer, CombatStatePayload> snapshots = new HashMap<>();
    private static Object snapshotLevel;
    private static float stamina = 100f;
    private static float maxStamina = 100f;
    private static int potionCharges;
    private static int targetId = -1;
    private static boolean combatMode;

    // ---- 本地输入状态 ----
    private static int requestSequence;
    private static int lockedId = -1;
    private static boolean guardHeld;
    private static boolean heavyHeld;
    /** 是否处于"界面打开/失焦/死亡"的中断状态（只发一次取消） */
    private static boolean interrupted;

    private ClientCombat() {
    }

    // ------------------------------------------------------------------
    // 同步
    // ------------------------------------------------------------------

    public static void handleState(CombatStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            checkLevel(minecraft);
            snapshots.put(payload.entityId(), payload);
            if (minecraft.player == null || minecraft.player.getId() != payload.entityId()) return;
            actionId = payload.actionId();
            actionSequence = payload.actionSequence();
            actionStartTick = payload.actionStartTick();
            stamina = payload.stamina();
            maxStamina = payload.maxStamina();
            potionCharges = payload.potionCharges();
            targetId = payload.targetId();
            combatMode = payload.combatMode();
        });
    }

    public static void handleLockOn(LockOnPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> lockedId = payload.accepted() ? payload.targetId() : -1);
    }

    public static String actionId() {
        return actionId;
    }

    public static int actionSequence() {
        return actionSequence;
    }

    public static long actionStartTick() {
        return actionStartTick;
    }

    public static float stamina() {
        return stamina;
    }

    public static float maxStamina() {
        return maxStamina;
    }

    public static int potionCharges() {
        return potionCharges;
    }

    public static boolean combatMode() {
        return combatMode;
    }

    public static int lockedId() {
        return lockedId;
    }

    public static int targetId() {
        return targetId;
    }

    public static CombatStatePayload stateFor(Player player) { return snapshots.get(player.getId()); }

    private static void checkLevel(Minecraft minecraft) {
        if (snapshotLevel != minecraft.level) {
            snapshotLevel = minecraft.level;
            snapshots.clear();
            resetLocal();
            // 断线 / 重连 / 换维度：战技镜像与动作缓存也按"新会话"处理。
            // 不一起清的话，上一局残留的 casting/activeSkillId 会一直挂在 HUD 上，
            // 残留的姿势缓存还会套到复用同一实体 id 的新实体身上。
            ClientSkills.resetLocal();
            SkillPoseAnimator.reset();
        }
    }

    // ------------------------------------------------------------------
    // 每 tick 输入
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        checkLevel(minecraft);
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            clearHeldState(false);
            return;
        }

        // 打开界面、失去窗口焦点、死亡：清除按住状态，不允许一直蓄力或防御
        if (minecraft.screen != null || !minecraft.isWindowActive() || player.isDeadOrDying()) {
            clearHeldState(true);
            return;
        }
        interrupted = false;

        while (EldenCombatClient.TOGGLE_COMBAT.consumeClick()) {
            send(IntentType.COMBAT_TOGGLE, -1, 0, 0);
        }
        while (EldenCombatClient.LOCK_ON.consumeClick()) {
            toggleLockOn(minecraft);
        }
        while (EldenCombatClient.ROLL.consumeClick()) {
            double[] direction = movementDirection(minecraft, player);
            send(IntentType.ROLL, lockedId, direction[0], direction[1]);
        }
        while (EldenCombatClient.PARRY.consumeClick()) {
            send(IntentType.PARRY, -1, 0, 0);
        }
        while (EldenCombatClient.DRINK.consumeClick()) {
            send(IntentType.DRINK, -1, 0, 0);
        }
        while (EldenCombatClient.SKILL.consumeClick()) {
            // 战技：服务端按主手武器决定技能；追击窗口内再按一次进入下一段。
            send(IntentType.SKILL, -1, 0, 0);
        }

        // 蓄力：按住 / 松开
        boolean heavyDown = EldenCombatClient.HEAVY.isDown();
        if (heavyDown && !heavyHeld) {
            heavyHeld = true;
            send(IntentType.HEAVY_START, -1, 0, 0);
        } else if (!heavyDown && heavyHeld) {
            heavyHeld = false;
            send(IntentType.HEAVY_RELEASE, -1, 0, 0);
        }

        // 盾防：按住右键
        boolean guardDown = minecraft.options.keyUse.isDown() && combatMode;
        if (guardDown && !guardHeld) {
            guardHeld = true;
            send(IntentType.GUARD_START, -1, 0, 0);
        } else if (!guardDown && guardHeld) {
            guardHeld = false;
            send(IntentType.GUARD_STOP, -1, 0, 0);
        }

        applyLockOnCamera(minecraft, player);
    }

    /** 左键轻攻击：战斗模式下取消原版挖掘/攻击，改为提交轻攻击意图。 */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !combatMode) {
            return;
        }
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(true);
            send(IntentType.LIGHT, lockedId, 0, 0);
        } else if (event.isUseItem() && minecraft.options.keyUse.isDown()) {
            // 持盾时右键用于防御，不触发物品使用
            if (player.getMainHandItem().is(com.dsh.eldencombat.server.Weapons.SHIELDS)
                    || player.getOffhandItem().is(com.dsh.eldencombat.server.Weapons.SHIELDS)) {
                event.setCanceled(true);
            }
        }
    }

    // ------------------------------------------------------------------
    // 锁定
    // ------------------------------------------------------------------

    private static void toggleLockOn(Minecraft minecraft) {
        if (!combatMode || minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (lockedId >= 0) {
            lockedId = -1;
            send(IntentType.LOCK_ON, -1, 0, 0);
            return;
        }
        int picked = pickTarget(minecraft);
        send(IntentType.LOCK_ON, picked, 0, 0);
    }

    /** 选择视线附近的存活敌对目标，最终由服务端校验范围与视线。 */
    private static int pickTarget(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Vec3 look = player.getViewVector(1.0f).normalize();
        double best = 0.86;
        int picked = -1;
        for (Entity entity : minecraft.level.getEntities(player, player.getBoundingBox().inflate(24.0))) {
            if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive()) {
                continue;
            }
            if (living instanceof Player || living.isSpectator()) {
                continue;
            }
            Vec3 to = living.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
            double dot = to.dot(look);
            if (dot > best) {
                best = dot;
                picked = entity.getId();
            }
        }
        return picked;
    }

    /** 锁定控制客户端视角，但攻击距离与隔墙打击仍由服务端判定。 */
    private static void applyLockOnCamera(Minecraft minecraft, LocalPlayer player) {
        if (lockedId < 0 || minecraft.level == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(lockedId);
        if (target == null || !target.isAlive()) {
            lockedId = -1;
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 point = target.getBoundingBox().getCenter();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static double[] movementDirection(Minecraft minecraft, LocalPlayer player) {
        double forward = (minecraft.options.keyUp.isDown() ? 1 : 0) - (minecraft.options.keyDown.isDown() ? 1 : 0);
        double strafe = (minecraft.options.keyRight.isDown() ? 1 : 0) - (minecraft.options.keyLeft.isDown() ? 1 : 0);
        if (forward == 0 && strafe == 0) {
            return new double[]{0, 0};
        }
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double x = forward * forwardX + strafe * rightX;
        double z = forward * forwardZ + strafe * rightZ;
        double length = Math.sqrt(x * x + z * z);
        return new double[]{x / length, z / length};
    }

    private static void clearHeldState(boolean notifyServer) {
        if (!interrupted) {
            interrupted = true;
            if (notifyServer && combatMode) {
                send(IntentType.CANCEL, -1, 0, 0);
            }
        }
        heavyHeld = false;
        guardHeld = false;
    }

    public static void send(IntentType intent, int targetId, double dirX, double dirZ) {
        requestSequence++;
        PacketDistributor.sendToServer(CombatIntentPayload.of(requestSequence, intent, targetId, dirX, dirZ));
    }

    public static void resetLocal() {
        lockedId = -1;
        heavyHeld = false;
        guardHeld = false;
        interrupted = false;
        combatMode = false;
        actionId = "NONE";
        actionStartTick = 0;
        actionSequence = 0;
        stamina = 100;
        maxStamina = 100;
        targetId = -1;
        potionCharges = 0;
    }
}
