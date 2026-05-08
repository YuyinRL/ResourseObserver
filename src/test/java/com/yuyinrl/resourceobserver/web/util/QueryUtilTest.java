package com.yuyinrl.resourceobserver.web.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryUtil 单元测试 —— 覆盖 query string 解析与 URL 解码边界情形。
 */
class QueryUtilTest {

    @Test
    void parsesNullAndEmptyAsEmptyMap() {
        assertTrue(QueryUtil.parseQuery(null).isEmpty());
        assertTrue(QueryUtil.parseQuery("").isEmpty());
    }

    @Test
    void parsesSimplePair() {
        Map<String, String> q = QueryUtil.parseQuery("foo=bar");
        assertEquals(1, q.size());
        assertEquals("bar", q.get("foo"));
    }

    @Test
    void parsesMultiplePairs() {
        Map<String, String> q = QueryUtil.parseQuery("a=1&b=2&c=3");
        assertEquals("1", q.get("a"));
        assertEquals("2", q.get("b"));
        assertEquals("3", q.get("c"));
    }

    @Test
    void preservesInsertionOrder() {
        Map<String, String> q = QueryUtil.parseQuery("z=1&a=2&m=3");
        assertEquals("[z, a, m]", q.keySet().toString());
    }

    @Test
    void treatsNoEqualsAsEmptyValue() {
        Map<String, String> q = QueryUtil.parseQuery("flag");
        assertEquals("", q.get("flag"));
    }

    @Test
    void duplicateKeyLastWins() {
        Map<String, String> q = QueryUtil.parseQuery("k=1&k=2");
        assertEquals("2", q.get("k"));
    }

    @Test
    void urlDecodesValues() {
        Map<String, String> q = QueryUtil.parseQuery("name=hello%20world&id=ae2%3Acontroller");
        assertEquals("hello world", q.get("name"));
        assertEquals("ae2:controller", q.get("id"));
    }

    @Test
    void urlDecodesPlusSignToSpace() {
        // application/x-www-form-urlencoded：+ 视为空格
        assertEquals("hello world", QueryUtil.urlDecode("hello+world"));
    }

    @Test
    void urlDecodeFallsBackOnInvalidEncoding() {
        // 非法 % 序列应原样返回，不抛异常
        assertEquals("100%", QueryUtil.urlDecode("100%"));
    }

    @Test
    void parsesEmptyValue() {
        Map<String, String> q = QueryUtil.parseQuery("k=");
        assertEquals("", q.get("k"));
    }
}
