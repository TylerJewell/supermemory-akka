package io.akka.supermemory.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.akka.supermemory.domain.Candidate;
import io.akka.supermemory.domain.Memory;
import io.akka.supermemory.domain.MemoryEvent;
import io.akka.supermemory.domain.MemorySource;
import io.akka.supermemory.domain.MemoryStoreState;
import io.akka.supermemory.domain.ScoredMemory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One container tag's memory store — SPEC-001 rules 1-10.
 *
 * <p>The entity id is the container tag. Embedding is a fast, deterministic local
 * computation (§4 decision 1), not a live network call, so unlike {@code mem0-akka}'s
 * extractor split, it happens directly inside the command handler rather than being
 * orchestrated from the endpoint.
 */
@Component(id = "memory-store")
public class MemoryStoreEntity extends EventSourcedEntity<MemoryStoreState, MemoryEvent> {

  public static final double SEARCH_DEFAULT_THRESHOLD = 0.6; // question-log #6, §4 decision 2
  public static final int SEARCH_DEFAULT_LIMIT = 10;
  public static final double FORGET_MATCH_DEFAULT_THRESHOLD = 0.5; // question-log #7, doc-only
  public static final int FORGET_MATCH_DEFAULT_MAX = 100; // question-log #7, doc-only
  public static final int FORGET_MATCH_ID_CAP = 500;

  private final String containerTag;
  private final Embedder embedder;

  public MemoryStoreEntity(EventSourcedEntityContext context) {
    this.containerTag = context.entityId();
    this.embedder = new HashingEmbedder();
  }

  @Override
  public MemoryStoreState emptyState() {
    return MemoryStoreState.empty();
  }

  // -- Direct memory creation (rule 1) --------------------------------------------------

  public record MemoryInput(String content, boolean isStatic, Map<String, Object> metadata) {}

  public record AddDirect(List<MemoryInput> memories) {}

  public Effect<List<Memory>> addDirect(AddDirect command) {
    List<Candidate> candidates = new ArrayList<>();
    for (MemoryInput input : command.memories()) {
      candidates.add(
          new Candidate(
              input.content(),
              input.isStatic(),
              input.metadata() == null ? Map.of() : input.metadata(),
              MemorySource.MEMORY));
    }
    try {
      MemoryStoreState.validateDirect(candidates);
    } catch (IllegalArgumentException e) {
      return effects().error(e.getMessage());
    }
    return persistAll(candidates);
  }

  // -- Raw content ingestion, chunked (rules 2-3) ---------------------------------------

  public record AddContent(String content, Map<String, Object> metadata) {}

  public Effect<List<Memory>> addContent(AddContent command) {
    List<String> chunks = Chunker.chunk(command.content());
    List<Candidate> candidates = new ArrayList<>();
    for (String chunk : chunks) {
      candidates.add(
          new Candidate(
              chunk,
              false,
              command.metadata() == null ? Map.of() : command.metadata(),
              MemorySource.CHUNK));
    }
    return persistAll(candidates);
  }

  private Effect<List<Memory>> persistAll(List<Candidate> candidates) {
    if (candidates.isEmpty()) {
      return effects().reply(List.of());
    }
    Instant now = Instant.now();
    List<MemoryEvent> events = new ArrayList<>();
    List<Memory> created = new ArrayList<>();
    for (Candidate c : candidates) {
      List<Double> embedding = embedder.embed(c.content());
      Memory memory = MemoryStoreState.toMemory(c, containerTag, embedding, now);
      events.add(new MemoryEvent.MemoryAdded(memory));
      created.add(memory);
    }
    return effects().persistAll(events).thenReply(state -> created);
  }

  // -- Search / recall with scoring (rules 4-5) -----------------------------------------

  public record Search(String query, Double threshold, Integer limit, boolean includeForgotten) {}

  public ReadOnlyEffect<List<ScoredMemory>> search(Search command) {
    List<Double> queryEmbedding = embedder.embed(command.query());
    double threshold = command.threshold() != null ? command.threshold() : SEARCH_DEFAULT_THRESHOLD;
    int limit = command.limit() != null ? command.limit() : SEARCH_DEFAULT_LIMIT;
    return effects()
        .reply(currentState().search(queryEmbedding, threshold, limit, command.includeForgotten()));
  }

