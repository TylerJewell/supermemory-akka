package io.akka.supermemory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Times {@link Scorer#cosineSimilarity} over a batch of vector pairs, best-of-N -- the Java
 * side of bench/compare_speed.py.
 *
 * <p>Args: nPairs, dimensions, repeats. Prints the best elapsed time in milliseconds on the
 * last line of stdout.
 */
public final class ScorerSpeedBenchMain {
  public static void main(String[] args) {
    int nPairs = Integer.parseInt(args[0]);
    int dimensions = Integer.parseInt(args[1]);
    int repeats = Integer.parseInt(args[2]);

    Random random = new Random(42);
    List<List<Double>> a = new ArrayList<>(nPairs);
    List<List<Double>> b = new ArrayList<>(nPairs);
    for (int i = 0; i < nPairs; i++) {
      a.add(randomVector(random, dimensions));
      b.add(randomVector(random, dimensions));
    }

    double bestMs = Double.MAX_VALUE;
    double sumAcrossAllRepeats = 0;
    for (int r = 0; r < repeats; r++) {
      long start = System.nanoTime();
      double sum = 0;
      for (int i = 0; i < nPairs; i++) {
        sum += Scorer.cosineSimilarity(a.get(i), b.get(i));
      }
      long elapsed = System.nanoTime() - start;
      sumAcrossAllRepeats += sum; // escapes to stdout below, so the loop above cannot be elided
      double ms = elapsed / 1_000_000.0;
      if (ms < bestMs) bestMs = ms;
    }

    System.err.println("checksum " + sumAcrossAllRepeats);
    System.out.println(bestMs);
  }

  private static List<Double> randomVector(Random random, int dimensions) {
    List<Double> v = new ArrayList<>(dimensions);
    for (int i = 0; i < dimensions; i++) v.add(random.nextDouble() * 2 - 1);
    return v;
  }
}
