# AI - Spring AI 2.x

Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x. Use it only when the app calls an LLM, an embedding model or a vector store. Check the version-specific details below against the [Spring AI reference](https://docs.spring.io/spring-ai/reference/) before relying on them: the API moves fast and 1.x snippets from blogs often no longer compile.

## Dependencies

Import the BOM, then add starters without versions. Releases come from Maven Central.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.2</version> <!-- use the latest 2.0.x release -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId> <!-- spring-ai-starter-model-<provider> -->
    </dependency>
</dependencies>
```

Starters are named `spring-ai-starter-model-<provider>`. Plain `spring-ai-<provider>` artifacts are modules without autoconfiguration. Take a milestone or RC (for example 2.1.0-RC2) only when asked, and then add the Spring snapshot/milestone repositories.

## Configuration

Secrets come from the environment, never from the repo (see `conventions`):

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
```

Model, temperature and other options differ per provider: take the property names from the provider page. In 2.0 the `.options` segment is dropped for non-chat models (`spring.ai.openai.embedding.model`, not `...embedding.options.model`).

## ChatClient

Inject the autoconfigured `ChatClient.Builder`. Do not call `ChatClient.create(chatModel)` or `ChatClient.builder(chatModel)` in application code: they skip observability and `ChatClientBuilderCustomizer` beans.

```java
@Service
public class AssistantService {

    private final ChatClient chatClient;

    public AssistantService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You answer questions about our product catalog.")
                .build();
    }

    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
```

With several models, build one `ChatClient` bean per model from `ChatClientBuilderConfigurer` and mark one `@Primary`.

`call()` only selects synchronous execution; the model runs on `content()`, `chatResponse()` or `entity()`. `chatResponse()` gives token usage metadata for metrics.

### Structured output

```java
public record Recommendation(String title, List<String> reasons) {}

Recommendation result = chatClient.prompt()
        .user("Recommend one book about Spring")
        .call()
        .entity(Recommendation.class);

List<Recommendation> list = chatClient.prompt()
        .user("Recommend three books about Spring")
        .call()
        .entity(new ParameterizedTypeReference<List<Recommendation>>() {});
```

Use records for output types. For flaky models add `.entity(Recommendation.class, spec -> spec.useProviderStructuredOutput().validateSchema())`. `entity()` is not supported on `stream()`: collect the text and convert with `BeanOutputConverter`.

### Streaming

`stream().content()` returns a `Flux<String>` and needs the reactive stack (`spring-boot-starter-webflux`) on the classpath. Keep blocking `call()` for ordinary MVC endpoints.

```java
@GetMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chat(@RequestParam String message) {
    return chatClient.prompt().user(message).stream().content();
}
```

## Tool calling

The model asks, your application executes. Describe every tool: without `description` the model has no hint when to call it.

```java
@Component
public class OrderTools {

    private final OrderService orderService;

    public OrderTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @Tool(description = "Get the status of an order by its id")
    public String orderStatus(@ToolParam(description = "Order id") Long orderId) {
        return orderService.status(orderId);
    }
}

String answer = chatClient.prompt()
        .user("Where is order 42?")
        .tools(orderTools)
        .call()
        .content();
```

- `tools(...)` per call appends to `defaultTools(...)`. Put read-only tools in the defaults, destructive ones on the single call that needs them.
- Tool parameters are required by default; a model asked for a value it cannot know invents one. Mark real optionals with `@ToolParam(required = false)`.
- A `RuntimeException` from a tool is sent back to the model as the error message so it can recover; checked exceptions are rethrown. Do not put secrets or stack traces in exception messages. `spring.ai.tools.throw-exception-on-error=true` throws to the caller instead.
- `MethodToolCallback` does not support `Optional`, `Future`, `Mono`/`Flux` or `Function`/`Supplier` parameters and return types.
- Tool names must be unique. `ChatClient` fails on duplicates; a hand-written loop over `ChatModel` does not, so a remote MCP tool could shadow a trusted local one.
- The model decides which tool runs and with what arguments, so authorize inside the tool method (the current user's rights), not only at the endpoint.

## RAG

Add `spring-ai-vector-store-advisor` for `QuestionAnswerAdvisor`, or `spring-ai-rag` for `RetrievalAugmentationAdvisor`, plus a vector store starter.

```java
var qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
        .searchRequest(SearchRequest.builder().similarityThreshold(0.8d).topK(6).build())
        .build();

String answer = chatClient.prompt()
        .advisors(qaAdvisor)
        .user(question)
        .call()
        .content();
```

- Per-request metadata filter, portable across stores: `.advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "tenant == 'acme'"))`. Derive the value from the authenticated user on the server, never from request text; it is the multi-tenant boundary.
- `RetrievalAugmentationAdvisor` with `VectorStoreDocumentRetriever` is the modular variant (query rewriting, post-processing). By default an empty retrieval makes the model decline to answer; allow it with `ContextualQueryAugmenter.builder().allowEmptyContext(true)`.
- Ingest documents with `vectorStore.add(List<Document>)` from a startup runner or a job, not in the request path.

## Chat memory

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();

ChatClient client = builder
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();

client.prompt()
        .user(message)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call()
        .content();
```

- `ChatMemory.CONVERSATION_ID` is required on every call and has no default (`IllegalArgumentException` otherwise). Derive it on the server from the authenticated user (plus a conversation id), never from a client-supplied value alone, or users read each other's history.
- `PromptChatMemoryAdvisor` is gone: use `MessageChatMemoryAdvisor`.
- Persistent memory: `spring-ai-starter-model-chat-memory-repository-jdbc`. Tool call messages are not persisted. With Flyway owning the schema, set `spring.ai.chat.memory.repository.jdbc.initialize-schema=never` and add the `SPRING_AI_CHAT_MEMORY` table in a migration (copy the vendor script from the starter). The 2.0 table has a `sequence_id` column used for ordering; existing tables need it added.

## MCP server

Expose tools to MCP clients with the server starter for your stack: `spring-ai-starter-mcp-server-webmvc` or `-webflux` (Streamable HTTP), `spring-ai-starter-mcp-server` (stdio).

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE   # STREAMABLE | STATELESS; SSE is deprecated since 2.0
        type: SYNC
```

```java
@Component
public class CatalogTools {

    @McpTool(name = "find-product", description = "Find a product by name")
    public String findProduct(@McpToolParam(description = "Product name", required = true) String name) {
        return "...";
    }
}
```

The HTTP transport exposes an unauthenticated endpoint (`POST /mcp`) that lists and runs every registered tool. Put Spring Security in front of it before it is reachable beyond localhost.

## Testing

- Test your own code (prompt assembly, tool methods, controllers) without a model: unit-test `@Tool` methods as plain methods, and stub `ChatModel` for the `ChatClient`. A plain Mockito mock can return `null` from methods the builder calls (for example `getOptions()`); stub them or write a small `ChatModel` implementation.
- Run vector stores against a real container with `@ServiceConnection` (for example a PostgreSQL container with a pgvector image), as in `references/testing.md`.
- Do not call a paid provider from the default test run. Evaluator tests (`RelevancyEvaluator`, `FactCheckingEvaluator`) need a real model: keep them in a separate profile or disable them unless an API key is present.

## Upgrading from 1.x

| 1.x | 2.0 |
|-----|-----|
| `FunctionCallback`, `FunctionCallback.builder().function(...)` | `ToolCallback`, `FunctionToolCallback.builder(name, fn)` |
| `defaultFunctions(...)`, `functions(...)` | `defaultTools(...)`, `tools(...)` |
| `ToolCallAdvisor` | `ToolCallingAdvisor` (auto-registered by `ChatClient`; remove explicit registrations) |
| `spring-ai-advisors-vector-store` | `spring-ai-vector-store-advisor` |
| `PromptChatMemoryAdvisor`, `ChatMemory.DEFAULT_CONVERSATION_ID` | `MessageChatMemoryAdvisor`, explicit `ChatMemory.CONVERSATION_ID` |
| `internalToolExecutionEnabled(...)`, `SpringBeanToolCallbackResolver`, `toolNames(...)` | removed: `ChatClient` + `ToolCallingAdvisor`, or `ToolCallback` beans / `@Tool` methods |
| `ChatClient.options(builtOptions)` | `options(optionsBuilder)`; copy with `mutate().build()` instead of `copy()` |
| `org.springaicommunity.mcp.annotation.*` | `org.springframework.ai.mcp.annotation.*` |
| `spring.ai.<provider>.embedding.options.model` | `spring.ai.<provider>.embedding.model` |

The JSON API moved to Jackson 3 (`tools.jackson`), matching Spring Boot 4. The full list is in the [upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html).

## Rules

- `ChatClient.Builder` is injected through the constructor; no `ChatClient.create(...)` in application code.
- API keys and tenant/user identifiers come from the environment and the security context, not from code or request text.
- Every `@Tool` has a description, and authorization is checked inside the tool.
- Conversation ids are derived on the server.
- Default tests run without a real model or API key.
