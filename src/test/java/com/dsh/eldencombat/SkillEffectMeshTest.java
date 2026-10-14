package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.dsh.eldencombat.effects.SkillEffectMesh.Kind;
import com.dsh.eldencombat.effects.WaveFront;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SkillEffectMeshTest {
    @Test void effectsHoldTheirBrightnessThenDissolveWithoutAnOpaqueTail() {
        assertEquals(.25f, SkillEffectMesh.opacity(0));
        assertEquals(1, SkillEffectMesh.opacity(.1f));
        assertEquals(1, SkillEffectMesh.opacity(.6f));
        assertTrue(SkillEffectMesh.opacity(.9f) < SkillEffectMesh.opacity(.8f));
        assertTrue(SkillEffectMesh.opacity(.999f) < .001f);
        assertEquals(0, SkillEffectMesh.opacity(1));
        assertEquals(0, SkillEffectMesh.opacity(Float.NaN));
    }

    @Test void everyMeshIsFiniteBoundedAndMadeOfNondegenerateBoxes() {
        for (Kind kind : Kind.values()) for (int phase = 0; phase < 3; phase++)
            for (float radius : new float[]{.25f, 1, 3, 6, Float.NaN, Float.POSITIVE_INFINITY})
                for (float p : new float[]{0, .1f, .5f, .99f}) {
                    var mesh = SkillEffectMesh.build(kind, radius, p, phase);
                    if (kind == Kind.NONE) assertTrue(mesh.isEmpty());
                    else assertFalse(mesh.isEmpty(), kind.toString());
                    assertTrue(mesh.size() <= SkillEffectMesh.MAX_BOXES);
                    for (var b : mesh) {
                        String context = kind + " radius=" + radius + " progress=" + p + " box=" + b;
                        assertTrue(b.x1() > b.x0(), context); assertTrue(b.y1() > b.y0(), context); assertTrue(b.z1() > b.z0(), context);
                        for (float coordinate : new float[]{b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1()}) {
                            assertTrue(Float.isFinite(coordinate)); assertTrue(Math.abs(coordinate) < 7);
                        }
                        assertTrue(b.alpha() > 0 && b.alpha() <= 1);
                    }
                }
    }

    @Test void lifespanAndInvalidInputsNeverLeaveAnImmortalMesh() {
        assertEquals(1, SkillEffectMesh.progress(Float.NaN, 20));
        assertEquals(1, SkillEffectMesh.progress(40, Integer.MAX_VALUE));
        assertEquals(0, SkillEffectMesh.progress(-5, 10));
        assertEquals(.5, SkillEffectMesh.progress(10, 20));
        for (Kind kind : Kind.values()) {
            assertTrue(SkillEffectMesh.build(kind, 3, 1, 0).isEmpty());
            assertTrue(SkillEffectMesh.build(kind, 3, Float.NaN, 0).isEmpty());
            assertTrue(SkillEffectMesh.build(kind, 3, Float.POSITIVE_INFINITY, 0).isEmpty());
        }
    }

    @Test void ritualHasFourTouchingSquareSidesAndStageDependentUpperFrames() {
        var first = SkillEffectMesh.build(Kind.BLOOD_RITUAL, 4, .2f, 0);
        var third = SkillEffectMesh.build(Kind.BLOOD_RITUAL, 4, .2f, 2);
        assertEquals(first.size() + 8, third.size());
        var north = first.get(0); var south = first.get(1); var west = first.get(2); var east = first.get(3);
        assertEquals(north.x0(), west.x0()); assertEquals(north.x1(), east.x1());
        assertEquals(north.z1(), west.z0()); assertEquals(north.z1(), east.z0());
        assertEquals(south.z0(), west.z1()); assertEquals(south.z0(), east.z1());
        assertEquals(south.x0(), west.x0()); assertEquals(south.x1(), east.x1());
        assertTrue(third.stream().anyMatch(b -> b.y1() > 4), "仪式有真正的立体光柱，不是地面粒子");
    }

    @Test void allEffectsHaveUniqueGeometryAndStableWireIds() {
        var ids = new HashSet<Integer>(); var meshes = new HashSet<Object>();
        for (Kind kind : Kind.values()) {
            assertTrue(ids.add(kind.wireId())); assertSame(kind, Kind.fromWire(kind.wireId()));
            assertTrue(meshes.add(SkillEffectMesh.build(kind, 3, .3f, 0)), kind.toString());
        }
        assertSame(Kind.NONE, Kind.fromWire(-1));
        assertSame(Kind.NONE, Kind.fromWire(10));
        assertTrue(SkillEffectMesh.build(Kind.fromWire(10), 3, .3f, 0).isEmpty());
    }

    @Test void groundWaveMatchesSyncedRadiusAndSharedTrailingProfile() {
        for (Kind kind : new Kind[]{Kind.GOLD_WAVE, Kind.FLAME_FRONT}) for (float r : new float[]{1, 3, 4.2f}) {
            var mesh = SkillEffectMesh.build(kind, r, .2f, 0);
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            for (var b : mesh) {
                minX = Math.min(minX, b.x0()); maxX = Math.max(maxX, b.x1());
                double trailing = kind == Kind.GOLD_WAVE ? WaveFront.lag(1, .2f * 16) : .45;
                assertTrue(b.z0() >= -trailing - .36 && b.z1() <= .8f);
            }
            assertEquals(-r, minX, .001); assertEquals(r, maxX, .006);
        }
    }

    @Test void goldenWaveMiddleRunsAheadOnlyWhileForming() {
        assertEquals(1, WaveFront.speedFactor(0));
        assertEquals(.55, WaveFront.speedFactor(1), 1e-9);
        for (double distance : new double[]{1, 4, 8, 16}) {
            double previous = -1;
            for (int i = 0; i <= 10; i++) {
                double lag = WaveFront.lag(i / 10.0, distance);
                assertTrue(lag >= previous); previous = lag;
                assertEquals(lag, WaveFront.lag(-i / 10.0, distance));
                assertEquals(Math.rint(lag / WaveFront.GRID), lag / WaveFront.GRID);
            }
        }
        double centerAdvance = 3 - WaveFront.lag(0, 3) - (1 - WaveFront.lag(0, 1));
        double edgeAdvance = 3 - WaveFront.lag(1, 3) - (1 - WaveFront.lag(1, 1));
        assertTrue(edgeAdvance < centerAdvance && edgeAdvance > 0);
        assertEquals(0, WaveFront.lag(1, Double.NaN));
    }

    @Test void formedGoldenWaveHasFixedOffsetsAndEveryLaneMovesAtCenterSpeed() {
        double formed = WaveFront.FORMATION_DISTANCE;
        for (int lane = 0; lane < WaveFront.LANES; lane++) {
            double offset = WaveFront.laneLag(lane, formed);
            for (double distance : new double[]{formed, formed + .9, 8, 12, 16}) {
                assertEquals(offset, WaveFront.laneLag(lane, distance));
                double advance = distance + .9 - WaveFront.laneLag(lane, distance + .9)
                        - (distance - WaveFront.laneLag(lane, distance));
                assertEquals(.9, advance, 1e-9);
            }
        }
        assertEquals(SkillEffectMesh.build(Kind.GOLD_WAVE, 4.2f, .12f, 0, (float) formed, 0),
                SkillEffectMesh.build(Kind.GOLD_WAVE, 4.2f, .12f, 0, 16, 0));
    }

    @Test void goldenWaveWidthAlsoStopsExpandingAfterFormation() {
        assertEquals(3, WaveFront.radius(3, 4.2, 0));
        assertEquals(3.6, WaveFront.radius(3, 4.2, WaveFront.FORMATION_DISTANCE / 2), 1e-9);
        for (double distance : new double[]{WaveFront.FORMATION_DISTANCE, 8, 12, 16})
            assertEquals(4.2, WaveFront.radius(3, 4.2, distance));
        assertEquals(3, WaveFront.radius(3, 4.2, Double.NaN));
        assertEquals(3, WaveFront.radius(3, 3, 16));
    }

    @Test void renderedLanesUseTheSameFrontAsHitsAndBlockedLanesDisappear() {
        float radius = 3, distance = 12;
        var mesh = SkillEffectMesh.build(Kind.GOLD_WAVE, radius, .2f, 0, distance, 0);
        for (int lane = 0; lane < WaveFront.LANES; lane++) {
            double u = WaveFront.laneCenter(lane);
            assertEquals(lane, WaveFront.lane(u * radius, radius));
            float edge = -(float) WaveFront.laneLag(lane, distance);
            float lateral = (float) (u * radius);
            assertTrue(mesh.stream().anyMatch(b -> b.x0() <= lateral && b.x1() >= lateral
                    && Math.abs(b.z0() - (edge - .1f)) < .001), "visible lane=" + lane);
        }
        assertTrue(SkillEffectMesh.build(Kind.GOLD_WAVE, radius, .2f, 0, distance, 0xffff).isEmpty());
        assertTrue(SkillEffectMesh.build(Kind.GOLD_WAVE, radius, .2f, 0, distance, 1).size() < mesh.size());
    }

    @Test void groundDebrisIsBoundedAndVisualOnly() {
        for (Kind kind : Kind.values()) for (float p : new float[]{0,.2f,.5f,.99f,1,Float.NaN}) {
            var pieces = SkillEffectMesh.debris(kind, 3, p);
            if (kind != Kind.GOLD_IMPACT && kind != Kind.GRAVITY_IMPACT && kind != Kind.GRAVITY_PULL || p >= 1 || !Float.isFinite(p)) {
                assertTrue(pieces.isEmpty()); continue;
            }
            assertTrue(pieces.size() >= 10 && pieces.size() <= 20);
            for (var b : pieces) {
                assertTrue(b.x1()>b.x0()&&b.y1()>b.y0()&&b.z1()>b.z0());
                assertTrue(b.y0()>=0&&b.y1()<2);
            }
        }
    }

    @Test void impactsHaveAsymmetricBlockRimsAndDifferentPropagation() {
        var gold = SkillEffectMesh.build(Kind.GOLD_IMPACT, 3, .2f, 0);
        assertNotEquals(gold, SkillEffectMesh.build(Kind.GRAVITY_IMPACT, 3, .2f, 0));
        assertEquals(gold, SkillEffectMesh.build(Kind.GOLD_IMPACT, 3, .2f, 0));
        var rim = gold.stream().filter(b -> b.rgb() == 0x33343C).toList();
        assertTrue(rim.size() >= 20);
        assertTrue(rim.stream().anyMatch(b -> rim.stream().noneMatch(other ->
                Math.abs((b.x0()+b.x1())/2 + (other.x0()+other.x1())/2) < .001
                        && Math.abs((b.z0()+b.z1())/2 - (other.z0()+other.z1())/2) < .001)));
        var early = SkillEffectMesh.debris(Kind.GOLD_IMPACT, 3, .1f).get(0);
        var late = SkillEffectMesh.debris(Kind.GOLD_IMPACT, 3, .5f).get(0);
        assertTrue(Math.hypot(late.x0()+late.x1(),late.z0()+late.z1()) > Math.hypot(early.x0()+early.x1(),early.z0()+early.z1()));
        var pullEarly = SkillEffectMesh.debris(Kind.GRAVITY_PULL, 3, .1f).get(0);
        var pullLate = SkillEffectMesh.debris(Kind.GRAVITY_PULL, 3, .5f).get(0);
        assertTrue(Math.hypot(pullLate.x0()+pullLate.x1(),pullLate.z0()+pullLate.z1()) < Math.hypot(pullEarly.x0()+pullEarly.x1(),pullEarly.z0()+pullEarly.z1()));
    }
}
