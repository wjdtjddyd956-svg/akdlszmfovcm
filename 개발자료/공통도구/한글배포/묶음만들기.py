#!/usr/bin/env python3
"""Preserve published bytes; expose Korean names and an offline install bundle."""
from pathlib import Path
import json,hashlib,zipfile,shutil
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());OUT=ROOT/'설치파일/01_마을상점';HERE=Path(__file__).resolve().parent
SOURCE=ROOT/'releases/civic-update/1.0.0';proof=json.loads((SOURCE/'validation.json').read_text())
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def copy(old,new):
 target=ROOT/new;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/old,target);assert sha(ROOT/old)==sha(target);return target
rows=[]
for name,title,version in [('KoreanEconomy','생활경제','2.0.3'),('KoreanMenu','마을수첩','2.2.0'),('CityTextures','도시화면','1.2.0'),('KoreanCivic','마을상점','1.0.0'),('KoreanFarming','커스텀농사','1.0.2'),('KoreanCodex','아이템도감','1.2.1')]:
 original=name+'-'+version+'.jar';assert sha(SOURCE/original)==proof['artifacts'][original]
 target=copy((SOURCE/original).relative_to(ROOT),OUT.relative_to(ROOT)/'플러그인'/(title+'-'+version+'.jar'))
 with zipfile.ZipFile(target) as z:
  assert 'name: '+name in z.read('plugin.yml').decode() and 'version: '+version in z.read('plugin.yml').decode()
 rows.append({'이름':title,'식별자':name,'버전':version,'파일':target.relative_to(OUT).as_posix(),'설치파일명':original,'SHA256':sha(target)})
installer=copy((SOURCE/'CivicInstaller.jar').relative_to(ROOT),OUT.relative_to(ROOT)/'설치도구.jar')
manifest={'형식':1,'기능배포버전':'1.0.0','설치도구':{'파일':'설치도구.jar','SHA256':sha(installer)},'플러그인':rows,'설명':'한글 이름의 배포 파일은 이전 검증 JAR와 동일. 설치할 때 원래 관리 이름으로 준비합니다.'}
(OUT/'설치목록.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(OUT/'설치하기.ps1').write_bytes((HERE/'설치하기.ps1').read_text().replace('\n','\r\n').encode('utf-8-sig'));(OUT/'설치하기.cmd').write_bytes((HERE/'설치하기.cmd').read_text().replace('\n','\r\n').encode('utf-8'))
packmaps=[('CityEconomy-Interface-26.3-1.2.0.zip','도시생활-메뉴팩-1.2.0.zip'),('CityEconomy-Interface-Complete-26.3-1.2.0.zip','도시생활-전체팩-1.2.0.zip')]
for old,new in packmaps:
 copy('releases/ui-update/'+old,OUT.relative_to(ROOT)/'리소스팩'/new)
 copy('releases/ui-update/'+old,Path('설치파일/02_리소스팩')/new)
for old,new in [('메뉴-미리보기.png','메뉴-미리보기.png'),('메뉴-미리보기.html','메뉴-미리보기.html'),('아이템별보너스.md','아이템별보너스.md'),('거래·검증.md','거래·복구안내.md')]:copy((SOURCE/old).relative_to(ROOT),OUT.relative_to(ROOT)/new)
for old,new in [('validation.json','자동검증-원본.json'),('installer-validation.json','설치검증-원본.json'),('packaged-sqlite.json','저장소검증-원본.json'),('preview-validation.json','화면검증-원본.json')]:copy((SOURCE/old).relative_to(ROOT),OUT.relative_to(ROOT)/'검증자료'/new)
for old,new in [('tycoon/LICENSE.txt','생활타이쿤-이용허가.txt'),('interface-resourcepack/LICENSE.txt','메뉴팩-이용허가.txt'),('farming-resourcepack/LICENSE.txt','농장팩-이용허가.txt'),('korean-codex/LICENSE.txt','아이템도감-이용허가.txt')]:
 mapping=json.loads((ROOT/'저장소구조.json').read_text())['이전경로대응'];first,_,tail=old.partition('/');copy(mapping[first]+'/'+tail,OUT.relative_to(ROOT)/'이용허가'/new)
# Other frequently requested files get readable Korean aliases, identical to their originals.
for old,new in [('economy_spawn_v3_handbuilt.schem','경제마을-자연형-3판.schem'),('economy_spawn_v3_handbuilt_preview.png','경제마을-평면도-3판.png'),('economy_spawn_v3_handbuilt_isometric.png','경제마을-전체모습-3판.png'),('설치안내_v3_handbuilt.txt','경제마을-설치안내-3판.txt')]:copy('releases/economy-spawn/'+old,Path('설치파일/03_지도')/new)
# The renamed schematic needs its actual name in the user instructions.
mapguide=ROOT/'설치파일/03_지도/경제마을-설치안내-3판.txt';text=mapguide.read_text();text=text.replace('economy_spawn_v3_handbuilt.schem','경제마을-자연형-3판.schem').replace('economy_spawn_v3_handbuilt','경제마을-자연형-3판');mapguide.write_text(text)
report_path=OUT/'정리검증.json'
if not report_path.exists():report_path.write_text(json.dumps({'파일정리':'게임 JAR·리소스팩 내용 동일','Windows운영적용':False},ensure_ascii=False,indent=2)+'\n')
entries={p.relative_to(OUT).as_posix():p.read_bytes() for p in OUT.rglob('*') if p.is_file() and p.suffix!='.zip' and p.name not in {'파일검증.sha256','README.md'}}
# ZIP resource packs are nested payloads; exclude only the outer distribution archive.
for p in (OUT/'리소스팩').glob('*.zip'):entries[p.relative_to(OUT).as_posix()]=p.read_bytes()
entries['파일검증.sha256']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(entries.items())).encode('utf-8')
archive=OUT/'도시생활-마을상점-한글설치-1.0.0.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for name,data in sorted(entries.items()):
  i=zipfile.ZipInfo(name,(2026,11,12,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o644<<16;z.writestr(i,data)
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 for line in z.read('파일검증.sha256').decode().splitlines():
  digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
(OUT/'파일검증.sha256').write_text(entries['파일검증.sha256'].decode()+sha(archive)+'  '+archive.name+'\n',encoding='utf-8')
print('PASS six original plugin hashes; Korean names; complete offline bundle:',archive.relative_to(ROOT))
