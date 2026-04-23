package com.yuyinrl.resourceobserver.web;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Web Dashboard 服务配置 —— 控制内置 HTTP 服务的启用、端口和绑定地址。
 * <p>
 * 默认值：enabled=true，host=127.0.0.1，port=28080。绑定在 127.0.0.1 确保仅本机访问。
 * 如需跨机访问可改为 0.0.0.0，但本模组不实现鉴权，请勿暴露至公网。
 */
public final class WebServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> HOST;
    public static final ModConfigSpec.IntValue PORT;
    public static final ModConfigSpec.BooleanValue CORS_ALLOW_ALL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("web");
        ENABLED = b.comment("是否启用内置 Web Dashboard HTTP 服务（仅在专用/集成服务端启动时生效）")
                .define("enabled", true);
        HOST = b.comment("HTTP 监听地址；默认 127.0.0.1 仅允许本机访问。设为 0.0.0.0 暴露给局域网（无鉴权，请自行评估安全风险）")
                .define("host", "127.0.0.1");
        PORT = b.comment("HTTP 监听端口")
                .defineInRange("port", 28080, 1, 65535);
        CORS_ALLOW_ALL = b.comment("是否对 /api/* 响应返回 Access-Control-Allow-Origin: *（便于前端开发调试）")
                .define("corsAllowAll", true);
        b.pop();
        SPEC = b.build();
    }

    private WebServerConfig() {
    }

    /** 在模组构造阶段注册为 COMMON 配置文件。 */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "resourceobserver-web.toml");
    }
}
