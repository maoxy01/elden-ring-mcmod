package com.dsh.eldencombat.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RollChargeStateTest {
    @Test void oneChargeAndFourSecondRecharge() {
        var state=new RollChargeState();
        state.tick(1,1,80);
        assertTrue(state.start(100,8,80));
        assertFalse(state.start(100,8,80));
        assertTrue(state.rolling(108));
        assertFalse(state.rolling(109));
        for(int i=0;i<79;i++) state.tick(1,1,80);
        assertFalse(state.canStart(179));
        state.tick(1,1,80);
        assertTrue(state.canStart(180));
    }
    @Test void attributesChangeCapacityAndRechargeWithoutExtraPayment() {
        var state=new RollChargeState();
        state.tick(2,1,80);
        for(int i=0;i<40;i++) state.tick(2,2,80);
        assertEquals(2,state.available());
        assertTrue(state.start(1,8,80));
        assertFalse(state.start(2,8,80));
        assertTrue(state.start(10,8,80));
        assertEquals(0,state.available());
        state.tick(0,1,80);
        assertEquals(0,state.available());
    }
    @Test void fractionalRechargeAndCapacityClamp() {
        var state=new RollChargeState();
        state.tick(1,1,80);
        state.start(0,8,80);
        for(int i=0;i<159;i++)state.tick(1,.5f,80);
        assertEquals(0,state.available());
        state.tick(1,.5f,80);
        assertEquals(1,state.available());
        state.tick(0,1,80);
        assertEquals(0,state.available());
    }
    @Test void newSessionDoesNotInheritOldRollingWindowOrCharges() {
        var old=new RollChargeState(); old.tick(1,1,80); old.start(200,8,80);
        var fresh=new RollChargeState();
        assertFalse(fresh.rolling(0));
        assertFalse(fresh.canStart(0));
        fresh.tick(1,1,80);
        assertTrue(fresh.canStart(0));
    }
}
