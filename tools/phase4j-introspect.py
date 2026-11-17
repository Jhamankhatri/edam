#!/usr/bin/env python3
"""Introspect EDAM source to generate structured data for Phase 4J documentation."""
import os, re, json, glob
from pathlib import Path

ROOT = Path("/home/user/edam")
SRC = ROOT / "src/main/java"
TEST = ROOT / "src/test/java"
MIG = ROOT / "src/main/resources/db/migration"

def read(p): return p.read_text()

def java_files(base): return list(base.rglob("*.java"))

# ---- parse controllers ----
HTTP_ANNOTS = ["GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"]
PERM_RE = re.compile(r"@EdamPermission\(\s*Permissions\.([A-Z_]+)")
ROUTE_RE = re.compile(r'@RequestMapping\(path\s*=\s*ApiPaths\.V1\s*\+\s*"([^"]+)"')
PUB_ENDPOINT_RE = re.compile(r"@PublicEndpoint")
AUTH_ENDPOINT_RE = re.compile(r"@AuthenticatedEndpoint")

def extract_string_arg(s):
    m = re.search(r'"([^"\\]*(?:\\.[^"\\]*)*)"', s)
    return m.group(1) if m else None

controllers = {}
for cf in java_files(SRC):
    rel = cf.relative_to(SRC)
    if "controller" not in str(rel).lower(): continue
    if cf.name in ("DevUserController.java",): continue
    text = read(cf)
    cls = cf.stem
    base_route_m = ROUTE_RE.search(text)
    base_route = base_route_m.group(1) if base_route_m else ""
    # strip comments crudely
    text_noc = re.sub(r"//[^\n]*", "", text)
    text_noc = re.sub(r"/\*.*?\*/", "", text_noc, flags=re.S)
    # walk lines to find methods and annotations above them
    # collapse the file to scan at annotation scope: walk characters, find each HTTP mapping
    # annotation and collect all annotation lines that sit between the previous method/block
    # boundary and this annotation.
    lines = text_noc.split("\n")
    endpoints = []
    for i, line in enumerate(lines):
        for ann in HTTP_ANNOTS:
            if f"@{ann}" in line:
                # Collect all consecutive annotation lines (@X, @Y, @Z...) around the HTTP mapping
                # (above AND below until we hit a non-@ line like `public ...`).
                ann_lines = [line.strip()]
                j = i - 1
                while j >= 0:
                    s = lines[j].strip()
                    if s.startswith("@"):
                        ann_lines.append(s); j -= 1; continue
                    if s.startswith("*") or s.startswith("/**") or s.startswith("*/") or s == "":
                        j -= 1; continue
                    break
                j = i + 1
                while j < len(lines):
                    s = lines[j].strip()
                    if s.startswith("@"):
                        ann_lines.append(s); j += 1; continue
                    break
                ann_text = "\n".join(ann_lines).replace("\n", " ")
                pm = PERM_RE.search(ann_text)
                perm = pm.group(1) if pm else None
                is_pub = ("@PublicEndpoint" in ann_text) or ("@com.edam.common.security.PublicEndpoint" in ann_text)
                is_auth = ("@AuthenticatedEndpoint" in ann_text) or ("@com.edam.common.security.AuthenticatedEndpoint" in ann_text)
                # find method signature
                k = i + 1
                sig = ""
                while k < len(lines) and "{" not in sig:
                    sig += " " + lines[k].strip()
                    k += 1
                # method name
                mm = re.search(r"public\s+[\w<>\[\],?\s.]+\s+(\w+)\s*\(", sig)
                mname = mm.group(1) if mm else "<unknown>"
                path = extract_string_arg(line) or ""
                method = ann.replace("Mapping","").upper()
                if method == "GET": verb = "GET"
                elif method == "POST": verb = "POST"
                elif method == "PUT": verb = "PUT"
                elif method == "DELETE": verb = "DELETE"
                elif method == "PATCH": verb = "PATCH"
                else: verb = method
                full_path = (base_route + (path if path.startswith("/") else "/" + path if path else "")).replace("//","/")
                endpoints.append({
                    "method": verb,
                    "path": full_path,
                    "methodName": mname,
                    "permission": perm,
                    "public": is_pub,
                    "authenticated": is_auth,
                })
                break
    controllers[cls] = {"base": base_route, "endpoints": endpoints, "file": str(rel)}

