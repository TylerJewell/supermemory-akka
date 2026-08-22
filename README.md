# supermemory-akka

Stores facts and content chunks scoped to a container tag, scores them against a
search query by cosine similarity, and forgets them — one at a time, or in bulk by
semantic match — without ever deleting a record outright.

A port of [supermemoryai/supermemory](https://github.com/supermemoryai/supermemory) onto
**Akka**, built with **Akka Specify**.

---

## Where it came from

supermemory is a memory and context layer for AI assistants and agents — content goes
in, facts and ranked search results come back out. It was ported to derive a
specification format precise enough to regenerate a system on a different stack — the
port is the vehicle, the specification is the deliverable.

The actual ingestion and search-ranking engine `supermemoryai/supermemory` runs is not
present as runnable source in that repository — it ships as a closed, hosted service and
a closed, binary-distributed local server. The one piece of real, runnable logic this
port could translate directly is the scoring formula; everything else was read from
documentation or given an explicit rule where the documentation itself was silent. Both
kinds of evidence, and which is which for every behaviour this port implements, are in
the question log linked below.

The specifications the port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `supermemory-port/`.

---

## supermemory → this port

📉 38 TypeScript lines (scope-matched: the scoring formula) → **14 Java lines**<br>
📁 1 file → **12 files**<br>
🧾 7/7 similarity scores → **7/7 agree, bit-for-bit**<br>
⚡ 0.489 ms → **0.870 ms** to cosine-score 5,000 pairs of 64-dimensional vectors

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/supermemory-port/bench/REPORT.md).

---

## What it took to build

⏱️ **0.7 hours** from the first command to the published repository, **0.7** of them active<br>
💬 **345** exchanges with the model<br>
✍️ **254,874** tokens written by the model, **72,895,342** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **36** tests

```bash
python toolkit/tokens.py --port supermemory    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

From the specification:

- **A search result never scores below the plain dot product of two unit vectors, and
  a negative one is floored at zero.** Two unrelated pieces of content can score exactly
  zero; two opposite ones never score less than that.
- **Forgetting a memory keeps its content in the store.** A forgotten memory stops
  appearing in search results but is never deleted — its text and its vector are exactly
  what they were before.
- **Updating a memory writes a new record and keeps the old one.** The previous version
  stays in the store, marked as no longer current, rather than being overwritten.
- **A bulk forget-by-topic can be previewed before anything changes.** Asking what a
  query would forget and asking it to actually forget are two different requests; the
  first one changes nothing.

---

## Design decisions

**Every memory belongs to exactly one container tag, and a container tag is one Akka
entity.** A container tag is however a caller chooses to group content — one person, one
project, one team. Keeping all of a tag's memories in one place means a search or a bulk
forget never has to look anywhere else to answer completely.

**The real embedding model was swapped for a small, local stand-in.** The actual model
supermemory uses is not something this port could run or reproduce, so a fast, built-in
substitute turns text into a vector instead. Search still works and still ranks
similar text higher — it just does not know what the real model would have known about
meaning.

**Splitting raw content into chunks uses blank lines, not a language model.**
supermemory's own splitting step is closed and not available to copy, so this port
picks the simplest rule a reader can check by eye: a blank line starts a new chunk.

**A forgotten or superseded memory is marked, never removed.** Nothing in this port ever
deletes a stored record. This keeps every past version and every forgetting decision
recoverable, at the cost of a store that only grows.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/supermemory-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9047.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

No model provider key is required. The step that would call a real embedding model is a
small, swappable stand-in in this port rather than a live call — see "Where it differs
from supermemory" below.

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9047**.

### Try it

```bash
curl -X POST http://localhost:9047/containers/alice/memories \
  -H "Content-Type: application/json" \
  -d '{"memories": [{"content": "Alice prefers dark mode", "isStatic": false, "metadata": {}}]}'

curl -X POST http://localhost:9047/containers/alice/memories/search \
  -H "Content-Type: application/json" \
  -d '{"q": "dark mode preference", "threshold": 0.2, "limit": 10, "includeForgotten": false}'
```

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `akka.javasdk.dev-mode.http-port` | `9047` | set in `src/main/resources/application.conf` |

---

## Where it differs from supermemory

Everything not listed here behaves the same way on purpose, including the parts that
look like mistakes.

- **What actually turns text into a vector.** supermemory embeds with a real model
  (locally, `Xenova/bge-base-en-v1.5`, or a hosted provider). This port uses a small,
  deterministic local stand-in — a word-hashing count vector — because the real model is
  not something this port reimplements (a fair stand-in, per this project's own rule).
  Search still ranks shared-vocabulary text higher; it does not carry any notion of
  meaning beyond shared words.
- **How raw content becomes chunks.** supermemory chunks and extracts facts through a
  closed pipeline this port cannot read or copy. This port splits on blank lines instead
  — a plain, checkable rule rather than an attempt to approximate a pipeline it cannot see.
- **The default search similarity threshold.** supermemory's own documentation states
  `0.5`; its actual request-validation code, run directly, returns `0.6`. This port
  copies the value obtained by running the code, since that is stronger evidence than
  the documentation it disagrees with.
- **Requests are not authenticated.** supermemory's real API is scoped by an API key.
  This port has no request-authentication surface — any caller can name any container
  tag — because authenticating a request was not part of the ingestion/recall/scoring
  slice this port covers. A caller-supplied container tag is trusted as given.
- **`rerank`, query rewriting, and file/OCR/transcription extraction are not
  implemented.** All three are the closed extraction and ranking engine itself, not the
  memory store or its scoring, so none of them has a route into this port.
- **Search over raw, un-chunked documents as a mode separate from memories does not
  exist.** supermemory can search memories and document chunks as two different kinds of
  result. This port stores both directly-created facts and content chunks as the same
  kind of record, so a search response reports each under `memory` or `chunk` by how it
  was created, not by querying two separate stores.

---

## Licence

supermemoryai/supermemory is MIT licensed, © supermemory 2025. This port reimplements
the documented behaviour described above, and one scoring formula translated directly
from the source, without copying any other source; see `ACKNOWLEDGEMENTS.md`.
