package com.dsh.eldencombat;

import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.skeleton.WeaponMeshData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class WornHandGeometryTest {
    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test void bothHandsExcludeReferencePlayerAndKeepAuthorGeometry() throws Exception {
        for (String id : new String[]{"claw", "fist"}) {
            var source = read(Path.of("art/equipment-2d/worn-hand-gear-20261021/" + id + "_worn.bbmodel"));
            var originals = new HashMap<String, JsonObject>();
            source.getAsJsonArray("elements").forEach(e -> originals.put(e.getAsJsonObject().get("uuid").getAsString(), e.getAsJsonObject()));
            for (String arm : new String[]{"right", "left"}) {
                var root = read(Path.of("src/main/resources/assets/eldencombat/worn/" + id + "_" + arm + ".json"));
                var model = ModelData.parse(root);
                assertEquals(id.equals("claw") ? 47 : 28, model.cubes.size());
                assertEquals(1, model.bones.size());
                assertEquals(16, model.unitsPerBlock);
                assertEquals(128, model.textureWidth);
                var pivot = root.getAsJsonObject("attachment").getAsJsonArray("authoredPivot");
                assertEquals(arm.equals("right") ? -5 : 5, pivot.get(0).getAsInt());
                assertEquals(22, pivot.get(1).getAsInt());
                for (var cube : model.cubes) {
                    var original = originals.get(cube.name());
                    assertNotNull(original);
                    assertTrue(!original.has("export") || original.get("export").getAsBoolean(), "Reference player leaked into runtime");
                    for (int axis = 0; axis < 3; axis++) {
                        assertEquals(original.getAsJsonArray("from").get(axis).getAsFloat(),
                                cube.from().get(axis) + pivot.get(axis).getAsFloat(), .00001);
                        assertEquals(original.getAsJsonArray("to").get(axis).getAsFloat(),
                                cube.to().get(axis) + pivot.get(axis).getAsFloat(), .00001);
                    }
                }
            }
        }
    }

    @Test void cachedMeshesRetainCompleteFacesAndValidUvs() throws Exception {
        for (String id : new String[]{"claw", "fist"}) for (String arm : new String[]{"right", "left"}) {
            var model = ModelData.parse(read(Path.of("src/main/resources/assets/eldencombat/worn/" + id + "_" + arm + ".json")));
            var mesh = WeaponMeshData.bake(model);
            assertEquals(model.cubes.size() * 24, mesh.vertexCount());
            for (int i = 0; i < mesh.vertexCount(); i++) {
                for (int field = 0; field < 8; field++) assertTrue(Float.isFinite(mesh.value(i, field)));
                assertTrue(mesh.value(i, 3) >= 0 && mesh.value(i, 3) <= 1);
                assertTrue(mesh.value(i, 4) >= 0 && mesh.value(i, 4) <= 1);
            }
        }
    }

    @Test void authoredLeftAndRightAreDisjointAlreadyMirroredMeshes() throws Exception {
        for (String id : new String[]{"claw", "fist"}) {
            var right = ModelData.parse(read(Path.of("src/main/resources/assets/eldencombat/worn/" + id + "_right.json")));
            var left = ModelData.parse(read(Path.of("src/main/resources/assets/eldencombat/worn/" + id + "_left.json")));
            assertEquals(right.cubes.size(), left.cubes.size());
            var rightIds = right.cubes.stream().map(ModelData.CubeSpec::name).collect(java.util.stream.Collectors.toSet());
            assertTrue(left.cubes.stream().noneMatch(c -> rightIds.contains(c.name())), "Runtime must use authored left geometry");
        }
    }
}
