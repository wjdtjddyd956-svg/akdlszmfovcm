#!/usr/bin/env python3
"""Fetch the exact official inputs recorded in our release manifest."""
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
manifest = json.loads((ROOT.parent / 'releases/survival-pack/sources.json').read_text())
for item in manifest['jars'] + manifest['source_archives']:
    target = ROOT / 'vendor' / item['file']
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == item['sha256']:
        print('Verified existing', item['file'])
        continue
    request = urllib.request.Request(item['url'], headers={'User-Agent': 'EconomySurvivalPack/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response:
        content = response.read()
    if hashlib.sha256(content).hexdigest() != item['sha256']:
        raise RuntimeError('Checksum mismatch: ' + item['file'])
    target.write_bytes(content)
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
    print('Downloaded and verified', item['file'])
(ROOT / 'vendor/sources.json').write_text(json.dumps(manifest['jars'], indent=2) + '\n')
(ROOT / 'vendor/upstream-source/sources.json').write_text(json.dumps(manifest['source_archives'], indent=2) + '\n')
