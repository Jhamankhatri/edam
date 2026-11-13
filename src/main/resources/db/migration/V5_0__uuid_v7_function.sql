-- =====================================================================================
-- EDAM — portable uuidv7() (V5.0)
--
-- WHY THIS EXISTS AS ITS OWN MIGRATION
--
-- PostgreSQL 18 ships a native uuidv7(). EDAM's documented floor is PostgreSQL 16+ (the CI
-- image is 18), and the business tables default their primary keys to uuidv7(). On 16 and 17
-- that function does not exist, so every CREATE TABLE in V5..V12 would fail with
--   ERROR: function uuidv7() does not exist
-- which reads like a schema defect rather than a version problem.
--
-- This migration therefore installs a fallback ONLY when the native function is absent, so a
-- PostgreSQL 18 deployment keeps the built-in implementation.
--
-- THE LAYOUT (RFC 9562) is fixed at 32 hex digits:
--
--   positions  1..12   unix_ts_ms   milliseconds since the epoch, big-endian (48 bits)
--   position      13   version      always '7'
--   positions 14..16   rand_a       12 random bits
--   position      17   variant      one of 8, 9, a, b  (top two bits = 10)
--   positions 18..32   rand_b       60 random bits
--
-- WHY NOT A v4 UUID WITH A PREFIX, OR A SEQUENCE
-- A v4 with a timestamp pasted on is not a v7: tools that read the timestamp field disagree,
-- and sorting does not match creation order. A sequence reveals row counts and creation order
-- and makes ids in URLs guessable. See AuditableEntity's Javadoc for the full comparison.
--
-- RANDOMNESS: gen_random_uuid() is core in PostgreSQL 13+ (no pgcrypto extension required) and
-- is backed by the platform CSPRNG. Its dashes are stripped to obtain the 32 random hex digits
-- that supply rand_a and rand_b.
-- =====================================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE p.proname = 'uuidv7'
          AND n.nspname = current_schema()
    ) THEN
        CREATE FUNCTION uuidv7() RETURNS uuid
        LANGUAGE sql
        VOLATILE
        AS $fn$
            SELECT (
                -- 48-bit millisecond timestamp, zero-padded to exactly 12 hex digits
                lpad(to_hex((extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0')
                -- version nibble
                || '7'
                -- rand_a: 12 random bits
                || substr(r, 14, 3)
                -- variant: one of 8/9/a/b chosen by the low two bits of a random nibble
                || substr('89ab', (('x' || substr(r, 17, 1))::bit(4)::int % 4) + 1, 1)
                -- rand_b: the remaining 15 random hex digits
                || substr(r, 18, 15)
            )::uuid::text
            FROM (
                SELECT replace(gen_random_uuid()::text, '-', '') AS r
            ) AS entropy
        $fn$;
    END IF;
END $$;

COMMENT ON FUNCTION uuidv7() IS
    'RFC 9562 version 7 UUID: 48-bit millisecond timestamp, version nibble 7, variant 10, 72 random bits. '
    'Installed as a fallback only when this PostgreSQL build does not provide its own uuidv7().';

-- Self-check: the generated value must be a well-formed v7 with the correct variant, and must sort
-- after a value generated later in wall-clock time is not required (clock resolution). This assertion
-- prevents a subtly wrong implementation from silently generating keys that only look like v7s.
DO $$
DECLARE
    probe uuid := uuidv7();
    txt   text := replace(probe::text, '-', '');
BEGIN
    IF substr(txt, 13, 1) <> '7' THEN
        RAISE EXCEPTION 'uuidv7 fallback produced version nibble % (expected 7) for %', substr(txt, 13, 1), probe;
    END IF;
    IF substr(txt, 17, 1) NOT IN ('8', '9', 'a', 'b') THEN
        RAISE EXCEPTION 'uuidv7 fallback produced variant nibble % (expected 8-b) for %', substr(txt, 17, 1), probe;
    END IF;
END $$;
