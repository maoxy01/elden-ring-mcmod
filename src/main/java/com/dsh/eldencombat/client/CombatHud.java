package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Final V0.2 art over native HUD layers. Never changes experience, oxygen or combat state. */
@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)
public final class CombatHud {
    private static int rightSlot = 53;
    private CombatHud() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "textures/gui/hud_v02/" + name + ".png");
    }

    private static boolean visible(Minecraft mc) {
        return mc.player != null && mc.level != null && !mc.options.hideGui && !mc.player.isSpectator();
    }

    private static boolean active(Minecraft mc) {
        return visible(mc) && mc.player.isAlive() && mc.gameMode != null
                && (mc.gameMode.canHurtPlayer() || ClientCombat.combatMode());
    }

    private static boolean airVisible(Minecraft mc) {
        return CombatHudLayout.showAir(mc.player.isEyeInFluid(FluidTags.WATER),
                mc.player.getAirSupply(), mc.player.getMaxAirSupply());
    }

    private static CombatHudLayout layout(Minecraft mc, GuiGraphics graphics) {
        return CombatHudLayout.at(graphics.guiWidth(), graphics.guiHeight(),
                mc.gui.leftHeight - CombatHudLayout.FP_RESERVATION, rightSlot);
    }

    @SubscribeEvent
    public static void beginFrame(RenderGuiEvent.Pre event) { rightSlot = 53; }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) return;
        var layer = event.getName();
        if (VanillaGuiLayers.HOTBAR.equals(layer)) {
            mc.gui.leftHeight += CombatHudLayout.STAT_LIFT;
            mc.gui.rightHeight += CombatHudLayout.STAT_LIFT;
            shifted(event, -2);
        } else if (VanillaGuiLayers.EXPERIENCE_BAR.equals(layer) || VanillaGuiLayers.JUMP_METER.equals(layer)) {
            shifted(event, -2);
        } else if (VanillaGuiLayers.EXPERIENCE_LEVEL.equals(layer)) {
            shifted(event, -5);
        } else if (VanillaGuiLayers.ARMOR_LEVEL.equals(layer)) {
            shifted(event, -1);
            if (mc.player.getArmorValue() == 0) mc.gui.leftHeight += 10;
            mc.gui.leftHeight += CombatHudLayout.FP_RESERVATION;
        } else if (VanillaGuiLayers.AIR_LEVEL.equals(layer)) {
            rightSlot = Math.max(53, mc.gui.rightHeight);
            if (!airVisible(mc)) mc.gui.rightHeight += 10;
        } else if (VanillaGuiLayers.SELECTED_ITEM_NAME.equals(layer)) {
            int nativeY = event.getGuiGraphics().guiHeight() - Math.max(59, Math.max(mc.gui.leftHeight, mc.gui.rightHeight));
            if (!mc.gameMode.canHurtPlayer()) nativeY += 14;
            shifted(event, layout(mc, event.getGuiGraphics()).selectedItemY() - nativeY);
        }
    }

    /** Render the original layer exactly once, with an isolated pose. */
    private static void shifted(RenderGuiLayerEvent.Pre event, int y) {
        event.setCanceled(true);
        var graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, y, 0);
            event.getLayer().render(graphics, event.getPartialTick());
            // The manager skips its Post event after cancellation. Forward it once
            // so armor/toughness and other native-layer companions still render.
            NeoForge.EVENT_BUS.post(new RenderGuiLayerEvent.Post(graphics, event.getPartialTick(), event.getName(), event.getLayer()));
        } finally {
            graphics.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;
        GuiGraphics graphics = event.getGuiGraphics();
        drawMapEntry(mc, graphics);
        if (!active(mc)) return;
        var layout = layout(mc, graphics);
        drawBar(graphics, "fp_bar", layout.fpX(), layout.fpY(), ClientSkills.fp(), ClientSkills.maxFp(), 81, 7);
        if (ClientSkills.maxFp() > 0 && ClientSkills.fp() <= ClientSkills.maxFp() * .15f)
            image(graphics, "low_resource", layout.fpX() - 10, layout.fpY(), 7, 7);
        if (!airVisible(mc)) {
            drawBar(graphics, "stamina_bar", layout.staminaX(), layout.staminaY(),
                    ClientCombat.stamina(), ClientCombat.maxStamina(), 81, 7);
            if (ClientCombat.stamina() <= 0)
                image(graphics, "low_resource", layout.staminaX() + 84, layout.staminaY(), 7, 7);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBoss(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;
        event.setCanceled(true);
        GuiGraphics graphics = event.getGuiGraphics();
        int offset = event.getX() < 6 + mapWidth(mc) + 6 ? 38 : 0;
        int y = event.getY() + offset;
        if (y + 6 >= graphics.guiHeight() / 2) return;
        drawBar(graphics, "boss_bar", event.getX(), y, event.getBossEvent().getProgress(), 1, 182, 6);
        graphics.drawCenteredString(mc.font, event.getBossEvent().getName(), graphics.guiWidth() / 2, y - 13, 0xFFFFFF);
        event.setIncrement(19);
    }

    private static void drawBar(GuiGraphics graphics, String bar, int x, int y, float current, float maximum, int width, int height) {
        RenderSystem.enableBlend();
        try {
            image(graphics, bar + "_background", x, y, width, height);
            int filled = CombatHudLayout.filled(current, maximum, width - 2);
            if (filled > 0) graphics.blit(texture(bar + "_fill"), x + 1, y + 1, 0, 0, filled, height - 2, width - 2, height - 2);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    private static void image(GuiGraphics graphics, String name, int x, int y, int width, int height) {
        graphics.blit(texture(name), x, y, 0, 0, width, height, width, height);
    }

    private static KeyMapping mapKey(Minecraft mc) {
        for (var key : mc.options.keyMappings)
            if (key.getName().equals("key.journeymap.map_toggle_alt")) return key;
        return null;
    }

    private static Component mapHint(Minecraft mc) {
        KeyMapping key = mapKey(mc);
        return key == null ? Component.translatable("hud.eldencombat.map_unavailable")
                : key.isUnbound() ? Component.translatable("hud.eldencombat.map_unbound")
                : Component.translatable("hud.eldencombat.map_key", key.getTranslatedKeyMessage());
    }

    private static int mapWidth(Minecraft mc) {
        return Math.max(60, Math.max(mc.font.width(Component.translatable("hud.eldencombat.grace_map")), mc.font.width(mapHint(mc))) + 12);
    }

    private static void drawMapEntry(Minecraft mc, GuiGraphics graphics) {
        graphics.fill(6, 6, 6 + mapWidth(mc), 34, 0x80000000);
        graphics.drawString(mc.font, Component.translatable("hud.eldencombat.grace_map"), 12, 10, 0xFFE2CD91, true);
        graphics.drawString(mc.font, mapHint(mc), 12, 22, mapKey(mc) == null ? 0xFF999999 : 0xFFE8E8E8, true);
    }
}
