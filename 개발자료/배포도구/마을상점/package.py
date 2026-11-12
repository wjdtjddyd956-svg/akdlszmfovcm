#!/usr/bin/env python3
"""Package only already validated artifacts; never compile or overwrite historical files."""
from pathlib import Path
import json,zipfile,hashlib,shutil,os,re,urllib.parse
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());OUT=Path(os.environ.get('CIVIC_OUTPUT',str(ROOT/'.build/마을상점-빌드')));prior=ROOT/'releases/ui-update/1.2.1'
def sourcepath(name):
 first,sep,tail=name.partition('/')
 mapping=json.loads((ROOT/'저장소구조.json').read_text())['이전경로대응']
 return ROOT/(mapping.get(first,first)+sep+tail)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def zipped(path,entries):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   i=zipfile.ZipInfo(name,(2026,11,11,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o644<<16;z.writestr(i,data)
report=json.loads((OUT/'validation.json').read_text());artifacts=[]
for name,digest in report['artifacts'].items():
 f=OUT/name;assert sha(f)==digest,name;artifacts.append(f)
for name,digest in report['source_sha256'].items():assert sha(sourcepath(name))==digest,name
assert json.loads((OUT/'installer-validation.json').read_text())['actual_release_jars_used']
guide=ROOT/'개발자료/배포도구/마을상점/README.md'
readme=guide.read_text()
def local_link(match):
 target=match.group(2)
 if re.match(r'\w+:|#',target):return match.group(0)
 resolved=(guide.parent/urllib.parse.unquote(target)).resolve()
 if resolved.name in {'거래·검증.md','메뉴-미리보기.png','도시생활-마을상점-1.0.0.zip'}:return match.group(1)+resolved.name+match.group(3)
 return match.group(1)+'https://github.com/wjdtjddyd956-svg/akdlszmfovcm/blob/main/'+urllib.parse.quote(resolved.relative_to(ROOT).as_posix(),safe='/')+match.group(3)
readme=re.sub(r'(\]\()([^\n)]+)(\))',local_link,readme)
(OUT/'설치안내.md').write_text(readme);shutil.copyfile(ROOT/'개발자료/배포도구/마을상점/거래·검증.md',OUT/'거래·검증.md');shutil.copyfile(prior/'아이템별보너스.md',OUT/'아이템별보너스.md')
entries={'plugins/'+f.name:f.read_bytes() for f in artifacts};entries['plugins/SHA256.properties']=''.join(f.name+'='+sha(f)+'\n' for f in artifacts).encode('ascii');entries['CivicInstaller.jar']=(OUT/'CivicInstaller.jar').read_bytes();entries['CivicInstaller.sha256']=(sha(OUT/'CivicInstaller.jar')+'\n').encode('ascii')
entries['Install-Civic.ps1']=(ROOT/'개발자료/공통도구/installer/Install-Civic.ps1').read_text().replace('\n','\r\n').encode('utf-8-sig');entries['Install-Civic.cmd']=(ROOT/'개발자료/공통도구/installer/Install-Civic.cmd').read_text().replace('\n','\r\n').encode('ascii')
for f in OUT.iterdir():
 if f.suffix in ['.md','.json','.png','.html']:entries[f.name]=f.read_bytes()
for name in ['CityEconomy-Interface-26.3-1.2.0.zip','CityEconomy-Interface-Complete-26.3-1.2.0.zip']:entries['resourcepacks/'+name]=(ROOT/'releases/ui-update'/name).read_bytes()
for module in ['개발자료/플러그인/마을상점','개발자료/플러그인/생활경제','개발자료/플러그인/마을수첩','개발자료/플러그인/도시화면']:
 for f in (ROOT/module/'src/main').rglob('*'):
  if f.is_file():entries['소스/'+f.relative_to(ROOT).as_posix()]=f.read_bytes()
for name,path in [('LICENSE-Tycoon.txt','사용안내/생활타이쿤/LICENSE.txt'),('LICENSE-Interface.txt','개발자료/리소스팩/메뉴팩/LICENSE.txt'),('LICENSE-Farming.txt','개발자료/리소스팩/농장팩/LICENSE.txt'),('LICENSE-Codex.txt','개발자료/플러그인/아이템도감/LICENSE.txt')]:entries[name]=(ROOT/path).read_bytes()
entries['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(entries.items())).encode('utf-8');archive=OUT/'도시생활-마을상점-1.0.0.zip';zipped(archive,entries)
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None and len([n for n in z.namelist() if n.startswith('plugins/') and n.endswith('.jar')])==6
 for row in z.read('SHA256SUMS.txt').decode().splitlines():
  digest,name=row.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest,name
(OUT/'SHA256SUMS.txt').write_text(''.join(sha(f)+'  '+f.name+'\n' for f in [*artifacts,OUT/'CivicInstaller.jar',archive]))
print('Packaged verified civic update:',archive)
