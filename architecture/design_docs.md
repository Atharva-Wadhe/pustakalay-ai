# Pustakalay.ai - System Architecture & Design Specification

This document details the architecture, database schema, sequence diagrams, and chunking strategy for **Pustakalay.ai**, a production-inspired Personal Knowledge Platform.

---

## 1. Architecture Diagram

The system is designed as a modular monolith in Spring Boot, communicating with external services (PostgreSQL, Kafka, Qdrant, Ollama) and serving a React + TypeScript frontend.

```mermaid
graph TD
    subgraph Frontend [Client Layer]
        React[React + TS SPA]
    end

    subgraph Backend [Spring Boot Modular Monolith]
        API[API Gateway / Controllers]
        
        subgraph Modules [Core Modules]
            BookService[Book Module]
            IndexingOrchestrator[Indexing Module]
            ChatService[Chat Module]
            EmbeddingService[Embedding Module]
            RetrievalService[Retrieval Module]
            MetadataService[Metadata Module]
            QdrantClient[Qdrant Client]
            OllamaClient[Ollama Client]
        end
        
        FileWatcher[File Watcher Service]
    end

    subgraph Storage [Storage Layer]
        Postgres[(PostgreSQL)]
        LocalStorage[(Local Storage: Incoming / Indexed)]
    end

    subgraph Messaging [Event Streaming]
        KafkaQueue[[Kafka Broker]]
    end

    subgraph AI [AI & Vector Search]
        Qdrant[(Qdrant Vector DB)]
        Ollama[Ollama LLM & Embedding Server]
    end

    %% Interactions
    React -->|HTTP / REST| API
    FileWatcher -->|Detects PDFs| LocalStorage
    FileWatcher -->|Registers Book| BookService
    BookService -->|Persists Metadata| Postgres
    BookService -->|Triggers Job| IndexingOrchestrator
    
    IndexingOrchestrator -->|Publishes Ingestion Event| KafkaQueue
    KafkaQueue -->|Consumes Event| IndexingOrchestrator
    
    IndexingOrchestrator -->|Reads PDF| LocalStorage
    IndexingOrchestrator -->|Extracts & Chunks| MetadataService
    IndexingOrchestrator -->|Generates Embeddings| EmbeddingService
    EmbeddingService -->|REST API| Ollama
    IndexingOrchestrator -->|Stores Vectors| QdrantClient
    QdrantClient -->|gRPC / REST| Qdrant
    
    ChatService -->|Retrieves Context| RetrievalService
    RetrievalService -->|Vector Search| QdrantClient
    RetrievalService -->|Queries Metadata| Postgres
    ChatService -->|Generates Answer| OllamaClient
    OllamaClient -->|REST API| Ollama
    ChatService -->|Logs Chat & Retrieval| Postgres
```

---

## 2. Database Schema (ER Diagram)

The database schema is designed to support complete traceability, job progress tracking, chat history, retrieval analytics, and configuration management.

```mermaid
erDiagram
    document {
        uuid id PK
        varchar document_type "BOOK, PAPER, NOTE"
        varchar title
        varchar author
        varchar publisher
        varchar isbn
        varchar category
        varchar language
        text description
        varchar file_name
        varchar file_path
        bigint file_size
        varchar file_hash
        integer page_count
        varchar cover_image_path
        varchar metadata_status "DISCOVERED, READY_TO_INDEX, INDEXING, INDEXED, FAILED, DELETED"
        varchar metadata_source
        double metadata_confidence
        varchar status
        timestamp created_at
        timestamp updated_at
    }

    document_event {
        uuid id PK
        uuid document_id FK
        varchar event_type "DISCOVERED, REGISTERED, METADATA_UPDATED, INDEX_REQUESTED, INDEX_STARTED, INDEX_COMPLETED, INDEX_FAILED, CHAT_REFERENCED"
        jsonb event_payload
        timestamp created_at
    }

    indexing_job {
        uuid id PK
        uuid document_id FK
        varchar trigger_type "AUTO, MANUAL, REINDEX"
        varchar status "QUEUED, RUNNING, COMPLETED, FAILED"
        timestamp queued_at
        timestamp started_at
        timestamp completed_at
        integer progress "0-100"
        text error_message
        integer chunk_count
        varchar embedding_model
        varchar chunk_strategy
        integer chunk_size
        integer chunk_overlap
        integer index_version
    }

    document_chunk {
        uuid id PK
        uuid document_id FK
        integer chunk_number
        varchar chapter
        varchar section
        integer page_start
        integer page_end
        varchar text_hash
        integer token_count
        integer character_count
        timestamp created_at
    }

    chat_session {
        uuid id PK
        varchar title
        timestamp created_at
        timestamp updated_at
    }

    chat_message {
        uuid id PK
        uuid session_id FK
        varchar role "user, assistant, system"
        text message
        timestamp created_at
    }

    retrieval_log {
        uuid id PK
        uuid message_id FK
        text query
        integer top_k
        integer retrieval_ms
        integer generation_ms
        integer tokens
        timestamp created_at
    }

    retrieved_chunk {
        uuid id PK
        uuid retrieval_id FK
        uuid chunk_id FK
        double similarity_score
        integer rank
    }

    system_configuration {
        varchar key PK
        varchar value
    }

    document ||--o{ document_event : "has"
    document ||--o{ indexing_job : "spawns"
    document ||--o{ document_chunk : "contains"
    chat_session ||--o{ chat_message : "contains"
    chat_message ||--o| retrieval_log : "logs"
    retrieval_log ||--o{ retrieved_chunk : "references"
    document_chunk ||--o{ retrieved_chunk : "cited_in"
```

