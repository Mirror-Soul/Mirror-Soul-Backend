ALTER TABLE recommendation.user_embeddings
    ALTER COLUMN embedding_provider SET DEFAULT 'OPENAI',
    ALTER COLUMN embedding_model SET DEFAULT 'text-embedding-3-small';
