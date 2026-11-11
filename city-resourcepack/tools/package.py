#!/usr/bin/env python3
from pathlib import Path
import json,zipfile,hashlib
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'releases/city-resourcepack';HERE=ROOT/'city-resourcepack'
validation=json.loads((OUT/'validation.json').read_text());assert validation['pack_checks']>=400 and validation['bridge_mockbukkit_cases']>=16 and validation['browser']['passed']
entries={}
for name in ['CityEconomy-26.3-1.0.0.zip','CityBuild-26.3-1.0.0.zip','CityEconomy-Complete-26.3-1.0.0.zip']:entries['클라이언트/'+name]=(OUT/name).read_bytes()
entries['서버/plugins/CityTextures-1.0.0.jar']=(OUT/'CityTextures-1.0.0.jar').read_bytes()
entries['설치안내.md']=(HERE/'README.md').read_bytes();entries['LICENSE.txt']=(HERE/'LICENSE.txt').read_bytes()
for name in ['미리보기.png','미리보기.html','건축재질-반복확인.png','자동배포-설정예시.yml','SHA1.txt','validation.json','build-info.json']:entries[name]=(OUT/name).read_bytes()
for module in ['city-textures','city-resourcepack']:
    for p in (ROOT/module).rglob('*'):
        if p.is_file() and 'build' not in p.relative_to(ROOT/module).parts and '__pycache__' not in p.parts and '/art/' not in str(p):entries['소스/'+str(p.relative_to(ROOT))]=p.read_bytes()
entries['SHA256SUMS.txt']='\n'.join(hashlib.sha256(data).hexdigest()+'  '+name for name,data in sorted(entries.items())).encode()
archive=OUT/'CityEconomy-Package-1.0.0.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for name,data in sorted(entries.items()):
        info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
(OUT/'SHA256SUMS.txt').write_text('\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name for p in sorted(OUT.iterdir()) if p.is_file() and p.suffix in ['.zip','.jar','.png','.html','.yml','.json'])+'\n')
print('Packaged',archive,'bytes',archive.stat().st_size)
