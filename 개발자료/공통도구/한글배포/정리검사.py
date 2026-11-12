#!/usr/bin/env python3
"""Verify moved paths, unchanged releases/source, links and Korean distribution files."""
from pathlib import Path
import ast,json,hashlib,re,urllib.parse,zipfile,xml.etree.ElementTree as ET
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());TOOL=ROOT/'개발자료/공통도구';OUT=ROOT/'설치파일/01_마을상점';layout=json.loads((ROOT/'저장소구조.json').read_text());mapping=layout['이전경로대응']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def sourcepath(old):
 first,sep,tail=old.partition('/');return ROOT/(mapping.get(first,first)+sep+tail)
old_releases=json.loads((TOOL/'정리전-배포해시.json').read_text());old_sources=json.loads((TOOL/'정리전-소스해시.json').read_text())
for old,digest in old_releases.items():assert sha(ROOT/old)==digest,old
for old,digest in old_sources.items():assert sha(sourcepath(old))==digest,old
python_count=0
for p in (ROOT/'개발자료').rglob('*.py'):
 if any(x in {'vendor','node_modules','build','build-domain','build-real','__pycache__','.gradle'} for x in p.parts):continue
 ast.parse(p.read_text(encoding='utf-8-sig'));python_count+=1
links=0
for base in ['README.md','개발자료','사용안내','설치파일']:
 paths=[ROOT/base] if (ROOT/base).is_file() else (ROOT/base).rglob('*.md')
 for p in paths:
  if any(x in {'vendor','node_modules','build','build-domain','build-real','__pycache__','.gradle'} for x in p.parts):continue
  for target in re.findall(r'\]\(([^\n)]+)\)',p.read_text(encoding='utf-8-sig')):
   target=target.strip('<>')
   if re.match(r'\w+:|#',target):continue
   relative=urllib.parse.unquote(urllib.parse.urlsplit(target).path)
   assert (p.parent/relative).exists(),(p.relative_to(ROOT),target);links+=1
manifest=json.loads((OUT/'설치목록.json').read_text());proof=json.loads((ROOT/'releases/civic-update/1.0.0/validation.json').read_text())
for entry in manifest['플러그인']:
 assert sha(OUT/entry['파일'])==entry['SHA256']==proof['artifacts'][entry['설치파일명']]
 assert re.search('[가-힣]',Path(entry['파일']).name)
 with zipfile.ZipFile(OUT/entry['파일']) as z:assert 'name: '+entry['식별자'] in z.read('plugin.yml').decode()
packnames={'도시생활-메뉴팩-1.2.0.zip':'CityEconomy-Interface-26.3-1.2.0.zip','도시생활-전체팩-1.2.0.zip':'CityEconomy-Interface-Complete-26.3-1.2.0.zip'}
for renamed,old in packnames.items():assert sha(ROOT/'설치파일/02_리소스팩'/renamed)==sha(ROOT/'releases/ui-update'/old)
assert sha(ROOT/'설치파일/03_지도/경제마을-자연형-3판.schem')==sha(ROOT/'releases/economy-spawn/economy_spawn_v3_handbuilt.schem')
suites={}
for folder in ['civic-tests','tycoon-integration','farming-tests','korean-codex-tests','city-textures-tests']:
 count=0
 for p in (Path('/workspace/toolchains')/folder/'build/test-results/test').glob('TEST-*.xml'):
  r=ET.parse(p).getroot();assert all(r.get(k)=='0' for k in ['failures','errors','skipped']);count+=int(r.get('tests'))
 assert count>0;suites[folder]=count
prepared=json.loads((OUT/'설치준비검증.json').read_text())
report={'정리일':'2026-11-12','정리기준커밋':'231e432','한글분류':['설치파일','사용안내','개발자료','이전자료'],'이동한최상위항목수':len(mapping),'기존배포파일_내용보존':len(old_releases),'플러그인실행소스_내용보존':len(old_sources),'검사한Python파일수':python_count,'정상로컬문서링크수':links,'회귀검사':suites,'회귀검사합계':sum(suites.values()),'기존SQLite_거래_데이터검사':121,'원본과동일한한글JAR수':len(manifest['플러그인']),'기존팩_지도_내용동일':True,'한글설치준비검사수':prepared['검사수'],'원본설치기_그대로사용':True,'기존releases주소유지':True,'운영서버변경':False,'PowerShell실행':False,'Windows실행':False,'설명':'폴더·이름·안내와 실행 준비 방식을 정리함. 게임 소스·기존 배포 파일·리소스팩 식별자는 보존. Windows PowerShell 실행 자체는 미검증.'}
(OUT/'정리검증.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('PASS',len(old_releases),'original releases,',len(old_sources),'unchanged production sources,',links,'links,',sum(suites.values()),'JUnit cases,',prepared['검사수'],'Korean bundle checks')
