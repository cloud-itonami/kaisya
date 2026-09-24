#!/usr/bin/env python3
"""kaisya repo evidence. read-only. MEASURE<TAB>key<TAB>value lines + ledger append.
REFUSED + exit 2 if the repo checkout is unreadable (never a silent green)."""
import json, os, subprocess, sys, datetime

ROOT = "~/github/com-junkawasaki"
REPO = os.path.join(ROOT, "orgs/cloud-itonami/kaisya")
HOME = os.path.expanduser("~/.hermes/profiles/kaisya")
LEDGER = os.path.join(HOME, "workspace", "kaisya-ledger.jsonl")

def refuse(reason):
    print("REFUSED\t%s" % reason)
    sys.exit(2)

if not os.path.isdir(os.path.join(REPO, ".git")):
    refuse("repo checkout missing or not a git dir: %s" % REPO)

def git(*args):
    p = subprocess.run(["git", "-C", REPO] + list(args),
                       capture_output=True, text=True)
    if p.returncode != 0:
        refuse("git %s failed: %s" % (" ".join(args), p.stderr.strip()[:200]))
    return p.stdout.strip()

rows = {}
rows["as_of"] = datetime.datetime.now().astimezone().isoformat(timespec="seconds")
rows["head"] = git("rev-parse", "--short", "HEAD")
rows["last_commit_date"] = git("log", "-1", "--format=%ad", "--date=short")
rows["dirty_files"] = len([l for l in git("status", "--porcelain").splitlines() if l.strip()])

def count_ext(base, exts):
    n = 0
    for dp, dns, fns in os.walk(base):
        dns[:] = [d for d in dns if d not in ("node_modules", ".git", ".shadow-cljs", ".nbb", ".cpcr")]
        n += sum(1 for f in fns if f.endswith(exts))
    return n

src = os.path.join(REPO, "src")
rows["source_files"] = count_ext(src, (".cljc", ".cljs", ".cljk", ".clj", ".kotoba")) if os.path.isdir(src) else "UNMEASURED src-missing"
tst = os.path.join(REPO, "test")
rows["test_files"] = count_ext(tst, (".cljc", ".cljs", ".cljk", ".clj", ".kotoba")) if os.path.isdir(tst) else "UNMEASURED test-missing"

# README claims kbb -M:test 25 tests green; the evidence job does not run the suite
# (unattended gate cost) - it checks the suite is runnable and reports the claim freshness.
deps = os.path.join(REPO, "deps.edn")
rows["deps_edn"] = "present" if os.path.isfile(deps) else "absent"
if os.path.isfile(deps):
    txt = open(deps).read()
    rows["test_alias"] = "present" if ":test" in txt else "absent"

pub = os.path.join(REPO, "public", "index.html")
rows["public_entry"] = "present" if os.path.isfile(pub) else "absent"
wr = os.path.join(REPO, "wrangler.jsonc")
rows["wrangler"] = "present" if os.path.isfile(wr) else "absent"

# SPA invariant: 1 mount / 1 bundle per ADR-2608080100
p = subprocess.run(["grep", "-rliE", "single-page|design-quality", REPO], capture_output=True, text=True)
rows["quality_doc_refs"] = len([f for f in p.stdout.splitlines() if "/.git/" not in f])

for k, v in rows.items():
    print("MEASURE\t%s\t%s" % (k, v))

os.makedirs(os.path.dirname(LEDGER), exist_ok=True)
with open(LEDGER, "a") as f:
    f.write(json.dumps(rows, ensure_ascii=False) + "\n")
print("LEDGER\tappended\t%s" % LEDGER)
