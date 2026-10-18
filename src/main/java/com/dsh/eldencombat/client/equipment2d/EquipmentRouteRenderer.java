package com.dsh.eldencombat.client.equipment2d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single entry point the item renderer uses for the equipment 2D route.
 *
 * <p>Contract §10.1–§10.3. The route takes over at the very start of
 * {@code ItemRenderer.render(ItemStack, ItemDisplayContext, boolean, PoseStack, MultiBufferSource,
 * int, int, BakedModel)} — before {@code pushPose}, before the old display transform and before any
 * vertex is committed — and returns {@code false} from {@link #render} whenever it is not fully
 * responsible, so the legacy path (ordinary model or BEWLR) draws the item exactly once.
 *
 * <p>Shape of the work:
 * <ul>
 *   <li><b>Prepare is read-only.</b> {@link #prepare} reads the real stack, the real holder and the
 *       display context, fixes the snapshot generation, chooses the state, the model, the faces and
 *       the single transform, and returns either {@code null} ("this route does not take over") or a
 *       plan that is ready to draw. It never touches the caller's pose and never commits a vertex, so
 *       "not taking over" can never leave half a picture behind.</li>
 *   <li><b>Exactly one transform owner.</b> The own-sprite route applies the route file's display and
 *       nothing else — no {@code WeaponCorrections}, no 3D bounds fitting, no fixed -45°. The
 *       provider routes apply the provider model's own display as the sole base.</li>
 *   <li><b>Provider routes draw the real model.</b> Passes, render types, foil and the full layer chain
 *       come from the baked model the provider produced; {@code getParticleIcon} is never used as a
 *       stand-in for a multi-layer item model, and a {@code null} own geometry is not an error.</li>
 *   <li><b>Draw failures are failures.</b> Ordinary vertex consumers cannot be un-committed, so an
 *       exception during the draw phase is logged and propagated rather than reported as a clean
 *       fallback.</li>
 * </ul>
 */
public final class EquipmentRouteRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("eldencombat/equipment2d");

    private EquipmentRouteRenderer() {
    }

    private static volatile Map<ModelResourceLocation, BakedModel> baked = Map.of();

    /** Scoped recursion guard: only this route is bypassed, and the previous value is restored. */
    private static final ThreadLocal<Boolean> DRAWING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Per-generation caches, so a hot render path does not re-probe resources or re-key models. */
    private static volatile long cacheGeneration = -1;
    private static final Map<ResourceLocation, BakedModel> MODEL_CACHE = new HashMap<>();
    private static volatile Set<String> availableProviderEntries = Set.of();
    private static final Set<String> diagnosticDraws = new HashSet<>();

    public static Set<String> diagnosticDraws() { return Set.copyOf(diagnosticDraws); }

    /** Explicit isolated acceptance only; recorded after real vertices have been submitted. */
    private static void traceDraw(ResourceLocation id, BakedModel model, ItemStack stack,
                                  ItemDisplayContext context, PoseStack pose, ItemThickness.Depth depth,
                                  float target) {
        if (!Boolean.getBoolean("eldencombat.artAcceptance")) return;
        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "|" + context + "|" + id;
        if (!diagnosticDraws.add(key)) return;
        Set<String> textures = new java.util.TreeSet<>();
        List<String> passes = new ArrayList<>();
        int quads = 0;
        for (BakedModel pass : model.getRenderPasses(stack, isSolidLayer(stack, context))) {
            passes.add(pass.getClass().getName());
            for (int side = 0; side <= Direction.values().length; side++) {
                for (BakedQuad quad : pass.getQuads(null,
                        side == Direction.values().length ? null : Direction.values()[side], RandomSource.create(42))) {
                    quads++;
                    textures.add(quad.getSprite().contents().name().toString());
                }
            }
        }
        float actual = depth.span() * ItemThickness.zScale(pose);
        LOGGER.info("ART_DRAW: {} sourceDepth={} actualDepth={} targetDepth={} quads={} passes={} textures={}",
                key, depth.span(), actual, target, quads, passes, textures);
        if (Math.abs(actual - target) > 1e-5f) throw new IllegalStateException("ART thickness mismatch: " + key);
    }

    public static void traceLegacy(ItemStack stack, ItemDisplayContext context) {
        if (!Boolean.getBoolean("eldencombat.artAcceptance")) return;
        String key = "legacy|" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + context;
        if (diagnosticDraws.add(key)) LOGGER.info("ART_FALLBACK_DRAW: {}", key);
    }

    /** Called from the model event once baking finished; the table is swapped as a whole. */
    public static void installBakedModels(Map<ModelResourceLocation, BakedModel> models) {
        baked = Map.copyOf(models);
        cacheGeneration = -1;
        ItemThickness.clear();
        diagnosticDraws.clear();
    }

    public static Map<ModelResourceLocation, BakedModel> bakedModels() {
        return baked;
    }

    /** True when this registry id has an admitted 2D route. */
    public static boolean handles(ResourceLocation registryId) {
        if (registryId == null) {
            return false;
        }
        return EquipmentRouteSnapshot.data().get(registryId.toString()) != null;
    }

    /**
     * Try to draw an equipment 2D route.
     *
     * @param holder the real render caller from {@link EquipmentRenderContext}, or {@code null} for
     *               GUI, ground, item frames and dropped items
     * @return true only when this route drew the item completely; false lets the legacy path continue
     */
    public static boolean render(ItemStack stack, ItemDisplayContext context, boolean leftHanded,
                                 PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                 LivingEntity holder, int seed, ResourceLocation registryId) {
        if (stack == null || stack.isEmpty() || registryId == null || context == null) {
            return false;
        }
        EquipmentRouteSnapshot.Entry entry = EquipmentRouteSnapshot.data().get(registryId.toString());
        if (entry == null) {
            return false;
        }
        if (Boolean.TRUE.equals(DRAWING.get())) {
            // A nested render inside our own draw: hand it back to the legacy path instead of
            // recursing into the same takeover.
            return false;
        }
        Plan plan;
        try {
            plan = prepare(entry, stack, context, leftHanded, holder, seed);
        } catch (RuntimeException failure) {
            LOGGER.warn("Equipment 2D prepare failed for {} in {}; legacy path kept: {}",
                    registryId, context, failure.toString());
            return false;
        }
        if (plan == null) {
            return false;
        }

        Boolean previous = DRAWING.get();
        DRAWING.set(Boolean.TRUE);
        pose.pushPose();
        try {
            plan.draw(pose, buffers, light, overlay);
        } catch (RuntimeException | Error failure) {
            // Vertices may already be committed; say so instead of pretending the fallback was clean.
            LOGGER.error("Equipment 2D route failed while drawing {} in {}: {}",
                    registryId, context, failure.toString());
            throw failure;
        } finally {
            pose.popPose();
            DRAWING.set(previous);
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Prepare: read-only, no pose mutation, no vertices
    // ---------------------------------------------------------------------------------------------

    /** A complete drawing plan; only produced when every input needed for the draw is present. */
    @FunctionalInterface
    private interface Plan {
        void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay);
    }

    private static Plan prepare(EquipmentRouteSnapshot.Entry entry, ItemStack stack,
                               ItemDisplayContext context, boolean leftHanded,
                               LivingEntity holder, int seed) {
        return switch (entry.route()) {
            case OWN_SPRITE -> prepareOwnSprite(entry, stack, context, leftHanded, holder);
            case VANILLA_MODEL, PROVIDER_MODEL ->
                    prepareProvider(entry, stack, context, leftHanded, holder, seed);
        };
    }

    private static Plan prepareOwnSprite(EquipmentRouteSnapshot.Entry entry, ItemStack stack,
                                        ItemDisplayContext context, boolean leftHanded,
                                        LivingEntity holder) {
        EquipmentGeometry geometry = entry.geometry();
        if (geometry == null) {
            return null;
        }
        Map<String, ResourceLocation> states = entry.ownStates();
        if (context == ItemDisplayContext.GUI) {
            // The inventory slot takes the independent small asset and its own size master: a held
            // canvas must never set the GUI geometry, and switching GUI resolution must not change
            // the held world length.
            ResourceLocation guiModel = entry.ownGui16() != null ? entry.ownGui16() : entry.ownGui32();
            if (guiModel == null) {
                guiModel = states.get(entry.baseState());
            }
            BakedModel model = model(guiModel);
            if (model == null) {
                return null;
            }
            return modelPlan(guiModel, model, EquipmentDisplay.Frame.identity(), leftHanded, stack, context);
        }

        if (entry.doubleFaced()) {
            return prepareShield(entry, stack, context, leftHanded, holder);
        }

        String state = switch (entry.kind()) {
            case BOW -> EquipmentStateResolver.bowState(holder, stack);
            case CROSSBOW -> EquipmentStateResolver.ballistaState(holder, stack);
            case FLASK -> states.containsKey("full") ? "full" : entry.baseState();
            default -> entry.baseState();
        };
        if (state == null) {
            // A reachable state the art does not cover: fall back completely rather than showing a
            // stand-by pose that claims to be the real state.
            return null;
        }
        ResourceLocation modelId = states.get(state);
        if (modelId == null) {
            if (entry.kind().dynamic()) {
                return null;
            }
            modelId = states.get(entry.baseState());
        }
        EquipmentDisplay display = entry.display();
        if (modelId == null || display == null) {
            return null;
        }
        BakedModel model = model(modelId);
        if (model == null) {
            return null;
        }
        return modelPlan(modelId, model, display.resolve(context), leftHanded, stack, context);
    }

    /**
     * Shields: one geometry, two face materials, emitted in a single pass.
     *
     * <p>Both faces must be present and distinct — drawing the front artwork twice would be a wrong
     * material, not a fallback — and the guard pose only re-poses the same two faces. The blocking
     * pose comes from the real using stack, so a player who is eating does not raise somebody else's
     * shield.
     */
    private static Plan prepareShield(EquipmentRouteSnapshot.Entry entry, ItemStack stack,
                                     ItemDisplayContext context, boolean leftHanded,
                                     LivingEntity holder) {
        ResourceLocation frontId = entry.ownStates().get("front");
        ResourceLocation backId = entry.ownStates().get("back");
        if (frontId == null || backId == null) {
            return null;
        }
        BakedModel front = model(frontId);
        BakedModel back = model(backId);
        if (front == null || back == null) {
            return null;
        }
        boolean blocking = EquipmentStateResolver.shieldBlocking(holder, stack);
        EquipmentDisplay display = blocking && entry.blockingDisplay() != null
                ? entry.blockingDisplay() : entry.display();
        if (display == null) {
            return null;
        }
        // The admitted front model contains both materials and real contour walls, not two flat quads.
        return modelPlan(frontId, front, display.resolve(context), leftHanded, stack, context);
    }

    private static Plan prepareProvider(EquipmentRouteSnapshot.Entry entry, ItemStack stack,
                                       ItemDisplayContext context, boolean leftHanded,
                                       LivingEntity holder, int seed) {
        if (entry.providerBase() == null || baked.isEmpty()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return null;
        }
        if (!providerAvailable(entry, minecraft.getResourceManager())) {
            return null;
        }
        List<ProviderModelAdapter.Resolved> resolved = ProviderModelAdapter.resolve(baked,
                minecraft.getResourceManager(), entry,
                predicate -> EquipmentPredicates.value(predicate, stack, holder));
        if (resolved.isEmpty()) {
            return null;
        }
        var selected = resolved.get(resolved.size() - 1);
        if (!EquipmentModelResources.complete(minecraft.getResourceManager(), selected.model())) return null;
        BakedModel model = selected.baked();
        EquipmentDisplay display = entry.display();
        if (display != null) {
            // An explicit replacement display is a stated per-context choice; then the standard camera
            // transform must not be applied on top of it.
            return modelPlan(selected.model(), model, display.resolve(context), leftHanded, stack, context);
        }
        return providerPlan(selected.model(), model, context, leftHanded, stack);
    }

    /** Draw a baked model in its own [0,1] model space with one explicit display transform. */
    private static Plan modelPlan(ResourceLocation id, BakedModel model, EquipmentDisplay.Frame frame, boolean leftHanded,
                                  ItemStack stack, ItemDisplayContext context) {
        ItemThickness.Depth depth = ItemThickness.depth(model, stack, isSolidLayer(stack, context));
        if (depth == null) return null;
        return (pose, buffers, light, overlay) -> {
            float thickness = ItemThickness.target(pose, context);
            frame.apply(leftHanded, pose);
            pose.translate(-0.5f, -0.5f, -0.5f);
            ItemThickness.normalize(pose, depth, thickness);
            emitModel(model, stack, context, pose, buffers, light, overlay);
            traceDraw(id, model, stack, context, pose, depth, thickness);
        };
    }

    /**
     * Provider routes: the provider model's own {@code display} is the sole base transform.
     *
     * <p>This is the same transform the standard path would have applied, applied once in a place
     * where it cannot be followed by a second one.
     */
    private static Plan providerPlan(ResourceLocation id, BakedModel model, ItemDisplayContext context, boolean leftHanded,
                                     ItemStack stack) {
        ItemThickness.Depth depth = ItemThickness.depth(model, stack, isSolidLayer(stack, context));
        if (depth == null) return null;
        return (pose, buffers, light, overlay) -> {
            float thickness = ItemThickness.target(pose, context);
            model.getTransforms().getTransform(context).apply(leftHanded, pose);
            pose.translate(-0.5f, -0.5f, -0.5f);
            ItemThickness.normalize(pose, depth, thickness);
            emitModel(model, stack, context, pose, buffers, light, overlay);
            traceDraw(id, model, stack, context, pose, depth, thickness);
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * The vanilla quad emission, without the camera transform (which this route already applied).
     *
     * <p>Render passes, render types and the foil buffer are the standard ones, so a provider model's
     * extra layers and its loader-produced geometry are preserved instead of being flattened to one
     * particle quad. The compass/clock animated-texture special case is deliberately absent: none of
     * this route's items is one.
     */
    private static void emitModel(BakedModel model, ItemStack stack, ItemDisplayContext context,
                                  PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean solid = isSolidLayer(stack, context);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (BakedModel pass : model.getRenderPasses(stack, solid)) {
            for (RenderType renderType : pass.getRenderTypes(stack, solid)) {
                VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffers, renderType, true,
                        stack.hasFoil());
                itemRenderer.renderModelLists(pass, stack, light, overlay, pose, consumer);
            }
        }
    }

    /** Mirrors the vanilla solid-layer decision so the same render type and foil buffer are chosen. */
    private static boolean isSolidLayer(ItemStack stack, ItemDisplayContext context) {
        if (context == ItemDisplayContext.GUI || context.firstPerson()) {
            return true;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            return !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Per-generation caches
    // ---------------------------------------------------------------------------------------------

    /**
     * Both keys a model can be baked under, resolved once per generation.
     *
     * <p>Only the two real baked-table keys are consulted. There is deliberately no fallback to the
     * item model shaper: an entry's model ID is a <em>model</em> ID, and asking the item registry for
     * it would silently hand back the missing model.
     *
     * <p>The render thread is the only caller, so the cache is a plain map; it is dropped whenever a
     * new snapshot generation lands, so no reference to a previous atlas survives a reload.
     */
    private static BakedModel model(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        long generation = EquipmentRouteSnapshot.generationId();
        if (generation != cacheGeneration) {
            MODEL_CACHE.clear();
            cacheGeneration = generation;
        }
        if (MODEL_CACHE.containsKey(id)) return MODEL_CACHE.get(id);
        BakedModel found = baked.get(EquipmentRouteSnapshot.inventoryModel(id));
        if (found == null) {
            found = baked.get(EquipmentRouteSnapshot.standaloneModel(id));
        }
        if (found != null && (!EquipmentModelResources.complete(
                Minecraft.getInstance().getResourceManager(), id) || hasMissingTexture(found))) {
            found = null;
        }
        MODEL_CACHE.put(id, found);
        return found;
    }

    /** A failed bake may return a non-null placeholder; a failed PNG may only show up in the atlas. */
    private static boolean hasMissingTexture(BakedModel model) {
        RandomSource random = RandomSource.create(42L);
        List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
        faces.add(null);
        for (Direction face : faces) {
            for (BakedQuad quad : model.getQuads(null, face, random)) {
                TextureAtlasSprite sprite = quad.getSprite();
                if (sprite == null || MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Capability probe for the provider chain, evaluated once per snapshot generation instead of once
     * per frame, and logged per equipment with a reason rather than per frame.
     */
    private static boolean providerAvailable(EquipmentRouteSnapshot.Entry entry,
                                             net.minecraft.server.packs.resources.ResourceManager manager) {
        long generation = EquipmentRouteSnapshot.generationId();
        if (generation != availabilityGeneration) {
            Set<String> available = new HashSet<>();
            List<EquipmentRouteSnapshot.Entry> providers = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (EquipmentRouteSnapshot.Entry candidate : EquipmentRouteSnapshot.data().entries().values()) {
                if (candidate.route() == EquipmentRouteSnapshot.Route.OWN_SPRITE) {
                    continue;
                }
                providers.add(candidate);
                boolean complete = true;
                for (ResourceLocation required : candidate.requiredPresence()) {
                    if (!EquipmentRouteSnapshot.present(manager, required)) {
                        complete = false;
                        missing.add(candidate.id() + " -> " + required);
                    }
                }
                if (complete) {
                    available.add(candidate.id());
                }
            }
            if (!missing.isEmpty()) {
                LOGGER.warn("Equipment 2D provider chain unavailable, legacy model kept: {}",
                        String.join(", ", missing));
            }
            availableProviderEntries = Set.copyOf(available);
            availabilityGeneration = generation;
        }
        return availableProviderEntries.contains(entry.id());
    }

    private static volatile long availabilityGeneration = -1;
}
