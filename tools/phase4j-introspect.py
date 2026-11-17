#!/usr/bin/env python3
"""Enhanced introspection: controllers + DTOs (records) + query params + request bodies."""
import json, os, re
from pathlib import Path

ROOT = Path("/home/user/edam")
SRC = ROOT / "src/main/java"
PERM_CONST = SRC / "com/edam/common/security/Permissions.java"
RES = ROOT / "src/main/resources"
MIG = RES / "db/migration"

HTTP_ANNOTS = ["GetMapping","PostMapping","PutMapping","DeleteMapping","PatchMapping"]
PERM_RE = re.compile(r"@EdamPermission\(\s*Permissions\.([A-Z_]+)")
PUB_ENDPOINT_RE = re.compile(r"@(?:com\.edam\.common\.security\.)?PublicEndpoint")
AUTH_ENDPOINT_RE = re.compile(r"@(?:com\.edam\.common\.security\.)?AuthenticatedEndpoint")

def strip_comments(s):
    s = re.sub(r"//[^\n]*", "", s)
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return s

def read(p): return Path(p).read_text()

def java_files(root):
    for p in Path(root).rglob("*.java"): yield p

# ---- permissions ----
perms = []
for line in read(PERM_CONST).split("\n"):
    m = re.search(r'public static final String ([A-Z_]+)\s*=\s*"([^"]+)"', line)
    if m: perms.append({"const": m.group(1), "code": m.group(2)})

# ---- DTOs: Java records under dto/ packages ----
def parse_dto_records():
    """Parse every Java record under dto/ into {fqcn, fields:[{name,type,annotations}]}.
    Handles records declared inside *Dtos.java multi-record files."""
    records = {}  # simpleName -> dict
    # Map fully-qualified simple names via package declaration
    for jf in java_files(SRC):
        if "/dto/" not in str(jf).replace("\\","/"): continue
        text = strip_comments(read(jf))
        pkg_m = re.search(r'package\s+([\w.]+);', text)
        pkg = pkg_m.group(1) if pkg_m else ""
        # find records (annotations may precede 'record')
        for m in re.finditer(r'(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+)?(?:static\s+)?(?:final\s+)?record\s+(\w+)\s*\(', text):
            name = m.group(1)
            start = m.end()
            depth=1; i=start
            while i<len(text) and depth>0:
                c=text[i]
                if c=='(': depth+=1
                elif c==')': depth-=1
                i+=1
            body = text[start:i-1]
            fields=[]
            # split parameters on commas not inside angle brackets
            parts=[]; depth=0; cur=""
            for ch in body:
                if ch=='<': depth+=1
                elif ch=='>': depth-=1
                if ch==',' and depth==0:
                    parts.append(cur.strip()); cur=""
                else:
                    cur+=ch
            if cur.strip(): parts.append(cur.strip())
            for p in parts:
                if not p: continue
                # annotations then type then name
                p2 = re.sub(r'@\w+(?:\([^)]*\))?\s*','',p).strip()
                # strip leading 'final '
                p2 = re.sub(r'^\s*(?:final|public|protected|private)\s+','',p2)
                # p2 looks like "String username" or "List<String> items" or "int page" or "@NotBlank String username" already stripped
                mm = re.match(r'((?:[\w$.]+(?:<[^>]+>)?(?:\s*,\s*[\w$.]+(?:<[^>]+>)?)*)?)\s+(\w+)$', p2)
                if mm:
                    typ = mm.group(1).strip(); fn = mm.group(2)
                    # collect annotations on the original part
                    annots = re.findall(r'@(\w+)(?:\(([^)]*)\))?', p)
                    fields.append({"name":fn,"type":typ,"annotations":[{"a":a,"args":(args or "").strip()} for a,args in annots]})
            fqcn = f"{pkg}.{name}" if pkg else name
            records[name] = {"name":name,"fqcn":fqcn,"fields":fields,"file":str(jf.relative_to(ROOT))}
    return records

