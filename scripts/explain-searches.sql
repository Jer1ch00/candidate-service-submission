-- Query plans for representative searches, using the exact SQL Hibernate generates for the search endpoint
-- (captured with spring.jpa.show-sql) with the bind parameters filled in.
--
-- Usage (after scripts/generate-candidates.sql):
--   docker compose exec -T postgres psql -U candidates -d candidates < scripts/explain-searches.sql
--
-- Section 2 drops the trigram index inside a transaction that is rolled back, to show what the index buys.

\pset pager off

\echo '=== A. Free text, selective (email fragment): GET /candidates?q=candidate.123456@'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
WHERE c1_0.search_text LIKE '%candidate.123456@%' ESCAPE '\'
ORDER BY c1_0.created_at DESC, c1_0.id DESC
OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;

\echo '=== A2. Free text, selective term only: GET /candidates?q=123456@'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
WHERE c1_0.search_text LIKE '%123456@%' ESCAPE '\'
ORDER BY c1_0.created_at DESC, c1_0.id DESC
OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;

\echo '=== B. Free text, two common terms (~2,500 matches): GET /candidates?q=priya sharma'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
WHERE c1_0.search_text LIKE '%priya%' ESCAPE '\'
  AND c1_0.search_text LIKE '%sharma%' ESCAPE '\'
ORDER BY c1_0.created_at DESC, c1_0.id DESC
OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;

\echo '=== C. Multi-criteria filter: skills java AND sql, 4-10 years, IN, NEW or SCREENING, by experience'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
WHERE c1_0.status IN ('NEW', 'SCREENING')
  AND EXISTS (SELECT 1 FROM candidate_skill s1_0 WHERE s1_0.skill IN ('java') AND c1_0.id = s1_0.candidate_id)
  AND EXISTS (SELECT 1 FROM candidate_skill s2_0 WHERE s2_0.skill IN ('sql') AND c1_0.id = s2_0.candidate_id)
  AND c1_0.years_of_experience >= 4 AND c1_0.years_of_experience <= 10
  AND c1_0.country = 'IN'
ORDER BY c1_0.years_of_experience DESC, c1_0.id DESC
OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;

\echo '=== D. Default listing, first page: GET /candidates'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
ORDER BY c1_0.created_at DESC, c1_0.id DESC
OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;

\echo '=== E. Exact total for the unfiltered listing (the COUNT behind totalItems)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT count(c1_0.id) FROM candidate c1_0;

\echo '=== F. Deep page: GET /candidates?page=25000&size=20 (OFFSET 500000)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
ORDER BY c1_0.created_at DESC, c1_0.id DESC
OFFSET 500000 ROWS FETCH FIRST 20 ROWS ONLY;

\echo '=== 2. Same free-text searches WITHOUT the trigram index (dropped inside a rolled-back transaction)'
BEGIN;
DROP INDEX idx_candidate_search_text_trgm;

\echo '=== A without index'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
WHERE c1_0.search_text LIKE '%candidate.123456@%' ESCAPE '\'
ORDER BY c1_0.created_at DESC, c1_0.id DESC
OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;

\echo '=== A2 without index'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
WHERE c1_0.search_text LIKE '%123456@%' ESCAPE '\'
ORDER BY c1_0.created_at DESC, c1_0.id DESC
OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;

\echo '=== B without index'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c1_0.id, c1_0.first_name, c1_0.last_name, c1_0.email, c1_0.status, c1_0.years_of_experience
FROM candidate c1_0
WHERE c1_0.search_text LIKE '%priya%' ESCAPE '\'
  AND c1_0.search_text LIKE '%sharma%' ESCAPE '\'
ORDER BY c1_0.created_at DESC, c1_0.id DESC
OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;

ROLLBACK;
