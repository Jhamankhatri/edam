#!/usr/bin/env python3
"""Measure how much of the migrated schema is actually mapped by a JPA entity.

This regenerates the counts in documentation/backend/MIGRATION_ENTITY_REPORT.md. The
first version of that report was produced by an ad-hoc scan that did not strip SQL
comments, so two comment lines mentioning "CREATE TABLE" in prose became phantom table
names, and it listed two tables as unmapped that are in fact mapped. This script exists
so the figure is reproducible rather than asserted.

Usage:  python3 tools/migration-entity-report.py [--markdown]

Exit status is 1 when an entity names a table that no migration creates, because that
pairing can only be satisfied by `ddl-auto` creating it — which contradicts the project's
rule that Flyway owns the schema.
"""
from __future__ import annotations

import glob
import re
import sys

MIGRATIONS = "src/main/resources/db/migration/*.sql"
SOURCES = "src/main/java/**/*.java"

_BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
_LINE_COMMENT = re.compile(r"--[^\n]*")
_CREATE = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z_][a-z0-9_]*)", re.I)
_DROP = re.compile(r"DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?([a-z_][a-z0-9_]*)", re.I)
_TABLE = re.compile(r'@Table\(\s*name\s*=\s*"([a-z0-9_]+)"')


def strip_sql_comments(text: str) -> str:
    """Remove comments only.

    This is the whole point of the script. Matching DDL keywords against raw file
    content counts prose, and prose about DDL is common in these migrations because
    each one explains itself.
    """
    text = _BLOCK_COMMENT.sub("", text)
    return _LINE_COMMENT.sub("", text)


def read_migrations() -> tuple[dict[str, str], set[str]]:
    created: dict[str, str] = {}
    dropped: set[str] = set()
    for path in sorted(glob.glob(MIGRATIONS)):
        name = path.rsplit("/", 1)[-1]
        body = strip_sql_comments(open(path, encoding="utf-8").read())
        for match in _CREATE.finditer(body):
            created[match.group(1).lower()] = name
        for match in _DROP.finditer(body):
            dropped.add(match.group(1).lower())
    return created, dropped


def read_entities() -> dict[str, str]:
    tables: dict[str, str] = {}
    for path in glob.glob(SOURCES, recursive=True):
        body = open(path, encoding="utf-8").read()
        if "@Entity" not in body:
            continue
        for match in _TABLE.finditer(body):
            tables[match.group(1).lower()] = path
    return tables


def main() -> int:
    created, dropped = read_migrations()
    live = {table: origin for table, origin in created.items() if table not in dropped}
    entities = read_entities()

    unmapped = sorted(set(live) - set(entities))
    orphans = sorted(set(entities) - set(live))

    print(f"create statements : {len(created)}")
    print(f"dropped           : {len(dropped)}")
    print(f"live tables       : {len(live)}")
    print(f"entities          : {len(entities)}")
    print(f"mapped            : {len(set(live) & set(entities))}")
    print(f"unmapped          : {len(unmapped)}")
    print(f"entity w/o table  : {len(orphans)}")

    if "--markdown" in sys.argv:
        by_migration: dict[str, list[str]] = {}
        for table in unmapped:
            by_migration.setdefault(live[table], []).append(table)
        print()
        print("### Live tables with no entity, by migration")

        def migration_order(name: str) -> tuple[int, int]:
            """V2 < V4 < V4_1 < V10 — lexical order would put V10 before V6."""
            match = re.match(r"V(\d+)(?:_(\d+))?", name)
            if not match:
                return (10**6, 0)
            return (int(match.group(1)), int(match.group(2) or 0))

        for migration in sorted(by_migration, key=migration_order):
            tables = ", ".join(f"`{t}`" for t in by_migration[migration])
            print(f"\n**{migration}:** {tables}")

    if orphans:
        print("\nENTITIES WITHOUT A TABLE:", ", ".join(orphans), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
