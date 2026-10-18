package com.dsh.eldencombat.client.equipment2d;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The equipment half of {@code assets/eldencombat/routes/2d.json}, parsed once per resource reload
 * into an immutable route table.
 *
 * <p>Contract §2 (the real boundary of the current code) and §3/§4. Design rules this class enforces:
 *
 * <ul>
 *   <li><b>Read/write consistency.</b> The route file's {@code held} and {@code gui} are JSON
 *       <em>objects</em> ({@code state -> relative path}), which is what the generator writes and what
 *       {@code gradle/equipment-2d.gradle} consumes. The parser reads that shape (the legacy array
 *       shape is still accepted so an older file cannot brick the client).</li>
 *   <li><b>{@code display} is really parsed.</b> Previously the local {@code Display} was always
 *       {@code null}, {@code displayOf} was never called, and every entry was rejected — a validation
 *       dead end. {@code display} is now read, validated and handed to the render path.</li>
 *   <li><b>Route-appropriate requirements.</b> {@code own_sprite} needs its own geometry and an
 *       explicit display, because it is the only owner of its transform. {@code vanilla_model} and
 *       {@code provider_model} inherit the provider's own display from the real baked model, so a
 *       {@code null} display there is a route difference, not a missing input.</li>
 *   <li><b>Only technical failures block.</b> {@code blockers} are hard; anything that is a receipt,
 *       an unreachable provider state or a pending visual acceptance lives in {@code notes} or
 *       {@code acceptance} and must not stop a structurally complete candidate from rendering.</li>
 *   <li>The parsed result is built completely before {@link #apply} installs it, so nothing ever
 *       renders against a half-built table, and rejections are grouped per equipment with a reason
 *       rather than logged per frame.</li>
 * </ul>
 *
 * <p>This listener only reads. It never writes item components and never touches damage, use time,
 * ammo or animation authority.
 */
public final class EquipmentRouteSnapshot extends SimplePreparableReloadListener<EquipmentRouteSnapshot.Data> {

    public static final ResourceLocation ROUTE_FILE =
            ResourceLocation.fromNamespaceAndPath("eldencombat", "routes/2d.json");

    private static final Logger LOGGER = LoggerFactory.getLogger("eldencombat/equipment2d");

    /** How a route produces its pixels. */
    public enum Route { OWN_SPRITE, VANILLA_MODEL, PROVIDER_MODEL }

    /** Play family, which is what decides whether a state chain is dynamic or double faced. */
    public enum Kind {
        MELEE, BOW, CROSSBOW, SHIELD, FLASK;

        public boolean doubleFaced() {
            return this == SHIELD;
        }

        public boolean dynamic() {
            return this == BOW || this == CROSSBOW;
        }

        static Kind of(String value) {
            return switch (value == null ? "" : value) {
                case "bow" -> BOW;
                case "crossbow" -> CROSSBOW;
                case "shield" -> SHIELD;
                case "flask" -> FLASK;
                default -> MELEE;
            };
        }
    }

    /** One provider state model together with the predicates that select it. */
    public record ProviderState(String state, ResourceLocation model,
                                Map<ResourceLocation, Float> predicates, boolean usable) {
    }

    /** Acceptance is a receipt, never a technical precondition (§11.4). */
    public record Acceptance(String state, boolean humanAccepted, String displayStatus) {
        public static Acceptance unknown() {
            return new Acceptance("unknown", false, "unknown");
        }

        public boolean accepted() {
            return humanAccepted || "accepted".equals(state);
        }
    }

    /**
     * An admitted equipment entry.
     *
     * @param ownHeldModel      base held model for own sprites; {@code null} for provider routes
     * @param ownGui16          independent 16-pixel GUI model (the default when present)
     * @param ownGui32          independent 32-pixel GUI model (only when explicitly selected)
     * @param ownStates         explicit model per own state, shield {@code front}/{@code back} included
     * @param providerBase      provider stand-by model; {@code null} for own sprites
     * @param providerStates    provider state chain in override order
     * @param requiredPresence  resources that must all resolve before the provider chain is used
     */
    public record Entry(String id, String assetId, Route route, Kind kind,
                        EquipmentGeometry geometry,
                        EquipmentDisplay display, EquipmentDisplay blockingDisplay,
                        ResourceLocation ownHeldModel, ResourceLocation ownGui16, ResourceLocation ownGui32,
                        Map<String, ResourceLocation> ownStates,
                        String providerNamespace, ResourceLocation providerBase,
                        List<ProviderState> providerStates, Set<ResourceLocation> requiredPresence,
                        Acceptance acceptance, List<String> notes, List<String> blockers) {

        /** The own state to draw when nothing more specific applies. */
        public String baseState() {
            if (ownStates.containsKey("standby")) {
                return "standby";
            }
            if (ownStates.containsKey("front")) {
                return "front";
            }
            return ownStates.isEmpty() ? null : ownStates.keySet().iterator().next();
        }

        public boolean doubleFaced() {
            return kind.doubleFaced() && ownStates.containsKey("front") && ownStates.containsKey("back");
        }
    }

    /** The whole table plus the per-equipment reasons a non-admitted entry stayed out of the render path. */
    public record Data(Map<String, Entry> entries, Map<String, List<String>> rejected,
                       Map<String, List<String>> notes, int admitted, int providerEntries) {

        public Entry get(String id) {
            return entries.get(id);
        }

        public static Data empty() {
            return new Data(Map.of(), Map.of(), Map.of(), 0, 0);
        }
    }

    private static volatile Data current = Data.empty();
    private static volatile long generation;

    private EquipmentRouteSnapshot() {
    }

    public static EquipmentRouteSnapshot create() {
        return new EquipmentRouteSnapshot();
    }

    /** Immutable view for the render path. */
    public static Data data() {
        return current;
    }

    public static long generationId() {
        return generation;
    }

    /**
     * Alias registration is deferred: the predicate values are ours, but the item set that needs them
     * only exists after the route file was parsed. The registrar is installed during client setup and
     * invoked once the table lands.
     */
    @FunctionalInterface
    public interface AliasRegistrar {
        void register(Set<String> aliasPredicates, Set<ResourceLocation> items);
    }

    private static volatile AliasRegistrar aliasRegistrar;
    private static volatile boolean aliasesInstalled;

    public static void installAliasRegistrar(AliasRegistrar registrar) {
        aliasRegistrar = registrar;
        if (!current.entries().isEmpty() && !aliasesInstalled) {
            registerAliases();
        }
    }

    private static void registerAliases() {
        AliasRegistrar registrar = aliasRegistrar;
        if (registrar == null || aliasesInstalled) {
            return;
        }
        Set<String> predicates = new LinkedHashSet<>();
        Set<ResourceLocation> items = new LinkedHashSet<>();
        for (Entry entry : current.entries().values()) {
            if (entry.route() == Route.OWN_SPRITE) {
                continue;
            }
            items.add(ResourceLocation.parse(entry.id()));
            for (ProviderState state : entry.providerStates()) {
                for (ResourceLocation predicate : state.predicates().keySet()) {
                    // Only own-namespace aliases are ours to publish; minecraft:* is handled natively.
                    if (!"minecraft".equals(predicate.getNamespace())) {
                        predicates.add(predicate.toString());
                    }
                }
            }
        }
        registrar.register(Set.copyOf(predicates), Set.copyOf(items));
        aliasesInstalled = true;
    }

    /** Every own model ID the generator produces for admitted entries; safe to register up front. */
    public static Set<String> ownModelIds() {
        return ownModelIds(current.entries().values());
    }

    /**
     * The same set, read straight from the route file through a resource manager.
     *
     * <p>{@code ModelEvent.RegisterAdditional} fires during baking and must not depend on whether the
     * reload listener has already run, so the generator's model IDs are derived from the resource the
     * manager already holds instead of from the parsed table.
     */
    public static Set<String> ownModelIds(ResourceManager manager) {
        try (Reader reader = manager.getResourceOrThrow(ROUTE_FILE).openAsReader()) {
            JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray equipment = document.getAsJsonArray("equipment");
            if (equipment == null) {
                return Set.of();
            }
            List<Entry> parsed = new ArrayList<>();
            for (JsonElement element : equipment) {
                Entry entry = parseOwnSpriteModels(element.getAsJsonObject());
                if (entry != null) {
                    parsed.add(entry);
                }
            }
            return ownModelIds(parsed);
        } catch (Exception failure) {
            LOGGER.warn("Equipment 2D model IDs could not be read from {}: {}", ROUTE_FILE, failure.toString());
            return Set.of();
        }
    }

    /** Register resource model IDs explicitly: override dependencies are not top-level baked keys. */
    public static Set<String> additionalModelIds(ResourceManager manager) {
        Set<String> ids = new LinkedHashSet<>(ownModelIds(manager));
        try (Reader reader = manager.getResourceOrThrow(ROUTE_FILE).openAsReader()) {
            Data data = parse(JsonParser.parseReader(reader).getAsJsonObject());
            for (Entry entry : data.entries().values()) {
                if (entry.route() == Route.OWN_SPRITE
                        || !entry.requiredPresence().stream().allMatch(id -> present(manager, id))) {
                    continue;
                }
                ids.add(entry.providerBase().toString());
                for (ProviderState state : entry.providerStates()) {
                    if (state.usable()) ids.add(state.model().toString());
                }
            }
        } catch (Exception failure) {
            LOGGER.warn("Equipment 2D provider model registration unavailable: {}", failure.toString());
        }
        return Set.copyOf(ids);
    }

    private static Set<String> ownModelIds(Iterable<Entry> entries) {
        Set<String> ids = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (entry.route() != Route.OWN_SPRITE) {
                continue;
            }
            for (ResourceLocation model : entry.ownStates().values()) {
                if ("eldencombat".equals(model.getNamespace())) {
                    ids.add(model.toString());
                }
            }
            if (entry.ownGui16() != null) {
                ids.add(entry.ownGui16().toString());
            }
            if (entry.ownGui32() != null) {
                ids.add(entry.ownGui32().toString());
            }
        }
        return ids;
    }

    /**
     * One de-duplicated capability report per resource reload, grouped per equipment: a missing
     * optional provider resource is reported by item and reason instead of once per frame.
     */
    public static void reportCapabilities() {
        Data data = current;
        data.rejected().forEach((id, reasons) ->
                reasons.stream().distinct().forEach(reason ->
                        LOGGER.warn("Equipment 2D route not active for {}: {}", id, reason)));
        data.notes().forEach((id, reasons) -> {
            if (LOGGER.isDebugEnabled()) {
                reasons.stream().distinct().forEach(note ->
                        LOGGER.debug("Equipment 2D note for {}: {}", id, note));
            }
        });
        long awaitingAcceptance = data.entries().values().stream()
                .filter(entry -> !entry.acceptance().accepted())
                .count();
        LOGGER.info("Equipment 2D routes admitted: {} live ({} provider chain(s)), {} not active, "
                        + "{} admitted entry/entries still awaiting visual acceptance",
                data.admitted(), data.providerEntries(), data.rejected().size(), awaitingAcceptance);
    }

    // ---------------------------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------------------------

    private static ResourceLocation id(String value) {
        if (value == null || !value.contains(":")) {
            throw new IllegalArgumentException("Resource namespace missing: " + value);
        }
        return ResourceLocation.parse(value);
    }

    /**
     * A predicate name in a model file may be written bare ({@code "pulling"}, {@code "pull"}): that
     * means the default namespace, which is how {@code ItemOverrides.Deserializer} resolves it with
     * {@code ResourceLocation.tryParse}. Being stricter here silently dropped every vanilla bow and
     * crossbow state — {@code short_bow} and {@code crossbow} were rejected with
     * "Resource namespace missing: pulling", which is a parser bug, not bad data.
     */
    private static ResourceLocation predicateId(String value) {
        return value.indexOf(':') < 0 ? ResourceLocation.withDefaultNamespace(value)
                : ResourceLocation.parse(value);
    }

    /** The namespace a predicate name belongs to, defaulting the way vanilla model JSON does. */
    private static String predicateNamespace(String value) {
        int separator = value.indexOf(':');
        return separator < 0 ? "minecraft" : value.substring(0, separator);
    }

    private static float[] triple(JsonObject object, String key, float[] fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(key + " must hold three numbers");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            throw new IllegalArgumentException(key + " must hold three numbers");
        }
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    /** JSON display translation is 1/16 block; the render path works in blocks everywhere. */
    private static EquipmentDisplay.Frame frameOf(JsonObject object) {
        float[] rotation = triple(object, "rotation", new float[] {0f, 0f, 0f});
        float[] translation = triple(object, "translation", new float[] {0f, 0f, 0f});
        float[] scale = triple(object, "scale", new float[] {1f, 1f, 1f});
        return new EquipmentDisplay.Frame(rotation,
                new float[] {translation[0] / 16f, translation[1] / 16f, translation[2] / 16f},
                scale);
    }

    /** {@code null} when absent or explicit JSON null, which is legal for provider-owned displays. */
    private static EquipmentDisplay displayOf(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        JsonObject display = element.getAsJsonObject();
        Map<String, EquipmentDisplay.Frame> contexts = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> value : display.entrySet()) {
            if (!value.getValue().isJsonObject()) {
                continue;
            }
            contexts.put(value.getKey(), frameOf(value.getValue().getAsJsonObject()));
        }
        if (contexts.isEmpty()) {
            return null;
        }
        JsonElement inheritance = display.get("inheritance");
        return new EquipmentDisplay(contexts, inheritance == null ? null : inheritance.getAsString());
    }

    private static EquipmentGeometry geometryOf(JsonObject entry) {
        JsonElement element = entry.get("geometry");
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing geometry");
        }
        JsonObject geometry = element.getAsJsonObject();
        JsonArray canvas = geometry.getAsJsonArray("canvas");
        JsonArray anchor = geometry.getAsJsonArray("anchorPixels");
        if (canvas == null || canvas.size() != 2 || anchor == null || anchor.size() != 2) {
            throw new IllegalArgumentException("geometry must carry canvas and anchorPixels pairs");
        }
        return EquipmentGeometry.of(canvas.get(0).getAsFloat(), canvas.get(1).getAsFloat(),
                anchor.get(0).getAsFloat(), anchor.get(1).getAsFloat(),
                geometry.get("worldLengthBlocks").getAsFloat(),
                geometry.get("sourceSpanPixels").getAsFloat());
    }

    private static ResourceLocation ownModel(String assetId, String state) {
        return ResourceLocation.fromNamespaceAndPath("eldencombat", "item/2d/" + assetId + "_" + state);
    }

    /** The inventory-variant key the baked model table uses for an item model ID. */
    public static ModelResourceLocation inventoryModel(ResourceLocation model) {
        // ModelBakery adds item/ only when reading JSON, not to the registered inventory key.
        String path = model.getPath();
        return ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(model.getNamespace(),
                path.startsWith("item/") ? path.substring(5) : path));
    }

    /** The standalone key, used when a model is only registered as a plain resource. */
    public static ModelResourceLocation standaloneModel(ResourceLocation model) {
        return ModelResourceLocation.standalone(model);
    }

    /** Own sprites always resolve to the generated sync-safe variants inside our own namespace. */
    private static ResourceLocation ownOrMinecraftModel(String assetId, String state) {
        if ("minecraft".equals(state)) {
            return ResourceLocation.withDefaultNamespace("item/" + assetId);
        }
        return ownModel(assetId, state);
    }

    private static ResourceLocation modelFile(ResourceLocation model) {
        return ResourceLocation.fromNamespaceAndPath(model.getNamespace(),
                "models/" + model.getPath() + ".json");
    }

    /**
     * Read only the model IDs of one own-sprite entry, without validating anything else. Used by
     * {@link #ownModelIds(ResourceManager)} so additional-model registration cannot depend on reload
     * ordering.
     */
    private static Entry parseOwnSpriteModels(JsonObject entry) {
        String status = entry.get("status").getAsString();
        if (!"own_sprite".equals(entry.get("route").getAsString()) || !"ready".equals(status)) {
            return null;
        }
        String assetId = entry.get("assetId").getAsString();
        Map<String, ResourceLocation> states = new LinkedHashMap<>();
        for (String state : stateKeys(entry.get("held"))) {
            states.put(state, ownOrMinecraftModel(assetId, state));
        }
        if (states.isEmpty()) {
            return null;
        }
        ResourceLocation gui16 = null;
        ResourceLocation gui32 = null;
        for (String size : stateKeys(entry.get("gui"))) {
            if ("32".equals(size)) {
                gui32 = ownModel(assetId, "gui32");
            } else {
                gui16 = ownModel(assetId, "gui16");
            }
        }
        return new Entry(entry.get("id").getAsString(), assetId, Route.OWN_SPRITE,
                Kind.of(entry.has("kind") ? entry.get("kind").getAsString() : null),
                null, null, null, null, gui16, gui32, Map.copyOf(states),
                null, null, List.of(), Set.of(), Acceptance.unknown(), List.of(), List.of());
    }

    /** {@code held}/{@code gui} are objects in schema v2; an older array file still parses. */
    private static List<String> stateKeys(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> value : element.getAsJsonObject().entrySet()) {
                keys.add(value.getKey());
            }
            return keys;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            JsonObject object = entry.getAsJsonObject();
            keys.add(object.has("state") ? object.get("state").getAsString()
                    : object.get("size").getAsString());
        }
        return keys;
    }

    /**
     * For {@code /*}-typed provider predicates the vanilla comparator only ever calls
     * {@code ItemPropertyFunction} or {@code ClampedItemPropertyFunction}. The method name recorded in
     * the disassembly is not the interface method, so the arity must be mapped explicitly. Unknown
     * arities are rejected rather than guessed.
     */
    private static int bridgeArity(String method, boolean clamped) {
        return switch (method) {
            case "call" -> 4;
            case "unclampedCall" -> 5;
            case "" -> clamped ? 4 : 5;
            default -> -1;
        };
    }

    /**
     * The whole document -> table step, as a pure function.
     *
     * <p>Split out of {@link #prepare} so the shipped route file can be parsed by tests without a
     * resource manager, and so one entry's failure cannot be confused with the document's. Throws when
     * the document itself is unusable; per-entry failures are recorded in {@code rejected} instead.
     */
    public static Data parse(JsonObject document) {
        if (!document.has("equipmentSchemaVersion")
                || document.get("equipmentSchemaVersion").getAsInt() != 2) {
            throw new IllegalArgumentException("routes/2d.json must declare equipmentSchemaVersion 2");
        }
        JsonArray equipment = document.getAsJsonArray("equipment");
        if (equipment == null || equipment.size() != 42) {
            throw new IllegalArgumentException("equipment must hold exactly the 42 entries");
        }

        Map<String, Entry> entries = new LinkedHashMap<>();
        Map<String, List<String>> rejected = new TreeMap<>();
        Map<String, List<String>> notes = new TreeMap<>();
        int admitted = 0;
        int providerEntries = 0;

        for (JsonElement element : equipment) {
            JsonObject entry = element.getAsJsonObject();
            String id = entry.get("id").getAsString();
            try {
                Entry parsed = parseEntry(entry, rejected, notes);
                if (parsed != null) {
                    entries.put(id, parsed);
                    admitted++;
                    if (parsed.route() != Route.OWN_SPRITE) {
                        providerEntries++;
                    }
                }
            } catch (RuntimeException failure) {
                blocked(rejected, id, failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
        return new Data(Map.copyOf(entries), Map.copyOf(rejected), Map.copyOf(notes),
                admitted, providerEntries);
    }

    @Override
    protected Data prepare(ResourceManager manager, ProfilerFiller profiler) {
        try (Reader reader = manager.getResourceOrThrow(ROUTE_FILE).openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception failure) {
            // A rejected route file must not take the client down: keep equipment on the legacy route.
            return new Data(Map.of(),
                    Map.of("routes/2d.json", List.of("equipment routes not loaded, legacy fallback active: "
                            + failure.getMessage())),
                    Map.of(), 0, 0);
        }
    }

    /** @return the admitted entry, or {@code null} when the entry legitimately stays out of the path */
    private static Entry parseEntry(JsonObject entry, Map<String, List<String>> rejected,
                                    Map<String, List<String>> notes) {
        String id = entry.get("id").getAsString();
        String routeName = entry.get("route").getAsString();
        String assetId = entry.get("assetId").getAsString();
        String status = entry.get("status").getAsString();
        List<String> hardBlockers = stringList(entry, "blockers");
        List<String> entryNotes = new ArrayList<>(stringList(entry, "notes"));

        if (!Set.of("pending", "ready").contains(status)) {
            throw new IllegalArgumentException("Unknown status " + status);
        }
        Route route = switch (routeName) {
            case "own_sprite" -> Route.OWN_SPRITE;
            case "vanilla_model" -> Route.VANILLA_MODEL;
            case "provider_model" -> Route.PROVIDER_MODEL;
            default -> throw new IllegalArgumentException("Unknown route " + routeName);
        };
        Kind kind = Kind.of(entry.has("kind") ? entry.get("kind").getAsString() : null);
        Acceptance acceptance = acceptanceOf(entry);

        EquipmentGeometry geometry = null;
        EquipmentDisplay display = displayOf(entry, "display");
        EquipmentDisplay blockingDisplay = displayOf(entry, "displayBlocking");
        ResourceLocation ownHeldModel = null;
        ResourceLocation ownGui16 = null;
        ResourceLocation ownGui32 = null;
        Map<String, ResourceLocation> ownStates = Map.of();
        String providerNamespace = null;
        ResourceLocation providerBase = null;
        List<ProviderState> providerStates = List.of();
        Set<ResourceLocation> requiredPresence = Set.of();

        if (route == Route.OWN_SPRITE) {
            geometry = geometryOf(entry);
            List<String> held = stateKeys(entry.get("held"));
            if (held.isEmpty()) {
                throw new IllegalArgumentException("own_sprite needs at least one held state");
            }
            Map<String, ResourceLocation> states = new LinkedHashMap<>();
            Set<ResourceLocation> presence = new LinkedHashSet<>();
            for (String state : held) {
                ResourceLocation model = ownOrMinecraftModel(assetId, state);
                states.put(state, model);
                presence.add(modelFile(model));
            }
            ownStates = Map.copyOf(states);
            for (String size : stateKeys(entry.get("gui"))) {
                if ("32".equals(size)) {
                    ownGui32 = ownModel(assetId, "gui32");
                    presence.add(modelFile(ownGui32));
                } else {
                    ownGui16 = ownModel(assetId, "gui16");
                    presence.add(modelFile(ownGui16));
                }
            }
            requiredPresence = Set.copyOf(presence);
            ownHeldModel = ownStates.get(states.containsKey("standby") ? "standby"
                    : states.keySet().iterator().next());
            if (display == null) {
                hardBlockers = new ArrayList<>(hardBlockers);
                hardBlockers.add("display is null: the own_sprite route is the only owner of its "
                        + "transform, so palm target p and rotation R must be resolved first");
            }
            if (blockingDisplay == null && kind.doubleFaced()) {
                entryNotes.add("no separate blocking display; the stand-by pose is reused while guarding");
            }
        } else {
            String model = entry.get("model").getAsString();
            providerNamespace = model.split(":", 2)[0];
            providerBase = id(model);
            JsonArray stateModels = entry.getAsJsonArray("stateModels");
            if (stateModels == null || stateModels.isEmpty()) {
                throw new IllegalArgumentException("provider route needs stateModels");
            }
            Map<String, Boolean> usableByState = usableStates(entry, entryNotes);
            List<ProviderState> states = new ArrayList<>();
            Set<ResourceLocation> presence = new LinkedHashSet<>();
            for (JsonElement stateElement : stateModels) {
                JsonObject stateEntry = stateElement.getAsJsonObject();
                String state = stateEntry.get("state").getAsString();
                ResourceLocation modelId = id(stateEntry.get("model").getAsString());
                Map<ResourceLocation, Float> predicates = new LinkedHashMap<>();
                JsonObject predicate = stateEntry.getAsJsonObject("predicate");
                if (predicate != null) {
                    for (Map.Entry<String, JsonElement> value : predicate.entrySet()) {
                        predicates.put(predicateId(value.getKey()), value.getValue().getAsFloat());
                    }
                }
                boolean usable = usableByState.getOrDefault(state, true);
                states.add(new ProviderState(state, modelId, Map.copyOf(predicates), usable));
                if (usable) {
                    presence.add(modelFile(modelId));
                } else {
                    entryNotes.add("provider state not selectable from own play: " + state);
                }
            }
            providerStates = List.copyOf(states);
            presence.add(modelFile(providerBase));
            requiredPresence = Set.copyOf(presence);
            // Display is inherited from the provider's own baked model: null here is the route
            // difference, not a missing input.
            entryNotes.add("display inherited from the provider baked model "
                    + providerBase + " (displayPolicy " + text(entry, "displayPolicy", "inherit_provider_display")
                    + ")");
        }

        if (!hardBlockers.isEmpty()) {
            rejected.put(id, List.copyOf(hardBlockers));
            return null;
        }
        if (!"ready".equals(status)) {
            rejected.put(id, List.of("status pending: static input or capability not yet complete ("
                    + entryNotes.size() + " recorded note(s))"));
            notes.put(id, List.copyOf(entryNotes));
            return null;
        }
        if (!entryNotes.isEmpty()) {
            notes.put(id, List.copyOf(entryNotes));
        }
        return new Entry(id, assetId, route, kind, geometry, display, blockingDisplay,
                ownHeldModel, ownGui16, ownGui32, ownStates, providerNamespace, providerBase,
                providerStates, requiredPresence, acceptance, List.copyOf(entryNotes), List.of());
    }

    /**
     * Which provider states own play can actually select.
     *
     * <p>A predicate the contract marks {@code blocked} or {@code unsupported} is recorded as evidence
     * but never faked, and — importantly — it must not seal the whole equipment off: only the state it
     * guards becomes unselectable, so the stand-by pose stays reachable (§6).
     */
    private static Map<String, Boolean> usableStates(JsonObject entry, List<String> notes) {
        Map<String, Boolean> usable = new LinkedHashMap<>();
        JsonArray bindings = entry.getAsJsonArray("predicateBindings");
        if (bindings == null) {
            return usable;
        }
        for (JsonElement bindingElement : bindings) {
            JsonObject binding = bindingElement.getAsJsonObject();
            String support = binding.get("support").getAsString();
            String predicate = binding.get("predicate").getAsString();
            String namespace = predicateNamespace(predicate);
            boolean resolvable;
            if ("minecraft".equals(namespace)) {
                resolvable = "supported".equals(support);
            } else {
                int arity = bridgeArity(shortMethod(binding), bindingClamped(binding));
                resolvable = switch (support) {
                    case "supported" -> arity == 4 || arity == 5;
                    case "supported_alias_required" -> arity > 0;
                    default -> false;
                };
            }
            if (!resolvable) {
                notes.add("predicate not selectable (" + support + "): " + predicate);
            }
            JsonArray states = binding.getAsJsonArray("states");
            if (states == null) {
                continue;
            }
            for (JsonElement state : states) {
                String name = state.getAsString();
                usable.merge(name, resolvable, (left, right) -> left && right);
            }
        }
        return usable;
    }

    private static Acceptance acceptanceOf(JsonObject entry) {
        JsonElement element = entry.get("acceptance");
        if (element == null || !element.isJsonObject()) {
            return Acceptance.unknown();
        }
        JsonObject acceptance = element.getAsJsonObject();
        return new Acceptance(
                text(acceptance, "state", "unknown"),
                acceptance.has("humanAccepted") && acceptance.get("humanAccepted").getAsBoolean(),
                text(entry, "displayStatus", text(acceptance, "displayStatus", "unknown")));
    }

    private static String text(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static String shortMethod(JsonObject binding) {
        return binding.has("method") ? binding.get("method").getAsString() : "";
    }

    private static boolean bindingClamped(JsonObject binding) {
        return binding.has("clamped") && binding.get("clamped").getAsBoolean();
    }

    private static List<String> stringList(JsonObject entry, String key) {
        JsonArray array = entry.getAsJsonArray(key);
        if (array == null) {
            return List.of();
        }
        List<String> list = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            list.add(element.getAsString());
        }
        return list;
    }

    private static void blocked(Map<String, List<String>> rejected, String id, String reason) {
        rejected.computeIfAbsent(id, key -> new ArrayList<>()).add(reason);
    }

    /** Parsed into {@code current} only after a complete pass, so no half table is ever rendered. */
    @Override
    protected void apply(Data prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        generation++;
        aliasesInstalled = false;
        registerAliases();
    }

    /** Resource lookup used by the provider adapter; never loads a class while merely probing. */
    public static boolean present(ResourceManager manager, ResourceLocation location) {
        try {
            Resource resource = manager.getResource(location).orElse(null);
            return resource != null;
        } catch (RuntimeException | Error failure) {
            return false;
        }
    }
}
