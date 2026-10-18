package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.equipment2d.EquipmentRouteRenderer;
import com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot;
import com.dsh.eldencombat.registry.ModItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Explicit, bounded extension of the existing client smoke entry; never included in the JAR. */
public final class SelectedArtAcceptanceHarness {
    private static int phase, age, startup;
    private static CompletableFuture<Void> work;
    private static List<JsonObject> providers;
    private static List<String> selectedPacks;
    private SelectedArtAcceptanceHarness() {}

    private static boolean ready(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (work != null) {
            if (!work.isDone()) return false;
            work.join();
            work = null;
        }
        if (++age > 400) throw new IllegalStateException("ART_ACCEPTANCE timeout phase=" + phase);
        return true;
    }

    private static void next() { phase++; age = 0; }
    private static void server(Minecraft mc, Runnable action) {
        work = CompletableFuture.runAsync(action, mc.getSingleplayerServer()::execute);
        next();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("ART_ACCEPTANCE_FAIL: " + message);
    }
    /** 异步写盘完成的截图名（Screenshot.grab 的回执在写文件之后才回调）。 */
    private static volatile String savedCapture;
    /** 已经发出、但还没等到落盘回执的截图名。 */
    private static String pendingCapture;
    private static void capture(Minecraft mc, String name) {
        savedCapture = null;
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> { EldenCombat.LOGGER.info("ART_CAPTURE: {} {}", name, message.getString()); savedCapture = name; });
    }
    /** 截图确实落盘（不是"发出了截图请求"）。 */
    private static boolean captured(String name) { return name.equals(savedCapture); }
    /**
     * 资源重载期间客户端会盖一层加载 overlay（截到它就成了"Mojang 加载界面"，不是有效画面证据）。
     * 这里要求：没有 overlay、目标界面已显示、并且连续 N 帧都是这样（即真的做过有效渲染帧）。
     */
    private static int cleanFrames;
    private static void countCleanFrames(Minecraft mc) {
        if (mc.getOverlay() == null && mc.screen != null) cleanFrames++; else cleanFrames = 0;
    }
    private static void requireCleanFrames(Minecraft mc, String what) {
        require(mc.getOverlay() == null, what + ": loading overlay still up");
        require(mc.screen != null, what + ": no target screen");
        require(cleanFrames >= 10, what + ": only " + cleanFrames + " overlay-free rendered frames");
    }
    private static List<JsonObject> providers(Minecraft mc) throws Exception {
        if (providers == null) {
            try (var reader = mc.getResourceManager().getResourceOrThrow(
                    ResourceLocation.parse("eldencombat:routes/2d.json")).openAsReader()) {
                providers = new ArrayList<>();
                for (var value : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("equipment")) {
                    var entry = value.getAsJsonObject();
                    if (entry.get("route").getAsString().equals("provider_model")) providers.add(entry);
                }
            }
        }
        return providers;
    }
    private static void prepare(Minecraft mc) {
        var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.clearFire(); p.fallDistance = 0;
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 6; z++)
            p.serverLevel().setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
        p.connection.teleport(.5, 81, .5, 0, 0);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, new ItemStack(ModItems.FLAIL.get()));
        int slot = 1;
        for (var spec : providers) p.getInventory().setItem(slot++,
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec.get("id").getAsString()))));
        p.getInventory().setItem(35, new ItemStack(net.minecraft.world.item.Items.ARROW, 16));
        p.inventoryMenu.broadcastChanges();
    }
    private static void camera(Minecraft mc, float pitch) {
        mc.setCameraEntity(mc.player);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.player.setYRot(0); mc.player.yRotO = 0;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }
    private static boolean drawn(String item, ItemDisplayContext context) {
        return EquipmentRouteRenderer.diagnosticDraws().stream().anyMatch(key -> key.startsWith(item + "|" + context + "|"));
    }
    /** 长弓那一条路由条目（提供方链的实际状态表）。 */
    private static EquipmentRouteSnapshot.Entry longBow() {
        return EquipmentRouteSnapshot.data().get("eldencombat:long_bow");
    }
    /**
     * 还没有被**真的画过**的可达提供方状态。
     *
     * <p>验收要的是"所有可达状态真正绘制"，不是"等够固定 tick 数"：这里直接对着渲染路径记录的
     * 实际绘制键逐一核对，少一个就继续等（直到超时给出明确失败），而不是拿 JSON/图集存在顶上。
     */
    private static List<String> undrawnProviderStates(EquipmentRouteSnapshot.Entry entry) {
        List<String> missing = new ArrayList<>();
        for (var state : entry.providerStates()) {
            if (!state.usable()) continue;
            boolean drawn = EquipmentRouteRenderer.diagnosticDraws().stream()
                    .anyMatch(key -> key.startsWith(entry.id() + "|") && key.endsWith("|" + state.model()));
            if (!drawn) missing.add(state.state());
        }
        return missing;
    }
    private static boolean fallback(String item) {
        return EquipmentRouteRenderer.diagnosticDraws().contains("legacy|" + item + "|GUI");
    }

    public static boolean flailScenes(Minecraft mc) {
        try {
            if (!ready(mc)) return false;
            switch (phase) {
                case 0 -> { providers(mc); server(mc, () -> prepare(mc)); }
                case 1 -> { if (age == 10) { camera(mc, 0); mc.setScreen(new InventoryScreen(mc.player)); }
                    if (age >= 30) {
                        require(drawn("eldencombat:flail", ItemDisplayContext.GUI), "flail inventory did not use admitted model");
                        for (var spec : providers) require(fallback(spec.get("id").getAsString()),
                                "absent provider did not draw legacy: " + spec.get("id"));
                        capture(mc, "art-flail-inventory-no-provider");
                        mc.setScreen(null);
                        server(mc, () -> {
                            var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                            var dropped = new ItemEntity(p.serverLevel(), .5, 81.2, 2.5, new ItemStack(ModItems.FLAIL.get()));
                            dropped.setNoPickUpDelay(); dropped.setPickUpDelay(32767);
                            p.serverLevel().addFreshEntity(dropped);
                        });
                    }
                }
                case 2 -> { camera(mc, 40);
                    if (age >= 35) {
                        require(drawn("eldencombat:flail", ItemDisplayContext.GROUND), "flail dropped entity not drawn");
                        capture(mc, "art-flail-ground");
                        server(mc, () -> {
                            var level = mc.getSingleplayerServer().overworld();
                            level.setBlockAndUpdate(new BlockPos(0, 82, 4), Blocks.STONE.defaultBlockState());
                            var frame = new ItemFrame(level, new BlockPos(0, 82, 3), Direction.NORTH);
                            frame.setItem(new ItemStack(ModItems.FLAIL.get()));
                            level.addFreshEntity(frame);
                        });
                    }
                }
                case 3 -> { camera(mc, 0);
                    if (age >= 35) {
                        require(drawn("eldencombat:flail", ItemDisplayContext.FIXED), "flail actual item frame not drawn");
                        capture(mc, "art-flail-item-frame");
                        EldenCombat.LOGGER.info("ART_FLAIL_COMPLETE: real inventory, ItemEntity and ItemFrame; provider absent fallbacks={}", providers.size());
                        next(); return true;
                    }
                }
                default -> { return true; }
            }
        } catch (Exception failure) { EldenCombat.LOGGER.error("ART_ACCEPTANCE_FAIL", failure); mc.stop(); }
        return false;
    }

    public static void providerTick(Minecraft mc) {
        try {
            if (mc.player == null || mc.getSingleplayerServer() == null) {
                if (++startup > 2400) throw new IllegalStateException("Provider client startup timeout");
                return;
            }
            if (!ready(mc)) return;
            switch (phase) {
                case 0 -> {
                    providers(mc);
                    require(net.neoforged.fml.ModList.get().isLoaded("spartan_weaponry_unofficial"), "Spartan not loaded");
                    for (var spec : providers) {
                        var id = ResourceLocation.parse(spec.get("providerItem").getAsString());
                        require(BuiltInRegistries.ITEM.containsKey(id), "provider registry missing: " + id);
                        EldenCombat.LOGGER.info("ART_PROVIDER_REGISTRY: {} -> {} actual={}", spec.get("id"), id,
                                BuiltInRegistries.ITEM.get(id).getClass().getName());
                    }
                    server(mc, () -> prepare(mc));
                }
                case 1 -> {
                    if (age == 10) { camera(mc, 0); mc.setScreen(new InventoryScreen(mc.player)); }
                    if (age >= 35) {
                        for (var spec : providers) require(drawn(spec.get("id").getAsString(), ItemDisplayContext.GUI),
                                "provider item fell back: " + spec.get("id"));
                        capture(mc, "art-provider-all-inventory"); mc.setScreen(null);
                        server(mc, () -> {
                            var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                            p.getInventory().setItem(0, new ItemStack(ModItems.LONG_BOW.get()));
                            p.inventoryMenu.broadcastChanges();
                        });
                    }
                }
                case 2 -> { camera(mc, 0);
                    if (age == 15) {
                        // 真实输入路径：**按住**"使用"键（等价于真人按住右键拉弓）。单发一次 useItem 只能让
                        // 使用状态活一两个 tick（客户端看到键没按住就发了 release），拉不到 pulling_1/2。
                        mc.options.keyUse.setDown(true);
                        EldenCombat.LOGGER.info("ART_PROVIDER_USE_START: keyUse held down; real input path, not a one-shot useItem");
                    }
                    if (age >= 15 && (age - 15) % 2 == 0) {
                        var held = mc.player.getMainHandItem();
                        EldenCombat.LOGGER.info("ART_PROVIDER_USE: useTicks={} clientUsing={} clientElapsed={} pull={} held={}",
                                age - 15, mc.player.isUsingItem(),
                                com.dsh.eldencombat.client.equipment2d.EquipmentStateResolver.elapsedTicks(mc.player, held),
                                com.dsh.eldencombat.client.equipment2d.EquipmentStateResolver.bowPull(mc.player, held),
                                held.getItem());
                        mc.getSingleplayerServer().execute(() -> {
                            var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                            EldenCombat.LOGGER.info("ART_PROVIDER_SERVER_USE: clientUseTicks={} serverUsing={} serverUseTicks={} arrows={}",
                                    age - 15, p.isUsingItem(), p.getTicksUsingItem(),
                                    p.getInventory().countItem(net.minecraft.world.item.Items.ARROW));
                        });
                    }
                    if (age == 20 || age == 29 || age == 44 || age == 64) capture(mc, "art-provider-bow-use-" + (age - 15));
                    if (age >= 15) {
                        var entry = longBow();
                        var missing = undrawnProviderStates(entry);
                        if (missing.isEmpty()) {
                            var usable = entry.providerStates().stream()
                                    .filter(EquipmentRouteSnapshot.ProviderState::usable).toList();
                            EldenCombat.LOGGER.info("ART_PROVIDER_STATES: all {} usable provider states actually drawn: {}",
                                    usable.size(), usable.stream()
                                            .map(EquipmentRouteSnapshot.ProviderState::model).toList());
                            mc.options.keyUse.setDown(false);
                            mc.gameMode.releaseUsingItem(mc.player);
                            mc.setScreen(new InventoryScreen(mc.player));
                            installBadPack(mc, entry.providerBase());
                            next();
                        } else if (age > 200) {
                            mc.options.keyUse.setDown(false);
                            require(false, "longbow states not actually drawn before timeout: " + missing
                                    + " (using=" + mc.player.isUsingItem()
                                    + " elapsed=" + com.dsh.eldencombat.client.equipment2d.EquipmentStateResolver
                                            .elapsedTicks(mc.player, mc.player.getMainHandItem()) + ")");
                        }
                    }
                }
                case 3 -> {
                    // 坏资源包重载完成后不能只看 tick 数：overlay 撤掉且目标界面真的渲染了才截图。
                    countCleanFrames(mc);
                    if (cleanFrames == 10) {
                        EldenCombat.LOGGER.info("ART_PROVIDER_FALLBACK_FRAME: loading overlay gone, {} overlay-free frames with screen={}", cleanFrames, mc.screen.getClass().getSimpleName());
                    }
                    if (cleanFrames >= 10 && pendingCapture == null) {
                        require(fallback("eldencombat:long_bow"), "broken provider texture did not draw legal fallback");
                        require(!drawn("eldencombat:long_bow", ItemDisplayContext.GUI), "broken provider still taken over");
                        requireCleanFrames(mc, "bad-resource fallback frame");
                        capture(mc, "art-provider-bad-resource-fallback");
                        pendingCapture = "art-provider-bad-resource-fallback";
                    } else if (pendingCapture != null && captured(pendingCapture)) {
                        require(cleanFrames >= 10, "bad-resource screenshot was taken without an overlay-free frame");
                        pendingCapture = null;
                        cleanFrames = 0;
                        mc.getResourcePackRepository().setSelected(selectedPacks);
                        work = mc.reloadResourcePacks(); next();
                    }
                }
                case 4 -> {
                    countCleanFrames(mc);
                    if (cleanFrames >= 10 && pendingCapture == null) {
                        require(drawn("eldencombat:long_bow", ItemDisplayContext.GUI), "restored provider did not resume");
                        requireCleanFrames(mc, "reload recovery frame");
                        capture(mc, "art-provider-restored");
                        pendingCapture = "art-provider-restored";
                    } else if (pendingCapture != null && captured(pendingCapture)) {
                        require(cleanFrames >= 10, "recovery screenshot was taken without an overlay-free frame");
                        pendingCapture = null;
                        EldenCombat.LOGGER.info("ART_PROVIDER_COMPLETE: {} real provider routes, longbow states, broken resource fallback and reload recovery", providers.size());
                        mc.stop(); next();
                    }
                }
            }
        } catch (Exception failure) { EldenCombat.LOGGER.error("ART_ACCEPTANCE_FAIL", failure); mc.stop(); }
    }

    private static void installBadPack(Minecraft mc, ResourceLocation model) throws Exception {
        var dir = mc.gameDirectory.toPath().resolve("resourcepacks"); Files.createDirectories(dir);
        try (var zip = new ZipOutputStream(Files.newOutputStream(dir.resolve("art-acceptance-bad.zip")))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write("{\"pack\":{\"pack_format\":34,\"description\":\"Isolated missing-texture acceptance fixture\"}}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json"));
            zip.write("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"eldencombat:acceptance_missing_texture\"}}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        selectedPacks = List.copyOf(mc.getResourcePackRepository().getSelectedIds());
        mc.getResourcePackRepository().reload();
        var selected = new ArrayList<>(selectedPacks); selected.add("file/art-acceptance-bad.zip");
        mc.getResourcePackRepository().setSelected(selected);
        require(mc.getResourcePackRepository().getSelectedIds().contains("file/art-acceptance-bad.zip"), "bad pack not selected");
        EldenCombat.LOGGER.info("ART_PROVIDER_BAD_RESOURCE: actual resource-pack reload overrides {}", model);
        work = mc.reloadResourcePacks();
    }
}
