import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.dsh.eldencombat.effects.SkillEffectMesh.Kind;
import com.dsh.eldencombat.effects.WaveFront;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import com.dsh.eldencombat.effects.VfxBudget;
import com.dsh.eldencombat.effects.FxPalette;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.GsonBuilder;
import java.util.Base64;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Exports the exact game's procedural geometry, not a second implementation for the browser. */
public class EffectPreviewExporter {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected output .js path and local Minecraft resource jar");
        var report = budgetReport();
        var json = new StringBuilder("window.SKILL_FX_PACK=[");
        for (Kind kind : Kind.values()) {
            if (kind == Kind.NONE) continue;
            if (kind.wireId() != 0) json.append(',');
            json.append("{\"kind\":\"").append(kind.name()).append("\",\"frames\":[");
            for (int i = 0; i <= 20; i++) {
                if (i > 0) json.append(',');
                json.append('[');
                float radius = kind == Kind.BLOOD_RITUAL ? 4f : 3f;
                float travel = i / 20f * 16;
                if (kind == Kind.GOLD_WAVE) radius = (float) WaveFront.radius(3, 4.2, travel);
                var boxes = new java.util.ArrayList<>(kind == Kind.GOLD_WAVE
                        ? SkillEffectMesh.build(kind, radius, i == 20 ? 1 : .12f, 2, travel, 0)
                        : SkillEffectMesh.build(kind, radius, i / 20f, 2));
                if (SkillSlashSprites.supports(kind) && FxPalette.current().slashSprites()) {
                    // Same faint native silhouette as the in-game primary/fallback channel.
                    for (int j = 0; j < boxes.size(); j++) {
                        var b = boxes.get(j);
                        boxes.set(j, new SkillEffectMesh.Box(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), b.rgb(), b.alpha() * .18f));
                    }
                }
                boxes.addAll(SkillEffectMesh.debris(kind, radius, i / 20f));
                for (int j = 0; j < boxes.size(); j++) {
                    if (j > 0) json.append(',');
                    var b = boxes.get(j);
                    json.append(String.format(Locale.ROOT, "[%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%.4f]",
                            b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), b.rgb(), b.alpha()));
                }
                json.append(']');
            }
            json.append("],\"sprites\":[");
            for (int i = 0; i <= 20; i++) {
                if (i > 0) json.append(',');
                json.append('[');
                var strips = FxPalette.current().slashSprites() ? SkillSlashSprites.build(kind, 3, i / 20f, 2) : java.util.List.<SkillSlashSprites.Strip>of();
                for (int j = 0; j < strips.size(); j++) {
                    if (j > 0) json.append(',');
                    var s = strips.get(j);
                    json.append(String.format(Locale.ROOT, "[%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%.4f,%d]",
                            s.x(), s.y(), s.z(), s.width(), s.height(), s.roll(), s.rgb(), s.alpha(), s.frame()));
                }
                json.append(']');
            }
            json.append("]}");
        }
        json.append("];\n");
        // Local preview only; the mod reads the installed particle atlas instead of bundling these bytes.
        json.append("window.SKILL_FX_EXPLOSION=[");
        try (var jar = new ZipFile(args[1])) {
            for (int i = 0; i < 16; i++) {
                if (i > 0) json.append(',');
                var entry = jar.getEntry("assets/minecraft/textures/particle/explosion_" + i + ".png");
                if (entry == null) throw new IllegalArgumentException("Missing vanilla explosion frame " + i);
                try (var in = jar.getInputStream(entry)) {
                    json.append('"').append("data:image/png;base64,").append(Base64.getEncoder().encodeToString(in.readAllBytes())).append('"');
                }
            }
        }
        json.append("];\n");
        var destination = Path.of(args[0]);
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.writeString(destination, json, StandardCharsets.UTF_8);
        Files.writeString(destination.resolveSibling("budget-report.json"), new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        System.out.println("Exported active native effects into " + destination);
    }

    private static JsonObject budgetReport() {
        var report = new JsonObject();report.addProperty("schemaVersion", 1);report.addProperty("status", "passed");
        report.addProperty("scope", "Static submission and data checks; NOT GPU timing or in-game compatibility");
        var limits = new JsonObject();limits.addProperty("boxes", VfxBudget.MAX_BOXES);limits.addProperty("quads", VfxBudget.MAX_QUADS);
        limits.addProperty("meshVertices", VfxBudget.MAX_VERTICES);limits.addProperty("frameVertices", VfxBudget.MAX_FRAME_VERTICES);
        limits.addProperty("debrisPerEntity", VfxBudget.MAX_DEBRIS);report.add("limits", limits);
        var effects = new JsonArray();int maximumVertices = 0, samples = 0;
        for (var kind : Kind.values()) {
            var effect = new JsonObject();effect.addProperty("kind", kind.name());var phases = new JsonArray();
            for (int phase = 0; phase < 3; phase++) {
                int maxBoxes = 0, maxVertices = 0, maxSprites = 0, maxDebris = 0;
                for (float radius : new float[]{.25f, 1, 3, 6}) for (int frame = 0; frame <= 40; frame++)
                    for (float travel : new float[]{0, 2, 4, 16}) for (int blocked : new int[]{0, 1, 0x5555, 0xffff}) {
                        if (kind != Kind.GOLD_WAVE && kind != Kind.FLAME_FRONT && (travel != 0 || blocked != 0)) continue;
                        float p = frame / 40f;
                        var boxes = SkillEffectMesh.build(kind, radius, p, phase, travel, blocked);
                        var sprites = SkillSlashSprites.build(kind, radius, p, phase);
                        var debris = SkillEffectMesh.debris(kind, radius, p);
                        var count = VfxBudget.validate(kind, boxes, sprites, debris);samples++;
                        for (var b : boxes) if (!FxPalette.current().colors().containsValue(b.rgb())) throw new IllegalStateException("Unregistered mesh color " + kind);
                        for (var s : sprites) if (!FxPalette.current().colors().containsValue(s.rgb())) throw new IllegalStateException("Unregistered sprite color " + kind);
                        for (var b : debris) if (!FxPalette.current().colors().containsValue(b.rgb())) throw new IllegalStateException("Unregistered debris color " + kind);
                        if (frame == 40 && (!boxes.isEmpty() || !sprites.isEmpty() || !debris.isEmpty())) throw new IllegalStateException("Effect survives end frame: " + kind);
                        maxBoxes = Math.max(maxBoxes, count.boxes());maxVertices = Math.max(maxVertices, count.vertices());
                        maxSprites = Math.max(maxSprites, count.sprites());maxDebris = Math.max(maxDebris, count.debris());
                    }
                var entry = new JsonObject();entry.addProperty("phase", phase);entry.addProperty("boxes", maxBoxes);
                entry.addProperty("quads", maxBoxes * 6);entry.addProperty("submittedVertices", maxVertices);
                entry.addProperty("sprites", maxSprites);entry.addProperty("debris", maxDebris);
                entry.add("gpuVisibleQuads", com.google.gson.JsonNull.INSTANCE);entry.add("gpuMs", com.google.gson.JsonNull.INSTANCE);
                phases.add(entry);maximumVertices = Math.max(maximumVertices, maxVertices);
            }
            effect.add("phases", phases);effects.add(effect);
        }
        report.add("effects", effects);report.addProperty("samples", samples);
        report.addProperty("eightEntitySubmittedVerticesUpperEstimate", maximumVertices * 8);
        if (maximumVertices * 8 > VfxBudget.MAX_FRAME_VERTICES) throw new IllegalStateException("Eight-entity static budget exceeded");
        var verified = new JsonObject();for (String key : new String[]{"vanillaInGame", "iris", "sodium", "entityCulling", "dedicatedServer", "eightPlayerPerformance"}) verified.addProperty(key, false);
        report.add("verified", verified);return report;
    }
}
