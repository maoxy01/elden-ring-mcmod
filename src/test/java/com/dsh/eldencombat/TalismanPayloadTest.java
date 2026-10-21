package com.dsh.eldencombat;

import com.dsh.eldencombat.core.TalismanEffects;
import com.dsh.eldencombat.core.TalismanSpec;
import com.dsh.eldencombat.network.TalismanDataPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TalismanPayloadTest {
    @Test void everyScopedEffectSurvivesTheActualNetworkCodec() {
        var spec = new TalismanSpec("eldencombat:probe", "测试", "group", 3, .2f, .3f, .4f, 5,
                new TalismanEffects(.1f, .2f, .02f, .15f, 3.5555556f, 5, 10, 5, 6));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var value = new TalismanDataPayload(List.of(spec));
            TalismanDataPayload.STREAM_CODEC.encode(buf, value);
            assertEquals(value, TalismanDataPayload.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void corruptCountsAreRejectedBeforeReadingEntries() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buf.writeVarInt(TalismanDataPayload.MAX_ENTRIES + 1);
            assertThrows(IllegalArgumentException.class, () -> TalismanDataPayload.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }
}
