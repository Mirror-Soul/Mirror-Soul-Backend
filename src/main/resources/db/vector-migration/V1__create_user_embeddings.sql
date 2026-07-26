CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS recommendation;

CREATE TABLE recommendation.user_embeddings (
    user_uuid UUID PRIMARY KEY,

    job_embedding vector(1536),
    profile_embedding vector(1536),
    clone_summary_embedding vector(1536),
    conversation_embedding vector(1536),
    interview_embedding vector(1536),

    job_source_hash VARCHAR(64),
    profile_source_hash VARCHAR(64),
    clone_summary_source_hash VARCHAR(64),
    conversation_source_hash VARCHAR(64),
    interview_source_hash VARCHAR(64),

    embedding_provider VARCHAR(30) NOT NULL DEFAULT 'QWEN',
    embedding_model VARCHAR(100) NOT NULL DEFAULT 'Qwen/Qwen3-Embedding-8B',
    embedding_dimension INTEGER NOT NULL DEFAULT 1536,
    embedding_version INTEGER NOT NULL DEFAULT 1,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_user_embeddings_dimension CHECK (embedding_dimension = 1536)
);
