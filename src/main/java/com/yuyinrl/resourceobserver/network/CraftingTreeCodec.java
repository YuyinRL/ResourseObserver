package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.integration.CraftingTreeNode;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link CraftingTreeNode} 的扁平递归 codec。手写而非 StreamCodec，
 * 以便控制递归深度上限，防止恶意/异常树触发栈溢出。
 */
public final class CraftingTreeCodec {

    /** 编码端硬上限：保护客户端解析。 */
    public static final int MAX_DEPTH = 12;
    public static final int MAX_NODES = 512;

    private CraftingTreeCodec() {
    }

    public static void writeOptional(FriendlyByteBuf buf, CraftingTreeNode root) {
        if (root == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        int[] budget = new int[]{MAX_NODES};
        writeNode(buf, root, 0, budget);
    }

    public static CraftingTreeNode readOptional(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        int[] budget = new int[]{MAX_NODES};
        return readNode(buf, 0, budget);
    }

    private static void writeNode(FriendlyByteBuf buf, CraftingTreeNode n, int depth, int[] budget) {
        if (depth >= MAX_DEPTH || budget[0] <= 0) {
            // 写一个 truncated 占位
            writeRaw(buf, CraftingTreeNode.truncatedMarker(), 0);
            return;
        }
        budget[0]--;
        writeRaw(buf, n, n.children() == null ? 0 : Math.min(n.children().size(), 64));
        if (n.children() != null) {
            int max = Math.min(n.children().size(), 64);
            for (int i = 0; i < max; i++) {
                writeNode(buf, n.children().get(i), depth + 1, budget);
            }
        }
    }

    private static void writeRaw(FriendlyByteBuf buf, CraftingTreeNode n, int childCount) {
        PayloadCodecUtils.writeOptionalString(buf, n.itemId(), 256);
        PayloadCodecUtils.writeOptionalString(buf, n.displayName(), 256);
        buf.writeVarLong(Math.max(0L, n.requiredAmount()));
        buf.writeVarLong(Math.max(0L, n.perExecOutAmount()));
        buf.writeVarLong(Math.max(0L, n.timesExecuted()));
        byte flags = 0;
        if (n.isMissing()) flags |= 1;
        if (n.isLoop()) flags |= 2;
        if (n.truncated()) flags |= 4;
        buf.writeByte(flags);
        buf.writeVarInt(childCount);
    }

    private static CraftingTreeNode readNode(FriendlyByteBuf buf, int depth, int[] budget) {
        String itemId = PayloadCodecUtils.readOptionalString(buf, 256);
        String name = PayloadCodecUtils.readOptionalString(buf, 256);
        long required = buf.readVarLong();
        long perExec = buf.readVarLong();
        long times = buf.readVarLong();
        byte flags = buf.readByte();
        boolean missing = (flags & 1) != 0;
        boolean loop = (flags & 2) != 0;
        boolean truncated = (flags & 4) != 0;
        int childCount = Math.min(buf.readVarInt(), 64);
        List<CraftingTreeNode> children;
        if (childCount <= 0 || depth >= MAX_DEPTH) {
            children = List.of();
            // 仍然消费掉子节点字节流以保持解码一致性
            for (int i = 0; i < childCount; i++) {
                readNode(buf, depth + 1, budget);
            }
        } else {
            children = new ArrayList<>(childCount);
            for (int i = 0; i < childCount; i++) {
                if (budget[0] <= 0) {
                    children.add(CraftingTreeNode.truncatedMarker());
                    // 仍然读完
                    readNode(buf, depth + 1, budget);
                    continue;
                }
                budget[0]--;
                children.add(readNode(buf, depth + 1, budget));
            }
        }
        return new CraftingTreeNode(itemId, name, required, perExec, times, missing, loop, truncated, children);
    }
}
