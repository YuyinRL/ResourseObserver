package com.yuyinrl.resourceobserver.world.block.entity;

import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Flux Networks 采样数据容器。
 * <p>
 * 与 {@link Ae2DataStore} 对称：集中管理每个 Flux 网络的最近一次采样结果，
 * 仅向调用方暴露 {@link #record}、{@link #get}、{@link #remove}、{@link #clearAll}
 * 等领域方法，外部不能直接持有内部 Map 引用。
 */
public class FluxDataStore {

    private final Map<String, FluxNetworksIntegration.FluxSampleResult> latest = new HashMap<>();

    /** 记录指定网络的最新一次 Flux 采样结果。 */
    public void record(String networkId, FluxNetworksIntegration.FluxSampleResult result) {
        latest.put(networkId, result);
    }

    /** 读取指定网络最近一次 Flux 采样结果；不存在时返回 {@code null}。 */
    @Nullable
    public FluxNetworksIntegration.FluxSampleResult get(String networkId) {
        return latest.get(networkId);
    }

    /** 移除指定网络的采样结果（绑定切换/解绑时调用）。 */
    public void remove(String networkId) {
        latest.remove(networkId);
    }

    /** 清空全部采样结果（卸载/重置时调用）。 */
    public void clearAll() {
        latest.clear();
    }
}
