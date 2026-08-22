package io.akka.supermemory.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic local stand-in for the real embedding model — SPEC-001 §4 decision 1, a given
 * rule, not a copied one. Hashes each lowercased word into one of {@link #DIMENSIONS} buckets,
 * counts term frequency per bucket, then L2-normalizes — so two texts sharing more words score
 * higher under {@link Scorer#cosineSimilarity}, and the vector is a unit vector as
 * {@link Scorer} requires.
 */
public final class HashingEmbedder implements Embedder {

  public static final int DIMENSIONS = 64;
  private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

  @Override
  public List<Double> embed(String text) {
    double[] buckets = new double[DIMENSIONS];
    var matcher = WORD.matcher(text.toLowerCase());
    while (matcher.find()) {
      int bucket = Math.floorMod(matcher.group().hashCode(), DIMENSIONS);
      buckets[bucket] += 1.0;
    }

    double norm = 0;
    for (double v : buckets) norm += v * v;
    norm = Math.sqrt(norm);

    List<Double> embedding = new ArrayList<>(DIMENSIONS);
    for (double v : buckets) {
      embedding.add(norm == 0 ? 0.0 : v / norm);
    }
    return embedding;
  }
}
