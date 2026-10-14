package com.dsh.eldencombat.core;

/** 武器类别。首版用原版铁剑 / 钻石剑 / 盾牌作为两种武器与盾牌的临时载体。 */
public enum WeaponClass {
    LIGHT_SWORD("light_sword"),
    GREAT_SWORD("great_sword"),
    NONE("none");

    private final String key;

    WeaponClass(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static WeaponClass byKey(String key) {
        for (WeaponClass value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return NONE;
    }
}
