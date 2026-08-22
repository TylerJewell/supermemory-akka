package io.akka.supermemory.domain;

import java.util.List;

/**
 * Ported from {@code packages/lib/similarity.ts} — SPEC-001 rule 4, question-log #2, #4.
 *
 * <p>The only piece of the source's ingestion/recall/scoring capability that has real,
 * runnable source in {@code supermemoryai/supermemory}; every embedding this is run
 * against is unit-normalized at creation time (see {@code Embedder} in the application
 * package), matching the source comment that cosine similarity equals a plain dot
 * product for unit vectors.
 */
public final class Scorer {

  private Scorer() {}

  public static double cosineSimilarity(List<Double> a, List<Double> b) {
    if (a.size() != b.size()) {
      throw new IllegalArgumentException("Vectors must have the same length");
    }
    double dotProduct = 0;
    for (int i = 0; i < a.size(); i++) {
      dotProduct += a.get(i) * b.get(i);
    }
    return dotProduct;
  }

  /** A negative dot product clamps to 0 rather than remapping the full [-1,1] range (question-log #4). */
  public static double semanticSimilarity(List<Double> a, List<Double> b) {
    double similarity = cosineSimilarity(a, b);
    return similarity >= 0 ? similarity : 0;
  }
}
