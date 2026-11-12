/**
 * The <strong>configuration plane</strong> (BRD §7.2, FM-07 … FM-11, FM-13, §21–§22).
 *
 * <p>Everything here is administered data, never code: categories, sub-categories, the Expense
 * Delegation Matrix, Expense Committee members and levels, currencies, the Department Head limit,
 * recommending authorities, exceeding limits, institution settings, feature flags and branding.
 *
 * <h2>Why this is its own top-level module</h2>
 * BRD §41 forbids hardcoding institution-specific business policy. The configuration plane is
 * where that policy is expressed instead, so it must be a stable, auditable substrate that the
 * expense, workflow and reporting modules read — and never write. The dependency arrow therefore
 * points <em>into</em> this package from business modules, never outward, which keeps the
 * configuration surface free of business workflows and makes it safe to expose to a designer UI.
 *
 * <h2>Versioning and audit</h2>
 * Configuration that affects approval routing is versioned and effective-dated, and every change
 * writes an audit record with before/after values (BRD AUD-02). A change never rewrites the route
 * of an already-submitted request (BRD §24.1): the request pins the version it used.
 */
package com.edam.configplane;
