-- Synthetic candidates for performance testing. NOT for production databases.
--
-- Usage (stack running, from the repository root):
--   docker compose exec -T postgres psql -U candidates -d candidates -v rows=1000000 < scripts/generate-candidates.sql
--
-- Generates :rows candidates (default 1,000,000) with 3 skills each, then refreshes planner statistics.
-- Ids are random v4 UUIDs here (PostgreSQL 17 has no built-in v7 generator); the application uses UUIDv7.
-- Rows are recognisable by their email (perf.candidate.<n>@example.com), so they can be removed with:
--   DELETE FROM candidate WHERE email LIKE 'perf.candidate.%';

\if :{?rows}
\else
    \set rows 1000000
\endif
\timing on

INSERT INTO candidate (id, first_name, last_name, email, phone, current_title, current_company, city, country,
                       years_of_experience, notice_period_days, status, created_at, updated_at, version)
SELECT gen_random_uuid(),
       (ARRAY ['Aarav','Priya','Rahul','Ananya','Vikram','Sneha','Arjun','Kavya','Rohan','Ishita',
               'Emma','Lucas','Olivia','Noah','Sofia','Wei','Fatima','Daniel','Aisha','Yuki'])[1 + g % 20],
       (ARRAY ['Sharma','Verma','Iyer','Mehta','Reddy','Singh','Nair','Gupta','Banerjee','Kulkarni',
               'Schmidt','Martin','Brown','Wilson','Rossi','Zhang','Khan','Kim','Bello','Tanaka'])[1 + (g / 20) % 20],
       'perf.candidate.' || g || '@example.com',
       NULL,
       (ARRAY ['Backend Engineer','Frontend Developer','Data Engineer','DevOps Engineer','QA Engineer',
               'Engineering Manager','Product Designer','Data Scientist','Mobile Developer',
               'Site Reliability Engineer','Full Stack Developer','Security Engineer'])[1 + g % 12],
       (ARRAY ['Acme Corp','Globex','Initech','Hooli','Umbrella Tech','Pied Piper','Stark Mobile','Wayne Systems',
               'Cyberdyne','Soylent','Tyrell','Wonka Labs','Aperture','Black Mesa','Vandelay'])[1 + g % 15],
       (ARRAY ['Bengaluru','Pune','Mumbai','Hyderabad','Chennai','Delhi','Berlin','London','Singapore','Toronto'])[1 + g % 10],
       (ARRAY ['IN','IN','IN','IN','IN','IN','DE','GB','SG','CA'])[1 + g % 10],
       g % 31,
       (ARRAY [0, 15, 30, 60, 90])[1 + g % 5],
       (ARRAY ['NEW','SCREENING','INTERVIEWING','OFFERED','HIRED','REJECTED'])[1 + g % 6],
       now() - (g % 1000) * interval '1 day',
       now() - (g % 1000) * interval '1 day',
       0
FROM generate_series(1, :rows) AS g;

INSERT INTO candidate_skill (candidate_id, skill)
SELECT c.id,
       (ARRAY ['java','spring boot','python','react','typescript','kubernetes','aws','sql','go','kotlin',
               'node.js','docker','terraform','kafka','postgresql','swift','android','machine learning','spark',
               'selenium','figma','scala','rust','c#','graphql'])[1 + (abs(hashtext(c.id::text)::bigint) + k * 7) % 25]
FROM candidate c
         CROSS JOIN generate_series(0, 2) AS k
WHERE c.email LIKE 'perf.candidate.%'
ON CONFLICT DO NOTHING;

ANALYZE candidate;
ANALYZE candidate_skill;

SELECT (SELECT count(*) FROM candidate) AS candidates, (SELECT count(*) FROM candidate_skill) AS skills;
