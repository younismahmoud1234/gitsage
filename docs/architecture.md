# Architecture

GitSage follows a clean, layered architecture designed for extensibility and maintainability.

## High-Level Architecture

```mermaid
graph TB
    subgraph "Clients"
        A[GitHub Copilot] -->|SSE| B
        C[REST API Client] -->|HTTP| D
        E[Scheduled Cron] -->|Timer| F
    end

    subgraph "GitSage Application"
        B[Copilot Extension Controller]
        D[Chat Controller]
        G[Index Controller]
        F[Indexing Scheduler]

        B --> H[RAG Service]
        D --> H
        G --> I[GitHub Indexer]
        F --> I

        H --> J[Retrieval Service]
        H --> K[LLM Client]

        J --> L[Embedding Service]
        J --> M[Vector Store]

        I --> N[GitHub Client]
        I --> O[Document Chunker]
        I --> L
        I --> M
    end

    subgraph "External Services"
        K -->|API| P[OpenAI GPT-4o]
        L -->|API| Q[OpenAI Embeddings]
        N -->|REST API| R[GitHub API]
        M -->|JDBC| S[(PostgreSQL + pgvector)]
    end

    style B fill:#6f42c1,color:#fff
    style H fill:#0969da,color:#fff
    style I fill:#1a7f37,color:#fff
    style S fill:#e34c26,color:#fff
```

## Data Flow

### Indexing Pipeline

```mermaid
sequenceDiagram
    participant S as Scheduler/API
    participant I as GitHubIndexer
    participant G as GitHubClient
    participant C as DocumentChunker
    participant E as EmbeddingService
    participant V as PgVectorStore

    S->>I: indexOrg()
    I->>G: listOrgRepos()
    G-->>I: repos[]

    loop Each Repository
        I->>G: fetchReadme(repo)
        I->>G: listRepoTree(repo)

        loop Each File
            I->>G: fetchFileContent(repo, path)
            G-->>I: content
            I->>C: chunk(content)
            C-->>I: chunks[]
            I->>E: embedBatch(chunks)
            E-->>I: embeddings[]
            I->>V: store(docId, chunk, embedding)
        end
    end

    I-->>S: IndexingSummary
```

### RAG Query Pipeline

```mermaid
sequenceDiagram
    participant U as User/Copilot
    participant C as ChatController
    participant R as RagService
    participant Ret as RetrievalService
    participant E as EmbeddingService
    participant V as VectorStore
    participant L as LLM (GPT-4o)

    U->>C: POST /api/chat {question}
    C->>R: chat(question)
    R->>Ret: retrieve(question)
    Ret->>E: embed(question)
    E-->>Ret: queryEmbedding
    Ret->>V: search(embedding, threshold)
    V-->>Ret: SearchResult[]
    Ret-->>R: context
    R->>L: generate(systemPrompt + context + question)
    L-->>R: answer
    R-->>C: answer
    C-->>U: ChatResponse
```

## Package Structure

| Package | Responsibility |
|---------|---------------|
| `dev.gitsage.config` | Application configuration (records, type-safe) |
| `dev.gitsage.github` | GitHub API client, indexer, document model |
| `dev.gitsage.embedding` | Document chunking, embedding generation |
| `dev.gitsage.store` | Vector store interface and pgvector implementation |
| `dev.gitsage.rag` | RAG orchestration, retrieval, prompt templates |
| `dev.gitsage.api` | REST API controllers (Chat, Index) |
| `dev.gitsage.copilot` | GitHub Copilot Extension protocol implementation |

## Key Design Decisions

### Why Micronaut?
- Faster startup than Spring Boot (critical for containerised deployments)
- Lower memory footprint
- GraalVM native image support for future optimisation
- Compile-time DI (no reflection overhead)

### Why pgvector?
- No additional infrastructure — runs inside PostgreSQL
- Excellent HNSW index performance for similarity search
- Mature ecosystem with JDBC support
- One less service to manage vs dedicated vector DBs

### Why LangChain4j?
- Java-native RAG framework (no Python dependency)
- Swappable providers (OpenAI, Ollama, HuggingFace)
- Built-in streaming support
- Active community and frequent releases

### Why OpenAI-compatible SSE for Copilot?
- GitHub Copilot Extensions use the OpenAI chat completions streaming format
- Enables drop-in compatibility with any OpenAI-compatible client
- Future-proof for protocol evolution
