package com.yuyinrl.resourceobserver.web;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Web Dashboard 服务配置 —— 控制内置 HTTP 服务的启用、端口、绑定地址与鉴权策略。
 * <p>
 * 默认值：enabled=true，host=127.0.0.1，port=28080，auth.mode=TOKEN。
 * 默认绑定 127.0.0.1 + Token 鉴权，以保证开箱即用的安全性。
 * 如需跨机访问可改为 0.0.0.0；如需关闭鉴权回退到旧行为，可设 auth.mode=NONE（不推荐）。
 */
public final class WebServerConfig {

    /** 鉴权模式 —— TOKEN：所有 /api/* 都需要 Bearer Token；NONE：不鉴权（旧行为）。 */
    public enum AuthMode { TOKEN, NONE }

    /** 老存档（无 owner 的 Observer）兼容策略 —— ADMIN_ONLY 仅 OP 可见；PUBLIC 任何登录玩家可见。 */
    public enum LegacyObserverPolicy { ADMIN_ONLY, PUBLIC }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> HOST;
    public static final ModConfigSpec.IntValue PORT;
    public static final ModConfigSpec.BooleanValue CORS_ALLOW_ALL;

    public static final ModConfigSpec.EnumValue<AuthMode> AUTH_MODE;
    public static final ModConfigSpec.EnumValue<LegacyObserverPolicy> LEGACY_OBSERVER_POLICY;
    public static final ModConfigSpec.ConfigValue<String> PUBLIC_URL_BASE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("web");
        ENABLED = b.comment("是否启用内置 Web Dashboard HTTP 服务（仅在专用/集成服务端启动时生效）")
                .define("enabled", true);
        HOST = b.comment("HTTP 监听地址；默认 127.0.0.1 仅允许本机访问。设为 0.0.0.0 暴露给局域网（默认已开启 Token 鉴权）")
                .define("host", "127.0.0.1");
        PORT = b.comment("HTTP 监听端口")
                .defineInRange("port", 28080, 1, 65535);
        CORS_ALLOW_ALL = b.comment("是否对 /api/* 响应返回 Access-Control-Allow-Origin: *（便于前端开发调试）")
                .define("corsAllowAll", true);
        b.pop();

        b.push("auth");
        AUTH_MODE = b.comment(
                "鉴权模式：",
                "  TOKEN —— 默认；所有 /api/observers/** 端点要求 Authorization: Bearer <token>；",
                "           token 由玩家通过游戏内终端 GUI 的「网页访问」按钮获取。",
                "  NONE  —— 关闭鉴权（恢复 v0.1 之前的旧行为，所有人可看全部，不推荐）。"
        ).defineEnum("mode", AuthMode.TOKEN);
        LEGACY_OBSERVER_POLICY = b.comment(
                "老存档兼容策略 —— 升级前放置的 Observer 没有 owner UUID 字段：",
                "  ADMIN_ONLY —— 默认；仅 OP 玩家可见，其它玩家需重新放置 Observer。",
                "  PUBLIC     —— 任何已登录玩家可见（兼容性优先，安全性较低）。"
        ).defineEnum("legacyObserverPolicy", LegacyObserverPolicy.ADMIN_ONLY);
        PUBLIC_URL_BASE = b.comment(
                "对外可访问的 URL 前缀（用于发给玩家的 Web 链接），",
                "格式如 http://example.com:28080 或 https://dash.mc.example.com；",
                "留空时回退到 host:port，host=0.0.0.0/:: 时无法自动推断，建议设置此项。"
        ).define("publicUrlBase", "");
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
