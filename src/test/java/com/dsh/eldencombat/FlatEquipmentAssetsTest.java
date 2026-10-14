package com.dsh.eldencombat;

import com.dsh.eldencombat.skeleton.HitVolumes;
import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import com.dsh.eldencombat.skeleton.WeaponMeshData;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlatEquipmentAssetsTest {
    @Test void everyRuntimeWeaponLoadsItsThinModelAndMatchingMesh() throws Exception {
        int checked = 0;
        for (String directory : List.of("art/weapons", "art/remembrance-game")) {
            try (var files = Files.list(Path.of(directory))) {
                for (var file : files.filter(p -> p.toString().endsWith(".model.json")).toList()) {
                    var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    var model = ModelData.parse(root);
                    assertEquals(16, model.unitsPerBlock);
                    assertEquals(128, model.textureWidth);
                    assertEquals(1, root.getAsJsonObject("flatProfile").get("pixelStep").getAsInt());
                    for (var cube : model.cubes) {
                        assertEquals(1f, cube.size().z);
                        assertEquals(0f, cube.rotation().lengthSquared());
                    }
                    var bounds = HitVolumes.modelAabb(SkeletonPose.rest(model));
                    var fallback = WeaponMeshData.bake(model);
                    fallback.validateBounds(bounds);
                    if (root.has("bakedMeshPath")) {
                        String meshName = root.get("bakedMeshPath").getAsString();
                        var data = JsonParser.parseString(Files.readString(file.resolveSibling(meshName))).getAsJsonObject();
                        assertEquals(model.id, data.get("id").getAsString());
                        WeaponMeshData.parse(data).validateBounds(bounds);
                        String resource = "/assets/eldencombat/skeletons/items/"
                                + (directory.endsWith("remembrance-game") ? "remembrance/" : "") + meshName;
                        assertNotNull(getClass().getResource(resource), "Mesh omitted from runtime resources: " + resource);
                    }
                    checked++;
                }
            }
        }
        assertEquals(41, checked);
    }

    @Test void legacyEquipmentAlsoLoadsWithoutChangingPlayerGeometry() throws Exception {
        for (String id : List.of("straight_sword", "great_sword", "shield", "healing_flask")) {
            var root = JsonParser.parseString(Files.readString(Path.of("art/player", id + ".model.json"))).getAsJsonObject();
            var model = ModelData.parse(root);
            assertEquals(64, model.textureWidth);
            for (var cube : model.cubes) assertEquals(1f, cube.size().z);
            WeaponMeshData.bake(model).validateBounds(HitVolumes.modelAabb(SkeletonPose.rest(model)));
        }
        for (String id : List.of("player_classic", "player_slim")) {
            assertArrayEquals(Files.readAllBytes(Path.of("art/player", id + ".model.json")),
                    Files.readAllBytes(Path.of("art/backups/flat-equipment-before-20261012/art/player", id + ".model.json")));
        }
    }
}
