package com.dsh.eldencombat;

import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.skeleton.WeaponMeshData;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import com.dsh.eldencombat.skeleton.HitVolumes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WeaponMeshDataTest {
    private static JsonObject fixture() {
        return JsonParser.parseString("""
                {"schemaVersion":1,"bone":"root","units":"blocks","indexType":"uint16",
                 "materialGroups":1,"vertexCount":3,"triangleCount":1,
                 "positions":[0,0,0,1,0,0,0,1,0],"normals":[0,0,1,0,0,1,0,0,1],
                 "uvs":[0.1,0.8,0.2,0.8,0.1,0.7],"indices":[0,1,2]}
                """).getAsJsonObject();
    }

    @Test void convertsTriangleToQuadAndFlipsVExactlyOnce() {
        var mesh = WeaponMeshData.parse(fixture());
        assertEquals(4, mesh.vertexCount());
        assertEquals(1, mesh.triangleCount());
        assertEquals(1, mesh.value(1, 0)); // no second division by 16
        assertEquals(.2f, mesh.value(0, 4), .00001f);
        for (int field = 0; field < 8; field++) assertEquals(mesh.value(2, field), mesh.value(3, field));
    }

    @Test void rejectsInvalidIndicesAndSchema() {
        for (String value : new String[]{"-1", "3", "0.5"}) {
            var root = fixture(); root.getAsJsonArray("indices").set(0, JsonParser.parseString(value));
            assertThrows(IllegalArgumentException.class, () -> WeaponMeshData.parse(root));
        }
        var root = fixture(); root.addProperty("schemaVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> WeaponMeshData.parse(root));
    }

    @Test void rejectsBadNormalsUvsCountsAndWinding() {
        var normal = fixture(); normal.getAsJsonArray("normals").set(2, JsonParser.parseString("0"));
        assertThrows(IllegalArgumentException.class, () -> WeaponMeshData.parse(normal));
        var uv = fixture(); uv.getAsJsonArray("uvs").set(0, JsonParser.parseString("1.1"));
        assertThrows(IllegalArgumentException.class, () -> WeaponMeshData.parse(uv));
        var count = fixture(); count.addProperty("triangleCount", -1);
        assertThrows(IllegalArgumentException.class, () -> WeaponMeshData.parse(count));
        var winding = fixture(); winding.add("indices", JsonParser.parseString("[0,2,1]"));
        assertThrows(IllegalArgumentException.class, () -> WeaponMeshData.parse(winding));
        var infinite = fixture(); infinite.getAsJsonArray("positions").set(0, JsonParser.parseString("1e100"));
        assertThrows(IllegalArgumentException.class, () -> WeaponMeshData.parse(infinite));
        var mesh = WeaponMeshData.parse(fixture());
        assertThrows(IllegalArgumentException.class, () -> mesh.validateBounds(new com.dsh.eldencombat.core.geom.Box(0, 0, 0, .1, .1, .1)));
    }

    @Test void allEightGameMeshesMatchModelBoundsAndHaveCachedFallback() throws Exception {
        int checked = 0;
        try (var paths = Files.list(Path.of("art/remembrance-game"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".model.json")).toList()) {
                var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                var model = ModelData.parse(root);
                var meshRoot = JsonParser.parseString(Files.readString(path.resolveSibling(root.get("bakedMeshPath").getAsString()))).getAsJsonObject();
                var mesh = WeaponMeshData.parse(meshRoot);
                assertEquals(model.id, meshRoot.get("id").getAsString());
                assertEquals(meshRoot.get("triangleCount").getAsInt() * 4, mesh.vertexCount());
                var bounds = HitVolumes.modelAabb(SkeletonPose.rest(model));
                for (int i = 0; i < mesh.vertexCount(); i++) {
                    assertTrue(mesh.value(i, 0) >= bounds.minX() - .00001 && mesh.value(i, 0) <= bounds.maxX() + .00001);
                    assertTrue(mesh.value(i, 1) >= bounds.minY() - .00001 && mesh.value(i, 1) <= bounds.maxY() + .00001);
                    assertTrue(mesh.value(i, 2) >= bounds.minZ() - .00001 && mesh.value(i, 2) <= bounds.maxZ() + .00001);
                }
                var fallback = WeaponMeshData.bake(model);
                assertEquals(model.cubes.size() * 24, fallback.vertexCount());
                checked++;
            }
        }
        assertEquals(8, checked);
    }
}
