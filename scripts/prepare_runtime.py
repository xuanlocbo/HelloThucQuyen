import hashlib, json, pathlib, shutil, sys, zipfile

root = pathlib.Path(__file__).resolve().parent.parent
lock = json.loads((root / "runtime-lock.json").read_text())
parts = [root / x["name"] for x in lock["parts"]]
missing = [p.name for p in parts if not p.is_file()]
if missing:
    sys.exit("Official runtime missing: " + ", ".join(missing))
archive = root / "HTQ-5.3-runtime-official.zip"
with archive.open("wb") as out:
    for spec, part in zip(lock["parts"], parts):
        data = part.read_bytes()
        if len(data) != spec["bytes"] or hashlib.sha256(data).hexdigest() != spec["sha256"]:
            sys.exit("Runtime part failed SHA-256: " + part.name)
        out.write(data)
if archive.stat().st_size != lock["bytes"] or hashlib.sha256(archive.read_bytes()).hexdigest() != lock["sha256"]:
    sys.exit("Official runtime SHA-256 mismatch")
prefix = "app/src/main/assets/htq/"
with zipfile.ZipFile(archive) as z:
    names = z.namelist()
    if len(names) != lock["entries"] or len(set(names)) != len(names) or any(not n.startswith(prefix) or any(x in ("", ".", "..") for x in n.split("/")) or "\\" in n for n in names):
        sys.exit("Invalid runtime archive entry")
    target = root / prefix
    if target.exists(): shutil.rmtree(target)
    z.extractall(root)
print(f"Official runtime verified: {len(names)} files, SHA-256 {lock['sha256']}")