def parse_regular_classes_dto():
    """Also pick up non-record DTOs (e.g. AuthDtos.LoginRequest inner static classes).
    For simplicity we look for static classes with @NotNull/@NotBlank etc. inside dto files.
    Returns {simpleName: {...}}."""
    out={}
    for jf in java_files(SRC):
        if "/dto/" not in str(jf).replace("\\","/"): continue
        text = strip_comments(read(jf))
        pkg_m = re.search(r'package\s+([\w.]+);', text)
        pkg = pkg_m.group(1) if pkg_m else ""
        for m in re.finditer(r'(?:public\s+)?static\s+(?:final\s+)?class\s+(\w+)\s*\{([^}]*)\}', text, re.S):
            name = m.group(1); body = m.group(2); fields=[]
            # field lines: [access] [type] [name];
            for fm in re.finditer(r'(?:private|public|protected)\s+(?:final\s+)?([\w$.<>?, ]+)\s+(\w+)\s*;', body):
                typ=fm.group(1).strip(); fn=fm.group(2)
                # find annotations preceding this field (within body, approximate)
                annots=[]
                fields.append({"name":fn,"type":typ,"annotations":annots})
            # attempt to capture @NotBlank/@NotNull/@Size/@Min/@Max/@Pattern/@Email near the field via scanning
            # (a bit loose; sufficient for doc generation)
            fqcn = f"{pkg}.{name}" if pkg else name
            out[name] = {"name":name,"fqcn":fqcn,"fields":fields,"file":str(jf.relative_to(ROOT))}
    return out

records = parse_dto_records()
static_classes = parse_regular_classes_dto()
records.update(static_classes)

# ---- controllers ----
controllers = {}
for jf in java_files(SRC / "com/edam"):
    text = read(jf)
    text_noc = strip_comments(text)
    # skip DevUserController
    if "DevUserController" in str(jf): continue
    m_ctrl = re.search(r'@RestController\b', text_noc)
    if not m_ctrl: continue
    rm = re.search(r'@RequestMapping\(path\s*=\s*ApiPaths\.V1\s*\+\s*"([^"]+)"', text_noc)
    base_route = rm.group(1) if rm else ""
    cls_m = re.search(r'class\s+(\w+)', text_noc)
    if not cls_m: continue
    cls = cls_m.group(1)

    endpoints=[]
    lines = text_noc.split("\n")
    for i,line in enumerate(lines):
        for ann in HTTP_ANNOTS:
            if f"@{ann}" in line:
                # parse path from annotation
                path_m = re.search(rf'@{ann}\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"', line) or re.search(rf'@{ann}\(\s*\)', line)
                sub_path = ""
                if path_m and path_m.lastindex and path_m.group(1):
                    sub_path = path_m.group(1)
                elif '"' in line:
                    sm = re.search(r'"([^"]*)"', line)
                    if sm: sub_path = sm.group(1)
                path = base_route + (sub_path if sub_path.startswith("/") else ("/"+sub_path if sub_path else ""))
                if not path.startswith("/"): path="/"+path
                # Collect surrounding annotations
                ann_lines=[line.strip()]
                j=i-1
                while j>=0:
                    s=lines[j].strip()
                    if s.startswith("@"):
                        ann_lines.append(s); j-=1; continue
                    if s.startswith("*") or s.startswith("/**") or s.startswith("*/") or s=="":
                        j-=1; continue
                    break
                j=i+1
                while j<len(lines):
                    s=lines[j].strip()
                    if s.startswith("@"):
                        ann_lines.append(s); j+=1; continue
                    break
                # find method signature (downward until ")" followed by "{" or ";")
                sig_buf=""
                k=i+1
                while k<len(lines):
                    sig_buf+= " "+lines[k].strip()
                    if "{" in sig_buf or ("(" in sig_buf and ");" in sig_buf):
                        break
                    k+=1
                sig = sig_buf.split("{")[0].strip()
                mm = re.search(r'(?:public|protected)\s+(?:[\w$.<>?, ]+)\s+(\w+)\s*\(([^)]*)\)', sig)
                method_name = mm.group(1) if mm else "?"
                params_s = mm.group(2) if mm else ""
                # parse params into list of (type, name, annotations)
                params=[]
                if mm:
                    pp=[]; depth=0; cur=""
                    for ch in params_s:
                        if ch=='<': depth+=1
                        elif ch=='>': depth-=1
                        if ch==',' and depth==0:
                            pp.append(cur.strip()); cur=""
                        else:
                            cur+=ch
                    if cur.strip(): pp.append(cur.strip())
                    for p in pp:
                        p2 = re.sub(r'@\w+(?:\([^)]*\))?\s*','',p).strip()
                        pm = re.match(r'(?:final\s+)?([\w$.<>?, ]+?)\s+(\w+)$', p2)
                        if pm:
                            typ=pm.group(1).strip(); nm=pm.group(2)
                            if typ in ("HttpServletRequest","HttpServletResponse","MultipartFile","BindingResult","Errors","Principal","Authentication"):
                                params.append({"name":nm,"type":typ,"annotations":[],"implicit":True})
                                continue
                            path_ann = re.search(r'@PathVariable(?:\(\s*(?:name|value)\s*=\s*"(\w+)"\s*\))?', p)
                            q_ann = re.search(r'@RequestParam(?:\(\s*[^)]*\))?', p)
                            body_ann = re.search(r'@RequestBody', p)
                            parts_ann = re.search(r'@RequestPart', p)
                            model_ann = re.search(r'@ModelAttribute', p)
                            valid_ann = re.search(r'@Valid(?:ated)?', p)
                            kind="plain"
                            if path_ann: kind="path"
                            elif q_ann: kind="query"
                            elif body_ann: kind="body"
                            elif parts_ann: kind="part"
                            params.append({"name":nm,"type":typ,"kind":kind,"annotations":[],"implicit":False})
                ann_text = " ".join(ann_lines)
                pm = PERM_RE.search(ann_text)
                perm = pm.group(1) if pm else None
                is_pub = bool(PUB_ENDPOINT_RE.search(ann_text))
                is_auth = bool(AUTH_ENDPOINT_RE.search(ann_text))
                method = ann.upper().replace("MAPPING","")
                endpoints.append({
                    "method":method,"path":path,"methodName":method_name,
                    "permission":perm,"public":is_pub,"authenticated":is_auth,
                    "params":params,
                    "returns":"?"
                })
    controllers[cls]={"class":cls,"basePath":base_route,"file":str(jf.relative_to(ROOT)),"endpoints":endpoints}

