package com.google.genai;

import java.util.Base64;

public class ReplayBase64Sanitizer implements ReplaySanitizer.Sanitizer {

  @Override
  public Object sanitize(Object obj, String path) {
    if (obj instanceof String) {
      String str = (String) obj;

      try {
        // 1. Attempt URL-safe Base64 decoding
        byte[] decoded = Base64.getUrlDecoder().decode(str);
        // If URL-safe decoding succeeds, encode back to standard Base64
        return Base64.getEncoder().encodeToString(decoded);

      } catch (IllegalArgumentException e1) {
        try {
          // 2. If URL-safe decoding fails, attempt standard Base64 decoding
          byte[] decoded = Base64.getDecoder().decode(str);
          return Base64.getEncoder()
              .encodeToString(decoded); // Encode back to standard Base64 for consistency

        } catch (IllegalArgumentException e2) {
          // 3. If both fail, it's not a valid Base64 string.
          throw e2;
        }
      }
    }
    return obj; // If it's not a string, return the object as is
  }
}
