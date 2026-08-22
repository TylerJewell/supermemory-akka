package io.akka.supermemory.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw content into chunks on blank-line boundaries — SPEC-001 rule 2, §4 decision 3.
 *
 * <p>A given rule, not a copied one: no chunking algorithm exists in the source
 * (question-log #9), which chunks via a closed LLM-driven extraction pipeline instead.
 */
public final class Chunker {

  private Chunker() {}

  public static List<String> chunk(String content) {
    List<String> chunks = new ArrayList<>();
    for (String piece : content.split("\\n\\s*\\n+")) {
      String trimmed = piece.trim();
      if (!trimmed.isEmpty()) {
        chunks.add(trimmed);
      }
    }
    return chunks;
  }
}
