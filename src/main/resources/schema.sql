-- pgvector and pg_trgm extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Embedding columns (added to existing job_listings table)
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS embedding vector(768);
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMP;
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(255);

-- Generated tsvector column for full-text search
-- Drop plain column first if Hibernate created it (non-generated), then recreate as generated
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'job_listings' AND column_name = 'search_vector'
          AND generation_expression IS NULL
    ) THEN
        ALTER TABLE job_listings DROP COLUMN search_vector;
    END IF;
END $$;

ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(company, '') || ' ' || coalesce(description, ''))
    ) STORED;

-- HNSW index for vector similarity search
CREATE INDEX IF NOT EXISTS idx_job_embedding_hnsw
    ON job_listings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for full-text keyword search
CREATE INDEX IF NOT EXISTS idx_job_search_vector
    ON job_listings USING GIN (search_vector);

-- Trigram indexes for substring matching
CREATE INDEX IF NOT EXISTS idx_job_title_trgm
    ON job_listings USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_job_desc_trgm
    ON job_listings USING GIN (description gin_trgm_ops);

-- ===== activity_listings table =====

CREATE TABLE IF NOT EXISTS activity_listings (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    organizer VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    category TEXT,
    start_date VARCHAR(255),
    end_date VARCHAR(255),
    description TEXT,
    collected_at TIMESTAMP NOT NULL DEFAULT NOW(),
    source_name VARCHAR(255),
    source_id VARCHAR(255),
    embedded_at TIMESTAMP,
    embedding_model VARCHAR(255),
    UNIQUE (source_name, source_id)
);

ALTER TABLE activity_listings ADD COLUMN IF NOT EXISTS embedding vector(768);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'activity_listings' AND column_name = 'search_vector'
          AND generation_expression IS NULL
    ) THEN
        ALTER TABLE activity_listings DROP COLUMN search_vector;
    END IF;
END $$;

ALTER TABLE activity_listings ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(organizer, '') || ' ' || coalesce(category, '') || ' ' || coalesce(description, ''))
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_activity_embedding_hnsw
    ON activity_listings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_activity_search_vector
    ON activity_listings USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_activity_title_trgm
    ON activity_listings USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_activity_desc_trgm
    ON activity_listings USING GIN (description gin_trgm_ops);
