package com.yuyinrl.resourceobserver.service.snapshot.overview;

/**
 * 存储 KPI 状态决策器 —— 纯逻辑判定 AE2 存储健康度。
 *
 * <p>F1.A.4 从 {@code OverviewViewModelMapper#buildStoragePresentation} 提取的
 * 决策树部分，剥离 i18n 调用以保持纯函数。决策结果为 {@link Result}（包含
 * {@link OverviewSnapshot.KpiStatus} 与 {@link Reason}），由消费方按 reason
 * 映射到具体的 hint 翻译键。
 *
 * <p>决策优先级（从高到低）：
 * <ol>
 *     <li>未绑定 AE2 → NEUTRAL / NO_AE2</li>
 *     <li>磁盘 + 外部都不可用 → WARNING / CELLS_UNAVAILABLE</li>
 *     <li>任一通道不可靠 → WARNING / UNRELIABLE</li>
 *     <li>物品/流体类型槽占满 → WARNING / TYPES_FULL</li>
 *     <li>物品/流体字节占用 ≥ {@value #BYTES_HIGH_THRESHOLD} → WARNING / BYTES_HIGH</li>
 *     <li>正常 → POSITIVE / NORMAL</li>
 * </ol>
 */
public final class StorageStatusEvaluator {

    /** 物品 / 流体字节占用进入预警的阈值（90%） */
    public static final double BYTES_HIGH_THRESHOLD = 0.9d;

    /** 决策原因，与具体 i18n 键解耦；调用方按枚举值映射文本 */
    public enum Reason {
        /** 未绑定 AE2 网络 */
        NO_AE2,
        /** 磁盘 + 外部存储均无法读取 */
        CELLS_UNAVAILABLE,
        /** 部分通道数据不可靠 */
        UNRELIABLE,
        /** 物品或流体类型槽已全部占满 */
        TYPES_FULL,
        /** 字节占用率高于阈值 */
        BYTES_HIGH,
        /** 一切正常 */
        NORMAL
    }

    /** 决策结果：状态枚举 + 决策原因 */
    public record Result(OverviewSnapshot.KpiStatus status, Reason reason) {
        public Result {
            if (status == null) {
                throw new IllegalArgumentException("status must not be null");
            }
            if (reason == null) {
                throw new IllegalArgumentException("reason must not be null");
            }
        }
    }

    /** 评估器输入 —— AE2 绑定标志、可用/可靠位、字节/类型用量。 */
    public record Inputs(
            boolean hasAe2Binding,
            boolean diskAvailable,
            boolean diskReliable,
            boolean externalAvailable,
            boolean externalReliable,
            long itemUsedBytes,
            long itemTotalBytes,
            long itemUsedTypes,
            long itemTotalTypes,
            long fluidUsedBytes,
            long fluidTotalBytes,
            long fluidUsedTypes,
            long fluidTotalTypes
    ) {
    }

    private StorageStatusEvaluator() {
    }

    /** 入口：按上文优先级给出 KPI 状态与原因。 */
    public static Result evaluate(Inputs inputs) {
        if (!inputs.hasAe2Binding()) {
            return new Result(OverviewSnapshot.KpiStatus.NEUTRAL, Reason.NO_AE2);
        }

        if (!inputs.diskAvailable() && !inputs.externalAvailable()) {
            return new Result(OverviewSnapshot.KpiStatus.WARNING, Reason.CELLS_UNAVAILABLE);
        }

        if (!inputs.diskReliable() || !inputs.externalReliable()) {
            return new Result(OverviewSnapshot.KpiStatus.WARNING, Reason.UNRELIABLE);
        }

        long itemTotalTypes = inputs.itemTotalTypes();
        long itemUsedTypes = inputs.itemUsedTypes();
        long fluidTotalTypes = inputs.fluidTotalTypes();
        long fluidUsedTypes = inputs.fluidUsedTypes();

        boolean itemTypesFull = itemTotalTypes > 0L && itemUsedTypes >= itemTotalTypes;
        boolean fluidTypesFull = fluidTotalTypes > 0L && fluidUsedTypes >= fluidTotalTypes;
        if (itemTypesFull || fluidTypesFull) {
            return new Result(OverviewSnapshot.KpiStatus.WARNING, Reason.TYPES_FULL);
        }

        long itemTotalBytes = clampTotal(inputs.itemUsedBytes(), inputs.itemTotalBytes());
        long fluidTotalBytes = clampTotal(inputs.fluidUsedBytes(), inputs.fluidTotalBytes());
        double itemFill = itemTotalBytes > 0L
                ? (double) inputs.itemUsedBytes() / (double) itemTotalBytes
                : 0.0d;
        double fluidFill = fluidTotalBytes > 0L
                ? (double) inputs.fluidUsedBytes() / (double) fluidTotalBytes
                : 0.0d;
        if (Math.max(itemFill, fluidFill) >= BYTES_HIGH_THRESHOLD) {
            return new Result(OverviewSnapshot.KpiStatus.WARNING, Reason.BYTES_HIGH);
        }

        return new Result(OverviewSnapshot.KpiStatus.POSITIVE, Reason.NORMAL);
    }

    /**
     * 防止 total &lt; used 导致负数：max(total, used)。
     * 与 buildStoragePresentation 中现有夹逼一致。
     */
    public static long clampTotal(long used, long total) {
        return Math.max(total, used);
    }

    /**
     * 按字节计算填充率（夹逼 used/total，避免 total &lt; used 引入 &gt; 1 的脏数据）。
     * @return 填充比例 [0, 1]；total &lt;= 0 时返回 0
     */
    public static double bytesFillRatio(long used, long total) {
        long clamped = clampTotal(used, total);
        if (clamped <= 0L) {
            return 0.0d;
        }
        return (double) used / (double) clamped;
    }
}
