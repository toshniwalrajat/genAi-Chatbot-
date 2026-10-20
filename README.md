# Local GenAI Chatbot (Spring Boot + LM Studio)

A beginner-friendly Java 21 backend that streams responses from a model running locally in LM Studio.

## Features

- Java 21, Maven, Spring Boot 3.5
- Controller -> Service structure
- Streaming responses using Server-Sent Events (SSE)
- CostGuard: maximum prompt length and per-client request limit
- In-memory cache for completed answers (10-minute default TTL)
- No OpenAI cloud API key, tool calling, database, or structured output

## 1. Prepare LM Studio

1. Open LM Studio and load your local model.
2. On a computer with 4 GB RAM and no dedicated GPU, use a small quantized model such as Qwen2.5-Coder-1.5B-Instruct Q4_K_M, if it is already installed and works acceptably.
3. Open the Developer tab and start the local server on port `1234`.
4. Check the model ID by opening `http://localhost:1234/v1/models` in your browser.
5. Copy the exact `id` returned by LM Studio and put it in `lmstudio.model` in `src/main/resources/application.properties`.

Example model IDs vary by model and LM Studio version. Do not assume the example value in the properties file exactly matches yours.

## 2. Run the Spring Boot project

Requirements:
- Java 21
- Maven 3.6.3 or newer
- LM Studio running with a model loaded and its server enabled

From the folder containing `pom.xml`:

```bash
mvn clean spring-boot:run
```

Health check:

```text
GET http://localhost:8080/api/chat/health
```

## 3. Test streaming with curl

Keep LM Studio running. In PowerShell, run:

```powershell
curl.exe -N -X POST "http://localhost:8080/api/chat/stream" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  -d "{\"question\":\"Explain Java classes with a simple example\"}"
```

Expected response is a stream of events similar to:

```text
event: token
data: A class in Java is...

event: token
data: ...

event: done
data: [DONE]
```

Each model delta is delivered as an SSE event. A cache hit is also emitted in small chunks.

## API request

`POST /api/chat/stream`

```json
{
  "question": "Explain dependency injection in Spring Boot"
}
```

## Settings

Edit `src/main/resources/application.properties`:

```properties
lmstudio.base-url=http://localhost:1234
lmstudio.model=PASTE_EXACT_MODEL_ID_FROM_LM_STUDIO
lmstudio.temperature=0.2
lmstudio.max-tokens=512
lmstudio.timeout-seconds=180
chat.max-input-characters=2000
chat.requests-per-minute=10
chat.cache-ttl-seconds=600
```

`max-tokens` limits generated output. `max-input-characters` limits prompt length. `requests-per-minute` is a simple in-memory per-client limit. The local cache only stores completed answers and clears expired entries when the same question is requested again; for a long-running production app, add scheduled cache cleanup or use a cache library.

## Important notes

- This is a learning project, not a production security boundary. The in-memory request limit resets when the app restarts and is not shared across multiple app instances.
- The cache key is the normalized question text; only identical questions (ignoring case and outer whitespace) reuse a saved answer.
- If the backend cannot connect, verify the LM Studio server is enabled on port 1234.
- If LM Studio reports an unknown model, update `lmstudio.model` using the exact ID from `/v1/models`.
- Streaming requires a client that supports Server-Sent Events. Browser `EventSource` does not natively send POST requests; a React frontend can use `fetch()` and read the response stream, or a dedicated SSE client.
- This starter does not store chat history. Each request is an independent question.
