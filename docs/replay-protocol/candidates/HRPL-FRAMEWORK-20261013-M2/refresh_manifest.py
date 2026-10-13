"""Refresh M2 package hashes after actual application artifacts are added."""
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent
sha=lambda raw:hashlib.sha256(raw).hexdigest()
baseline=json.loads((ROOT/"baseline-w1"/"manifest.json").read_text("utf-8"))
files={str(p.relative_to(ROOT)).replace("\\","/"):sha(p.read_bytes()) for p in sorted(ROOT.rglob("*"))
       if p.is_file() and p!=ROOT/"manifest.json" and "__pycache__" not in p.parts and p.name!=".DS_Store"}
contracts={name:files[name] for name in ("SPEC.md","CONVERGENCE.md","INITIATIVE.md","FRAMEWORK.md")}
manifest=dict(package_id="HRPL-FRAMEWORK-20261013-M2",baseline_package_id=baseline["package_id"],
    baseline_contract_sha256s=baseline["candidate_contract_sha256s"],candidate_contract_sha256s=contracts,
    status="mac-amendment-for-consolidated-windows-adaptation",application_status="see MATRIX.md and actual result files",files=files)
(ROOT/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,sort_keys=True,indent=2)+"\n","utf-8")
print("M2 manifest:",len(files),"files;",sha((ROOT/"manifest.json").read_bytes()))
for name,digest in contracts.items(): print(name,digest)
