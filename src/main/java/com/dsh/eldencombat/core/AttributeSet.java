package com.dsh.eldencombat.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * 玩家五项属性（《成长路径与手感收口方案》§2.5）。
 *
 * <p>属性加点 UI 不在本轮范围；这里先把"数值契约"落地：命令式加点 + 存档附件，
 * 让锻造/质变/补正这条链先跑通，UI 到位后直接对接。
 *
 * <p>纯数据，不依赖 MC 类型。
 */
public record AttributeSet(int strength, int dexterity, int intelligence, int faith, int arcane) {

    public static final AttributeSet EMPTY = new AttributeSet(0, 0, 0, 0, 0);

    public AttributeSet {
        strength = clamp(strength);
        dexterity = clamp(dexterity);
        intelligence = clamp(intelligence);
        faith = clamp(faith);
        arcane = clamp(arcane);
    }

    public int points(AttributeKind kind) {
        return switch (kind) {
            case STRENGTH -> strength;
            case DEXTERITY -> dexterity;
            case INTELLIGENCE -> intelligence;
            case FAITH -> faith;
            case ARCANE -> arcane;
        };
    }

    public AttributeSet with(AttributeKind kind, int value) {
        int v = clamp(value);
        return switch (kind) {
            case STRENGTH -> new AttributeSet(v, dexterity, intelligence, faith, arcane);
            case DEXTERITY -> new AttributeSet(strength, v, intelligence, faith, arcane);
            case INTELLIGENCE -> new AttributeSet(strength, dexterity, v, faith, arcane);
            case FAITH -> new AttributeSet(strength, dexterity, intelligence, v, arcane);
            case ARCANE -> new AttributeSet(strength, dexterity, intelligence, faith, v);
        };
    }

    public Map<AttributeKind, Integer> asMap() {
        Map<AttributeKind, Integer> map = new EnumMap<>(AttributeKind.class);
        for (AttributeKind kind : AttributeKind.values()) {
            map.put(kind, points(kind));
        }
        return map;
    }

    public int total() {
        return strength + dexterity + intelligence + faith + arcane;
    }

    public String describe() {
        return String.format("力量%d 灵巧%d 智力%d 信仰%d 神秘%d",
                strength, dexterity, intelligence, faith, arcane);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(AttributeKind.MAX_POINTS, value));
    }
}
