package com.yuyinrl.resourceobserver.web.auth;

import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Web 请求鉴权上下文 —— 由 {@link AuthFilter} 注入到 {@link HttpExchange} 的属性中，
 * 各 handler 通过 {@link #from(HttpExchange)} 获取并据此过滤可见的 Observer。
 * <p>
 * 当配置 {@code auth.mode=NONE} 时仍会注入一个 admin=true 的"匿名管理员"上下文，
 * 这样下游 handler 不需要对鉴权模式分支处理。
 *
 * @param viewer 玩家 UUID；{@code null} 表示匿名（仅在 NONE 模式下出现）
 * @param viewerName 玩家名（用于日志/whoami），匿名时为 {@code "anonymous"}
 * @param admin 是否拥有管理员权限（OP 玩家或匿名管理员）
 */
public record AuthContext(@Nullable UUID viewer, String viewerName, boolean admin) {

    /** HttpExchange 属性键 —— AuthFilter 写入、handler 读取。 */
    public static final String ATTRIBUTE_KEY = "resourceobserver.auth";

    /** 读取 handler 链上注入的上下文；若鉴权未启用或未通过则返回 null。 */
    public static @Nullable AuthContext from(HttpExchange ex) {
        Object v = ex.getAttribute(ATTRIBUTE_KEY);
        return v instanceof AuthContext ctx ? ctx : null;
    }

    /** 匿名管理员上下文 —— 仅当 auth.mode=NONE 时使用。 */
    public static AuthContext anonymousAdmin() {
        return new AuthContext(null, "anonymous", true);
    }
}
