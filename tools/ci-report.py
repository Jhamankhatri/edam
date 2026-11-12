#!/usr/bin/env python3
"""Surface the important parts of a Maven build log as GitHub annotations.

CI logs are downloaded through a host this environment cannot reach, so the workflow
routes build diagnostics through the annotations API instead. This script keeps the
extraction rules in one reviewable place rather than in shell heredocs.

Usage: python3 tools/ci-report.py build.log
"""

from __future__ import annotations

import glob
import re
import sys
import xml.etree.ElementTree as ET


def emit(kind: str, title: str, message: str) -> None:
    text = " ".join(str(message).split())
    # GitHub accepts up to 64000 chars per annotation but the runner truncates much earlier in
    # practice; chunk long messages so diagnostics are never silently lost.
    for index in range(0, max(len(text), 1), 1200):
        chunk = text[index:index + 1200]
        suffix = f" [{index // 1200 + 1}]" if len(text) > 1200 else ""
        print(f"::{kind} title={title}{suffix}::{chunk}")


def report_test_results() -> None:
    for report in sorted(glob.glob("target/surefire-reports/*.xml")):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            classname = (case.get("classname") or "").split(".")[-1]
            name = case.get("name") or "?"
            for tag in ("failure", "error"):
                for bad in case.findall(tag):
                    # AssertJ reports the useful explanation in the element text, and
                    # JUnit only sometimes duplicates a message attribute; keep both.
                    parts = [bad.get("message"), bad.text]
                    detail = " | ".join(part.strip() for part in parts if part and part.strip())
                    emit("error", f"FAIL {classname}.{name}", detail)


def report_log(log: str) -> None:
    patterns = [
        ("caused-by", r"Caused by: ([\w.$]+)(?::\s*(.{0,220}))?"),
        ("diag", r"INTERCEPTOR_DIAG[^\n]*"),
        ("compile", r"\[ERROR\] /home/runner/\S+\.java:\[[^\]]+\] .{0,300}"),
        ("ctx", r"(?:UnsatisfiedDependencyException|BeanCreationException|NoSuchBeanDefinitionException"
                r"|No qualifying bean|Failed to instantiate|Migration .* failed|Schema-validation"
                r"|JasperException|Unable to compile class for JSP|NoClassDefFoundError"
                r"|ClassNotFoundException)"),
    ]
    for title, pattern in patterns:
        for match in list(re.finditer(pattern, log))[:10]:
            start = log.rfind("\n", 0, match.start()) + 1
            end = log.find("\n", match.end())
            line = log[start:end if end != -1 else None]
            emit("error", title, line)


def report_summaries() -> None:
    for report in sorted(glob.glob("target/surefire-reports/*.txt")):
        with open(report, encoding="utf-8", errors="replace") as handle:
            for line in handle:
                if line.startswith("Tests run"):
                    emit("notice", f"TEST {report.split('/')[-1][:-4]}", line)
                    break


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "build.log"
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            log = handle.read()
    except FileNotFoundError:
        emit("warning", "ci-report", f"{path} not found")
        return 0
    report_log(log)
    report_test_results()
    report_summaries()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
