package com.dsh.eldencombat.skeleton;

import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.geom.Box;
import com.dsh.eldencombat.core.geom.Collision;
import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.Map;

/** Shared, game-independent weapon sampling, also used by regression tests. */
public final class BossHitGeometry {
    private BossHitGeometry() {}

    public static boolean hits(SkeletonAssets assets, String clipId, double tick, CombatConfig.BossAttack attack,
                               Box target, double x, double y, double z, double yaw) {
        var clip = assets.clip(clipId);
        if (tick < clip.hitStart() || tick >= clip.hitEnd()) return false;
        double start = Math.max(clip.hitStart(), tick - 1);
        SkeletonPose previous = SkeletonPose.of(assets.margit(), clip.poseAt((float) start));
        for (int step = 0; step <= 4; step++) {
            var now = SkeletonPose.of(assets.margit(), clip.poseAt((float) (start + (tick - start) * step / 4)));
            if (attack.hammerBox) {
                var hammer = SkeletonPose.of(assets.spectralHammer(), Map.of()).attachTo(now, "spectral_socket");
                Box head = HitVolumes.cubeBox(hammer, "head");
                Box world = worldBox(head, x, y, z, yaw);
                if (overlaps(world, target)) return true;
            } else {
                var a = capsule(assets, clipId, previous, attack.capsuleRadius);
                var b = capsule(assets, clipId, now, attack.capsuleRadius);
                if (Collision.sweptCapsuleHitsBox(world(a.a(), x,y,z,yaw), world(a.b(),x,y,z,yaw),
                        world(b.a(),x,y,z,yaw), world(b.b(),x,y,z,yaw),target,attack.capsuleRadius)) return true;
            }
            previous = now;
        }
        return false;
    }

    private static HitVolumes.Capsule capsule(SkeletonAssets assets, String clip, SkeletonPose pose, double radius) {
        if (!clip.equals("dagger_slash")) return HitVolumes.cubeAxisCapsule(pose,"staff_shaft",radius);
        var socket = pose.boneWorld("spectral_socket");
        return new HitVolumes.Capsule(new Vector3d(socket.transformPosition(new Vector3f(0,4.5f/16,0))),
                new Vector3d(socket.transformPosition(new Vector3f(0,18f/16,0))),radius);
    }

    public static Vector3d world(Vector3d point, double x, double y, double z, double yaw) {
        double angle = Math.toRadians(180-yaw), cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vector3d(x+point.x*cos+point.z*sin,y+point.y,z-point.x*sin+point.z*cos);
    }

    private static Box worldBox(Box b, double x, double y, double z, double yaw) {
        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY), max = new Vector3d(Double.NEGATIVE_INFINITY);
        for (int i=0;i<8;i++) {
            var p = world(new Vector3d((i&1)==0?b.minX():b.maxX(),(i&2)==0?b.minY():b.maxY(),
                    (i&4)==0?b.minZ():b.maxZ()),x,y,z,yaw);
            min.min(p); max.max(p);
        }
        return new Box(min.x,min.y,min.z,max.x,max.y,max.z);
    }
    private static boolean overlaps(Box a, Box b) {
        return a.maxX()>=b.minX()&&a.minX()<=b.maxX()&&a.maxY()>=b.minY()&&a.minY()<=b.maxY()
                &&a.maxZ()>=b.minZ()&&a.minZ()<=b.maxZ();
    }
}