---

## 3. Sequence Diagrams

### 3.1 Book Registration and Indexing (Asynchronous Pipeline)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FW as File Watcher
    participant BS as Book Service
    participant DB as PostgreSQL
    participant IO as Indexing Orchestrator
    participant K as Kafka Broker
    participant IW as Indexing Worker
    participant O as Ollama (Embeddings)
    participant Q as Qdrant DB

    alt File Watcher Auto-Detect
        FW->>LocalStorage: Scan Incoming Folder
        LocalStorage-->>FW: New PDF Detected
        FW->>BS: Register Book (File Path, Hash)
    else Manual Registration
        User->>BS: Upload/Register Book
    end

    BS->>DB: Insert Document (Status: DISCOVERED)
    BS->>DB: Insert Document Event (DISCOVERED)
    
    BS->>IO: Request Indexing Job
    IO->>DB: Create Indexing Job (Status: QUEUED)
    IO->>K: Publish Ingestion Event (bookId, jobId)
    IO-->>BS: Job Queued Ack
    
    K->>IW: Consume Ingestion Event (bookId, jobId)
    IW->>DB: Update Job (Status: RUNNING)
    IW->>DB: Update Document (Status: INDEXING)
    
    IW->>LocalStorage: Read PDF File
    IW->>IW: Parse PDF (Apache PDFBox)
    IW->>IW: Extract Pages, Headings, Paragraphs
    IW->>IW: Apply Semantic Chunking Strategy
    
    loop For Each Chunk
        IW->>O: Generate Embeddings (nomic-embed-text)
        O-->>IW: Vector Embeddings
        IW->>Q: Store Vector + Payload (library_chunks)
        IW->>DB: Insert Document Chunk Metadata
    end
    
    IW->>LocalStorage: Move PDF from Incoming -> Indexed
    IW->>DB: Update Document (Status: INDEXED, file_path)
    IW->>DB: Update Job (Status: COMPLETED, progress: 100)
    IW->>DB: Insert Document Event (INDEX_COMPLETED)
```

### 3.2 Chat Query Execution (Retrieval-Augmented Generation)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant CS as Chat Service
    participant DB as PostgreSQL
    participant RS as Retrieval Service
    participant O as Ollama (Embeddings & LLM)
    participant Q as Qdrant DB

    User->>CS: Send Chat Message (Session ID, Query, Scope)
    CS->>DB: Save Chat Message (Role: user)
    
    CS->>RS: Retrieve Context (Query, Scope, Top-K)
    RS->>O: Generate Query Embedding (nomic-embed-text)
    O-->>RS: Query Vector
    
    RS->>Q: Search Vector Space (Filter by Scope: Document ID, Category, etc.)
    Q-->>RS: Top-K Matching Vectors + Payloads (Chunk IDs, Text, Citations)
    
    RS->>DB: Log Retrieval Event (Query, Latency)
    loop For Each Retrieved Chunk
        RS->>DB: Log Retrieved Chunk (Similarity, Rank, Chunk ID)
    end
    
    RS-->>CS: Context Chunks + Citations
    
    CS->>CS: Build System Prompt (Context + Chat History + User Query)
    CS->>O: Generate Response (qwen3:8b)
    O-->>CS: Generated Response Text
    
    CS->>DB: Save Chat Message (Role: assistant, Message)
    CS-->>User: Return Response + Citations (Sources, Pages, Chapters)
```

---

## 4. Hierarchical Semantic Chunking Strategy

To ensure high-quality retrieval, we implement a **Hierarchical Semantic Chunker**:

1. **Document Parsing**: Extract text while preserving page boundaries and structure.
2. **Heading & Section Detection**: Identify chapters and section headers using font size, style, or regex patterns.
3. **Paragraph Aggregation**: Group sentences into paragraphs. Paragraphs act as the atomic units of text.
4. **Semantic Chunk Assembly**:
   - Accumulate paragraphs into a chunk until the target size (e.g., 600 tokens) is reached.
   - Maintain a sliding window overlap (e.g., 100 tokens) by prepending paragraphs from the end of the previous chunk.
   - Never split a paragraph across chunks unless it exceeds the maximum chunk size.
5. **Metadata Enrichment**: Each chunk is tagged with its document ID, title, author, category, chapter, section, page range, paragraph range, chunk index, and index version. This metadata is stored in both Qdrant (payload) and PostgreSQL (`document_chunk` table) to enable precise filtering and citations.
