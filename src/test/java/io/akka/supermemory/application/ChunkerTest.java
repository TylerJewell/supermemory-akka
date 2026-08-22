package io.akka.supermemory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 rule 2 — a given rule, not a copied one (question-log #9). */
public class ChunkerTest {

  @Test
  public void splitsOnBlankLines() {
    List<String> chunks = Chunker.chunk("first paragraph\nstill first\n\nsecond paragraph");
    assertThat(chunks).containsExactly("first paragraph\nstill first", "second paragraph");
  }

  @Test
  public void multipleBlankLinesCollapseToOneBoundary() {
    List<String> chunks = Chunker.chunk("first\n\n\n\nsecond");
    assertThat(chunks).containsExactly("first", "second");
  }

  @Test
  public void blankChunksAreDropped() {
    List<String> chunks = Chunker.chunk("first\n\n   \n\nsecond");
    assertThat(chunks).containsExactly("first", "second");
  }

  @Test
  public void singleParagraphIsOneChunk() {
    assertThat(Chunker.chunk("just one paragraph, no blank lines")).containsExactly("just one paragraph, no blank lines");
  }
}
