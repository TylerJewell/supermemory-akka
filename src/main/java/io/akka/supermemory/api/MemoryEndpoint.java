package io.akka.supermemory.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.supermemory.application.MemoryStoreEntity;
import io.akka.supermemory.domain.Memory;
import io.akka.supermemory.domain.ScoredMemory;
import java.util.List;
import java.util.Map;

/** SPEC-001 — ingestion and recall over a memory store with scoring, one container tag at a time. */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/containers/{containerTag}/memories")
public class MemoryEndpoint {

  private final ComponentClient componentClient;

  public MemoryEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record AddMemoriesRequest(List<MemoryStoreEntity.MemoryInput> memories) {}

  @Post
  public List<Memory> addMemories(String containerTag, AddMemoriesRequest body) {
    return componentClient
        .forEventSourcedEntity(containerTag)
        .method(MemoryStoreEntity::addDirect)
        .invoke(new MemoryStoreEntity.AddDirect(body.memories()));
  }

  public record AddContentRequest(String content, Map<String, Object> metadata) {}

  @Post("/documents")
  public List<Memory> addContent(String containerTag, AddContentRequest body) {
    return componentClient
        .forEventSourcedEntity(containerTag)
        .method(MemoryStoreEntity::addContent)
        .invoke(new MemoryStoreEntity.AddContent(body.content(), body.metadata()));
  }

  @Get
  public List<Memory> list(String containerTag) {
    return componentClient
        .forEventSourcedEntity(containerTag)
        .method(MemoryStoreEntity::getMemories)
        .invoke();
  }

  public record SearchRequest(String q, Double threshold, Integer limit, boolean includeForgotten) {}

  @Post("/search")
  public List<ScoredMemory> search(String containerTag, SearchRequest body) {
    return componentClient
        .forEventSourcedEntity(containerTag)
        .method(MemoryStoreEntity::search)
        .invoke(
            new MemoryStoreEntity.Search(
                body.q(), body.threshold(), body.limit(), body.includeForgotten()));
  }

  public record ForgetRequest(String id, String content, String reason) {}

  @Delete
  public MemoryStoreEntity.ForgetOneResult forget(String containerTag, ForgetRequest body) {
    return componentClient
        .forEventSourcedEntity(containerTag)
        .method(MemoryStoreEntity::forgetOne)
        .invoke(new MemoryStoreEntity.ForgetOne(body.id(), body.content(), body.reason()));
  }

  public record ForgetMatchingRequest(
      String query, List<String> ids, boolean dryRun, Double threshold, Integer maxForget, String reason) {}

  @Post("/forget-matching")
  public MemoryStoreEntity.ForgetMatchResult forgetMatching(String containerTag, ForgetMatchingRequest body) {
    return componentClient
        .forEventSourcedEntity(containerTag)
        .method(MemoryStoreEntity::forgetMatching)
        .invoke(
            new MemoryStoreEntity.ForgetMatching(
                body.query(), body.ids(), body.dryRun(), body.threshold(), body.maxForget(), body.reason()));
  }

  public record UpdateRequest(String id, String content, String newContent, Map<String, Object> metadata) {}

  @Patch
  public Memory update(String containerTag, UpdateRequest body) {
    return componentClient
        .forEventSourcedEntity(containerTag)
        .method(MemoryStoreEntity::updateMemory)
        .invoke(
            new MemoryStoreEntity.UpdateMemory(
                body.id(), body.content(), body.newContent(), body.metadata()));
  }
}
