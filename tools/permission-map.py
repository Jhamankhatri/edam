#!/usr/bin/env python3
"""Regenerate documentation/backend/PERMISSION_ENDPOINT_MAP.md from the controllers.

The map is derived, never hand-maintained: it reads every @RestController source,
pairs each mapped method with the guard annotation that governs it, and writes the
tables. Run it after adding an endpoint, and commit the result.

Why a script rather than a written list: a hand-kept table of 127 endpoints drifts
within one sprint, and a stale security document is worse than none — it asserts
things about authorization that are no longer true.

Usage:  python3 tools/permission-map.py
"""

import collections
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTROLLERS = os.path.join(ROOT, "src/main/java/com/edam/**/controller/*.java")
PERMISSIONS = os.path.join(ROOT, "src/main/java/com/edam/common/security/Permissions.java")
OUTPUT = os.path.join(ROOT, "documentation/backend/PERMISSION_ENDPOINT_MAP.md")

MAPPING = re.compile(r"\s*@(Get|Post|Put|Delete|Patch)Mapping(?:\(([^)]*)\))?")
PERM = re.compile(r"@EdamPermission\(Permissions\.(\w+)\)")
BASE = re.compile(r'@RequestMapping\(path\s*=\s*ApiPaths\.V1\s*\+\s*"([^"]*)"')
CONSTANT = re.compile(r"public static final String (\w+)\s*=")

PHASE_3 = ["WORKFLOW_VIEW", "WORKFLOW_MANAGE", "WORKFLOW_PUBLISH", "APPROVAL_VIEW", "APPROVAL_ACT"]

# The module each unconsumed permission belongs to. Kept here rather than inferred,
# because the point of the table is the human judgement about what is missing and why.
PENDING = {
    "AUDIT_EXPORT": ("Audit export", "Not in Phase 4A Step 1 scope (AUD-03)"),
    "CONFIGURATION_VIEW": ("System configuration screen", "Not in Phase 1-3 scope"),
    "DASHBOARD_MANAGE": ("Dashboard widgets", "Not in Phase 1-3 scope"),
    "DOCUMENT_UPLOAD": ("Document management", "Phase 4"),
    "DOCUMENT_DOWNLOAD": ("Document management", "Phase 4"),
    "DOCUMENT_DELETE": ("Document management", "Phase 4"),
    "EMAIL_CONFIGURATION_MANAGE": ("Notifications", "Phase 4"),
    "NOTIFICATION_VIEW": ("Notifications", "Phase 4"),
    "NOTIFICATION_TEMPLATE_MANAGE": ("Notifications", "Phase 4"),
    "INTEGRATION_VIEW": ("External integration", "Not in Phase 1-3 scope"),
    "INTEGRATION_MANAGE": ("External integration", "Not in Phase 1-3 scope"),
    "REPORT_VIEW": ("Reporting", "Not in Phase 1-3 scope"),
    "REPORT_EXPORT": ("Reporting", "Not in Phase 1-3 scope"),
    "REQUEST_EXPORT": ("Register export", "Not yet scheduled"),
    "REQUEST_VIEW_ALL": ("Register-wide visibility", "Gap - see the note below"),
    "REQUEST_VIEW_SCOPE": ("Scope-based visibility", "Gap - see the note below"),
    "REQUEST_REVERSE": ("Reversing an approved request", "Not in Phase 1-3 scope"),
    "RULE_VIEW": ("Business rule authoring", "Blocked with OD-P3-04"),
    "RULE_MANAGE": ("Business rule authoring", "Blocked with OD-P3-04"),
    "RULE_PUBLISH": ("Business rule authoring", "Blocked with OD-P3-04"),
    "SYSTEM_ADMINISTER": ("Installation administration", "Not in Phase 1-3 scope"),
}


def collect():
    """Every mapped method with the guard that governs it."""
    rows = []
    for path in sorted(glob.glob(CONTROLLERS, recursive=True)):
        source = open(path, encoding="utf-8").read()
        base_match = BASE.search(source)
        base = base_match.group(1) if base_match else ""
        lines = source.split("\n")
        for index, line in enumerate(lines):
            mapping = MAPPING.match(line)
            if not mapping:
                continue
            args = (mapping.group(2) or "").strip()
            sub = re.search(r'"([^"]*)"', args)
            sub = sub.group(1) if sub else ""
            guard = None
            for ahead in range(index + 1, min(index + 9, len(lines))):
                found = PERM.search(lines[ahead])
                if found:
                    guard = found.group(1)
                    break
                if "@PublicEndpoint" in lines[ahead]:
                    guard = "PUBLIC"
                    break
                if "@AuthenticatedEndpoint" in lines[ahead]:
                    guard = "AUTHENTICATED"
                    break
                if re.search(r"\tpublic\s+\w", lines[ahead]):
                    break
            rows.append((guard or "MISSING", mapping.group(1).upper(), base + sub))
    return rows


