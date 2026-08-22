package io.akka.supermemory.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.supermemory.domain.Memory;
import io.akka.supermemory.domain.MemoryEvent;
import io.akka.supermemory.domain.MemoryStoreState;
import io.akka.supermemory.domain.ScoredMemory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 rules 1, 3-10 as a caller sees them, through the entity. */
public class MemoryStoreEntityTest {

  private EventSourcedTestKit<MemoryStoreState, MemoryEvent, MemoryStoreEntity> store() {
    return EventSourcedTestKit.of("user_123", MemoryStoreEntity::new);
  }

  private MemoryStoreEntity.MemoryInput input(String content) {
    return new MemoryStoreEntity.MemoryInput(content, false, Map.of());
  }

  // -- Rule 1: direct-create validation --------------------------------------------------

  @Test
  public void addDirectRejectsEmptyBatch() {
    var kit = store();
    var result =
        kit.method(MemoryStoreEntity::addDirect).invoke(new MemoryStoreEntity.AddDirect(List.of()));
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void addDirectRejectsBatchOver100() {
    var kit = store();
    var oversized = java.util.stream.IntStream.range(0, 101).mapToObj(i -> input("fact " + i)).toList();
    var result =
        kit.method(MemoryStoreEntity::addDirect).invoke(new MemoryStoreEntity.AddDirect(oversized));
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void addDirectRejectsBlankContent() {
    var kit = store();
    var result =
        kit.method(MemoryStoreEntity::addDirect)
            .invoke(new MemoryStoreEntity.AddDirect(List.of(input("   "))));
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void addDirectRejectsContentOverMaxLength() {
    var kit = store();
    String tooLong = "x".repeat(MemoryStoreState.MAX_CONTENT_LENGTH + 1);
    var result =
        kit.method(MemoryStoreEntity::addDirect)
            .invoke(new MemoryStoreEntity.AddDirect(List.of(input(tooLong))));
    assertThat(result.isError()).isTrue();
  }

  // -- Rule 3: every stored entry has an embedding -----------------------------------------

  @Test
  public void addDirectCreatesMemoriesWithEmbeddings() {
    var kit = store();
    var result =
        kit.method(MemoryStoreEntity::addDirect)
            .invoke(new MemoryStoreEntity.AddDirect(List.of(input("John prefers dark mode"))));

    assertThat(result.getReply()).hasSize(1);
    Memory memory = result.getReply().get(0);
    assertThat(memory.embedding()).isNotEmpty();
    assertThat(memory.containerTag()).isEqualTo("user_123");
    assertThat(memory.version()).isEqualTo(1);
    assertThat(memory.isLatest()).isTrue();
    assertThat(result.getAllEvents()).hasSize(1);
    assertThat(result.getAllEvents()).allMatch(e -> e instanceof MemoryEvent.MemoryAdded);
  }

  // -- Rule 2 (integration): raw content is chunked on blank lines --------------------------

  @Test
  public void addContentChunksOnBlankLines() {
    var kit = store();
    var result =
        kit.method(MemoryStoreEntity::addContent)
            .invoke(new MemoryStoreEntity.AddContent("first fact\n\nsecond fact", Map.of()));

    assertThat(result.getReply()).hasSize(2);
    assertThat(result.getReply().get(0).content()).isEqualTo("first fact");
    assertThat(result.getReply().get(1).content()).isEqualTo("second fact");
  }

  // -- Rules 4-5: search scores, filters by threshold, sorts, limits -----------------------

  @Test
  public void searchReturnsAboveThresholdSortedDescending() {
    var kit = store();
    kit.method(MemoryStoreEntity::addDirect)
        .invoke(
            new MemoryStoreEntity.AddDirect(
                List.of(
                    input("the user prefers dark mode and dark themes"),
                    input("quarterly revenue forecast spreadsheet"))));

    var result =
        kit.method(MemoryStoreEntity::search)
            .invoke(new MemoryStoreEntity.Search("dark mode themes preference", 0.0, 10, false));

    List<ScoredMemory> scored = result.getReply();
    assertThat(scored).isNotEmpty();
    assertThat(scored.get(0).memory().content()).contains("dark mode");
    for (int i = 1; i < scored.size(); i++) {
      assertThat(scored.get(i - 1).similarity()).isGreaterThanOrEqualTo(scored.get(i).similarity());
    }
  }

  @Test
  public void searchExcludesForgottenByDefault() {
    var kit = store();
    var added =
        kit.method(MemoryStoreEntity::addDirect)
            .invoke(new MemoryStoreEntity.AddDirect(List.of(input("temporary fact about a trip"))))
            .getReply();
    String id = added.get(0).id();
    kit.method(MemoryStoreEntity::forgetOne)
        .invoke(new MemoryStoreEntity.ForgetOne(id, null, "no longer relevant"));

    var excluding =
        kit.method(MemoryStoreEntity::search)
            .invoke(new MemoryStoreEntity.Search("trip", 0.0, 10, false));
    assertThat(excluding.getReply()).isEmpty();

    var including =
        kit.method(MemoryStoreEntity::search)
            .invoke(new MemoryStoreEntity.Search("trip", 0.0, 10, true));
    assertThat(including.getReply()).hasSize(1);
  }

  // -- Rule 6: forget by id or content preserves content and embedding --------------------

  @Test
  public void forgetByIdPreservesContentAndEmbedding() {
    var kit = store();
    var added =
        kit.method(MemoryStoreEntity::addDirect)
            .invoke(new MemoryStoreEntity.AddDirect(List.of(input("John is from Seattle"))))
            .getReply();
    Memory original = added.get(0);

    var result =
        kit.method(MemoryStoreEntity::forgetOne)
            .invoke(new MemoryStoreEntity.ForgetOne(original.id(), null, "moved away"));

    assertThat(result.getReply().found()).isTrue();
    Memory forgotten = result.getReply().memory();
    assertThat(forgotten.isForgotten()).isTrue();
    assertThat(forgotten.forgetReason()).isEqualTo("moved away");
    assertThat(forgotten.content()).isEqualTo(original.content());
    assertThat(forgotten.embedding()).isEqualTo(original.embedding());
  }

  @Test
  public void forgetByContentMatchesExactText() {
    var kit = store();
    kit.method(MemoryStoreEntity::addDirect)
        .invoke(new MemoryStoreEntity.AddDirect(List.of(input("John likes tea"))));

    var result =
        kit.method(MemoryStoreEntity::forgetOne)
            .invoke(new MemoryStoreEntity.ForgetOne(null, "John likes tea", null));

    assertThat(result.getReply().found()).isTrue();
  }

  @Test
  public void forgetOneNotFoundReturnsFoundFalse() {
    var kit = store();
    var result =
        kit.method(MemoryStoreEntity::forgetOne)
            .invoke(new MemoryStoreEntity.ForgetOne("no-such-id", null, null));
    assertThat(result.getReply().found()).isFalse();
    assertThat(result.getAllEvents()).isEmpty();
  }

  // -- Rule 7: forget-matching dryRun scores but mutates nothing ---------------------------

  @Test
  public void forgetMatchingDryRunReturnsCandidatesWithoutMutating() {
    var kit = store();
    kit.method(MemoryStoreEntity::addDirect)
        .invoke(new MemoryStoreEntity.AddDirect(List.of(input("Project Titan ships in Q3"))));

    var result =
        kit.method(MemoryStoreEntity::forgetMatching)
            .invoke(new MemoryStoreEntity.ForgetMatching("Project Titan", null, true, 0.0, 100, null));

    assertThat(result.getReply().dryRun()).isTrue();
    assertThat(result.getReply().candidates()).hasSize(1);
    assertThat(result.getAllEvents()).isEmpty();

    var stillThere = kit.method(MemoryStoreEntity::getMemories).invoke();
    assertThat(stillThere.getReply()).hasSize(1);
  }

  // -- Rule 8: apply by query re-scores; apply by ids ignores unknown ids ------------------

  @Test
  public void forgetMatchingApplyByQueryTagsSharedBatchId() {
    var kit = store();
    kit.method(MemoryStoreEntity::addDirect)
        .invoke(
            new MemoryStoreEntity.AddDirect(
                List.of(input("Project Titan ships in Q3"), input("Project Titan budget approved"))));

    var result =
        kit.method(MemoryStoreEntity::forgetMatching)
            .invoke(new MemoryStoreEntity.ForgetMatching("Project Titan", null, false, 0.0, 100, "cancelled"));

    assertThat(result.getReply().dryRun()).isFalse();
    assertThat(result.getReply().forgotten()).hasSize(2);
    String batchId = result.getReply().forgetBatchId();
    assertThat(batchId).isNotNull();
    assertThat(result.getReply().forgotten()).allMatch(m -> batchId.equals(m.forgetBatchId()));
  }

  @Test
  public void forgetMatchingApplyByIdsIgnoresUnknownIds() {
    var kit = store();
    var added =
        kit.method(MemoryStoreEntity::addDirect)
            .invoke(new MemoryStoreEntity.AddDirect(List.of(input("keep this one distinct fact"))))
            .getReply();
    String realId = added.get(0).id();

    var result =
        kit.method(MemoryStoreEntity::forgetMatching)
            .invoke(
                new MemoryStoreEntity.ForgetMatching(
                    null, List.of(realId, "unknown-id"), false, null, null, "cleanup"));

    assertThat(result.getReply().forgotten()).hasSize(1);
    assertThat(result.getReply().forgotten().get(0).id()).isEqualTo(realId);
  }

  // -- Rule 9: versioned update supersedes without deleting the prior version --------------

  @Test
  public void updateMemoryCreatesNewVersionAndSupersedesOld() {
    var kit = store();
    var added =
        kit.method(MemoryStoreEntity::addDirect)
            .invoke(new MemoryStoreEntity.AddDirect(List.of(input("Original content goes here"))))
            .getReply();
    Memory original = added.get(0);

    var result =
        kit.method(MemoryStoreEntity::updateMemory)
            .invoke(
                new MemoryStoreEntity.UpdateMemory(
                    original.id(), null, "Updated content replaces it", null));

    Memory updated = result.getReply();
    assertThat(updated.content()).isEqualTo("Updated content replaces it");
    assertThat(updated.version()).isEqualTo(2);
    assertThat(updated.parentMemoryId()).isEqualTo(original.id());
    assertThat(updated.isLatest()).isTrue();

    var current = kit.method(MemoryStoreEntity::getMemories).invoke().getReply();
    assertThat(current).hasSize(1);
    assertThat(current.get(0).content()).isEqualTo("Updated content replaces it");
  }

  @Test
  public void updateMemoryNotFoundReturnsError() {
    var kit = store();
    var result =
        kit.method(MemoryStoreEntity::updateMemory)
            .invoke(new MemoryStoreEntity.UpdateMemory("missing", null, "new text", null));
    assertThat(result.isError()).isTrue();
  }

  // -- Rule 10: listing returns current, non-forgotten, most-recently-updated first --------

  @Test
  public void getMemoriesReturnsCurrentNonForgottenMostRecentFirst() throws InterruptedException {
    var kit = store();
    kit.method(MemoryStoreEntity::addDirect).invoke(new MemoryStoreEntity.AddDirect(List.of(input("first fact"))));
    Thread.sleep(5);
    kit.method(MemoryStoreEntity::addDirect).invoke(new MemoryStoreEntity.AddDirect(List.of(input("second fact"))));

    var memories = kit.method(MemoryStoreEntity::getMemories).invoke();
    assertThat(memories.getReply()).hasSize(2);
    assertThat(memories.getReply().get(0).content()).isEqualTo("second fact");
  }
}
