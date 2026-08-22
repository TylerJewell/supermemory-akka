package io.akka.supermemory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One durably stored, embedded fact or chunk — SPEC-001 §2. */
public record Memory(
    String id,
    String containerTag,
    String content,
    boolean isStatic,
    Map<String, Object> metadata,
    List<Double> embedding,
    MemorySource source,
    int version,
    boolean isLatest,
    String parentMemoryId,
    boolean isForgotten,
    String forgetReason,
    String forgetBatchId,
    Instant createdAt,
    Instant updatedAt) {

  public Memory forgotten(String reason, String forgetBatchId, Instant now) {
    return new Memory(
        id, containerTag, content, isStatic, metadata, embedding, source, version, isLatest,
        parentMemoryId, true, reason, forgetBatchId, createdAt, now);
  }

  public Memory notLatest() {
    return new Memory(
        id, containerTag, content, isStatic, metadata, embedding, source, version, false,
        parentMemoryId, isForgotten, forgetReason, forgetBatchId, createdAt, updatedAt);
  }
}
