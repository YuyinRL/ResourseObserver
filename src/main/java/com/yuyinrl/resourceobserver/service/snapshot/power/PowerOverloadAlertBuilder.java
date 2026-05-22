package com.yuyinrl.resourceobserver.service.snapshot.power;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 过载告警构建器 —— 由 {@link PowerSnapshot.DeviceSnapshot} 列表生成
 * {@link PowerSnapshot.OverloadAlertSnapshot} 列表。
 *
 * <p>仅过滤非 NORMAL 的设备；告警描述以"翻译键 + 参数列表"形态输出，
 * 由消费者负责本地化（避免在 service 层依赖 MC i18n）。
 */
public final class PowerOverloadAlertBuilder {

    public static final String DESCRIPTION_KEY = "screen.resourceobserver.power.alert.description";

    private PowerOverloadAlertBuilder() {
    }

    public static List<PowerSnapshot.OverloadAlertSnapshot> build(List<PowerSnapshot.DeviceSnapshot> devices) {
        if (devices == null || devices.isEmpty()) return List.of();

        List<PowerSnapshot.OverloadAlertSnapshot> alerts = new ArrayList<>();
        for (PowerSnapshot.DeviceSnapshot d : devices) {
            if (d.alertLevel() == PowerSnapshot.AlertLevel.NORMAL) continue;

            double loss = PowerAlertEvaluator.computeThroughputLoss(
                    d.usageRatio(), d.storedEnergy(), d.energyPerTick()
            );
            String lossText = String.format(Locale.ROOT, "%.0f%%", loss);

            alerts.add(new PowerSnapshot.OverloadAlertSnapshot(
                    d.nodeId(),
                    d.displayName(),
                    d.alertLevel(),
                    loss,
                    DESCRIPTION_KEY,
                    List.of(d.displayName(), lossText)
            ));
        }
        return alerts;
    }
}
