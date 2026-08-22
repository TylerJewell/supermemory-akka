package io.akka.supermemory.domain;

import java.util.List;

/**
 * Prints {@link Scorer#cosineSimilarity} for a fixed set of vector pairs, one per line, so
 * bench/compare_similarity.py can diff it against the identical function run in TypeScript
 * (the source's own language) on the same inputs -- SPEC-001 rule 4, "same answers first"
 * (PIPELINE.md step e).
 */
public final class ScorerBenchMain {
  public static void main(String[] args) {
    List<List<Double>> a = List.of(
        List.of(1.0, 0.0, 0.0),
        List.of(1.0, 0.0, 0.0),
        List.of(1.0, 0.0, 0.0),
        List.of(1.0, 0.0, 0.0),
        List.of(0.6, 0.8, 0.0),
        List.of(0.3, 0.1, -0.5, 0.9),
        List.of(0.0, 0.0, 0.0));
    List<List<Double>> b = List.of(
        List.of(1.0, 0.0, 0.0),
        List.of(0.0, 1.0, 0.0),
        List.of(-1.0, 0.0, 0.0),
        List.of(0.6, 0.8, 0.0),
        List.of(1.0, 0.0, 0.0),
        List.of(-0.2, 0.4, 0.1, 0.6),
        List.of(0.0, 0.0, 0.0));

    for (int i = 0; i < a.size(); i++) {
      System.out.println(Scorer.cosineSimilarity(a.get(i), b.get(i)));
    }
  }
}
