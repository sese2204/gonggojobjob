-- Upstage Solar embedding 컬럼 추가 (4096차원)
-- 실행 대상: prod PostgreSQL (Railway)
-- local은 ddl-auto: update로 자동 추가됨

ALTER TABLE activity_listings
    ADD COLUMN IF NOT EXISTS embedding_upstage vector(4096),
    ADD COLUMN IF NOT EXISTS embedded_upstage_at timestamp,
    ADD COLUMN IF NOT EXISTS embedding_upstage_model varchar(255);

-- 벡터 유사도 검색 인덱스 (백필 완료 후 생성 권장)
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_activity_embedding_upstage
--     ON activity_listings USING ivfflat (embedding_upstage vector_cosine_ops)
--     WITH (lists = 100);
