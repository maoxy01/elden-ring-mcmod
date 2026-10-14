package com.dsh.eldencombat;

import com.dsh.eldencombat.core.WeaponDisplayTransforms;
import com.dsh.eldencombat.core.WeaponDisplayTransforms.View;
import com.dsh.eldencombat.core.geom.Box;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 武器手持/图标变换。
 *
 * <p>这里钉住的是"剑型武器握持不对"那个 bug 的三半：
 * <ol>
 *   <li><b>数据</b>：物品模型必须声明 <b>原版 item/handheld 的 display 块</b>
 *       （也就是钻石剑的握法）。缺了它，原版 {@code ItemRenderer} 传给 BEWLR 的就是单位变换，
 *       手持姿势只能靠代码手搓——那正是之前的做法，也是错的根源之一；</li>
 *   <li><b>位置</b>：BEWLR 拿到的坐标空间是**图标格 [0,1]³**（原版在调用 BEWLR 之前还做了
 *       {@code translate(-0.5,-0.5,-0.5)}），所以握把必须映射到图标格中心的 (0.5,0.5,0.5)，
 *       而不是想当然的 (0,0,0)；</li>
 *   <li><b>朝向</b>：原版剑贴图的刀身在图标平面内斜 45°（护手在中心、尖在右上角），
 *       所以网格的 +Y 刀身要绕 Z 逆向转 45°，这样在手里才和钻石剑一个方向。</li>
 * </ol>
 */
class WeaponDisplayTransformsTest {
    private static final Box LONG = new Box(-.25, -.75, -.15, .45, 2.7, .15);

