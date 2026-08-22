package io.akka.supermemory.domain;

/** A search or forget-matching result — SPEC-001 rules 4-5, 7. */
public record ScoredMemory(Memory memory, double similarity) {}
