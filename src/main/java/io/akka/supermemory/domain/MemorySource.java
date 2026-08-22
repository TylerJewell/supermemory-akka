package io.akka.supermemory.domain;

/** Which ingestion path produced a {@link Memory} — SPEC-001 §2, drives the search response shape. */
public enum MemorySource {
  MEMORY,
  CHUNK
}