    private static JsonObject placeholder(String name) throws IOException {
        Path path = Path.of("src", "main", "resources", "assets", "eldencombat", "models", "item",
                name + ".json");
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonArray array(JsonObject display, String context, String field) {
        return display.getAsJsonObject(context).getAsJsonArray(field);
    }

    private static void assertContext(JsonObject display, String context, double[] rotation,
                                      double[] translation, double[] scale) {
        for (int i = 0; i < 3; i++) {
            assertEquals(rotation[i], array(display, context, "rotation").get(i).getAsDouble(), 1.0e-6,
                    context + " rotation[" + i + "]");
            assertEquals(translation[i], array(display, context, "translation").get(i).getAsDouble(), 1.0e-6,
                    context + " translation[" + i + "]");
            assertEquals(scale[i], array(display, context, "scale").get(i).getAsDouble(), 1.0e-6,
                    context + " scale[" + i + "]");
        }
    }

    @Test void meleeModelsDeclareTheVanillaDiamondSwordHold() throws IOException {
        JsonObject model = placeholder("weapon_placeholder");
        // 必须是 builtin/entity，否则原版根本不会调用我们的 BEWLR（模型会渲染成空）
        assertEquals("minecraft:builtin/entity", model.get("parent").getAsString());
        JsonObject display = model.getAsJsonObject("display");
        // 这四个值就是从 minecraft:item/handheld（钻石剑的父模型）抄来的，不允许"再手搓一套"
        assertContext(display, "thirdperson_righthand", new double[]{0, -90, 55}, new double[]{0, 4, 0.5},
                new double[]{0.85, 0.85, 0.85});
        assertContext(display, "thirdperson_lefthand", new double[]{0, 90, -55}, new double[]{0, 4, 0.5},
                new double[]{0.85, 0.85, 0.85});
        assertContext(display, "firstperson_righthand", new double[]{0, -90, 25}, new double[]{1.13, 3.2, 1.13},
                new double[]{0.68, 0.68, 0.68});
        assertContext(display, "firstperson_lefthand", new double[]{0, 90, -25}, new double[]{1.13, 3.2, 1.13},
                new double[]{0.68, 0.68, 0.68});
    }

    @Test void bowAndCrossbowModelsUseTheirOwnVanillaHolds() throws IOException {
        JsonObject bow = placeholder("weapon_placeholder_bow");
        assertEquals("minecraft:builtin/entity", bow.get("parent").getAsString());
        assertContext(bow.getAsJsonObject("display"), "thirdperson_righthand",
                new double[]{-80, 260, -40}, new double[]{-1, -2, 2.5}, new double[]{0.9, 0.9, 0.9});
        assertContext(bow.getAsJsonObject("display"), "firstperson_righthand",
                new double[]{0, -90, 25}, new double[]{1.13, 3.2, 1.13}, new double[]{0.68, 0.68, 0.68});

        JsonObject crossbow = placeholder("weapon_placeholder_crossbow");
        assertEquals("minecraft:builtin/entity", crossbow.get("parent").getAsString());
        assertContext(crossbow.getAsJsonObject("display"), "thirdperson_righthand",
                new double[]{-90, 0, -60}, new double[]{2, 0.1, -3}, new double[]{0.9, 0.9, 0.9});
    }

    @Test void bowAndCrossbowItemsPointAtTheirOwnPlaceholder() throws IOException {
        for (String id : new String[]{"short_bow", "long_bow", "great_bow"}) {
            assertEquals("eldencombat:item/weapon_placeholder_bow",
                    placeholder(id).get("parent").getAsString(), id);
        }
        for (String id : new String[]{"crossbow", "ballista"}) {
            assertEquals("eldencombat:item/weapon_placeholder_crossbow",
                    placeholder(id).get("parent").getAsString(), id);
        }
        // 剑型武器仍然走近战 placeholder（= 钻石剑握法）
        for (String id : new String[]{"straight_sword", "great_sword", "katana", "sacred_relic_sword"}) {
            assertEquals("eldencombat:item/weapon_placeholder",
                    placeholder(id).get("parent").getAsString(), id);
        }
    }

    @Test void heldTransformsEmulateTheVanillaSwordSprite() {
        // 原版 diamond_sword.png 实测（像素矩阵）：护手在 (8,8)=图标中心，刀身主轴 -45°，
        // 剑尖在 (15,0) → 图标格 (0.9375, 1.0)。下面把这三条钉进变换里。
        for (var view : new View[]{View.FIRST_PERSON, View.THIRD_PERSON}) {
            for (boolean left : new boolean[]{false, true}) {
                var matrix = WeaponDisplayTransforms.matrix(LONG, view, left);
                var grip = matrix.transformPosition(new Vector3f());
                assertEquals(WeaponDisplayTransforms.ICON_GRIP_X, grip.x, 1.0e-6, "实际棕色柄部 X");
                assertEquals(WeaponDisplayTransforms.ICON_GRIP_Y, grip.y, 1.0e-6, "实际棕色柄部 Y");
                assertEquals(0.5f, grip.z, 1.0e-6, "握把应落在图标格中心的 Z");
                // 刀身：平面内朝右上 45°（原版贴图的斜向），且不离开图标平面
                var blade = matrix.transformDirection(new Vector3f(0, 1, 0)).normalize();
                assertEquals((float) Math.cos(Math.toRadians(45)), blade.y, 1.0e-6, "刀身 +Y 分量");
                assertEquals((float) Math.sin(Math.toRadians(45)), blade.x, 1.0e-6, "刀身 +X 分量");
                assertEquals(0f, blade.z, 1.0e-6, "刀身不该有 Z 分量");
            }
        }
        // 直剑（Codex 网格实测 y 从 -0.3125 到 1.375）缩放旋转后，剑尖应贴着图标右上角：
        // 原版剑尖 (0.9375,1.0)，我们允许 0.06 的差（我们柄短、刀身长）。
        var straight = new Box(-.3125, -.3125, -.03125, .3125, 1.375, .03125);
        var tip = WeaponDisplayTransforms.matrix(straight, View.THIRD_PERSON, false)
                .transformPosition(new Vector3f(0, 1.375f, 0));
        assertEquals(WeaponDisplayTransforms.ICON_GRIP_X + 1.375f / (float)Math.sqrt(2), tip.x, 1e-6, "默认保留源尺寸");
        assertEquals(tip.x, tip.y, 1e-6, "仍沿原版贴图方向");
        assertEquals(0.5f, tip.z, 1.0e-6, "剑尖应留在图标平面上");
    }

    @Test void heldFallbackPreservesSourceSizeDifferences() {
        var dagger = new Box(-.1, -.2, -.05, .1, 1.2, .05);
        var longFit = WeaponDisplayTransforms.matrix(LONG, View.FIRST_PERSON, false).getScale(new Vector3f()).x;
        var shortFit = WeaponDisplayTransforms.matrix(dagger, View.FIRST_PERSON, false).getScale(new Vector3f()).x;
        assertEquals(shortFit, longFit, 1e-6, "缺表时保留源几何，而非归一等长");
        assertTrue(3.45f * longFit > 1.4f * shortFit);
        // 手持的"第一人称比第三人称小"现在由物品模型的 display 块提供（0.68 vs 0.85），
        // 所以 BEWLR 侧的缩放对两种视角一致。
        assertEquals(longFit, WeaponDisplayTransforms.matrix(LONG, View.THIRD_PERSON, false)
                .getScale(new Vector3f()).x, 1.0e-6);
    }

    @Test void authoredRatiosSurviveDisplayScaleWithoutMovingTheGrip() throws IOException {
        var root = JsonParser.parseString(Files.readString(Path.of("art/weapon-proportions/profiles.json"))).getAsJsonObject();
        var profiles = com.dsh.eldencombat.core.WeaponProportions.parse(root);
        assertEquals(41, profiles.size());
        for (var value : root.getAsJsonArray("entries")) {
            var entry = value.getAsJsonObject();
            String id = entry.get("id").getAsString().substring("eldencombat:".length());
            var model = com.dsh.eldencombat.skeleton.ModelData.parse(JsonParser.parseString(
                    Files.readString(Path.of(entry.get("sourceModel").getAsString()))).getAsJsonObject());
            var bounds = com.dsh.eldencombat.skeleton.HitVolumes.modelAabb(com.dsh.eldencombat.skeleton.SkeletonPose.rest(model));
            float height = (float)(bounds.maxY()-bounds.minY());
            for (var view : new View[]{View.FIRST_PERSON, View.THIRD_PERSON}) for (boolean left : new boolean[]{false,true}) {
                for (float upstream : new float[]{.68f,.85f,.9f,.6f}) {
                    var profile = profiles.get(id);
                    float scale = profile.heldScale(height, view == View.FIRST_PERSON, upstream);
                    var transform = new org.joml.Matrix4f().scale(upstream)
                            .mul(WeaponDisplayTransforms.matrix(bounds, view, left, scale,profile.gripX(),profile.gripY(),profile.gripZ()));
                    var grip = transform.transformPosition(new Vector3f(profile.gripX(),profile.gripY(),profile.gripZ()));
                    assertEquals(WeaponDisplayTransforms.ICON_GRIP_X*upstream, grip.x, 1e-6, id+" grip");
                    assertEquals(WeaponDisplayTransforms.ICON_GRIP_Y*upstream, grip.y, 1e-6, id+" grip");
                    assertEquals(.5f*upstream, grip.z, 1e-6, id+" grip");
                    float playerHeight = view == View.FIRST_PERSON ? profile.referencePlayerHeight()
                            : com.dsh.eldencombat.core.WeaponProportions.PLAYER_MODEL_HEIGHT;
                    assertEquals(profile.ratioToPlayer()*playerHeight,
                            transform.transformDirection(new Vector3f(0,height,0)).length(), 1e-5, id+" ratio");
                }
            }
        }
        assertTrue(profiles.get("dagger").ratioToPlayer()<profiles.get("straight_sword").ratioToPlayer());
        assertTrue(profiles.get("straight_sword").ratioToPlayer()<profiles.get("spear").ratioToPlayer());
    }

    @Test void iconsAreCenteredAndFitEvenAfterRotation() {
        for (var bounds : new Box[]{LONG, new Box(-1, -.8, -.6, 1, .8, .6), new Box(-.01, -.5, -.01, .01, .5, .01)}) {
            var matrix = WeaponDisplayTransforms.matrix(bounds, View.GUI, false);
            var center = new Vector3f((float) (bounds.minX() + bounds.maxX()) / 2,
                    (float) (bounds.minY() + bounds.maxY()) / 2, (float) (bounds.minZ() + bounds.maxZ()) / 2);
            assertEquals(0, matrix.transformPosition(center).length(), 1e-6);
            for (int i = 0; i < 8; i++) {
                var p = matrix.transformPosition(new Vector3f((float) ((i & 1) == 0 ? bounds.minX() : bounds.maxX()),
                        (float) ((i & 2) == 0 ? bounds.minY() : bounds.maxY()),
                        (float) ((i & 4) == 0 ? bounds.minZ() : bounds.maxZ())));
                assertTrue(Math.abs(p.x) <= .441 && Math.abs(p.y) <= .441);
            }
        }
    }

    @Test void groundItemsRemainSmall() {
        assertEquals(.5 / 3.45, WeaponDisplayTransforms.matrix(LONG, View.GROUND, false).m00(), 1e-6);
    }

    @Test void actualHandleCentersStayAtThePalmUnderScaleAndCorrectionRotation() throws Exception {
        var root=JsonParser.parseString(Files.readString(Path.of("art/weapon-proportions/sword-grip-regions.json"))).getAsJsonObject();
        var grips=com.dsh.eldencombat.core.WeaponProportions.parseGrips(root);
        assertEquals(3,grips.size());
        for(var value:root.getAsJsonArray("entries")) {
            var entry=value.getAsJsonObject();
            String id=entry.get("id").getAsString().substring("eldencombat:".length());
            String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(Path.of(entry.get("model").getAsString()))));
            assertEquals(grips.get(id).modelSha256(),hash,"grip geometry hash must match current model");
            var g=grips.get(id);
            for(float scale:new float[]{.6f,1f,1.15f,1.3f}) {
                var correction=new org.joml.Matrix4f().translate(WeaponDisplayTransforms.ICON_GRIP_X,WeaponDisplayTransforms.ICON_GRIP_Y,.5f)
                        .rotateY(.7f).scale(1.2f).translate(-WeaponDisplayTransforms.ICON_GRIP_X,-WeaponDisplayTransforms.ICON_GRIP_Y,-.5f);
                var matrix=correction.mul(WeaponDisplayTransforms.matrix(LONG,View.THIRD_PERSON,false,scale,g.x(),g.y(),g.z()));
                var center=matrix.transformPosition(new Vector3f(g.x(),g.y(),g.z()));
                assertEquals(WeaponDisplayTransforms.ICON_GRIP_X,center.x,1e-6);
                assertEquals(WeaponDisplayTransforms.ICON_GRIP_Y,center.y,1e-6);
                assertEquals(.5f,center.z,1e-6);
            }
        }
    }
}
