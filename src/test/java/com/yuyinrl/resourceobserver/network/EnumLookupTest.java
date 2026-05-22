package com.yuyinrl.resourceobserver.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnumLookup 单元测试 —— 覆盖 fromId / fromKey 查找与默认值回退。
 */
class EnumLookupTest {

    enum Color {
        RED(1, "red"),
        GREEN(2, "green"),
        BLUE(3, "blue");

        private final int id;
        private final String key;

        Color(int id, String key) {
            this.id = id;
            this.key = key;
        }

        int id() {
            return id;
        }

        String key() {
            return key;
        }
    }

    @Test
    void fromIdReturnsMatchingValue() {
        assertEquals(Color.RED, EnumLookup.fromId(Color.values(), Color::id, 1, Color.BLUE));
        assertEquals(Color.GREEN, EnumLookup.fromId(Color.values(), Color::id, 2, Color.BLUE));
    }

    @Test
    void fromIdReturnsDefaultWhenMissing() {
        assertEquals(Color.BLUE, EnumLookup.fromId(Color.values(), Color::id, 99, Color.BLUE));
    }

    @Test
    void fromKeyMatchesIgnoreCase() {
        assertEquals(Color.RED, EnumLookup.fromKey(Color.values(), Color::key, "red", Color.BLUE));
        assertEquals(Color.RED, EnumLookup.fromKey(Color.values(), Color::key, "RED", Color.BLUE));
        assertEquals(Color.RED, EnumLookup.fromKey(Color.values(), Color::key, "Red", Color.BLUE));
    }

    @Test
    void fromKeyReturnsDefaultForNullOrBlank() {
        assertEquals(Color.BLUE, EnumLookup.fromKey(Color.values(), Color::key, null, Color.BLUE));
        assertEquals(Color.BLUE, EnumLookup.fromKey(Color.values(), Color::key, "", Color.BLUE));
        assertEquals(Color.BLUE, EnumLookup.fromKey(Color.values(), Color::key, "   ", Color.BLUE));
    }

    @Test
    void fromKeyReturnsDefaultWhenUnknown() {
        assertEquals(Color.BLUE, EnumLookup.fromKey(Color.values(), Color::key, "purple", Color.BLUE));
    }
}