# ---- migrations ----
migrations=[]
for f in sorted(MIG.glob("V*.sql")):
    t = read(f)
    t_noc = re.sub(r"--[^\n]*","",t); t_noc=re.sub(r"/\*.*?\*/","",t_noc,flags=re.S)
    migrations.append({"file":f.name,
        "creates":list(set(re.findall(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)", t_noc, re.I))),
        "alters":list(set(re.findall(r"ALTER\s+TABLE\s+(?:ONLY\s+)?(\w+)", t_noc, re.I)))})

# ---- entities ----
entities={}
for jf in java_files(SRC):
    text=read(jf)
    m_table = re.search(r'@Table\(name\s*=\s*"(\w+)"', text)
    if not m_table: continue
    tbl=m_table.group(1); cls=jf.stem; cols=[]
    for cm in re.finditer(r'@Column\(([^)]*)\)\s+private\s+([\w<>?, ]+?)\s+(\w+)\s*;', text, re.S):
        attrs=cm.group(1); jtype=cm.group(2).strip(); fname=cm.group(3)
        nullable = not ("nullable = false" in attrs)
        length_m = re.search(r'length\s*=\s*(\d+)', attrs)
        prec_m = re.search(r'precision\s*=\s*(\d+)', attrs)
        cols.append({"field":fname,"column":re.search(r'name\s*=\s*"(\w+)"',attrs).group(1) if re.search(r'name\s*=\s*"(\w+)"',attrs) else fname,
                     "jtype":jtype,"nullable":nullable,"length":length_m.group(1) if length_m else None,
                     "precision":prec_m.group(1) if prec_m else None})
    # join columns
    for jm in re.finditer(r'@JoinColumn\(\s*name\s*=\s*"(\w+)"[^)]*\)(.*?)(?:private|public|protected)\s+(\w+)\s+(\w+)\s*;', strip_comments(text), re.S):
        cols.append({"field":jm.group(4),"column":jm.group(1),"jtype":jm.group(3),"join":True,"nullable":not ("nullable = false" in jm.group(0))})
    entities[tbl]={"class":cls,"file":str(jf.relative_to(ROOT)),"cols":cols}

OUT = ROOT / "docs/phase-4j/_generated"
OUT.mkdir(parents=True, exist_ok=True)
(OUT/"controllers.json").write_text(json.dumps(controllers,indent=2))
(OUT/"permissions.json").write_text(json.dumps(perms,indent=2))
(OUT/"migrations.json").write_text(json.dumps(migrations,indent=2))
(OUT/"entities.json").write_text(json.dumps(entities,indent=2))
(OUT/"dtos.json").write_text(json.dumps(records,indent=2))

total_eps=sum(len(c['endpoints']) for c in controllers.values())
print(f"controllers: {len(controllers)}, endpoints: {total_eps}, permissions: {len(perms)}, migrations: {len(migrations)}, entities: {len(entities)}, dtos: {len(records)}")