def write(rows):
    by = collections.defaultdict(list)
    for guard, verb, path in rows:
        by[guard].append((verb, path))

    catalogue = set(CONSTANT.findall(open(PERMISSIONS, encoding="utf-8").read()))
    used = {g for g in by if g not in ("PUBLIC", "AUTHENTICATED", "MISSING")}
    pending = sorted(catalogue - used)

    out = []
    out.append("# Permission-to-endpoint map\n\n")
    out.append(
        "Generated from the controllers by `tools/permission-map.py`, not maintained by hand. Every "
        "`@EdamPermission` below was read off a mapped method, and "
        "`EndpointAuthorizationArchitectureTest` fails the build if a mapped method carries no guard at all.\n\n")
    out.append(
        f"**{len(rows)} mapped methods. {len(catalogue)} permission constants exist; {len(used)} are "
        f"consumed by an endpoint; {len(pending)} are not.** The unused ones are listed at the end with the "
        "module each belongs to, because an unused permission is not a defect — it is a module that has not "
        "been built yet.\n\n")

    out.append("## Phase 3 — workflow and approvals\n\n")
    out.append(
        "These five were seeded in V12 and Phase 3 is their first consumer. No new permission was added and "
        "no migration was needed.\n\n")
    out.append("| Permission | Method | Path |\n|---|---|---|\n")
    for permission in PHASE_3:
        for verb, path in by.get(permission, []):
            out.append(f"| `{permission}` | {verb} | `{path}` |\n")

    out.append("""
### Why `workflow:publish` is separate from `workflow:manage`

Authoring a revision and making it live are different authorities. With one permission, whoever can draft a chain
can also put it into force, and the bank has no way to require a second pair of eyes on the configuration itself.
The split costs one permission and makes four-eyes review of the approval rules possible — which matters
precisely because those rules decide who may approve money.

### Why `approval:act` is not enough on its own

`approval:act` guards the four action endpoints, but holding it confers no authority over any particular task.
Every action additionally requires an open task naming the caller (`ApprovalService.requireAssigned`). The
endpoint permission answers "may this user act on approvals at all"; the assignment answers "may they act on
*this* one". A test that proved only the first would pass on an implementation that let anyone approve anything.

## All permissions in use

| Permission | Methods | Paths |
|---|---|---|
""")
    for guard in sorted(by, key=lambda g: (g in ("PUBLIC", "AUTHENTICATED"), g)):
        paths = by[guard]
        if guard == "PUBLIC":
            out.append(f"| _Public endpoints_ | {len(paths)} | {', '.join('`' + p + '`' for _, p in paths)} |\n")
        elif guard == "AUTHENTICATED":
            out.append(f"| _Any authenticated user_ | {len(paths)} | "
                       f"{', '.join('`' + p + '`' for _, p in paths)} |\n")
        else:
            out.append(f"| `{guard}` | {len(paths)} | {', '.join('`' + p + '`' for _, p in paths)} |\n")

    out.append("\n## Permissions with no endpoint yet\n\n")
    out.append(
        "A permission in the catalogue with no consumer is a module not yet built. Listing them keeps the "
        "audit's \"unimplemented permission\" figure honest instead of letting it quietly become \"permission "
        "that does not exist\".\n\n")
    out.append("| Permission | Module it belongs to | Phase |\n|---|---|---|\n")
    for permission in pending:
        module, phase = PENDING.get(permission, ("Unclassified — add it to PENDING in the generator", "?"))
        out.append(f"| `{permission}` | {module} | {phase} |\n")

    out.append("""
### `request:view-all` and `request:view-scope` are a real gap

The register endpoint is guarded by `request:view-own` and filters by data scope inside the service. There is
therefore **no permission that lets a finance user see every submitted request in the bank**, and no separate
permission for scope-based visibility. Today the widest visibility available is an `ALL` data scope attached to
`request:view-own`.

This is not a Phase 3 regression — Phase 2 built it that way — but it is the kind of thing that only shows up when
the permission catalogue is compared against the endpoints, which is why it is recorded here rather than left for
someone to discover from a support ticket.
""")

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as handle:
        handle.write("".join(out))
    return rows, catalogue, used, pending


def main():
    rows = collect()
    unguarded = [row for row in rows if row[0] == "MISSING"]
    for guard, verb, path in unguarded:
        print(f"UNGUARDED: {verb} {path}", file=sys.stderr)
    rows, catalogue, used, pending = write(rows)
    print(f"endpoints={len(rows)} catalogue={len(catalogue)} used={len(used)} unused={len(pending)}")
    return 1 if unguarded else 0


if __name__ == "__main__":
    sys.exit(main())
