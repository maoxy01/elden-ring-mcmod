package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Native stats and boss bars stay in place; combat replaces only the XP display. */
@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)
public final class CombatHud {
    private static final ResourceLocation STAMINA_BACKGROUND = sprite("experience_bar_background");
    private static final ResourceLocation STAMINA_PROGRESS = sprite("experience_bar_progress");
    private static final ResourceLocation JUMP_BACKGROUND = sprite("jump_bar_background");
    private static final ResourceLocation JUMP_PROGRESS = sprite("jump_bar_progress");
    private static final ResourceLocation JUMP_COOLDOWN = sprite("jump_bar_cooldown");

    private CombatHud() {}

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.withDefaultNamespace("hud/" + name);
    }

    private static boolean visible(Minecraft mc) {
        return mc.player != null && mc.level != null && !mc.options.hideGui && !mc.player.isSpectator();
    }

    private static boolean combat(Minecraft mc) {
        return visible(mc) && mc.player.isAlive() && ClientCombat.combatMode();
    }

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!combat(mc)) return;
        var layer = event.getName();
        if (VanillaGuiLayers.EXPERIENCE_BAR.equals(layer)) {
            event.setCanceled(true);
            // Reserve another row for mounted jump charge, leaving the stamina slot fixed.
            if (mc.player.jumpableVehicle() != null) {
                mc.gui.leftHeight += 8;
                mc.gui.rightHeight += 8;
            }
        } else if (VanillaGuiLayers.SELECTED_ITEM_NAME.equals(layer)) {
            // Keep the native tooltip, font and timer; lift its own layer above the FP label.
            event.setCanceled(true);
            var graphics = event.getGuiGraphics();
            var layout = CombatHudLayout.at(graphics.guiWidth(), graphics.guiHeight(), mc.gui.leftHeight, mc.gui.rightHeight);
            int nativeY = graphics.guiHeight() - Math.max(59, Math.max(mc.gui.leftHeight, mc.gui.rightHeight) + 9);
            if (!mc.gameMode.canHurtPlayer()) nativeY += 14;
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, -layout.selectedItemLift(nativeY, mc.font.lineHeight), 0);
                event.getLayer().render(graphics, event.getPartialTick());
            } finally {
                graphics.pose().popPose();
            }
        } else if (VanillaGuiLayers.EXPERIENCE_LEVEL.equals(layer)
                || VanillaGuiLayers.JUMP_METER.equals(layer)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;
        GuiGraphics graphics = event.getGuiGraphics();
        drawMapEntry(mc, graphics);
        if (!combat(mc)) return;

        var layout = CombatHudLayout.at(graphics.guiWidth(), graphics.guiHeight(), mc.gui.leftHeight, mc.gui.rightHeight);
        drawNativeBar(graphics, STAMINA_BACKGROUND, STAMINA_PROGRESS, layout.staminaX(), layout.staminaY(),
                CombatHudLayout.filled(ClientCombat.stamina(), ClientCombat.maxStamina(), CombatHudLayout.STAMINA_WIDTH));
        var vehicle = mc.player.jumpableVehicle();
        if (vehicle != null) {
            drawNativeBar(graphics, JUMP_BACKGROUND, vehicle.getJumpCooldown() > 0 ? JUMP_COOLDOWN : JUMP_PROGRESS,
                    layout.staminaX(), layout.jumpY(), vehicle.getJumpCooldown() > 0 ? CombatHudLayout.STAMINA_WIDTH
                            : CombatHudLayout.filled(mc.player.getJumpRidingScale(), 1, CombatHudLayout.STAMINA_WIDTH));
        }

        int x = layout.fpX(), y = layout.fpY();
        graphics.fill(x - 1, y - 1, x + CombatHudLayout.FP_WIDTH + 1, y + CombatHudLayout.FP_HEIGHT + 1, 0xC0000000);
        graphics.fill(x, y, x + CombatHudLayout.FP_WIDTH, y + CombatHudLayout.FP_HEIGHT, 0xFF17253A);
        int filled = CombatHudLayout.filled(ClientSkills.fp(), ClientSkills.maxFp(), CombatHudLayout.FP_WIDTH);
        if (filled > 0) graphics.fill(x, y, x + filled, y + CombatHudLayout.FP_HEIGHT, 0xFF3F7FD0);
        graphics.drawString(mc.font, Component.translatable("hud.eldencombat.fp", Math.round(ClientSkills.fp()),
                Math.round(ClientSkills.maxFp())), x, y - 10, 0xFF9FC3F0, true);
    }

    private static void drawNativeBar(GuiGraphics graphics, ResourceLocation background, ResourceLocation progress,
                                      int x, int y, int filled) {
        RenderSystem.enableBlend();
        try {
            graphics.blitSprite(background, x, y, CombatHudLayout.STAMINA_WIDTH, CombatHudLayout.STAMINA_HEIGHT);
            if (filled > 0) graphics.blitSprite(progress, CombatHudLayout.STAMINA_WIDTH, CombatHudLayout.STAMINA_HEIGHT,
                    0, 0, x, y, filled, CombatHudLayout.STAMINA_HEIGHT);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    private static void drawMapEntry(Minecraft mc, GuiGraphics graphics) {
        KeyMapping mapKey = null;
        for (var mapping : mc.options.keyMappings) {
            if (mapping.getName().equals("key.journeymap.map_toggle_alt")) { mapKey = mapping; break; }
        }
        Component hint = mapKey == null ? Component.translatable("hud.eldencombat.map_unavailable")
                : mapKey.isUnbound() ? Component.translatable("hud.eldencombat.map_unbound")
                : Component.translatable("hud.eldencombat.map_key", mapKey.getTranslatedKeyMessage());
        Component title = Component.translatable("hud.eldencombat.grace_map");
        int width = Math.max(60, Math.max(mc.font.width(title), mc.font.width(hint)) + 12);
        graphics.fill(6, 6, 6 + width, 34, 0x80000000);
        graphics.drawString(mc.font, title, 12, 10, 0xFFE2CD91, true);
        graphics.drawString(mc.font, hint, 12, 22, mapKey == null ? 0xFF999999 : 0xFFE8E8E8, true);
    }
}
