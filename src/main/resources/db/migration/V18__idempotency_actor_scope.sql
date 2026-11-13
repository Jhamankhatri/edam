-- Module O — Idempotency key scoping (Phase 4A Step 2)
--
-- The idempotency_records table was created in V11 with a UNIQUE (idempotency_key, operation_type)
-- constraint, which lets two different users collide on the same client-generated key. Idempotency
-- keys are scoped per authenticated actor in addition to the operation, so one user's retry
-- cannot see another user's recorded response and two clients that coincidentally pick the same
-- key string do not block each other.

ALTER TABLE idempotency_records
    ADD COLUMN actor_id UUID;

-- Backfill: pre-Step-2 rows should not exist in deployments that have not enabled idempotency
-- on any endpoint yet; if they do, mark them with a nil actor so they remain replayable by
-- nobody but never collide with a real actor key.
UPDATE idempotency_records SET actor_id = '00000000-0000-0000-0000-000000000000'::uuid WHERE actor_id IS NULL;

ALTER TABLE idempotency_records ALTER COLUMN actor_id SET NOT NULL;

-- The operation_type and request_hash columns remain length 100/64 as in V11; extend operation_type
-- to 120 just to give stable identifiers room.
ALTER TABLE idempotency_records ALTER COLUMN operation_type TYPE VARCHAR(120);

-- Replace the V11 unique constraint with one that includes actor_id.
ALTER TABLE idempotency_records DROP CONSTRAINT IF EXISTS uk_idempotency_records_key;
ALTER TABLE idempotency_records
    ADD CONSTRAINT uk_idempotency_records_key UNIQUE (idempotency_key, operation_type, actor_id);

COMMENT ON TABLE idempotency_records IS
    'Durable idempotency keys, scoped by (key, operation_type, actor_id). The key is hashed before '
    'storage; the response snapshot is a compact JSON envelope used for replays. UNIQUE constraint '
    'is the concurrency arbiter for duplicate submissions.';
