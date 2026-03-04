-- GitSage Database Schema
-- Stores indexed documents, embeddings, and indexing state

CREATE EXTENSION IF NOT EXISTS vector;

-- Indexed documents with metadata
CREATE TABLE documents (
    id              BIGSERIAL PRIMARY KEY,
    repo_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(1024) NOT NULL,
    doc_type        VARCHAR(50) NOT NULL,
    language        VARCHAR(50),
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    metadata        JSONB DEFAULT '{}',
    indexed_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_documents_repo_path UNIQUE (repo_name, file_path)
);

-- Document chunks with vector embeddings
CREATE TABLE document_chunks (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(1536),
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_chunks_doc_index UNIQUE (document_id, chunk_index)
);

-- HNSW index for fast similarity search
CREATE INDEX idx_chunks_embedding ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Indexing state to track progress
CREATE TABLE indexing_state (
    id              BIGSERIAL PRIMARY KEY,
    org_name        VARCHAR(255) NOT NULL,
    repo_name       VARCHAR(255) NOT NULL,
    last_indexed_at TIMESTAMP WITH TIME ZONE,
    last_commit_sha VARCHAR(40),
    status          VARCHAR(20) DEFAULT 'pending',
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_indexing_state_repo UNIQUE (org_name, repo_name)
);

-- Indexes for common queries
CREATE INDEX idx_documents_repo ON documents(repo_name);
CREATE INDEX idx_documents_type ON documents(doc_type);
CREATE INDEX idx_documents_hash ON documents(content_hash);
CREATE INDEX idx_indexing_state_status ON indexing_state(status);
