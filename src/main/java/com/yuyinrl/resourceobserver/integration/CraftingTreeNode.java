package com.yuyinrl.resourceobserver.integration;

import java.util.List;

/**
 * 合成树节点 —— 用于 Modern UI / Web UI 在下单 review 与运行中任务点击时
 * 展示完整合成路径。
 *
 * <p>构建语义（保留"每次执行"原始比例，避免反直觉的"1 input → 1 output"）：</p>
 * <ul>
 *   <li>{@code requiredAmount} 是该节点在本次订单中所需的总数量。</li>
 *   <li>对内部节点（非叶子）：{@code perExecOutAmount} 是该样板每执行一次的主产物数量；
 *       {@code timesExecuted} 是该样板需要触发的次数；
 *       {@code requiredAmount = perExecOutAmount × timesExecuted}（理论值，受合并/装配影响可能略大于
 *       上游需求）。子节点的 {@code requiredAmount} = 该输入每次执行所需 × {@code timesExecuted}。</li>
 *   <li>{@code isMissing}：缺失材料的叶子。</li>
 *   <li>{@code isLoop}：检测到循环依赖时截断的占位节点。</li>
 *   <li>{@code truncated}：因深度/节点数限制被截断的占位节点。</li>
 * </ul>
 */
public record CraftingTreeNode(
        String itemId,
        String displayName,
        long requiredAmount,
        long perExecOutAmount,
        long timesExecuted,
        boolean isMissing,
        boolean isLoop,
        boolean truncated,
        List<CraftingTreeNode> children
) {
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    public static CraftingTreeNode leaf(String itemId, String name, long amount, boolean missing) {
        return new CraftingTreeNode(itemId, name, amount, 0L, 0L, missing, false, false, List.of());
    }

    public static CraftingTreeNode loopMarker(String itemId, String name, long amount) {
        return new CraftingTreeNode(itemId, name, amount, 0L, 0L, false, true, false, List.of());
    }

    public static CraftingTreeNode truncatedMarker() {
        return new CraftingTreeNode("resourceobserver:truncated", "…", 0L, 0L, 0L, false, false, true, List.of());
    }
}
