package io.akka.supermemory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.akka.supermemory.domain.Scorer;
import org.junit.jupiter.api.Test;

/** SPEC-001 §4 decision 1 — a given rule; checks its own two stated properties, nothing more. */
public class HashingEmbedderTest {

  private final HashingEmbedder embedder = new HashingEmbedder();

  @Test
  public void embeddingIsUnitLength() {
    var v = embedder.embed("the quick brown fox jumps over the lazy dog");
    double normSquared = v.stream().mapToDouble(d -> d * d).sum();
    assertThat(normSquared).isCloseTo(1.0, offset(1e-9));
  }

  @Test
  public void sharedWordsScoreHigherThanNoSharedWords() {
    var a = embedder.embed("the user prefers dark mode");
    var b = embedder.embed("the user prefers dark themes");
    var c = embedder.embed("quarterly revenue forecast spreadsheet");

    double related = Scorer.cosineSimilarity(a, b);
    double unrelated = Scorer.cosineSimilarity(a, c);

    assertThat(related).isGreaterThan(unrelated);
  }

  @Test
  public void emptyTextEmbedsToZeroVector() {
    var v = embedder.embed("");
    assertThat(v).allMatch(d -> d == 0.0);
  }
}
