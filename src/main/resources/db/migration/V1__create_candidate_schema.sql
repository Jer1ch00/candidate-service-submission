-- Candidate management schema.
-- Design notes (see docs/DESIGN.md for the full rationale):
--  * Constraints live in the database as the last line of defence; the API validates first for friendly errors.
--  * Indexes are deliberately few. Each one exists for a known query; more are added from EXPLAIN evidence.

-- Trigram matching makes substring search (LIKE '%term%') index-assisted.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE candidate (
    id                  UUID         PRIMARY KEY,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(254) NOT NULL,
    phone               VARCHAR(20),
    current_title       VARCHAR(150),
    current_company     VARCHAR(150),
    city                VARCHAR(100),
    country             VARCHAR(2),
    years_of_experience INTEGER      NOT NULL,
    notice_period_days  INTEGER,
    status              VARCHAR(20)  NOT NULL,
    -- Single lower-cased search document so free-text search is one indexed predicate per token.
    search_text         TEXT GENERATED ALWAYS AS (
                            lower(first_name || ' ' || last_name || ' ' || email || ' ' ||
                                  coalesce(current_title, '') || ' ' || coalesce(current_company, ''))
                        ) STORED,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL,

    -- Race-safe uniqueness: the application checks first, this constraint settles concurrent inserts.
    CONSTRAINT uk_candidate_email UNIQUE (email),
    CONSTRAINT ck_candidate_email_normalized CHECK (email = lower(email)),
    CONSTRAINT ck_candidate_experience CHECK (years_of_experience BETWEEN 0 AND 60),
    CONSTRAINT ck_candidate_notice_period CHECK (notice_period_days IS NULL OR notice_period_days BETWEEN 0 AND 365),
    CONSTRAINT ck_candidate_country CHECK (country IS NULL OR country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_candidate_status CHECK (status IN ('NEW', 'SCREENING', 'INTERVIEWING', 'OFFERED', 'HIRED', 'REJECTED'))
);

-- Free-text search: each query token becomes "search_text LIKE '%token%'", served by this GIN trigram index.
CREATE INDEX idx_candidate_search_text_trgm ON candidate USING gin (search_text gin_trgm_ops);

-- Default listing order (newest first) with the id tie-breaker used for deterministic pagination.
CREATE INDEX idx_candidate_created_at_id ON candidate (created_at, id);

CREATE TABLE candidate_skill (
    candidate_id UUID        NOT NULL REFERENCES candidate (id) ON DELETE CASCADE,
    skill        VARCHAR(50) NOT NULL,
    CONSTRAINT pk_candidate_skill PRIMARY KEY (candidate_id, skill)
);

-- Skill filters look candidates up by skill ("who knows java?"), the reverse of the primary key order.
CREATE INDEX idx_candidate_skill_skill ON candidate_skill (skill, candidate_id);

CREATE TABLE candidate_status_history (
    id           UUID         PRIMARY KEY,
    candidate_id UUID         NOT NULL REFERENCES candidate (id) ON DELETE CASCADE,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20)  NOT NULL,
    note         VARCHAR(500),
    changed_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_status_history_candidate ON candidate_status_history (candidate_id, changed_at);
