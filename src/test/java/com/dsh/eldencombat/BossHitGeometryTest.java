package com.dsh.eldencombat;

import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.geom.Box;
import com.dsh.eldencombat.skeleton.BossHitGeometry;
import com.dsh.eldencombat.skeleton.SkeletonAssets;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossHitGeometryTest {
    @Test void weaponsReachPlayersInFrontAtEveryCardinalHeading() throws Exception {
        var assets = SkeletonAssets.load();
        var config = CombatConfig.defaults();
        for (var entry:config.bossAttacks.entrySet()) {
            var id = entry.getKey();
            var clip = assets.clip(id);
            double range = id.equals("dagger_slash") ? 1.6 : 2.1;
            for (double yaw:new double[]{0,90,180,270}) {
                var p=BossHitGeometry.world(new Vector3d(0,0,-range),10,80,20,yaw);
                Box front=new Box(p.x-.3,80,p.z-.3,p.x+.3,81.8,p.z+.3);
                boolean hit=false;
                for(int tick=clip.hitStart();tick<clip.hitEnd();tick++)
                    hit |= BossHitGeometry.hits(assets,id,tick,entry.getValue(),front,10,80,20,yaw);
                assertTrue(hit,id+" misses front target at yaw="+yaw);
                assertFalse(BossHitGeometry.hits(assets,id,clip.hitStart()-1,entry.getValue(),front,10,80,20,yaw));
                assertFalse(BossHitGeometry.hits(assets,id,clip.hitEnd(),entry.getValue(),front,10,80,20,yaw));
                Box distant=new Box(100,80,100,100.6,81.8,100.6);
                for(int tick=clip.hitStart();tick<clip.hitEnd();tick++)
                    assertFalse(BossHitGeometry.hits(assets,id,tick,entry.getValue(),distant,10,80,20,yaw));
            }
        }
    }
}
