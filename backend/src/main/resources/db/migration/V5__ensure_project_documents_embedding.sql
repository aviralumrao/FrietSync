ALTER TABLE project_documents
    ADD COLUMN IF NOT EXISTS embedding vector(1536);

CREATE INDEX IF NOT EXISTS idx_docs_embedding_hnsw
    ON project_documents USING hnsw (embedding vector_cosine_ops);
