package io.akka.supermemory.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One container tag's memories — SPEC-001 rules 1-10.
 *
 * <p>All validation, scoring, and lookup logic lives here as pure functions, so it is
 * tested without a runtime; {@code MemoryStoreEntity} decides only what to persist and
 * what to answer, the same split {@code mem0-akka}'s {@code MemoryStoreState} already uses
 * in this harness.
 */
public record MemoryStoreState(Map<String, Memory> memories) {

  public static final int MAX_DIRECT_BATCH = 100;
  public static final int MAX_CONTENT_LENGTH = 10_000;

  public static MemoryStoreState empty() {
    return new MemoryStoreState(Map.of());
  }

  /** Rule 1 — direct-create batch size and per-item content length. */
  public static void validateDirect(List<Candidate> candidates) {
    if (candidates.isEmpty() || candidates.size() > MAX_DIRECT_BATCH) {
      throw new IllegalArgumentException(
          "memories must contain between 1 and " + MAX_DIRECT_BATCH + " items");
    }
    for (Candidate c : candidates) {
      if (c.content() == null || c.content().isBlank()) {
        throw new IllegalArgumentException("memory content must not be blank");
      }
      if (c.content().length() > MAX_CONTENT_LENGTH) {
        throw new IllegalArgumentException(
            "memory content must not exceed " + MAX_CONTENT_LENGTH + " characters");
      }
    }
  }

  /** Rule 3 — id, version, and timestamps assigned here; the embedding is never null. */
  public static Memory toMemory(
      Candidate candidate, String containerTag, List<Double> embedding, Instant now) {
    return new Memory(
        UUID.randomUUID().toString(),
        containerTag,
        candidate.content(),
        candidate.isStatic(),
        candidate.metadata(),
        embedding,
        candidate.source(),
        1,
        true,
        null,
        false,
        null,
        null,
        now,
        now);
  }

  /** A2 — pure: every sealed variant is handled, so this never throws. */
  public MemoryStoreState apply(MemoryEvent event) {
    return switch (event) {
      case MemoryEvent.MemoryAdded added -> withMemory(added.memory());
      case MemoryEvent.MemoryUpdated updated ->
          withMemory(updated.supersededVersion()).withMemory(updated.newVersion());
      case MemoryEvent.MemoryForgotten forgotten -> {
        MemoryStoreState state = this;
        for (Memory m : forgotten.forgottenMemories()) {
          state = state.withMemory(m);
        }
        yield state;
      }
    };
  }

  private MemoryStoreState withMemory(Memory memory) {
    var updated = new HashMap<>(memories);
    updated.put(memory.id(), memory);
    return new MemoryStoreState(Map.copyOf(updated));
  }

  /** Rules 4-5 — cosine-score every eligible memory, threshold cutoff, descending, limited. */
  public List<ScoredMemory> search(
      List<Double> queryEmbedding, double threshold, int limit, boolean includeForgotten) {
    return memories.values().stream()
        .filter(m -> includeForgotten || !m.isForgotten())
        .map(m -> new ScoredMemory(m, Scorer.semanticSimilarity(queryEmbedding, m.embedding())))
        .filter(s -> s.similarity() >= threshold)
        .sorted(Comparator.comparingDouble(ScoredMemory::similarity).reversed())
        .limit(limit)
        .toList();
  }

  /** Rule 7 — forget-matching candidates: non-forgotten, current versions only. */
  public List<ScoredMemory> forgetCandidates(
      List<Double> queryEmbedding, double threshold, int maxForget) {
    return memories.values().stream()
        .filter(m -> !m.isForgotten() && m.isLatest())
        .map(m -> new ScoredMemory(m, Scorer.semanticSimilarity(queryEmbedding, m.embedding())))
        .filter(s -> s.similarity() >= threshold)
        .sorted(Comparator.comparingDouble(ScoredMemory::similarity).reversed())
        .limit(maxForget)
        .toList();
  }

  /** Rule 8 — explicit ids: unknown or already-forgotten ids are ignored, not errors. */
  public List<Memory> resolveIds(List<String> ids) {
    List<Memory> resolved = new ArrayList<>();
    for (String id : ids) {
      Memory m = memories.get(id);
      if (m != null && !m.isForgotten()) {
        resolved.add(m);
      }
    }
    return resolved;
  }

  /** Rule 6, 9 — target for forget-by-content or update-by-content: current, non-forgotten. */
  public Optional<Memory> findTarget(String id, String content) {
    if (id != null) {
      Memory m = memories.get(id);
      return (m != null && !m.isForgotten()) ? Optional.of(m) : Optional.empty();
    }
    return memories.values().stream()
        .filter(m -> !m.isForgotten() && m.isLatest() && m.content().equals(content))
        .findFirst();
  }

  /** Rule 10 — current, non-forgotten memories, most-recently-updated first. */
  public List<Memory> currentMemories() {
    return memories.values().stream()
        .filter(m -> m.isLatest() && !m.isForgotten())
        .sorted(Comparator.comparing(Memory::updatedAt).reversed())
        .toList();
  }
}
