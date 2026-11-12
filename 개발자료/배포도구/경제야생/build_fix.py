#!/usr/bin/env python3
"""Package only the configuration fix; blocked Essentials development JARs are absent."""
from pathlib import Path
import hashlib
import json
import zipfile
import shutil
import yaml

ROOT=Path(__file__).resolve().parent
REPO=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file())
RELEASE=REPO/'releases/survival-pack'
RELEASE.mkdir(parents=True,exist_ok=True)
BUILD=ROOT/'build/paper26.3-fix'
if BUILD.exists():shutil.rmtree(BUILD)
(BUILD/'plugins').mkdir(parents=True)
jar=REPO/'개발자료/플러그인/경제설정/build/libs/KoreanPackSetup-1.0.0.jar'
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    meta=yaml.safe_load(z.read('plugin.yml'))
    assert meta['load']=='STARTUP'
    assert set(meta['loadbefore'])=={'Essentials','EssentialsSpawn','QuickShop-Hikari'}
    assert not any(n.startswith(('org/','net/')) for n in z.namelist())
shutil.copyfile(jar,BUILD/'plugins'/jar.name)
shutil.copyfile(jar,RELEASE/jar.name)
shutil.copyfile(REPO/'개발자료/플러그인/경제설정/README.md',BUILD/'설치안내.md')
(BUILD/'EssentialsX-개발빌드받기.ps1').write_text((ROOT/'EssentialsX-개발빌드받기.ps1').read_text(),encoding='utf-8-sig')
report=json.loads((REPO/'개발자료/플러그인/경제설정/build/test-results.json').read_text())
report.update({'fix_plugin':meta['name'],'version':str(meta['version']),
    'essentials_2_22_0_confirmed_unsupported_for_26_3':True,
    'development_jars_included':False,'development_download_blocker':'Cloud network policy: HTTP 403 for essentialsx.net and ci.ender.zone',
    'actual_windows_downloader_tested':False,
    'quickshop_display_mode':3,'existing_player_balances_modified':False,
    'reference_development_readme':'https://github.com/EssentialsX/Essentials/blob/2.x/README.md',
    'reference_development_version_code':'https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/java/com/earth2me/essentials/utils/VersionUtil.java',
    'snakeyaml_compile_dependency':{'url':'https://repo.maven.apache.org/maven2/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar','sha256':hashlib.sha256((ROOT/'vendor/setup/snakeyaml-2.2.jar').read_bytes()).hexdigest(),'bundled':False}})
(BUILD/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
source=BUILD/'KoreanPackSetup-1.0.0-source.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for p in (REPO/'개발자료/플러그인/경제설정').rglob('*'):
        if p.is_file() and 'build' not in p.relative_to(REPO/'개발자료/플러그인/경제설정').parts and '__pycache__' not in p.parts:
            z.write(p,p.relative_to(REPO))
    z.write(REPO/'개발자료/플러그인/마을수첩/tools/compile_fixtures.py','개발자료/플러그인/마을수첩/tools/compile_fixtures.py')
    for p in (REPO/'개발자료/플러그인/경제설정/build/main').rglob('*'):
        if p.is_file() and not p.name.endswith('.class'):
            z.write(p,'generated-resources/'+p.relative_to(REPO/'개발자료/플러그인/경제설정/build/main').as_posix())
(BUILD/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(BUILD).as_posix()+'\n' for p in sorted(BUILD.rglob('*')) if p.is_file()))
dest=RELEASE/'Paper26.3-Economy-Fix-1.0.0.zip'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(BUILD.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(BUILD))
with zipfile.ZipFile(dest) as z:assert z.testzip() is None
shutil.copyfile(BUILD/'validation.json',RELEASE/'Paper26.3-Fix-validation.json')
shutil.copyfile(BUILD/'설치안내.md',RELEASE/'Paper26.3-수정안내.md')
(RELEASE/'SHA256SUMS-Paper26.3-Fix.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in [dest,RELEASE/jar.name]))
print(json.dumps(report,ensure_ascii=False,indent=2))
print('Created',dest,dest.stat().st_size,'bytes')
