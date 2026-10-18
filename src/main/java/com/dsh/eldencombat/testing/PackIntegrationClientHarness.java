package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.*;
import net.combat_roll.client.Keybindings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=EldenCombat.MODID,value=Dist.CLIENT)
public final class PackIntegrationClientHarness {
    private static int ticks, waiting, readyTicks;
    private static boolean connected, finished, checked, readyRunning;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("eldencombat.packIntegration") || finished)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) {
            if(connected || ++waiting>3600){finished=true; mc.stop();}
            return;
        }
        connected=true;ticks++;
        if(ticks==10)org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());
        if(!checked && mc.screen==null && ClientCombat.combatMode()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getNamespace().equals("simplyswords")) {
            try {
                PackIntegrationHarness.check(java.util.Arrays.stream(mc.options.keyMappings).noneMatch(k->k==EldenCombatClient.ROLL),"old roll key absent from controls");
                PackIntegrationHarness.check(Keybindings.roll.getKey().getValue()==org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT,"Combat Roll bound to Left Alt");
                PackIntegrationHarness.check(ClientCombat.combatMode(),"R combat state synchronized to client");
                var input=new InputEvent.InteractionKeyMappingTriggered(0,mc.options.keyAttack,InteractionHand.MAIN_HAND);
                ClientCombat.onInteractionKey(input);
                PackIntegrationHarness.check(!input.isCanceled(),"foreign weapon left click passes in combat mode");
                var use=new InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.OFF_HAND);
                ClientCombat.onInteractionKey(use);
                PackIntegrationHarness.check(!use.isCanceled(),"foreign weapon offhand shield use passes in combat mode");
                checked=true;mc.player.connection.sendCommand("packcheck ready");
            }catch(Throwable error){EldenCombat.LOGGER.error("PACK_CLIENT_FAIL",error);finished=true;mc.stop();}
        }
        if(checked && !ClientCombat.combatMode())readyRunning=true;
        if(readyRunning)readyTicks++;
        // Wait out vanilla's login spawn protection before the zero-iframe damage check.
        if(readyTicks==65)Keybindings.roll.setDown(true);
        if(readyTicks==70)Keybindings.roll.setDown(false);
        if(readyTicks==100) {
            var directory=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("screenshots");
            try {
                java.nio.file.Files.createDirectories(directory);
                try(var shot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {shot.writeToFile(directory.resolve("pack-hud.png"));}
            } catch(java.io.IOException error) {throw new IllegalStateException("PACK_CLIENT_FAIL: screenshot",error);}
        }
        if(readyTicks==225)EldenCombat.LOGGER.info("PACK_INTEGRATION_CLIENT_COMPLETE: full client loaded and input scenario executed; visual/IME acceptance separate");
    }
}
