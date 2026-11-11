"""Small offline upgrade for existing 1.2/2.0 Windows servers; no third-party downgrade."""
from pathlib import Path
import hashlib, io, json, zipfile
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'releases/tycoon-2.0.1'
def sha(data): return hashlib.sha256(data).hexdigest()
report=json.loads((OUT/'windows-build.json').read_text(encoding='utf-8'))
for item in report['artifacts']:
    assert sha((OUT/item['file']).read_bytes())==item['sha256']
    for name,digest in item['source_sha256'].items(): assert sha((ROOT/name).read_bytes())==digest,name
base=ROOT/'releases/tycoon-2.0/설치도구.jar'
with zipfile.ZipFile(base) as z: assert z.testzip() is None
source=io.BytesIO()
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for module in ('korean-economy','korean-menu','korean-commands','korean-pack-setup','tools','tycoon'):
        for f in sorted((ROOT/module).rglob('*')):
            rel=f.relative_to(ROOT)
            if f.is_file() and not any(x.startswith(('build','__pycache__')) for x in rel.parts[:-1]) and f.suffix in ('.java','.py','.cjs','.json','.ps1','.yml','.md','.txt','.gradle'):
                z.write(f,rel.as_posix())
archive=OUT/'생활타이쿤-Windows-업그레이드-2.0.1.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for item in report['artifacts']: z.write(OUT/item['file'],'plugins/'+item['file'])
    z.writestr('plugins/SHA256.properties','\n'.join(i['file']+'='+i['sha256'] for i in report['artifacts'])+'\n')
    z.write(base,'설치도구.jar')
    script=(ROOT/'tools/installer/Upgrade-2.0.1.ps1').read_text(encoding='utf-8-sig')
    z.writestr('install.ps1',script.replace('\r\n','\n').replace('\n','\r\n').encode('utf-8-sig'))
    z.writestr('설치하기.cmd',b'@echo off\r\ncd /d "%~dp0"\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"\r\npause\r\n')
    for name in ('설치·운영안내.md','검증결과.md','windows-build.json','installer-validation.json'):
        if (OUT/name).exists(): z.write(OUT/name,name)
    z.write(ROOT/'korean-economy/src/main/resources/config.yml','설정예시/KoreanEconomy-config.yml')
    z.write(ROOT/'tycoon/LICENSE.txt','LICENSE.txt')
    z.writestr('소스/자체플러그인-2.0.1.zip',source.getvalue())
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert len([n for n in z.namelist() if n.startswith('plugins/') and n.endswith('.jar')])==4
    assert not any(n.startswith(('plugins/Essentials','plugins/GriefPrevention','plugins/prism')) for n in z.namelist())
(OUT/'SHA256SUMS.txt').write_text('\n'.join(sha(f.read_bytes())+'  '+f.name for f in sorted(OUT.iterdir()) if f.suffix in ('.jar','.zip'))+'\n',encoding='utf-8')
print('PASS:',archive.name,archive.stat().st_size,'bytes (existing 1.2/2.0 installation required)')
