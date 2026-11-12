#!/usr/bin/env python3
"""Build one Korean inventory from metadata and verify the existing distribution files.
No plugin download, server edit, configuration reset or republishing of new third-party binaries.
"""
from pathlib import Path
import csv,io,json,re,hashlib,zipfile,collections
from urllib.parse import quote,urlparse
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());HERE=ROOT/'사용안내/플러그인목록';OUT=ROOT/'releases/plugin-catalog';OUT.mkdir(parents=True,exist_ok=True)
REPO='https://github.com/wjdtjddyd956-svg/akdlszmfovcm';RAW='https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/'
data=json.loads((HERE/'플러그인목록.json').read_text());rows=data['플러그인'];assert len(rows)==46;assert len({r['식별자'] for r in rows})==len(rows)
if data.get("마을상점모듈"):raise SystemExit("Historical catalog builder: use 개발자료/배포도구/마을상점/update_catalog.py for the current civic release.")
def sha(b):return hashlib.sha256(b).hexdigest()
def link(path,raw=False):return (RAW if raw else REPO+'/blob/main/')+quote(path,safe='/')
def md(s):return str(s).replace('|',' / ').replace('\n',' ')
checks_path=HERE/'자료링크검증.json';checks=json.loads(checks_path.read_text()) if checks_path.exists() else {'검사':[]};checked={r['주소']:r for r in checks['검사']}
artifact_reports={}
for r in rows:
 assert urlparse(r['공식자료']).scheme=='https'
 r['링크확인']=checked.get(r['공식자료'],{}).get('결과','접속 미확인')
 path=r['저장소JAR']
 if not path:continue
 p=ROOT/path;assert p.is_file(),path
 with zipfile.ZipFile(p) as z:
  assert z.testzip() is None
  text=z.read('plugin.yml').decode('utf-8')
  name=re.search(r'(?m)^name:\s*([^\r\n]+)',text).group(1).strip().strip('"\'')
  version=re.search(r'(?m)^version:\s*([^\r\n]+)',text).group(1).strip().strip('"\'')
  assert name==r['공식이름'],(name,r['공식이름'])
  match=re.search(r'(?m)^depend:[ \t]*(.*)$',text)
  deps=[]
  if match:
   raw=match.group(1).strip()
   if raw.startswith('[') and raw.endswith(']'):deps=[x.strip().strip('"\'') for x in raw[1:-1].split(',') if x.strip()]
   elif not raw:
    tail=text[match.end():];section=re.split(r'\n[^ \t\r\n]',tail,maxsplit=1)[0];deps=re.findall(r'(?m)^[ \t]*-[ \t]*["\']?([A-Za-z0-9_.-]+)',section)
   else:deps=[raw.strip('"\'')]
  r['필수의존성']=deps;r['의존성출처']='위 배포 JAR의 plugin.yml depend 선언. 연결대상은 별도 선택 기능을 포함'
  r['버전']=version;r['파일SHA256']=sha(p.read_bytes());r['파일크기']=p.stat().st_size
  r['배포파일링크']=link(path,True)
  artifact_reports[path]={'플러그인':name,'버전':version,'SHA256':r['파일SHA256'],'바이트':p.stat().st_size}
