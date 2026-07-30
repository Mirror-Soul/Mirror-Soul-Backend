ALTER TABLE recommendation.user_embeddings
    ALTER COLUMN embedding_provider SET DEFAULT 'GEMINI',
    ALTER COLUMN embedding_model SET DEFAULT 'gemini-embedding-001';
