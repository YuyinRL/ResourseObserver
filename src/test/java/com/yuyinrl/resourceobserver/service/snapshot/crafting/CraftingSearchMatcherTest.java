package com.yuyinrl.resourceobserver.service.snapshot.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CraftingSearchMatcherTest {

    @Test
    void emptyQueryMatchesAnything() {
        assertTrue(CraftingSearchMatcher.PLAIN.matches("anything", ""));
        assertTrue(CraftingSearchMatcher.PLAIN.matches("anything", null));
        assertTrue(CraftingSearchMatcher.PLAIN.matches(null, ""));
    }

    @Test
    void caseInsensitiveSubstring() {
        assertTrue(CraftingSearchMatcher.PLAIN.matches("Iron Ingot", "iron"));
        assertTrue(CraftingSearchMatcher.PLAIN.matches("iron ingot", "INGOT"));
        assertTrue(CraftingSearchMatcher.PLAIN.matches("Diamond", "amo"));
    }

    @Test
    void noMatch() {
        assertFalse(CraftingSearchMatcher.PLAIN.matches("Diamond", "iron"));
    }

    @Test
    void nullTextWithQueryNoMatch() {
        assertFalse(CraftingSearchMatcher.PLAIN.matches(null, "x"));
    }
}

class CraftingLocalizerTest {

    @Test
    void identityPrefersDisplayName() {
        assertEquals("DisplayName", CraftingLocalizer.IDENTITY.localizeItem("mod:item", "DisplayName"));
    }

    @Test
    void identityFallsBackToItemId() {
        assertEquals("mod:item", CraftingLocalizer.IDENTITY.localizeItem("mod:item", null));
        assertEquals("mod:item", CraftingLocalizer.IDENTITY.localizeItem("mod:item", ""));
        assertEquals("mod:item", CraftingLocalizer.IDENTITY.localizeItem("mod:item", "   "));
    }

    @Test
    void identityEmptyAllNull() {
        assertEquals("", CraftingLocalizer.IDENTITY.localizeItem(null, null));
    }
}