assert not any('EssentialsX-2.22.0.jar' in str(r.get('저장소JAR')) for r in rows)
# Record the historic external versions and hashes without treating them as newly fetched JARs.
external=[]
for r in json.loads((ROOT/'개발자료/배포도구/생활서버/dependencies.json').read_text()):external.append({'파일':r['file'],'당시버전':r['version'],'당시다운로드주소':r['url'],'해시방식':r['algorithm'],'당시해시':r['hash'],'상태':'기존 의존성 기록. 이번 다운로드·설치·현재 접속 가능 여부 미확인'})
(HERE/'기존외부배포기록.json').write_text(json.dumps(external,ensure_ascii=False,indent=2)+'\n')
(HERE/'플러그인목록.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
fields=['한글이름','공식이름','분류','우선순위','기능','버전','저장소JAR','배포파일링크','공식자료','링크확인','한글화상태','검증범위','필수의존성','의존성출처','연결작업','선택시참고','파일SHA256']
buffer=io.StringIO(newline='');writer=csv.DictWriter(buffer,fieldnames=fields,extrasaction='ignore',lineterminator='\n');writer.writeheader();writer.writerows(rows);(HERE/'플러그인목록.csv').write_bytes(buffer.getvalue().encode('utf-8-sig'))
summary=collections.Counter(r['분류'] for r in rows)
text='# 한글 플러그인 전체 정리 — 46종\n\nWindows · Paper 26.3 · 약 10명 생활 경제 서버 기준입니다. 이용자 안내·설치 순서·기능 설명은 한국어로 정리하고 공식 이름은 검색과 버전 확인을 위해 함께 표시합니다.\n\n'
text+='**이 목록은 전체 설치 지시가 아닙니다.** 기존 구성 16종 / 추가 후보 14종 / 콘텐츠 확장 11종 / 접속 확장 4종 / 대체 후보 1종입니다. 최신 메뉴 통합 배포는 클라우드 자동 검증 결과이며 이번 작업에서 Windows 서버 설치 상태를 조회하거나 변경하지 않았습니다.\n\n'
text+='- [한글 정리·기존 자체 플러그인 묶음 ZIP]('+link('releases/plugin-catalog/한글플러그인-정리묶음-1.0.0.zip',True)+')\n- [설치 순서와 기능 선택](설치순서.md) · [연결·한글화 현황](연결·한글화.md) · [의존성·검증 범위](의존성·검증.md)\n- [엑셀용 전체 목록](플러그인목록.csv) · [구조화된 전체 목록](플러그인목록.json) · [자료 링크 확인 기록](자료링크검증.json)\n- [최신 메뉴·도감·농장 통합 설치 파일]('+link(data['최신통합설치'],True)+') · [사용법]('+link('개발자료/배포도구/메뉴통합/README.md')+')\n\n'
text+='## 정리 묶음에 실제로 들어 있는 것\n\n기존에 제작·배포한 메뉴 통합 설치 자료를 그대로 포함합니다. `설치자료/도시생활UI/Install-Interface.cmd`는 기존 생활서버 2.0.1/2.0.2를 전체 백업 후 5개 JAR로 업데이트하는 설치기입니다. 추가 후보를 일괄 설치하지 않습니다.\n\n| 실제 포함 파일 | 버전 | 역할 |\n| --- | --- | --- |\n'
for r in rows[:7]:text+='| '+md(r['공식이름'])+' | '+md(r['버전'])+' | '+md(r['기능'])+' |\n'
text+='\n앞의 5개는 `설치자료/도시생활UI/plugins/`, Commands·Setup은 `설치자료/기존한글보조/`에 있습니다. 통합 리소스팩·설치기·안내·검증 결과도 포함합니다. 외부 플러그인과 추가 후보의 신규 JAR는 이 묶음에 넣지 않았으며 아래 공식 자료에서 확인합니다. 저장소에 이미 있는 외부 JAR는 개별 링크로 구분합니다.\n\n'
for group in ['기존 구성','추가 후보','콘텐츠 확장','접속 확장','대체 후보']:
 text+='## '+group+' — '+str(summary[group])+'종\n\n| 한글 이름 · 공식 이름 | 기능 | 우선순위 | 버전·파일 | 공식 자료 |\n| --- | --- | --- | --- | --- |\n'
 for r in rows:
  if r['분류']!=group:continue
  file='['+md(r['버전'])+' JAR]('+r['배포파일링크']+')' if r.get('배포파일링크') else md(r['버전'])+' / 신규 JAR 미포함'
  text+='| '+md(r['한글이름'])+' · **'+md(r['공식이름'])+'** | '+md(r['기능'])+' | '+md(r['우선순위'])+' | '+file+' | [공식 자료]('+r['공식자료']+') |\n'
 text+='\n'
text+='## 한글화와 호환성의 기준\n\n자체 플러그인의 한국어 메뉴·명령과 기존 설정 자료를 보존합니다. **추가 후보는 한글 설명만 작성했으며 내부 문구·설정·새 한글 명령 별칭·자체 도감/농장 표시값 연결을 완료한 상태가 아닙니다.** [연결·한글화 현황](연결·한글화.md) · [의존성·검증 범위](의존성·검증.md)에 항목별로 구분했습니다. 아직 없는 한글 명령을 사용할 수 있다고 안내하지 않습니다.\n\n공식 자료의 HTTP 정상 응답은 자료 주소 확인입니다. Paper 26.3 지원·실제 플레이·완전 한글화 검증을 의미하지 않습니다. 추가 후보의 버전은 미선정이며 설치 전에 공식 지원 범위와 의존성을 확인해야 합니다. 현재 메뉴 통합 배포의 자동 테스트 132개와 설치 검사 37개는 해당 배포의 범위이고 신규 후보를 검증한 숫자가 아닙니다.\n\n'
text+='## 먼저 추가할 순서\n\nLuckPerms → 월드 경계를 정하고 Chunky → PlaceholderAPI 연결 범위 결정 → TAB·DecentHolograms 한국어 표시 → BetterRTP의 야생 입장 연결 순서입니다. `/spark`가 이미 제공되면 추가 JAR 없이 사용합니다.\n\nEssentialsX 2.22.0은 오류 이력으로 최신 설치 목록에서 제외합니다. 기존 호환 본체·Spawn 쌍과 Vault·보호·기록·상점 데이터는 유지합니다. 인벤토리 복구·새 경매장·핵 탐지·베드락 접속은 기존 소비/판매/능력치/계정과의 연결 검증이 필요합니다.\n'
(HERE/'README.md').write_text(text)
language='# 연결·한글화 현황\n\n모든 설명은 한국어입니다. 공식 플러그인 이름·식별자·설정 키·파일명은 원형을 유지합니다. 문서가 한국어인 것과 외부 플러그인 내부 문구가 모두 번역된 것은 구분합니다.\n\n| 한글 이름 · 공식 이름 | 한글화 상태 | 연결 작업 | 선택 시 참고 |\n| --- | --- | --- | --- |\n'
for r in rows:language+='| '+md(r['한글이름'])+' · '+r['공식이름']+' | '+md(r['한글화상태'])+' | '+md(r['연결작업'])+' | '+md(r['선택시참고'])+' |\n'
language+='\n자체 메뉴·도감·농장 일반 흐름은 쉬프트+F와 NPC에서 시작할 수 있습니다. 기존 명령은 [한글 명령 안내]('+link('개발자료/배포도구/생활서버/명령어.md')+')와 [최신 UI 사용법]('+link('개발자료/배포도구/메뉴통합/README.md')+')을 참고합니다. 추가 후보용 한국어 별칭은 아직 등록하지 않았습니다. 향후 연결할 때에도 플레이어 권한·비용·재사용 대기시간을 그대로 적용하고 운영자 권한을 우회하지 않습니다.\n'
(HERE/'연결·한글화.md').write_text(language)
dependency='# 의존성·검증 범위\n\n확인한 배포 JAR의 필수 의존 선언과 선택적인 연결 대상을 구분합니다. 추가 후보는 배포판·버전을 아직 선택하지 않았으므로 필수 의존성이 없다고 가정하지 않습니다. 실제 시작 순서는 서버가 플러그인 선언에 따라 처리합니다.\n\n| 플러그인 | 확인한 파일의 필수 의존 | 연결 대상 · 선택 기능 포함 | 검증 범위 |\n| --- | --- | --- | --- |\n'
for r in rows:
 deps=', '.join(r['필수의존성']) or '선언 없음' if '필수의존성' in r else '배포 버전 선택 후 공식 문서 확인'
 dependency+='| '+r['공식이름']+' | '+md(deps)+' | '+md(', '.join(r['연결대상']) or '별도 연결 대상 미지정')+' | '+md(r['검증범위'])+' |\n'
(HERE/'의존성·검증.md').write_text(dependency)
validation={'기준일':data['기준일'],'전체플러그인수':len(rows),'분류별수':dict(summary),'저장소에서확인한JAR수':len(artifact_reports),'묶음에포함한자체JAR수':7,'확인한기존배포파일':artifact_reports,'추가후보JAR다운로드':False,'추가후보Paper26_3실행검증':False,'추가후보내부전체한글화':False,'Windows설치상태조회':False,'Windows신규설치':False,'공식링크검사설명':'정상 응답은 자료 주소 확인이며 설치·호환·완전 한글화 보증이 아님'}
# Assemble the existing UI payload without changing its installers, scripts, plugin bytes or resource packs.
entries={}
for p in HERE.iterdir():
 if p.is_file():entries['플러그인정리/'+p.name]=p.read_bytes()
source=ROOT/data['최신통합설치']
with zipfile.ZipFile(source) as original:
 assert original.testzip() is None
 for n in original.namelist():
  assert not n.startswith('/') and '..' not in Path(n).parts
  entries['설치자료/도시생활UI/'+n]=original.read(n)
for r in rows[5:7]:entries['설치자료/기존한글보조/'+Path(r['저장소JAR']).name]=(ROOT/r['저장소JAR']).read_bytes()
validation['그대로포함한UI설치ZIP_SHA256']=sha(source.read_bytes())
(OUT/'정리검증.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2)+'\n');entries['플러그인정리/정리검증.json']=(OUT/'정리검증.json').read_bytes()
entries['먼저읽기.txt']=('한글 플러그인 전체 정리 46종\n\n플러그인정리/README.md와 설치순서.md를 먼저 읽으세요.\n실제 설치 자료: 설치자료/도시생활UI/Install-Interface.cmd\n이 설치기는 기존 생활서버 2.0.1/2.0.2용이며 전체 서버 백업 후 자체 플러그인 5개를 교체합니다.\n설치자료/기존한글보조/의 2개 JAR는 기존 배포본 참고용입니다.\n추가 후보의 신규 JAR·구매 파일·번역 설정은 포함하지 않았습니다. 공식 자료와 필요한 연결 작업을 한글로 정리했습니다.\nWindows 서버는 이번 정리 작업에서 변경하지 않았습니다.\n').encode('utf-8-sig')
entries['SHA256SUMS.txt']=''.join(sha(b)+'  '+n+'\n' for n,b in sorted(entries.items())).encode()
archive=OUT/'한글플러그인-정리묶음-1.0.0.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for n,b in sorted(entries.items()):
  info=zipfile.ZipInfo(n,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,b)
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 jars=[n for n in z.namelist() if n.endswith('.jar') and ('/plugins/' in n or '/기존한글보조/' in n)]
 assert len(jars)==7,jars
 for n in jars:
  with zipfile.ZipFile(io.BytesIO(z.read(n))) as jar:assert 'plugin.yml' in jar.namelist()
 for line in z.read('SHA256SUMS.txt').decode().splitlines():
  h,n=line.split('  ',1);assert sha(z.read(n))==h,n
(OUT/'SHA256SUMS.txt').write_text(sha(archive.read_bytes())+'  '+archive.name+'\n')
(OUT/'README.md').write_text('# 한글 플러그인 정리 묶음 1.0.0\n\n[46종 전체 목록]('+REPO+'/tree/main/plugin-catalog) · [정리 묶음 다운로드]('+link('releases/plugin-catalog/'+archive.name,True)+')\n\n기존 자체 플러그인 7개·메뉴 통합팩·기존 설치기와 한국어 정리 자료를 포함합니다. 외부 플러그인은 공식 자료·기존 배포 파일 링크·버전 및 검증 상태로 구분합니다. 추가 후보의 신규 JAR·완전 한글화·Windows 설치를 완료했다는 의미는 아닙니다.\n\n설치자료/도시생활UI/Install-Interface.cmd는 기존 생활서버 2.0.1/2.0.2의 자체 플러그인 5개 교체용이며 전체 백업 후 진행합니다. 자세한 내용은 묶음의 먼저읽기.txt와 설치순서.md를 읽으세요.\n')
print('PASS: 46 records; 11 local plugin JARs checked; 7 own JARs bundled; original UI payload preserved; all ZIP hashes valid');print('Archive bytes:',archive.stat().st_size)
