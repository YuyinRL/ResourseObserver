package com.yuyinrl.resourceobserver.service.snapshot.power;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerOverloadAlertBuilderTest {

    private static PowerSnapshot.DeviceSnapshot dev(String id, PowerSnapshot.AlertLevel level,
                                                    double ratio, long stored, long ept) {
        return new PowerSnapshot.DeviceSnapshot(id, "name-" + id,
                PowerSnapshot.LoadCategory.OTHER, "mod", ept, stored, 1000L, ratio, level);
    }

    @Test
    void emptyInput() {
        assertTrue(PowerOverloadAlertBuilder.build(null).isEmpty());
        assertTrue(PowerOverloadAlertBuilder.build(List.of()).isEmpty());
    }

    @Test
    void normalIsFiltered() {
        var devices = List.of(dev("a", PowerSnapshot.AlertLevel.NORMAL, 0.5, 500L, 100L));
        assertTrue(PowerOverloadAlertBuilder.build(devices).isEmpty());
    }

    @Test
    void warningIsKept() {
        var devices = List.of(dev("a", PowerSnapshot.AlertLevel.WARNING, 0.85, 850L, 100L));
        var alerts = PowerOverloadAlertBuilder.build(devices);
        assertEquals(1, alerts.size());
        assertEquals("a", alerts.get(0).nodeId());
        assertEquals(PowerSnapshot.AlertLevel.WARNING, alerts.get(0).alertLevel());
    }

    @Test
    void criticalCapacityHasLossAndDescription() {
        // usageRatio=1.0 → loss=50%
        var devices = List.of(dev("c", PowerSnapshot.AlertLevel.CRITICAL, 1.0, 1000L, 100L));
        var alerts = PowerOverloadAlertBuilder.build(devices);
        assertEquals(1, alerts.size());
        var a = alerts.get(0);
        assertEquals(50.0, a.throughputLoss(), 1e-9);
        assertEquals(PowerOverloadAlertBuilder.DESCRIPTION_KEY, a.descriptionKey());
        assertEquals(2, a.descriptionArgs().size());
        assertEquals("name-c", a.descriptionArgs().get(0));
        assertEquals("50%", a.descriptionArgs().get(1));
    }

    @Test
    void drainedCriticalLossIs100Percent() {
        // stored=0, ept>0 → loss=100%
        var devices = List.of(dev("d", PowerSnapshot.AlertLevel.CRITICAL, 0.0, 0L, 100L));
        var alerts = PowerOverloadAlertBuilder.build(devices);
        assertEquals(100.0, alerts.get(0).throughputLoss(), 1e-9);
        assertEquals("100%", alerts.get(0).descriptionArgs().get(1));
    }

    @Test
    void multipleAlertsPreserveOrder() {
        var devices = List.of(
                dev("n1", PowerSnapshot.AlertLevel.NORMAL, 0.5, 500L, 0L),
                dev("w", PowerSnapshot.AlertLevel.WARNING, 0.85, 850L, 100L),
                dev("c", PowerSnapshot.AlertLevel.CRITICAL, 1.0, 1000L, 100L)
        );
        var alerts = PowerOverloadAlertBuilder.build(devices);
        assertEquals(2, alerts.size());
        assertEquals("w", alerts.get(0).nodeId());
        assertEquals("c", alerts.get(1).nodeId());
    }
}
