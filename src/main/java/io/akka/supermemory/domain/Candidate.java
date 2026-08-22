package io.akka.supermemory.domain;

import java.util.Map;

/** Ingestion input, not persisted on its own — SPEC-001 §2. */
public record Candidate(String content, boolean isStatic, Map<String, Object> metadata, MemorySource source) {}
