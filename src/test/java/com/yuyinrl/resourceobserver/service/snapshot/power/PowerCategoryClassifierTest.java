package com.yuyinrl.resourceobserver.service.snapshot.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerCategoryClassifierTest {

    @Test
    void mining() {
        assertEquals(PowerSnapshot.LoadCategory.MINING,
                PowerCategoryClassifier.inferCategory("modid:miner_basic"));
        assertEquals(PowerSnapshot.LoadCategory.MINING,
                PowerCategoryClassifier.inferCategory("mod:quarry"));
        assertEquals(PowerSnapshot.LoadCategory.MINING,
                PowerCategoryClassifier.inferCategory("mod:rotary_drill"));
        assertEquals(PowerSnapshot.LoadCategory.MINING,
                PowerCategoryClassifier.inferCategory("mod:water_pump"));
        assertEquals(PowerSnapshot.LoadCategory.MINING,
                PowerCategoryClassifier.inferCategory("mod:excavator"));
    }

    @Test
    void assembly() {
        assertEquals(PowerSnapshot.LoadCategory.ASSEMBLY,
                PowerCategoryClassifier.inferCategory("ae2:molecular_assembler"));
        assertEquals(PowerSnapshot.LoadCategory.ASSEMBLY,
                PowerCategoryClassifier.inferCategory("ae2:inscriber"));
        assertEquals(PowerSnapshot.LoadCategory.ASSEMBLY,
                PowerCategoryClassifier.inferCategory("mod:furnace"));
        assertEquals(PowerSnapshot.LoadCategory.ASSEMBLY,
                PowerCategoryClassifier.inferCategory("mod:industrial_grinder"));
        assertEquals(PowerSnapshot.LoadCategory.ASSEMBLY,
                PowerCategoryClassifier.inferCategory("mod:auto_machine"));
    }

    @Test
    void logistics() {
        assertEquals(PowerSnapshot.LoadCategory.LOGISTICS,
                PowerCategoryClassifier.inferCategory("ae2:export_bus"));
        assertEquals(PowerSnapshot.LoadCategory.LOGISTICS,
                PowerCategoryClassifier.inferCategory("ae2:interface"));
        assertEquals(PowerSnapshot.LoadCategory.LOGISTICS,
                PowerCategoryClassifier.inferCategory("mod:fluid_pipe"));
        assertEquals(PowerSnapshot.LoadCategory.LOGISTICS,
                PowerCategoryClassifier.inferCategory("mod:item_router"));
    }

    @Test
    void otherDefaults() {
        assertEquals(PowerSnapshot.LoadCategory.OTHER,
                PowerCategoryClassifier.inferCategory("mod:cable"));
        assertEquals(PowerSnapshot.LoadCategory.OTHER,
                PowerCategoryClassifier.inferCategory("mod:battery"));
        assertEquals(PowerSnapshot.LoadCategory.OTHER,
                PowerCategoryClassifier.inferCategory(null));
        assertEquals(PowerSnapshot.LoadCategory.OTHER,
                PowerCategoryClassifier.inferCategory(""));
    }

    @Test
    void caseInsensitive() {
        assertEquals(PowerSnapshot.LoadCategory.MINING,
                PowerCategoryClassifier.inferCategory("Mod:MINER"));
        assertEquals(PowerSnapshot.LoadCategory.ASSEMBLY,
                PowerCategoryClassifier.inferCategory("Mod:Assembler"));
    }

    @Test
    void translationKeys() {
        assertEquals("screen.resourceobserver.power.category.mining",
                PowerCategoryClassifier.translationKey(PowerSnapshot.LoadCategory.MINING));
        assertEquals("screen.resourceobserver.power.category.assembly",
                PowerCategoryClassifier.translationKey(PowerSnapshot.LoadCategory.ASSEMBLY));
        assertEquals("screen.resourceobserver.power.category.logistics",
                PowerCategoryClassifier.translationKey(PowerSnapshot.LoadCategory.LOGISTICS));
        assertEquals("screen.resourceobserver.power.category.other",
                PowerCategoryClassifier.translationKey(PowerSnapshot.LoadCategory.OTHER));
    }

    @Test
    void extractModNamespace() {
        assertEquals("ae2", PowerCategoryClassifier.extractModNamespace("ae2:controller"));
        assertEquals("flux", PowerCategoryClassifier.extractModNamespace("flux:plug"));
        assertEquals("", PowerCategoryClassifier.extractModNamespace(""));
        assertEquals("", PowerCategoryClassifier.extractModNamespace(null));
        assertEquals("noColon", PowerCategoryClassifier.extractModNamespace("noColon"));
        // 注意：lead colon → indexOf=0 → 走 fallback 整串
        assertEquals(":leading", PowerCategoryClassifier.extractModNamespace(":leading"));
    }
}
