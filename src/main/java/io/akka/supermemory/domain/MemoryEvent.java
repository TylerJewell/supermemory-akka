package io.akka.supermemory.domain;

import akka.javasdk.annotations.TypeName;
import java.util.List;

/**
 * SPEC-001 §2 — the store's append-only history.
 *
 * <p>Every event carries the already fully-formed {@link Memory} value(s) to persist;
 * {@code MemoryStoreState.apply} only merges them into the map. Timestamps and ids are
 * assigned once, by the entity's command handler, before the event is built — never
 * recomputed during replay.
 */
public sealed interface MemoryEvent {
  @TypeName("memory-added")
  record MemoryAdded(Memory memory) implements MemoryEvent {}

  @TypeName("memory-updated")
  record MemoryUpdated(Memory supersededVersion, Memory newVersion) implements MemoryEvent {}

  @TypeName("memory-forgotten")
  record MemoryForgotten(List<Memory> forgottenMemories) implements MemoryEvent {}
}
