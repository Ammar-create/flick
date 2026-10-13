#!/usr/bin/env python3
"""Recompute sha256 hashes of every extension listed in repo/repo.json.

Run this after editing or adding a plugin. The app refuses to load an extension whose
hash does not match the manifest, so a stale hash is a plugin that silently does nothing.

Usage: python3 scripts/repo_hash.py
"""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
REPO = ROOT / "repo"
MANIFEST = REPO / "repo.json"


def main() -> int:
    data = json.loads(MANIFEST.read_text())
    changed = 0

    for plugin in data.get("plugins", []):
        rel = plugin.get("file")
        if not rel:
            print(f"  skip {plugin.get('id')}: no file field")
            continue
        target = REPO / rel
        if not target.is_file():
            print(f"  MISSING {rel} (plugin {plugin.get('id')})")
            continue
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        new = f"sha256:{digest}"
        if plugin.get("hash") != new:
            plugin["hash"] = new
            changed += 1
            print(f"  {plugin.get('id')}: {digest[:16]}...")
        else:
            print(f"  {plugin.get('id')}: unchanged")

    MANIFEST.write_text(json.dumps(data, indent=2) + "\n")
    print(f"repo.json updated, {changed} hash(es) changed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
