package io.akka.supermemory.application;

import java.util.List;

/**
 * Turns text into a unit-normalized vector — SPEC-001 §4 decision 1.
 *
 * <p>The real embedding model is out of scope for reimplementation (a fair stand-in, per
 * the method's own rule); this interface exists so a real model can be substituted without
 * touching {@link MemoryStoreEntity} or {@link Scorer}.
 */
public interface Embedder {
  List<Double> embed(String text);
}
