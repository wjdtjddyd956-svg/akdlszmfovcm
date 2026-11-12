#!/usr/bin/env python3
"""Ship the NPC economy replacement without replacing user data or other plugin JARs."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile
import yaml

ROOT=Path(__file__).resolve().parent
REPO=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file())
RELEASE=REPO/'releases/survival-pack'
STAGE=ROOT/'build/npc-shop-update'
if STAGE.exists():shutil.rmtree(STAGE)
(STAGE/'plugins').mkdir(parents=True)
RELEASE.mkdir(parents=True,exist_ok=True)
jar=REPO/'개발자료/플러그인/생활경제/build/libs/KoreanEconomy-1.1.0.jar'
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    meta=yaml.safe_load(z.read('plugin.yml'))
    assert str(meta['api-version'])=='26.3'
    assert meta['permissions']['koreaneconomy.npc.admin']['default']=='op'
    assert not any(n.startswith(('org/','net/')) for n in z.namelist())
shutil.copyfile(jar,STAGE/'plugins'/jar.name)
shutil.copyfile(jar,RELEASE/jar.name)
shutil.copyfile(ROOT/'NPC상점-설치안내.md',STAGE/'설치안내.md')
report=json.loads((REPO/'개발자료/플러그인/생활경제/build/test-results.json').read_text())
report.update({'version':'1.1.0','target_paper':'26.3','npc_backend':'native MANNEQUIN or VILLAGER',
    'additional_npc_plugin_required':False,'actual_paper_runtime_tested':False,
    'actual_world_save_reload_tested':False,'source_api_reference':'https://github.com/PaperMC/Paper/tree/main/paper-api/src/main/java/org/bukkit',
    'worldguard_cancelled_interactions_respected':True,'other_plugin_jars_replaced':False,
    'essential_economy_provider_still_required':True})
(STAGE/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
(STAGE/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(STAGE).as_posix()+'\n' for p in sorted(STAGE.rglob('*')) if p.is_file()))
dest=RELEASE/'NPC-Shop-Korean-Update-1.1.0.zip'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(STAGE.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(STAGE))
with zipfile.ZipFile(dest) as z:assert z.testzip() is None
shutil.copyfile(STAGE/'설치안내.md',RELEASE/'NPC상점-설치안내.md')
shutil.copyfile(STAGE/'validation.json',RELEASE/'NPC-Shop-validation.json')
(RELEASE/'SHA256SUMS-NPC-Shop.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in [dest,RELEASE/jar.name]))
print(json.dumps(report,ensure_ascii=False,indent=2))
print('Created',dest,dest.stat().st_size,'bytes')
