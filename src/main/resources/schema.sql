-- Drop stale Hibernate-generated enum CHECK constraints.
-- Hibernate ddl-auto=update creates these on first run but never updates them when enum values change.
-- With columnDefinition="VARCHAR(...)" on @Enumerated fields, Hibernate won't recreate them.
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_role_check;;
ALTER TABLE bookmarked_jobs DROP CONSTRAINT IF EXISTS bookmarked_jobs_type_check;;
ALTER TABLE bookmarked_jobs DROP CONSTRAINT IF EXISTS bookmarked_jobs_status_check;;
ALTER TABLE daily_recommendations DROP CONSTRAINT IF EXISTS daily_recommendations_category_check;;

-- pgvector and pg_trgm extensions
CREATE EXTENSION IF NOT EXISTS vector;;
CREATE EXTENSION IF NOT EXISTS pg_trgm;;

-- Embedding columns (added to existing job_listings table)
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS embedding vector(768);;
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMP;;
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(255);;

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
END $$;;

ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(company, '') || ' ' || coalesce(description, ''))
    ) STORED;;

-- HNSW index for vector similarity search
CREATE INDEX IF NOT EXISTS idx_job_embedding_hnsw
    ON job_listings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);;

-- GIN index for full-text keyword search
CREATE INDEX IF NOT EXISTS idx_job_search_vector
    ON job_listings USING GIN (search_vector);;

-- Trigram indexes for substring matching
CREATE INDEX IF NOT EXISTS idx_job_title_trgm
    ON job_listings USING GIN (title gin_trgm_ops);;

CREATE INDEX IF NOT EXISTS idx_job_desc_trgm
    ON job_listings USING GIN (description gin_trgm_ops);;

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
);;

ALTER TABLE activity_listings ADD COLUMN IF NOT EXISTS embedding vector(768);;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'activity_listings' AND column_name = 'search_vector'
          AND generation_expression IS NULL
    ) THEN
        ALTER TABLE activity_listings DROP COLUMN search_vector;
    END IF;
END $$;;

ALTER TABLE activity_listings ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(organizer, '') || ' ' || coalesce(category, '') || ' ' || coalesce(description, ''))
    ) STORED;;

CREATE INDEX IF NOT EXISTS idx_activity_embedding_hnsw
    ON activity_listings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);;

CREATE INDEX IF NOT EXISTS idx_activity_search_vector
    ON activity_listings USING GIN (search_vector);;

CREATE INDEX IF NOT EXISTS idx_activity_title_trgm
    ON activity_listings USING GIN (title gin_trgm_ops);;

CREATE INDEX IF NOT EXISTS idx_activity_desc_trgm
    ON activity_listings USING GIN (description gin_trgm_ops);;

-- ===== bookmarked_jobs table extensions for activity bookmarks =====

ALTER TABLE bookmarked_jobs ADD COLUMN IF NOT EXISTS type VARCHAR(20) DEFAULT 'JOB' NOT NULL;;
ALTER TABLE bookmarked_jobs ADD COLUMN IF NOT EXISTS activity_listing_id BIGINT;;
ALTER TABLE bookmarked_jobs ADD COLUMN IF NOT EXISTS category VARCHAR(255);;
ALTER TABLE bookmarked_jobs ADD COLUMN IF NOT EXISTS start_date VARCHAR(255);;
ALTER TABLE bookmarked_jobs ADD COLUMN IF NOT EXISTS end_date VARCHAR(255);;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_bookmarked_activity_listing'
    ) THEN
        ALTER TABLE bookmarked_jobs
            ADD CONSTRAINT fk_bookmarked_activity_listing
            FOREIGN KEY (activity_listing_id) REFERENCES activity_listings(id) ON DELETE SET NULL;
    END IF;
END $$;;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bookmarked_job_user_activity
    ON bookmarked_jobs (user_id, activity_listing_id) WHERE activity_listing_id IS NOT NULL;;

CREATE INDEX IF NOT EXISTS idx_bookmarked_job_type ON bookmarked_jobs (type);;

-- ===== activity_search_histories table =====

CREATE TABLE IF NOT EXISTS activity_search_histories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    tags_string VARCHAR(255),
    query VARCHAR(500),
    result_count INT NOT NULL DEFAULT 0,
    searched_at TIMESTAMP NOT NULL DEFAULT NOW()
);;

CREATE INDEX IF NOT EXISTS idx_activity_search_history_user_id
    ON activity_search_histories (user_id);;

-- ===== recommended_activities table =====

CREATE TABLE IF NOT EXISTS recommended_activities (
    id BIGSERIAL PRIMARY KEY,
    activity_search_history_id BIGINT NOT NULL REFERENCES activity_search_histories(id) ON DELETE CASCADE,
    activity_listing_id BIGINT NOT NULL REFERENCES activity_listings(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    organizer VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    category VARCHAR(255),
    start_date VARCHAR(255),
    end_date VARCHAR(255),
    match_score INT NOT NULL,
    reason TEXT NOT NULL
);;

CREATE INDEX IF NOT EXISTS idx_recommended_activity_search_id
    ON recommended_activities (activity_search_history_id);;

CREATE INDEX IF NOT EXISTS idx_recommended_activity_listing_id
    ON recommended_activities (activity_listing_id);;

-- ===== daily_recommendations table =====

CREATE TABLE IF NOT EXISTS daily_recommendations (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    job_listing_id BIGINT REFERENCES job_listings(id) ON DELETE CASCADE,
    activity_listing_id BIGINT REFERENCES activity_listings(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    company_or_organizer VARCHAR(255),
    url TEXT NOT NULL,
    activity_category VARCHAR(255),
    start_date VARCHAR(255),
    end_date VARCHAR(255),
    match_score INT NOT NULL,
    reason TEXT NOT NULL,
    generated_at DATE NOT NULL DEFAULT CURRENT_DATE
);;

CREATE INDEX IF NOT EXISTS idx_daily_rec_date_cat_score
    ON daily_recommendations (generated_at, category, match_score DESC);;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_daily_rec_exactly_one_listing'
    ) THEN
        ALTER TABLE daily_recommendations
            ADD CONSTRAINT chk_daily_rec_exactly_one_listing
            CHECK (
                (job_listing_id IS NOT NULL AND activity_listing_id IS NULL) OR
                (job_listing_id IS NULL AND activity_listing_id IS NOT NULL)
            );
    END IF;
END $$;;

-- Clean up rows with old category values after enum rename
DELETE FROM daily_recommendations
WHERE category NOT IN ('IT_DEV', 'BUSINESS', 'MARKETING', 'DESIGN', 'SALES',
                       'IT_CONTEST', 'MARKETING_CONTEST', 'VOLUNTEER', 'GLOBAL', 'ACADEMIC');;
