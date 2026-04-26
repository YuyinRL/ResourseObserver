package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.integration.CraftingTreeNode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：合成计划摘要。
 * <p>
 * 客户端收到此包后弹出"审阅 → 确认"对话框；包含 {@code planId} 时表示计划已缓存于服务端，
 * 客户端可发送 {@code CONFIRM_CRAFT_ORDER}（actionValue=planId）来正式提交；
 * {@code planId} 为空时仅展示信息（如计算失败、缺料模拟）。
 */
public record CraftingPlanResultPayload(
        String status,
        String message,
        String planId,
        boolean simulation,
        long bytes,
        String finalOutputItemId,
        String finalOutputDisplayName,
        long finalOutputAmount,
        List<String> usedItems,
        List<String> missingItems,
        List<String> emittedItems,
        // 每条 [outputItemId, displayName, timesStr] 三连，描述本次计划用到的样板及次数
        List<String> patternTimes,
        // 每条 [name, storageBytesStr, coProcStr, busyStr("0"/"1")] 四连，描述网络中的所有 CPU
        List<String> cpus,
        // 合成树标识（与 TREE_CACHE 对齐）；空表示无树
        String treeId,
        // 内联的合成树（review 阶段直接展示）；可能为 null
        CraftingTreeNode treeRoot
) implements CustomPacketPayload {

    public static final Type<CraftingPlanResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "crafting_plan_result"));

    public static final StreamCodec<FriendlyByteBuf, CraftingPlanResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CraftingPlanResultPayload decode(FriendlyByteBuf buf) {
            String status = PayloadCodecUtils.readOptionalString(buf, 64);
            String message = PayloadCodecUtils.readOptionalString(buf, 256);
            String planId = PayloadCodecUtils.readOptionalString(buf, 64);
            boolean simulation = buf.readBoolean();
            long bytes = buf.readVarLong();
            String outId = PayloadCodecUtils.readOptionalString(buf, 256);
            String outName = PayloadCodecUtils.readOptionalString(buf, 256);
            long outAmt = buf.readVarLong();
            List<String> used = readStackList(buf);
            List<String> missing = readStackList(buf);
            List<String> emitted = readStackList(buf);
            List<String> patternTimes = readStackList(buf);
            List<String> cpus = readCpuList(buf);
            String treeId = PayloadCodecUtils.readOptionalString(buf, 64);
            CraftingTreeNode treeRoot = CraftingTreeCodec.readOptional(buf);
            return new CraftingPlanResultPayload(status, message, planId, simulation, bytes,
                    outId, outName, outAmt, used, missing, emitted, patternTimes, cpus, treeId, treeRoot);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CraftingPlanResultPayload p) {
            PayloadCodecUtils.writeOptionalString(buf, p.status(), 64);
            PayloadCodecUtils.writeOptionalString(buf, p.message(), 256);
            PayloadCodecUtils.writeOptionalString(buf, p.planId(), 64);
            buf.writeBoolean(p.simulation());
            buf.writeVarLong(p.bytes());
            PayloadCodecUtils.writeOptionalString(buf, p.finalOutputItemId(), 256);
            PayloadCodecUtils.writeOptionalString(buf, p.finalOutputDisplayName(), 256);
            buf.writeVarLong(p.finalOutputAmount());
            writeStackList(buf, p.usedItems());
            writeStackList(buf, p.missingItems());
            writeStackList(buf, p.emittedItems());
            writeStackList(buf, p.patternTimes());
            writeCpuList(buf, p.cpus());
            PayloadCodecUtils.writeOptionalString(buf, p.treeId(), 64);
            CraftingTreeCodec.writeOptional(buf, p.treeRoot());
        }
    };

    /**
     * 摘要列表用 ["itemId", "displayName", "amountStr"] 三连方式扁平存储，避免新增嵌套类型。
     */
    private static List<String> readStackList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n <= 0) return List.of();
        List<String> out = new ArrayList<>(n * 3);
        for (int i = 0; i < n; i++) {
            out.add(PayloadCodecUtils.readOptionalString(buf, 256));
            out.add(PayloadCodecUtils.readOptionalString(buf, 256));
            out.add(Long.toString(buf.readVarLong()));
        }
        return out;
    }

    private static void writeStackList(FriendlyByteBuf buf, List<String> list) {
        int n = list == null ? 0 : list.size() / 3;
        buf.writeVarInt(n);
        if (list == null) return;
        for (int i = 0; i + 2 < list.size(); i += 3) {
            PayloadCodecUtils.writeOptionalString(buf, list.get(i), 256);
            PayloadCodecUtils.writeOptionalString(buf, list.get(i + 1), 256);
            long amt;
            try {
                amt = Long.parseLong(list.get(i + 2));
            } catch (NumberFormatException e) {
                amt = 0L;
            }
            buf.writeVarLong(amt);
        }
    }

    /** CPU 列表：每条 [name, storageBytes, coProcessors, busy("0"/"1")]。 */
    private static List<String> readCpuList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n <= 0) return List.of();
        List<String> out = new ArrayList<>(n * 4);
        for (int i = 0; i < n; i++) {
            out.add(PayloadCodecUtils.readOptionalString(buf, 128));
            out.add(Long.toString(buf.readVarLong()));
            out.add(Integer.toString(buf.readVarInt()));
            out.add(buf.readBoolean() ? "1" : "0");
        }
        return out;
    }

    private static void writeCpuList(FriendlyByteBuf buf, List<String> list) {
        int n = list == null ? 0 : list.size() / 4;
        buf.writeVarInt(n);
        if (list == null) return;
        for (int i = 0; i + 3 < list.size(); i += 4) {
            PayloadCodecUtils.writeOptionalString(buf, list.get(i), 128);
            long sb;
            int co;
            try { sb = Long.parseLong(list.get(i + 1)); } catch (NumberFormatException e) { sb = 0L; }
            try { co = Integer.parseInt(list.get(i + 2)); } catch (NumberFormatException e) { co = 0; }
            buf.writeVarLong(sb);
            buf.writeVarInt(co);
            buf.writeBoolean("1".equals(list.get(i + 3)));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
