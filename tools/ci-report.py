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
        ("compile", r"\[ERROR\] /home/runner/\S+\.java:\[[^\]]+\] .{0,300}"),
        # Dependency resolution failures never contain a .java path, so they need their own
        # patterns; without these the report is empty and the failure looks unexplained.
        ("deps", r"(?:Could not resolve dependencies|Could not find artifact|Failed to read artifact descriptor"
                r"|Non-resolvable import POM|Could not transfer artifact|Unresolveable build extension"
                r"|The following artifacts could not be resolved)"),
        ("plugin", r"(?:Failed to execute goal [\w.:-]+|Plugin .* or one of its dependencies could not be resolved)"),
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


def report_error_lines(log: str) -> None:
    """Final safety net: surface any [ERROR] line not already covered.

    A silent CI report is worse than a noisy one, because it turns a build failure into an
    unexplained build failure.
    """
    seen: set[str] = set()
    for match in list(re.finditer(r"\[ERROR\][^\n]{0,400}", log))[:15]:
        line = " ".join(match.group(0).split())
        if line in seen:
            continue
        seen.add(line)
        emit("error", "build", line)


def report_summaries() -> None:
    """Per-class totals, read from the XML rather than the .txt summary.

    The .txt file beside a report describes only the outer class, so a class whose
    tests all live in @Nested classes reports "Tests run: 0" there even though every
    test executed. Reading the XML counts the nested test cases too, which is the
    number a reviewer needs; without it a green build looks like a build that ran
    nothing.
    """
    total = failures = errors = skipped = 0
    classes = 0
    for report in sorted(glob.glob("target/surefire-reports/*.xml")):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        cases = list(root.iter("testcase"))
        if not cases:
            continue
        classes += 1
        case_fail = sum(1 for c in cases if c.findall("failure"))
        case_err = sum(1 for c in cases if c.findall("error"))
        case_skip = sum(1 for c in cases if c.findall("skipped"))
        total += len(cases)
        failures += case_fail
        errors += case_err
        skipped += case_skip
        name = (root.get("name") or report.split("/")[-1][:-4]).split(".")[-1]
        emit("notice", f"TEST {name}",
             f"Tests run: {len(cases)}, Failures: {case_fail}, Errors: {case_err}, Skipped: {case_skip}")
    # One aggregate, so a partial run is distinguishable from a complete one.
    emit("notice", "TEST TOTAL",
         f"Classes: {classes}, Tests run: {total}, Failures: {failures}, Errors: {errors}, "
         f"Skipped: {skipped}")


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "build.log"
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            log = handle.read()
    except FileNotFoundError:
        emit("warning", "ci-report", f"{path} not found")
        return 0
    report_log(log)
    report_error_lines(log)
    report_test_results()
    report_summaries()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
