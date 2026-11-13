-- =====================================================================================
-- V16 — request numbering, approval-limit support and recommending-authority support
--
-- WHY THIS EXISTS
-- V9 created the request tables but nothing could yet allocate a request number. Deriving
-- the next number from MAX(...)+1 in application code races: two concurrent submissions
-- read the same maximum and one fails on the unique constraint, which surfaces to the user
-- as an opaque 409. A database sequence cannot race.
--
-- Everything here is additive. No column is dropped, no constraint is weakened, and no
-- existing row is rewritten.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Request number allocation
--
-- A single sequence, formatted by the application as PREFIX-YYYYMM-NNNNNN. One sequence
-- rather than one per category: a per-category sequence would need a row in a table to
-- hold it, and the formatting already carries the category code, so collisions across
-- categories are impossible regardless.
-- -------------------------------------------------------------------------------------
CREATE SEQUENCE expense_request_number_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO CYCLE;

COMMENT ON SEQUENCE expense_request_number_seq IS
    'Monotonic allocator for human-readable request numbers. Never reset, never reused, never rolled back.';

-- -------------------------------------------------------------------------------------
-- Approval-limit overlap prevention
--
-- Two active limits for the same department, expense type, currency and authority would
-- make the applicable figure depend on row order. The exclusion constraint makes that
-- state unrepresentable, exactly as it already does for delegation rules.
--
-- The authority dimension is included so that a department limit and a per-user limit are
-- not treated as overlapping: they are different authorities and are compared, not merged.
-- -------------------------------------------------------------------------------------
ALTER TABLE department_approval_limits
    ADD CONSTRAINT ex_department_approval_limits_no_overlap
    EXCLUDE USING gist (
        department_id WITH =,
        currency_id WITH =,
        expense_type WITH =,
        COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&
    ) WHERE (is_active);

-- Supports the resolver's query shape: active limits for a department on a given instant.
CREATE INDEX ix_department_approval_limits_lookup
    ON department_approval_limits (department_id, currency_id, expense_type, effective_from DESC)
    WHERE is_active;

-- -------------------------------------------------------------------------------------
-- Recommending-authority lookups
--
-- Resolution asks "who recommends for this department, this expense type, on this date?",
-- so the index matches that predicate rather than merely the foreign key.
-- -------------------------------------------------------------------------------------
CREATE INDEX ix_recommending_authorities_lookup
    ON recommending_authorities (department_id, expense_type, effective_from DESC)
    WHERE is_active;

-- A department must not have two active recommending authorities for the same expense type
-- over overlapping periods: the workflow could otherwise pick arbitrarily, and two people
-- would each believe they were the designated authority.
ALTER TABLE recommending_authorities
    ADD CONSTRAINT ex_recommending_authorities_no_overlap
    EXCLUDE USING gist (
        department_id WITH =,
        expense_type WITH =,
        tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&
    ) WHERE (is_active);

-- -------------------------------------------------------------------------------------
-- Exceeding-limit rule overlap prevention
--
-- Two active rules with the same classification and nature but different escalation types
-- would make the outcome depend on row order — a governance defect, not a data quirk.
-- -------------------------------------------------------------------------------------
ALTER TABLE exceeding_limit_rules
    ADD CONSTRAINT ex_exceeding_limit_rules_no_overlap
    EXCLUDE USING gist (
        currency_id WITH =,
        nature_id WITH =,
        expense_type WITH =,
        numrange(threshold_amount, 'infinity'::numeric, '[)') WITH &&
    ) WHERE (is_active);

-- -------------------------------------------------------------------------------------
-- Request lifecycle support
--
-- A reversed request keeps its original status history; reversal is a terminal transition
-- that must record when and by whom. The columns are nullable because most requests are
-- never reversed.
-- -------------------------------------------------------------------------------------
ALTER TABLE expense_requests
    ADD COLUMN cancelled_at   TIMESTAMPTZ,
    ADD COLUMN cancelled_by   UUID REFERENCES users (id),
    ADD COLUMN cancel_reason  VARCHAR(500);

COMMENT ON COLUMN expense_requests.cancelled_at IS
    'Set when the requester withdraws. Distinct from rejected, which is a decision by an approver.';

-- -------------------------------------------------------------------------------------
-- Documents: a request may carry a document type marker for conditional-attachment checks.
-- V9 already stores is_mandatory at upload time; this records which rule demanded it, so a
-- later configuration change cannot rewrite why the document was required.
-- -------------------------------------------------------------------------------------
ALTER TABLE request_documents
    ADD COLUMN required_by_rule VARCHAR(150);
