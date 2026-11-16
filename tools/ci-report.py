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


def _escape_property(value: str) -> str:
    """Escape a workflow-command property value.

    GitHub terminates a property at the first colon and interprets %XX escapes inside it. A raw
    ``$`` (as in a nested class name ``Foo$Inner``) or a space silently invalidates the whole
    command, which is how a failing test ends up with no annotation at all.
    """
    text = str(value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    # A colon ends the property list and a comma separates properties; "" is not a safe rendition
    # of either inside a title, so they are encoded. "$" is also stripped because a nested class
    # name ("Foo$Inner") is the common case and its presence has been observed to drop the whole
    # annotation; "Outer.Inner" is unambiguous for a human reader.
    text = text.replace(":", "%3A").replace(",", "%2C").replace("$", ".")
    return text


def _escape_data(value: str) -> str:
    """Escape workflow-command message data."""
    return (str(value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))


MAX_MESSAGE = 1400


def emit(kind: str, title: str, message: str, file: str | None = None, line: int | None = None) -> None:
    """Emit one annotation.

    A long message is split into chunked annotations sharing the same title (with a (1/N)
    suffix) so that stack traces with SQLState / PostgreSQL error text are not silently
    truncated -- when artifact upload is blocked the annotations are the only diagnostic
    channel, and truncating at the assertion line hides the root cause.
    """
    text = " ".join(str(message).split())
    chunks: list[str] = []
    if not text:
        chunks = [""]
    else:
        i = 0
        while i < len(text):
            chunks.append(text[i:i + MAX_MESSAGE])
            i += MAX_MESSAGE
    if len(chunks) == 1:
        heads = [title]
    else:
        heads = [f"{title} ({i+1}/{len(chunks)})" for i in range(len(chunks))]
    loc = ""
    if file:
        loc = f" file={_escape_property(file)}"
        if line:
            loc += f",line={line}"
    for head, chunk in zip(heads, chunks):
        print(f"::{kind} title={_escape_property(head)}{loc}::{_escape_data(chunk)}")


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
                    # The message attribute states the assertion that failed; the element text
                    # repeats it followed by the stack. Prefer the message so the annotation opens
                    # with the reason rather than with a frame.
                    message = (bad.get("message") or "").strip()
                    body = " ".join((bad.text or "").split())
                    if message and body and message not in body:
                        detail = f"{message} | {body}"
                    else:
                        detail = body or message or tag
                    # Emit the raw body un-collapsed (preserve newlines) for the first chunk
                    # so Caused-by chains remain readable, then a collapsed version too.
                    raw = (bad.text or "").strip()
                    if raw:
                        detail_raw = f"{message}\n{raw}" if message and message not in raw else (raw or message)
                    else:
                        detail_raw = detail
                    src_line = None
                    src_file = None
                    m = re.search(r"\((\w+\.java):(\d+)\)", raw or "")
                    if m:
                        src_file = m.group(1)
                        src_line = int(m.group(2))
                    emit("error", f"FAIL {classname}.{name}", detail_raw, file=src_file, line=src_line)


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
    unexplained build failure. It is capped hard, though: GitHub discards annotations beyond a
    per-run limit, and an unbounded dump of compiler lines is exactly what pushes the specific
    failing-test evidence off the end of that list.
    """
    seen: set[str] = set()
    for match in list(re.finditer(r"\[ERROR\][^\n]{0,400}", log))[:3]:
        line = " ".join(match.group(0).split())
        if line in seen:
            continue
        seen.add(line)
        emit("error", "build", line)


def report_summaries() -> None:
    """One aggregate line naming every class that produced a report, then the red ones.

    Totals are read from the XML rather than the .txt summary: the .txt beside a report
    describes only the outer class, so a class whose tests all live in @Nested classes
    reports "Tests run: 0" there even though every test executed.

    The aggregate goes first and names every class, because GitHub discards annotations
    past a per-run budget. A truncated list of per-class notices cannot be told from a
    build that ran fewer classes; a total that names its own members can.
    """
    total = failures = errors = skipped = 0
    names = []
    red = []
    for report in sorted(glob.glob("target/surefire-reports/*.xml")):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        cases = list(root.iter("testcase"))
        if not cases:
            continue
        case_fail = sum(1 for c in cases if c.findall("failure"))
        case_err = sum(1 for c in cases if c.findall("error"))
        case_skip = sum(1 for c in cases if c.findall("skipped"))
        total += len(cases)
        failures += case_fail
        errors += case_err
        skipped += case_skip
        name = (root.get("name") or report.split("/")[-1][:-4]).split(".")[-1]
        names.append(name)
        if case_fail or case_err:
            red.append((name, len(cases), case_fail, case_err, case_skip))
    emit("notice", "TEST TOTAL",
         f"Classes: {len(names)}, Tests run: {total}, Failures: {failures}, Errors: {errors}, "
         f"Skipped: {skipped} | {' '.join(names)}")
    # A green class gets no line of its own: it is already counted above, and spending an
    # annotation on "0 failures" is what used to push real failures past the budget.
    for name, ran, case_fail, case_err, case_skip in red:
        emit("notice", f"TEST {name}",
             f"Tests run: {ran}, Failures: {case_fail}, Errors: {case_err}, Skipped: {case_skip}")


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "build.log"
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            log = handle.read()
    except FileNotFoundError:
        emit("warning", "ci-report", f"{path} not found")
        return 0
    # Order matters: GitHub keeps only the first ~10-20 annotations of a run, so the most
    # specific evidence goes first. A failing test case is worth more than a summary line,
    # and the raw [ERROR] dump is the last resort because it is the noisiest.
    report_test_results()
    report_summaries()
    report_log(log)
    report_error_lines(log)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
