"""Restore the pinned prebuilt input when building an unpacked source snapshot.

This intentionally does not reset, delete, or replace an existing checkout.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
RELATIVE = "lib/fcitx5/src/main/cpp/prebuilt"
COMMIT = "86ce2c95d42f1132746fbf60c278193aa1f4b758"
URL = "https://github.com/fcitx5-android/prebuilt.git"
target = ROOT / RELATIVE
if target.exists() and any(target.iterdir()):
    result = subprocess.run(
        ["git", "-C", str(target), "rev-parse", "HEAD"],
        text=True, capture_output=True,
    )
    if result.returncode != 0 or result.stdout.strip() != COMMIT:
        raise SystemExit("Existing prebuilt directory does not match the recorded input; preserve and inspect it.")
    print("Pinned prebuilt input already exists; existing files were preserved.")
else:
    target.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "init", str(target)], check=True)
    subprocess.run(["git", "-C", str(target), "remote", "add", "origin", URL], check=True)
    subprocess.run(["git", "-C", str(target), "fetch", "--depth=1", "origin", COMMIT], check=True)
    subprocess.run(["git", "-C", str(target), "checkout", "--detach", "FETCH_HEAD"], check=True)
    print("Restored pinned prebuilt input.")
