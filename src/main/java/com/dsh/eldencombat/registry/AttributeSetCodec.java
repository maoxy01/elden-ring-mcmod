package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.core.AttributeKind;
import com.dsh.eldencombat.core.AttributeSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@link AttributeSet} 的落盘序列化（§2.5 的接口契约：属性值先随玩家存档，UI 后置）。
 *
 * <p>字段名用属性 key，缺字段按 0 处理：这样以后加"耐力/记忆"这类新属性时，
 * 老存档不会被读坏。
 */
public final class AttributeSetCodec {

    public static final Codec<AttributeSet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("strength", 0).forGetter(AttributeSet::strength),
            Codec.INT.optionalFieldOf("dexterity", 0).forGetter(AttributeSet::dexterity),
            Codec.INT.optionalFieldOf("intelligence", 0).forGetter(AttributeSet::intelligence),
            Codec.INT.optionalFieldOf("faith", 0).forGetter(AttributeSet::faith),
            Codec.INT.optionalFieldOf("arcane", 0).forGetter(AttributeSet::arcane)
    ).apply(instance, AttributeSet::new));

    private AttributeSetCodec() {
    }

    /** 诊断用：把一条属性按 kind 名解析（命令参数用）。 */
    public static AttributeKind parseKind(String name) {
        return AttributeKind.byKey(name);
    }
}
