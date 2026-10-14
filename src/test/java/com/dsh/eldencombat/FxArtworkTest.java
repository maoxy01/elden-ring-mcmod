package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class FxArtworkTest {
    @Test void waveUsesFullRadiusAndDisjointUvWithStableFormedGeometry() {
        var lanes=FxArtwork.lanes(4.2f,4,0,false);
        assertEquals(16,lanes.size());
        assertEquals(8.4,lanes.stream().mapToDouble(FxArtwork.Lane::width).sum(),1e-5);
        assertEquals(0,lanes.getFirst().u0());assertEquals(1,lanes.getLast().u1());
        for(int i=1;i<16;i++)assertEquals(lanes.get(i-1).u1(),lanes.get(i).u0());
        assertEquals(lanes,FxArtwork.lanes(4.2f,12,0,false));
        assertEquals(15,FxArtwork.lanes(4.2f,4,1,false).size());
        assertTrue(FxArtwork.lanes(4.2f,4,0xffff,false).isEmpty());
        assertTrue(FxArtwork.lanes(3,4,0,true).stream().allMatch(l->l.forward()==0));
    }
    @Test void allFrameCountsRemainReachableWithoutSixteenStepQuantization() {
        for(int count=1;count<=64;count++) {
            var seen=new HashSet<Integer>();
            for(int n=0;n<count;n++) seen.add(FxArtwork.frame((n+.5f)/count,count));
            assertEquals(count,seen.size());
        }
        assertEquals(-1,FxArtwork.frame(1,16));
        assertEquals(-1,FxArtwork.frame(Float.NaN,16));
        var frames=new HashSet<Integer>();
        for(int tick=0;tick<8;tick++) for(float partial:new float[]{0,.5f}) frames.add(FxArtwork.frame((tick+partial)/8,16));
        assertEquals(16,frames.size());
    }
    @Test void runtimeGeometryAndAllSyncedPixelsMatchAuthoredManifest() throws Exception {
        var manifest=JsonParser.parseString(Files.readString(Path.of("art/vfx-2d/manifest.json"))).getAsJsonObject();
        var routes=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/eldencombat/routes/2d.json"))).getAsJsonObject().getAsJsonObject("fx").getAsJsonObject("routes");
        assertEquals(10,routes.size());
        for(var value:manifest.getAsJsonArray("assets")) {
            var asset=value.getAsJsonObject();String id=asset.get("id").getAsString();
            var route=routes.getAsJsonObject(id.equals("gold_wave")?"golden_wave":id);
            var art=FxArtwork.parse(route.getAsJsonObject("artwork"));
            assertEquals(asset.getAsJsonArray("worldSizeBlocks").get(0).getAsFloat(),art.width());
            assertEquals(asset.getAsJsonArray("worldSizeBlocks").get(1).getAsFloat(),art.height());
            assertEquals(asset.getAsJsonArray("anchorNormalized").get(1).getAsFloat(),art.anchorV());
            assertEquals("ready",route.get("status").getAsString());
            for(var f:asset.getAsJsonArray("frames")) {
                String file=f.getAsJsonObject().get("file").getAsString();
                assertArrayEquals(Files.readAllBytes(Path.of("art/vfx-2d",file)),Files.readAllBytes(Path.of("build/resources/main/assets/eldencombat",file)));
            }
        }
    }
}
