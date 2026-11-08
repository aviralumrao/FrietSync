ALTER TABLE sprints ADD COLUMN IF NOT EXISTS embedding vector(1536);

CREATE INDEX IF NOT EXISTS idx_sprints_embedding_hnsw
    ON sprints USING hnsw (embedding vector_cosine_ops);
