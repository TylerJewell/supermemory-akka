# Acknowledgements

This project is a port of **[supermemoryai/supermemory](https://github.com/supermemoryai/supermemory)** —
ingestion and recall over a memory store with scoring.

## Licence and copyright

`supermemoryai/supermemory` is licensed under the **MIT License**, copyright supermemory
2025 — read directly from `supermemory-src/LICENSE` in the cloned source. MIT is
permissive: a derivative work may be licensed differently, provided the original
copyright and licence notice are retained where the original work's own files are
redistributed.

## Was anything copied verbatim?

One file's logic was ported directly: `packages/lib/similarity.ts`'s `cosineSimilarity`
and `calculateSemanticSimilarity` functions were reimplemented in Java
(`supermemory-akka/src/main/java/io/akka/supermemory/domain/Scorer.java`) as a
line-for-line behavioural translation — same dot-product formula, same
negative-similarity clamp — not a copy-paste (Java cannot copy-paste TypeScript), but a
deliberate, checked reproduction (`bench/compare_similarity.py` confirms 7/7 agreement).
This is the one piece of the source's ingestion/recall/scoring capability that has real,
runnable code to translate from (question-log #1, #2); everything else in this port was
either read from documentation or given a rule, because the source has no route to it.

No other file, prompt string, or UI copy was copy-pasted from `supermemory-src/`.

## Is behaviour derived even where no text was copied?

Yes, extensively, and it is the central fact this port's spec is built around: the
actual ingestion pipeline (chunking, fact extraction, embedding generation) and the
actual search-time ranking/reranking are **not present as runnable source anywhere in
the open-source repository** (`docs/question-log.md` #1, #2, #3 in `supermemory-port`) —
they ship as a closed, hosted platform and a closed, binary-distributed local server.
What this port reproduces instead is the *documented* contract
(`apps/docs/ingestion/add-memories.mdx`, `apps/docs/recall/search.mdx`,
`apps/docs/recall/memory-operations.mdx` — read, not run) for what a caller sends and
receives: container tags, direct memory creation with its validation limits, soft-delete
semantics, versioned updates, and bulk semantic forgetting with a dry-run preview. Where
the documentation itself was ambiguous or silent (chunking algorithm, the embedding
model, and a genuine discrepancy between the docs' stated `0.5` search threshold default
and the runnable schema's actual `0.6` — question-log #6), the port was given an
explicit rule rather than guessing at one; see `supermemory-port/specs/SPEC-001-supermemory.md`
§4 for every such decision and its reasoning.

## What licence does that force on this project?

MIT places no restriction on licensing a behaviour-derived rebuild differently, and no
file from `supermemoryai/supermemory` is redistributed here verbatim except the ported
scoring formula, which MIT permits under attribution (this file). Consistent with
PIPELINE.md step f's default, this repository is published **private** — a decision
about whether to make it public is separate from whether it is legally permitted, and is
left to a deliberate choice rather than a side effect of backing work up.

## Also used

- Akka (the Agentic Systems Platform) — Java SDK, `io.akka:akka-javasdk-parent`.
