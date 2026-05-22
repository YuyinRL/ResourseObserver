package com.yuyinrl.resourceobserver.web;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JsonWriter 单元测试 —— 覆盖 JSON 序列化各类型与边界情形。
 */
class JsonWriterTest {

    @Test
    void writesPrimitives() {
        assertEquals("null", JsonWriter.write(null));
        assertEquals("true", JsonWriter.write(true));
        assertEquals("false", JsonWriter.write(false));
        assertEquals("42", JsonWriter.write(42));
        assertEquals("3.14", JsonWriter.write(3.14));
    }

    @Test
    void writesNanAndInfinityAsNull() {
        assertEquals("null", JsonWriter.write(Double.NaN));
        assertEquals("null", JsonWriter.write(Double.POSITIVE_INFINITY));
        assertEquals("null", JsonWriter.write(Double.NEGATIVE_INFINITY));
    }

    @Test
    void escapesStringSpecialChars() {
        assertEquals("\"hello\"", JsonWriter.write("hello"));
        assertEquals("\"a\\\"b\"", JsonWriter.write("a\"b"));
        assertEquals("\"a\\\\b\"", JsonWriter.write("a\\b"));
        assertEquals("\"line1\\nline2\"", JsonWriter.write("line1\nline2"));
        assertEquals("\"tab\\there\"", JsonWriter.write("tab\there"));
    }

    @Test
    void escapesControlCharsAsUnicode() {
        // 0x01 应当被编码成 \u0001
        assertEquals("\"\\u0001\"", JsonWriter.write("\u0001"));
    }

    @Test
    void writesEmptyAndPopulatedMap() {
        assertEquals("{}", JsonWriter.write(new LinkedHashMap<>()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "alice");
        m.put("age", 30);
        m.put("active", true);
        assertEquals("{\"name\":\"alice\",\"age\":30,\"active\":true}", JsonWriter.write(m));
    }

    @Test
    void writesEmptyAndPopulatedArray() {
        assertEquals("[]", JsonWriter.write(List.of()));
        assertEquals("[1,2,3]", JsonWriter.write(List.of(1, 2, 3)));
    }

    @Test
    void writesNestedStructure() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", List.of("a", "b"));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", 2);
        root.put("meta", meta);
        assertEquals("{\"items\":[\"a\",\"b\"],\"meta\":{\"count\":2}}", JsonWriter.write(root));
    }

    @Test
    void preservesMapInsertionOrder() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("z", 1);
        m.put("a", 2);
        m.put("m", 3);
        assertEquals("{\"z\":1,\"a\":2,\"m\":3}", JsonWriter.write(m));
    }
}
