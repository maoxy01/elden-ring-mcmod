package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.entity.MargitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 战斗 HUD：HP / 精力 / FP（蓝条）/ 药瓶 / 当前动作。
 *
 * <p>同样必须挂在游戏事件总线上（{@code RenderGuiEvent.Post} 是 GAME 总线事件）。
 * FP 只读 {@link ClientSkills} 的客户端镜像——那是服务端同步下来的展示值，
 * 客户端不参与任何 FP 结算，也不存在"蓝条自己涨"的充能机制。
 */
@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)
public final class CombatHud {

    private static final int BAR_WIDTH = 150;
    private static final int BAR_HEIGHT = 6;
    /** 精力条与 FP 条的颜色（HP 条沿用旧色） */
    private static final int STAMINA_COLOR = 0xFF7FB069;
    private static final int FP_COLOR = 0xFF3F7FD0;
    /** 失衡条：常态金色，崩解期红/亮黄交替爆闪 */
    private static final int POISE_COLOR = 0xFFC9A227;
    private static final int BROKEN_COLOR = 0xFFFF3B30;
    private static final int BROKEN_FLASH_COLOR = 0xFFFFF1A8;
    /** 失衡条只在 Boss 处于这个距离内时显示 */
    private static final double POISE_BAR_RANGE = 32.0;

    private CombatHud() {
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientCombat.combatMode()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = graphics.guiWidth() / 2;
        int x = centerX - BAR_WIDTH / 2;
        int y = graphics.guiHeight() - 110;
        PresentationRoutes.drawIcon(graphics,"fp",x-18,y+2*(BAR_HEIGHT+3)-5);

        float health = minecraft.player.getHealth() / Math.max(1.0f, minecraft.player.getMaxHealth());
        drawBar(graphics, x, y, health, 0xFFC0392B);

        float stamina = ClientCombat.maxStamina() <= 0 ? 0 : ClientCombat.stamina() / ClientCombat.maxStamina();
        drawBar(graphics, x, y + BAR_HEIGHT + 3, stamina, STAMINA_COLOR);

        // FP（蓝条）：阈值来自服务端同步，单人 -PmanualSkills 下服务端就是"永远满"的状态
        drawBar(graphics, x, y + 2 * (BAR_HEIGHT + 3), ClientSkills.ratio(), FP_COLOR);
        graphics.drawString(minecraft.font,
                Component.literal(String.format("FP %.0f/%.0f", ClientSkills.fp(), ClientSkills.maxFp())),
                x + BAR_WIDTH + 4, y + 2 * (BAR_HEIGHT + 3) - 2, 0xFF9FC3F0, true);

        graphics.drawString(minecraft.font, Component.literal("药瓶 " + ClientCombat.potionCharges()),
                x, y + 3 * (BAR_HEIGHT + 3) + 2, 0xFFE8E8E8, true);

        // 失衡条（§6 削韧 payoff）：只读 Boss 实体同步下来的 DATA_STAGGER / DATA_BROKEN，
        // 客户端不参与失衡结算。崩解窗口内整条爆闪，玩家才知道"现在该全力输出"。
        MargitEntity boss = nearestBoss(minecraft);
        if (boss != null) {
            int poiseY = y + 4 * (BAR_HEIGHT + 3) + 6;
            float ratio = boss.staggerValue() / Math.max(1f, boss.poiseMax());
            boolean broken = boss.isBroken();
            long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
            int color = broken ? ((time / 4) % 2 == 0 ? BROKEN_COLOR : BROKEN_FLASH_COLOR) : POISE_COLOR;
            drawBar(graphics, x, poiseY, broken ? 1f : ratio, color);
            graphics.drawString(minecraft.font,
                    Component.literal(broken ? "崩解 +50%" : String.format("失衡 %.0f%%", Math.max(0f, ratio) * 100f)),
                    x + BAR_WIDTH + 4, poiseY - 2, broken ? BROKEN_COLOR : 0xFFC9A227, true);
        }

        String action = ClientCombat.actionId();
        int sequence = ClientCombat.actionSequence();
        long tick = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) - ClientCombat.actionStartTick();
        String lock = ClientCombat.lockedId() >= 0 ? ("锁定 #" + ClientCombat.lockedId()) : "未锁定";
        graphics.drawString(minecraft.font,
                Component.literal(action + " #" + sequence + " +" + Math.max(0, tick) + "t  " + lock),
                x, y - 11, 0xFFD0D0D0, true);

        // 施放中的战技（阶段 + 段数）：客户端镜像，用于确认"按键真的被服务端受理了"
        if (ClientSkills.casting()) {
            graphics.drawString(minecraft.font,
                    Component.literal("战技 " + ClientSkills.activeSkillId() + " 段" + ClientSkills.stageIndex()),
                    x, y - 22, 0xFFFFD98A, true);
        } else if (!ClientSkills.lastSkillId().isEmpty()) {
            graphics.drawString(minecraft.font,
                    Component.literal("上次战技 " + ClientSkills.lastSkillId()),
                    x, y - 22, 0xFFBFBFBF, true);
        }
    }

    /** 最近的 Boss（在 {@link #POISE_BAR_RANGE} 内）；没有就不画失衡条。 */
    private static MargitEntity nearestBoss(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        MargitEntity nearest = null;
        double best = POISE_BAR_RANGE;
        for (MargitEntity boss : minecraft.level.getEntitiesOfClass(MargitEntity.class,
                minecraft.player.getBoundingBox().inflate(POISE_BAR_RANGE))) {
            double distance = boss.distanceTo(minecraft.player);
            if (distance <= best) {
                best = distance;
                nearest = boss;
            }
        }
        return nearest;
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, float ratio, int color) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xC0000000);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF2A2A2A);
        int filled = (int) (BAR_WIDTH * clamped);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + BAR_HEIGHT, color);
        }
    }
}
