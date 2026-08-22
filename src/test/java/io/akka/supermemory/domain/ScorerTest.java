package io.akka.supermemory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 rule 4 — ported from {@code similarity.ts}, checked against the identical
 * vectors run in question-log #4 via {@code probes/run_similarity.ts}.
 */
public class ScorerTest {

  private static final List<Double> UNIT_X = List.of(1.0, 0.0, 0.0);
  private static final List<Double> UNIT_Y = List.of(0.0, 1.0, 0.0);
  private static final List<Double> NEGATIVE_X = List.of(-1.0, 0.0, 0.0);
  private static final List<Double> ANGLED = List.of(0.6, 0.8, 0.0);

  @Test
  public void identicalVectorsScoreOne() {
    assertThat(Scorer.cosineSimilarity(UNIT_X, UNIT_X)).isEqualTo(1.0);
  }

  @Test
  public void orthogonalVectorsScoreZero() {
    assertThat(Scorer.cosineSimilarity(UNIT_X, UNIT_Y)).isEqualTo(0.0);
  }

  @Test
  public void oppositeVectorsScoreNegativeOne() {
    assertThat(Scorer.cosineSimilarity(UNIT_X, NEGATIVE_X)).isEqualTo(-1.0);
  }

  @Test
  public void angledVectorMatchesDotProduct() {
    assertThat(Scorer.cosineSimilarity(UNIT_X, ANGLED)).isEqualTo(0.6);
  }

  @Test
  public void semanticSimilarityClampsNegativeToZero() {
    assertThat(Scorer.cosineSimilarity(UNIT_X, NEGATIVE_X)).isEqualTo(-1.0);
    assertThat(Scorer.semanticSimilarity(UNIT_X, NEGATIVE_X)).isEqualTo(0.0);
  }

  @Test
  public void mismatchedLengthThrows() {
    assertThatThrownBy(() -> Scorer.cosineSimilarity(List.of(1.0, 0.0), UNIT_X))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Vectors must have the same length");
  }
}