  // -- Forget by id or exact content (rule 6) -------------------------------------------

  public record ForgetOne(String id, String content, String reason) {}

  public record ForgetOneResult(boolean found, Memory memory) {}

  public Effect<ForgetOneResult> forgetOne(ForgetOne command) {
    Optional<Memory> target = currentState().findTarget(command.id(), command.content());
    if (target.isEmpty()) {
      return effects().reply(new ForgetOneResult(false, null));
    }
    Instant now = Instant.now();
    Memory forgotten = target.get().forgotten(command.reason(), null, now);
    return effects()
        .persist(new MemoryEvent.MemoryForgotten(List.of(forgotten)))
        .thenReply(state -> new ForgetOneResult(true, forgotten));
  }

  // -- Forget matching: dryRun preview, or apply by query or explicit ids (rules 7-8) --

  public record ForgetMatching(
      String query, List<String> ids, boolean dryRun, Double threshold, Integer maxForget, String reason) {}

  public record ForgetMatchResult(
      boolean dryRun, List<ScoredMemory> candidates, List<Memory> forgotten, String forgetBatchId) {}

  public Effect<ForgetMatchResult> forgetMatching(ForgetMatching command) {
    List<ScoredMemory> candidates;
    if (command.ids() != null) {
      candidates =
          currentState().resolveIds(command.ids()).stream().map(m -> new ScoredMemory(m, 1.0)).toList();
    } else {
      double threshold =
          command.threshold() != null ? command.threshold() : FORGET_MATCH_DEFAULT_THRESHOLD;
      int maxForget = command.maxForget() != null ? command.maxForget() : FORGET_MATCH_DEFAULT_MAX;
      List<Double> queryEmbedding = embedder.embed(command.query());
      candidates = currentState().forgetCandidates(queryEmbedding, threshold, maxForget);
    }

    if (command.dryRun()) {
      return effects().reply(new ForgetMatchResult(true, candidates, List.of(), null));
    }
    if (candidates.isEmpty()) {
      return effects().reply(new ForgetMatchResult(false, List.of(), List.of(), null));
    }

    String forgetBatchId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    List<Memory> forgotten =
        candidates.stream().map(s -> s.memory().forgotten(command.reason(), forgetBatchId, now)).toList();
    return effects()
        .persist(new MemoryEvent.MemoryForgotten(forgotten))
        .thenReply(state -> new ForgetMatchResult(false, List.of(), forgotten, forgetBatchId));
  }

  // -- Versioned update (rule 9) ---------------------------------------------------------

  public record UpdateMemory(String id, String content, String newContent, Map<String, Object> metadata) {}

  public Effect<Memory> updateMemory(UpdateMemory command) {
    Optional<Memory> target = currentState().findTarget(command.id(), command.content());
    if (target.isEmpty()) {
      return effects().error("no memory found matching the given id or content");
    }
    Memory old = target.get();
    Instant now = Instant.now();
    Memory superseded = old.notLatest();
    Candidate candidate =
        new Candidate(
            command.newContent(),
            old.isStatic(),
            command.metadata() != null ? command.metadata() : old.metadata(),
            old.source());
    List<Double> embedding = embedder.embed(command.newContent());
    Memory base = MemoryStoreState.toMemory(candidate, containerTag, embedding, now);
    Memory newVersion =
        new Memory(
            base.id(), containerTag, base.content(), base.isStatic(), base.metadata(),
            base.embedding(), base.source(), old.version() + 1, true, old.id(),
            false, null, null, now, now);
    return effects()
        .persist(new MemoryEvent.MemoryUpdated(superseded, newVersion))
        .thenReply(state -> newVersion);
  }

  // -- Listing (rule 10) -------------------------------------------------------------------

  public ReadOnlyEffect<List<Memory>> getMemories() {
    return effects().reply(currentState().currentMemories());
  }

  @Override
  public MemoryStoreState applyEvent(MemoryEvent event) {
    return currentState().apply(event);
  }
}
