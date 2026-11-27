"""Verify the exact already-built development APK; never publish the APK or mark release-ready."""
import collections
import io
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parent.parent
apk = Path(sys.argv[1])
out = Path(sys.argv[2])
lock = json.loads((root / "runtime-lock.json").read_text())
runtime = root / "app/src/main/assets/htq"
expected = {p.relative_to(runtime).as_posix(): p for p in runtime.rglob("*") if p.is_file()}
assert len(expected) == lock["entries"], len(expected)
package = json.loads((runtime / "manifest/package.json").read_text())
assert package["release_ready"] is False
assert package["native_only"] is True
sdk = Path(os.environ["ANDROID_SDK_ROOT"])
tools = sdk / "build-tools/34.0.0"
def run(*args):
    return subprocess.check_output([str(x) for x in args], text=True, stderr=subprocess.STDOUT)
badging = run(tools / "aapt", "dump", "badging", apk)
permissions = run(tools / "aapt", "dump", "permissions", apk)
signature = run(tools / "apksigner", "verify", "--verbose", "--print-certs", apk)
assert "package: name='vn.htq.mobile'" in badging
assert "sdkVersion:'24'" in badging and "targetSdkVersion:'35'" in badging
for name in (
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CAMERA", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
    "com.android.vending.BILLING", "com.google.android.gms.permission.AD_ID",
):
    assert name not in permissions, name
with zipfile.ZipFile(apk) as z:
    entries = z.namelist()
    assert len(entries) == len(set(entries))
    assert "classes.dex" in entries and "AndroidManifest.xml" in entries
    prefix = "assets/htq/"
    bundled = {p[len(prefix):] for p in entries if p.startswith(prefix) and not p.endswith("/")}
    assert bundled == set(expected), (sorted(set(expected)-bundled)[:10], sorted(bundled-set(expected))[:10])
    for rel, path in expected.items():
        assert hashlib.sha256(z.read(prefix+rel)).digest() == hashlib.sha256(path.read_bytes()).digest(), rel
    for name in ("assets/htq_ui/content.json","assets/htq-font/NotoSans-Regular.ttf","assets/htq-font/NotoSans-Bold.ttf","assets/htq-font/LICENSE-NotoSans.txt"):
        assert name in entries, name
    home_root = root / "app/src/main/assets/htq_home/layers"
    home_packs = {
        "scene.zip": "3adaa879b20b264390387da9b9b5a652fa77e1cfac3a350bdfa4957c1bca4438",
        "header.zip": "01144c863603fcbc62eec7316efafb94cc8f01dd20311731b321bf6e1a2c9033",
        "tiles_a.zip": "d745f96837a235474cc15f623629c1243a82336327d8d628df60bca2c257a6c1",
        "tiles_b.zip": "82aea3f4ff685ef63d44d2db053c9510cabf08fbe8cb0b5a80788be3f2e2cc10",
    }
    layer_names = set()
    layout = None
    assert "assets/htq_home/approved.webp" not in entries
    for name, digest in home_packs.items():
        data = (home_root / name).read_bytes()
        assert hashlib.sha256(data).hexdigest() == digest
        assert z.read("assets/htq_home/layers/" + name) == data
        with zipfile.ZipFile(io.BytesIO(data)) as part:
            assert part.testzip() is None
            for entry in part.namelist():
                assert entry not in layer_names
                layer_names.add(entry)
                if entry == "layout.json":
                    layout = json.loads(part.read(entry))
                else:
                    image = part.read(entry)
                    assert image[:4] == b"RIFF" and image[8:12] == b"WEBP" and image[12:16] == b"VP8L", entry
    assert layout["source_sha256"] == "3ddbddcc5f128f480f89ee0b0d7cfcad9f29d8dfcc975349edf0de29589802c2"
    assert len(layout["layers"]) == 16 and sum(l["kind"] == "button" for l in layout["layers"]) == 8
    assert layer_names == {"layout.json", "scene.webp"} | {l["id"] + ".webp" for l in layout["layers"]}
    home_sha = layout["source_sha256"]
    print("HTQ_APPROVED_HOME_ART_PASS", home_sha, "lossless_layers=17 independent_buttons=8")
    media_counts = dict(collections.Counter(p.suffix.lower() for p in expected.values()))
    for name in entries:
        if name.startswith("assets/htq/") and name.endswith((".ogg",".webp",".png")):
            assert z.getinfo(name).compress_type == zipfile.ZIP_STORED, name
credits = json.loads((runtime / "credits/audio_attributions.json").read_text())
assert isinstance(credits, list)
statuses = dict(collections.Counter(str(r.get("license_review_status", "not_recorded")) for r in credits))
# A prior metadata record is not an independent legal clearance.
pending = [{"asset_id": r.get("asset_id",r.get("id",r.get("audio_id","unknown"))),
            "status": r.get("license_review_status","not_recorded")}
           for r in credits if r.get("license_review_status") != "retain_prior_record_evidence"]
report = {
    "source_commit": os.environ.get("GITHUB_SHA","local"),
    "runtime_version": lock["runtime_version"],
    "runtime_sha256": lock["sha256"],
    "verified_packaged_runtime_files": len(expected),
    "approved_home_sha256": home_sha,
    "approved_home_lossless_packs": home_packs,
    "approved_home_lossless_layers": 17,
    "file_types": media_counts,
    "apk_bytes": apk.stat().st_size,
    "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "manifest_badging": [l for l in badging.splitlines() if l.startswith(("package:","sdkVersion:","targetSdkVersion:","application-label:","launchable-activity:"))],
    "manifest_permissions": permissions.splitlines(),
    "debug_signature_verification": signature.splitlines(),
    "audio_attribution_records": len(credits),
    "recorded_audio_license_status_counts": statuses,
    "audio_records_requiring_review": pending,
    "independent_source_rights_verified": False,
    "physical_device_verified": False,
    "specialist_content_verified": False,
    "release_ready": False,
    "apk_publication_enabled": False,
}
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print("HTQ_PACKAGE_PASS", "files="+str(len(expected)), "bytes="+str(apk.stat().st_size), "sha256="+report["apk_sha256"])
print("HTQ_RECORDED_LICENSE_STATUS", json.dumps(statuses, ensure_ascii=False), "release_ready=false")
