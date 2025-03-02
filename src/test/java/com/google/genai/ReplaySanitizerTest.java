package com.google.genai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;

public class ReplaySanitizerTest {

  private static final ReplaySanitizer.Sanitizer sanitizer = (data, path) -> "sanitized";

  @Test
  @DisplayName("Sanitize Success")
  void testSanitizeSuccess() {
    testSanitizeCase(mapOf("k1", "v1"), "k1", mapOf("k1", "sanitized"));
    testSanitizeCase(
        mapOf("k1", mapOf("k2", "v2")), "k1.k2", mapOf("k1", mapOf("k2", "sanitized")));
    testSanitizeCase(
        mapOf("k1", listOf("v1", "v1")), "[]k1", mapOf("k1", listOf("sanitized", "sanitized")));
    testSanitizeCase(
        mapOf("k1", listOf(mapOf("k2", "v2"), mapOf("k2", "v2"))),
        "[]k1.k2",
        mapOf("k1", listOf(mapOf("k2", "sanitized"), mapOf("k2", "sanitized"))));
    testSanitizeCase(
        mapOf("k1", mapOf("k2", listOf("v2", "v2"))),
        "k1.[]k2",
        mapOf("k1", mapOf("k2", listOf("sanitized", "sanitized"))));
  }

  @Test
  @DisplayName("Path Name Mismatch, No Sanitize")
  void testPathNameMismatch() {
    testSanitizeCase(mapOf("k1", "v1"), "wrongPath", mapOf("k1", "v1"));
    testSanitizeCase(
        mapOf("k1", mapOf("k2", "v2")), "k1.wrongPath", mapOf("k1", mapOf("k2", "v2")));
    testSanitizeCase(
        mapOf("k1", listOf("v1", "v1")), "[]wrongPath", mapOf("k1", listOf("v1", "v1")));
    testSanitizeCase(
        mapOf("k1", listOf(mapOf("k2", "v2"), mapOf("k2", "v2"))),
        "[]wrongPath.k2",
        mapOf("k1", listOf(mapOf("k2", "v2"), mapOf("k2", "v2"))));
    testSanitizeCase(
        mapOf("k1", mapOf("k2", listOf("v2", "v2"))),
        "k1.[]wrongPath",
        mapOf("k1", mapOf("k2", listOf("v2", "v2"))));
  }

  @Test
  @DisplayName("Path Type Mismatch, No Sanitize")
  void testPathTypeMismatch() {
    testSanitizeCase(
        mapOf("k1", listOf("v1", "v1")), "k1.wrongPath", mapOf("k1", listOf("v1", "v1")));
    testSanitizeCase(mapOf("k1", mapOf("k2", "v2")), "k1.[]k2", mapOf("k1", mapOf("k2", "v2")));
    testSanitizeCase(
        mapOf("k1", mapOf("k2", mapOf("k3", "v3"))),
        "k1.[]k2.k3",
        mapOf("k1", mapOf("k2", mapOf("k3", "v3"))));
  }

  private void testSanitizeCase(
      Map<String, Object> input, String path, Map<String, Object> expected) {
    Map<String, Object> actual =
        new HashMap<>(input); // Create a copy to avoid modifying the original test data
    ReplaySanitizer.sanitizeMapByPath(actual, path, sanitizer, false);
    assertEquals(expected, actual, "Input: " + input + ", Path: " + path);
  }

  // Helper functions for creating maps and lists for cleaner test cases
  private static Map<String, Object> mapOf(Object... entries) {
    if (entries.length % 2 != 0) {
      throw new IllegalArgumentException(
          "mapOf must be called with an even number of arguments (key-value pairs)");
    }

    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      String key = (String) entries[i]; // Explicit cast to String
      Object value = entries[i + 1];
      map.put(key, value);
    }
    return map;
  }

  private static List<Object> listOf(Object... elements) {
    return new ArrayList<>(Arrays.asList(elements));
  }
}