# ---- permissions ----
perm_const = SRC / "com/edam/common/security/Permissions.java"
perms = []
for line in read(perm_const).split("\n"):
    m = re.search(r'public static final String ([A-Z_]+)\s*=\s*"([^"]+)"', line)
    if m: perms.append({"const": m.group(1), "code": m.group(2)})

# ---- migrations ----
migrations = []
for f in sorted(MIG.glob("V*.sql")):
    name = f.name
    t = read(f)
    t_noc = re.sub(r"--[^\n]*", "", t)
    t_noc = re.sub(r"/\*.*?\*/", "", t_noc, flags=re.S)
    tables_created = re.findall(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)", t_noc, re.I)
    tables_altered = re.findall(r"ALTER\s+TABLE\s+(?:ONLY\s+)?(\w+)", t_noc, re.I)
    migrations.append({"file": name, "creates": tables_created, "alters": list(set(tables_altered))})

# ---- entities / tables ----
entity_dir = SRC
entities = {}
for jf in java_files(entity_dir):
    text = read(jf)
    m_table = re.search(r'@Table\(name\s*=\s*"(\w+)"', text)
    if not m_table: continue
    tbl = m_table.group(1)
    cls = jf.stem
    cols = []
    # @Column matches
    for cm in re.finditer(r'@Column\(([^)]*)\)\s+private\s+([\w<>?,\s]+)\s+(\w+)\s*;', text, re.S):
        attrs = cm.group(1)
        jtype = cm.group(2).strip()
        fname = cm.group(3)
        cname_m = re.search(r'name\s*=\s*"(\w+)"', attrs)
        cname = cname_m.group(1) if cname_m else fname
        nullable_m = re.search(r'nullable\s*=\s*(true|false)', attrs)
        nullable = nullable_m.group(1) if nullable_m else "true"
        coldef_m = re.search(r'columnDefinition\s*=\s*"([^"]+)"', attrs)
        length_m = re.search(r'length\s*=\s*(\d+)', attrs)
        precision_m = re.search(r'precision\s*=\s*(\d+)', attrs)
        cols.append({"field": fname, "column": cname, "jtype": jtype, "nullable": nullable, "coldef": coldef_m.group(1) if coldef_m else None, "length": length_m.group(1) if length_m else None, "precision": precision_m.group(1) if precision_m else None})
    # @JoinColumn
    for jm in re.finditer(r'@JoinColumn\(([^)]*)\)\s+private\s+([\w<>?,\s]+)\s+(\w+)\s*;', text, re.S):
        attrs = jm.group(1)
        fname = jm.group(3)
        cname_m = re.search(r'name\s*=\s*"(\w+)"', attrs)
        cname = cname_m.group(1) if cname_m else fname+"_id"
        cols.append({"field": fname, "column": cname, "jtype": jm.group(2).strip(), "nullable": "true", "join": True})
    # id
    for im in re.finditer(r'@Id\s+@Column\(name\s*=\s*"(\w+)"', text):
        pass
    entities[tbl] = {"class": cls, "file": str(jf.relative_to(SRC)), "cols": cols}

# ---- write outputs ----
out = ROOT / "docs/phase-4j"
out.mkdir(parents=True, exist_ok=True)
(out / "_generated").mkdir(exist_ok=True)
(out / "postman").mkdir(exist_ok=True)
(out / "_generated/controllers.json").write_text(json.dumps(controllers, indent=2))
(out / "_generated/permissions.json").write_text(json.dumps(perms, indent=2))
(out / "_generated/migrations.json").write_text(json.dumps(migrations, indent=2))
(out / "_generated/entities.json").write_text(json.dumps(entities, indent=2))

print(f"controllers: {len(controllers)}, endpoints: {sum(len(c['endpoints']) for c in controllers.values())}")
print(f"permissions: {len(perms)}")
print(f"migrations: {len(migrations)}")
print(f"entities: {len(entities)}")
