# Pustakalay.ai 📚

Pustakalay.ai is a premium, high-performance **Personal Knowledge Platform** powered by Retrieval-Augmented Generation (RAG). It enables users to drop documents (PDFs) into a watched directory, automatically processes and indexes them using semantic chunking and vector embeddings, and provides a stateful chat interface to query the knowledge base with precise citations.

---

## 🏗️ System Architecture & Tech Stack

### Backend (`/backend`)
* **Framework**: Spring Boot 3.3.2, Spring AI
* **Database**: PostgreSQL (relational metadata storage)
* **Vector Store**: Qdrant (vector embeddings storage & similarity search)
* **Message Broker**: Apache Kafka (decoupled asynchronous indexing pipeline)
* **LLM & Embeddings**: Ollama (`qwen3:8b` for chat, `nomic-embed-text` for embeddings)

### Frontend (`/frontend`)
* **Framework**: React, Vite, TypeScript
* **Icons**: Lucide Icons
* **Styling**: Vanilla CSS (sleek dark mode, glassmorphism, responsive design)

---

## 📁 Key Directories & Storage Paths

* **Incoming Folder**: `D:\Library\Incoming`
  * Drop new PDF files here. The system watches this folder and automatically registers new books.
* **Indexed Folder**: `D:\Library\Indexed`
  * Once indexing is successfully completed, files are automatically moved here.

---

## ⚙️ Prerequisites

Ensure you have the following installed and configured:
1. **Java 17+** & **Maven**
2. **Node.js 18+** & **npm**
3. **Docker** (for running Qdrant and Kafka)
4. **PostgreSQL** running locally on port `5432`
   * Create a database named `pustakalay_ai`.
   * Default credentials configured: `username: postgres`, `password: 08102004`.
5. **Ollama** running locally on port `11434`
   * Pull the required models:
     ```bash
     ollama pull qwen3:8b
     ollama pull nomic-embed-text
     ```

---

## 🚀 How to Run the Application

Follow these steps to start all services and run the application:

### Step 1: Start Infrastructure Services (Docker & Local)

1. **Start Qdrant**:
   ```bash
   docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
   ```
   *(If the container already exists, run `docker start qdrant`)*

2. **Start Kafka & Zookeeper**:
   ```bash
   docker start kafkadocker-zookeeper-1 kafkadocker-kafka-1
   ```

3. **Start Ollama**:
   Ensure Ollama is running in the background or run:
   ```bash
   ollama serve
   ```

4. **Start PostgreSQL**:
   Ensure your local PostgreSQL service is running.

---

### Step 2: Run the Backend

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
   The backend will start on `http://localhost:8080`.

---

### Step 3: Run the Frontend

1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```
2. Install dependencies (if running for the first time):
   ```bash
   npm install
   ```
3. Run the Vite development server:
   ```bash
   npm run dev
   ```
   The frontend will start on `http://localhost:5173`.

---

## 📖 Ingestion & Usage Flow

1. **Drop a PDF**: Place any PDF file into `D:\Library\Incoming`.
2. **Auto-Detection**: The backend's `FileWatcherService` detects the file, registers it in PostgreSQL, and publishes an indexing job to Kafka.
3. **Asynchronous Indexing**: The Kafka consumer picks up the job, parses the PDF, chunks it semantically, generates vector embeddings via Ollama, and saves them to Qdrant.
4. **Move to Indexed**: Once completed, the PDF is moved to `D:\Library\Indexed`.
5. **Chat & Query**: Open `http://localhost:5173`, navigate to the **Chat** tab, and start querying your library. The system will retrieve the most relevant context chunks and cite the source document, chapter, and page numbers.
6. **Dynamic Settings**: Tweak RAG parameters (like `top_k`, `temperature`, and models) on the fly in the **Settings** tab.
