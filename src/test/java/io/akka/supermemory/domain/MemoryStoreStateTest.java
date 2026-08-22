package io.akka.supermemory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 rules 1, 6, 10 on the pure state, without a runtime. */
public class MemoryStoreStateTest {

  private static final Candidate FACT =
      new Candidate("a fact", false, Map.of(), MemorySource.MEMORY);

  @Test
  public void validateDirectRejectsEmptyOrOversizedBatch() {
    assertThatThrownBy(() -> MemoryStoreState.validateDirect(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    var oversized = java.util.Collections.nCopies(101, FACT);
    assertThatThrownBy(() -> MemoryStoreState.validateDirect(oversized))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void validateDirectAcceptsWithinBounds() {
    MemoryStoreState.validateDirect(List.of(FACT));
  }

  @Test
  public void toMemoryAssignsIdVersionAndTimestamps() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Memory m = MemoryStoreState.toMemory(FACT, "tag", List.of(1.0, 0.0), now);
    assertThat(m.id()).isNotBlank();
    assertThat(m.version()).isEqualTo(1);
    assertThat(m.isLatest()).isTrue();
    assertThat(m.isForgotten()).isFalse();
    assertThat(m.createdAt()).isEqualTo(now);
  }

  @Test
  public void currentMemoriesExcludesSupersededAndForgotten() {
    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    Memory current = MemoryStoreState.toMemory(FACT, "tag", List.of(1.0), t0);
    Memory superseded = current.notLatest();
    Memory forgotten =
        MemoryStoreState.toMemory(FACT, "tag", List.of(1.0), t0).forgotten("gone", null, t0);

    var state =
        MemoryStoreState.empty()
            .apply(new MemoryEvent.MemoryAdded(superseded))
            .apply(new MemoryEvent.MemoryAdded(forgotten))
            .apply(new MemoryEvent.MemoryAdded(current));

    assertThat(state.currentMemories()).containsExactly(current);
  }

  @Test
  public void findTargetByIdIgnoresForgotten() {
    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    Memory forgotten = MemoryStoreState.toMemory(FACT, "tag", List.of(1.0), t0).forgotten("gone", null, t0);
    var state = MemoryStoreState.empty().apply(new MemoryEvent.MemoryAdded(forgotten));

    assertThat(state.findTarget(forgotten.id(), null)).isEmpty();
  }
}
