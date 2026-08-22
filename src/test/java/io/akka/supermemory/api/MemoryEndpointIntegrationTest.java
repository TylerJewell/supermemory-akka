package io.akka.supermemory.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.supermemory.application.MemoryStoreEntity;
import io.akka.supermemory.domain.Memory;
import io.akka.supermemory.domain.ScoredMemory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 against a running service, through the real HTTP endpoint. */
public class MemoryEndpointIntegrationTest extends TestKitSupport {

  @Test
  public void ingestAndRecallWithScoringEndToEnd() {
    String tag = "it_user_" + System.nanoTime();

    var addResponse =
        httpClient
            .POST("/containers/" + tag + "/memories")
            .withRequestBody(
                new MemoryEndpoint.AddMemoriesRequest(
                    List.of(
                        new MemoryStoreEntity.MemoryInput(
                            "the user prefers dark mode and dark themes", false, Map.of()),
                        new MemoryStoreEntity.MemoryInput(
                            "quarterly revenue forecast spreadsheet", false, Map.of()))))
            .responseBodyAs(Memory[].class)
            .invoke();
    assertThat(addResponse.httpResponse().status().isSuccess()).isTrue();
    assertThat(addResponse.body()).hasSize(2);

    var searchResponse =
        httpClient
            .POST("/containers/" + tag + "/memories/search")
            .withRequestBody(new MemoryEndpoint.SearchRequest("dark mode preference", 0.2, 10, false))
            .responseBodyAs(ScoredMemory[].class)
            .invoke();
    assertThat(searchResponse.httpResponse().status().isSuccess()).isTrue();
    assertThat(searchResponse.body()).isNotEmpty();
    assertThat(searchResponse.body()[0].memory().content()).contains("dark mode");

    String targetId = addResponse.body()[0].id();
    var forgetResponse =
        httpClient
            .DELETE("/containers/" + tag + "/memories")
            .withRequestBody(new MemoryEndpoint.ForgetRequest(targetId, null, "no longer needed"))
            .responseBodyAs(MemoryStoreEntity.ForgetOneResult.class)
            .invoke();
    assertThat(forgetResponse.httpResponse().status().isSuccess()).isTrue();
    assertThat(forgetResponse.body().found()).isTrue();

    var afterForget =
        httpClient
            .POST("/containers/" + tag + "/memories/search")
            .withRequestBody(new MemoryEndpoint.SearchRequest("dark mode preference", 0.2, 10, false))
            .responseBodyAs(ScoredMemory[].class)
            .invoke();
    assertThat(afterForget.body()).isEmpty();
  }
}
