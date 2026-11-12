"""Reproducible distributable: own JAR/configs + exact official download manifest, no vendor JAR redistribution."""
from pathlib import Path
import hashlib,json,shutil,zipfile
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());MOD=ROOT/'개발자료/플러그인/서버연결';OUT=ROOT/'releases/plugin-integration/1.0.0'
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
build=json.loads((OUT/'build-info.json').read_text(encoding='utf-8'))
assert sha(OUT/'KoreanServerBridge-1.0.0.jar')==build['jar_sha256']
for name,expected in build['sources'].items():assert sha(ROOT/name)==expected,name
with zipfile.ZipFile(OUT/'KoreanServerBridge-1.0.0.jar') as z:
    assert not any(n.startswith(('org/bukkit/','net/luckperms/','net/milkbowl/','me/clip/')) or 'Test.class' in n or 'Probe.class' in n for n in z.namelist())
urls=json.loads((ROOT/'.build/catalog-vendor/urls.json').read_text())
sources={
'LuckPerms':('https://github.com/LuckPerms/LuckPerms','MIT','https://luckperms.net/download'),
'Chunky':('https://github.com/pop4959/Chunky','GPL-3.0','https://modrinth.com/plugin/chunky/version/RBuKjN4k'),
'PlaceholderAPI':('https://github.com/PlaceholderAPI/PlaceholderAPI/tree/2.12.3','GPL-3.0','https://github.com/PlaceholderAPI/PlaceholderAPI/releases/tag/2.12.3'),
'TAB':('https://github.com/NEZNAMY/TAB/tree/6.2.1','Apache-2.0','https://github.com/NEZNAMY/TAB/releases/tag/6.2.1'),
'DecentHolograms':('https://github.com/DecentSoftware-eu/DecentHolograms/tree/2.10.2','GPL-3.0','https://github.com/DecentSoftware-eu/DecentHolograms/releases/tag/2.10.2')}
vendor=[]
for filename,url in urls.items():
    file=ROOT/'.build/catalog-vendor'/filename
    with zipfile.ZipFile(file) as z:
        descriptor=z.read('plugin.yml' if 'plugin.yml' in z.namelist() else 'paper-plugin.yml').decode()
    def field(key):
        return next(line.split(':',1)[1].strip().strip("'\"") for line in descriptor.splitlines() if line.startswith(key+':'))
    name=field('name');source,license,release=sources[name]
    vendor.append(dict(name=name,version=field('version'),file=filename,url=url,sha256=sha(file),size=file.stat().st_size,source=source,license=license,release=release))
for name in ['Install-PluginIntegration.ps1','Install-PluginIntegration.cmd']:
    text=(MOD/name).read_text(encoding='utf-8').replace('\r\n','\n').replace('\n','\r\n')
    (OUT/name).write_bytes(text.encode('utf-8-sig' if name.endswith('.ps1') else 'ascii'))
shutil.copy2(ROOT/'releases/ui-update/1.2.1/InterfaceInstaller.jar',OUT/'InterfaceInstaller.jar')
configs=[]
for file in (MOD/'config').rglob('*'):
    if file.is_file():
        target=OUT/'config'/file.relative_to(MOD/'config');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,target)
        entry=dict(path=file.relative_to(MOD/'config').as_posix(),sha256=sha(file))
        if entry['path']=='GriefPreventionData/messages.yml':
            entry['originalSha256']=json.loads((MOD/'gp-overlay.json').read_text(encoding='utf-8'))['originalSha256']
        configs.append(entry)
bundled=[dict(path=f.relative_to(OUT).as_posix(),sha256=sha(f)) for f in [OUT/'KoreanServerBridge-1.0.0.jar',OUT/'InterfaceInstaller.jar',OUT/'Install-PluginIntegration.ps1',*[OUT/'config'/c['path'] for c in configs]]]
# Payload path matches the installer and contains only the new module.
(OUT/'plugins').mkdir(exist_ok=True);shutil.copy2(OUT/'KoreanServerBridge-1.0.0.jar',OUT/'plugins/KoreanServerBridge-1.0.0.jar')
bundled.append(dict(path='plugins/KoreanServerBridge-1.0.0.jar',sha256=sha(OUT/'KoreanServerBridge-1.0.0.jar')))
(OUT/'files.json').write_text(json.dumps(dict(schema=1,vendor=vendor,bundled=bundled,configs=configs,bridgeSha256=sha(OUT/'KoreanServerBridge-1.0.0.jar')),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(OUT/'versions.json').write_text(json.dumps(dict(paper='26.3 build 238',java='27.0.1 actual runtime; target bytecode21',installed_additions=vendor,not_selected={'BetterRTP':'No verified 26.3 release; official binary action blocked and legacy source dependencies unavailable. No paid Addons used.','spark':'Already bundled with Paper; no duplicate JAR.','CoreProtect':'Keep existing Prism records and rollback instead.','Citizens,Quests,JobsReborn,ItemsAdder,Oraxen':'Existing native systems cover these needs; not installed.'}),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for name in ['설치안내.md','명령어표.md','검증결과.md','46종검토.md','README.md','translation-scope.json','gp-overlay.json','NOTICES.md','LICENSE']:
    file=MOD/name
    if file.is_file():
        if file.suffix=='.md':
            text=file.read_text(encoding='utf-8').replace('../releases/plugin-integration/1.0.0/','')
            text=text.replace('../interface-update/README.md','https://github.com/wjdtjddyd956-svg/akdlszmfovcm/blob/main/개발자료/배포도구/메뉴통합/README.md')
            (OUT/name).write_text(text,encoding='utf-8')
        else:shutil.copy2(file,OUT/name)
entries={}
for f in OUT.rglob('*'):
    if f.is_file() and f.suffix.lower() not in ['.zip'] and f.name!='SHA256SUMS.txt' and 'vendor-downloads' not in f.parts:
        entries[f.relative_to(OUT).as_posix()]=f.read_bytes()
for f in MOD.rglob('*'):
    if f.is_file():entries['소스/plugin-integration/'+f.relative_to(MOD).as_posix()]=f.read_bytes()
entries['소스/tools/installer/PackInstaller.java']=(ROOT/'개발자료/공통도구/installer/PackInstaller.java').read_bytes()
archive=OUT/'생활서버-플러그인연결-1.0.0.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name,data in sorted(entries.items()):
        info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert not any(f['file'] in z.namelist() for f in vendor)
    assert not any('Probe.jar' in n or n.endswith(('.db','.mv.db')) for n in z.namelist())
(OUT/'SHA256SUMS.txt').write_text(''.join(sha(f)+'  '+f.name+'\n' for f in [OUT/'KoreanServerBridge-1.0.0.jar',archive]),encoding='utf-8')
print('PASS bundle, pinned five official downloads, no vendor redistribution:',archive.name,sha(archive))
