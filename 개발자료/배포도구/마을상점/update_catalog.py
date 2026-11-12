#!/usr/bin/env python3
"""Update existing 46 review IDs and record the new module separately, preserving the bridge."""
from pathlib import Path
from urllib.parse import quote
import json,csv,hashlib,zipfile,re
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());folder=ROOT/'사용안내/플러그인목록';file=folder/'플러그인목록.json';data=json.loads(file.read_text());report=json.loads((ROOT/'releases/civic-update/1.0.0/validation.json').read_text());assert len(data['플러그인'])==46
scope='이번 마을·상점 조합: 실제 Paper API 컴파일·MockBukkit·SQLite·설치기 검증. 이번 Windows 적용·실게임·10인 부하는 미검증. 개발자료/배포도구/마을상점/거래·검증.md 참조.'
base='releases/civic-update/1.0.0/'
for row in data['플러그인']:
 name=row['공식이름']
 if name not in report['versions']:continue
 version=report['versions'][name];path=base+name+'-'+version+'.jar';content=(ROOT/path).read_bytes();digest=hashlib.sha256(content).hexdigest();assert report['artifacts'][Path(path).name]==digest
 with zipfile.ZipFile(ROOT/path) as z:
  y=z.read('plugin.yml').decode('utf-8');assert 'name: '+name in y and 'version: '+version in y
 row.update({'버전':version,'저장소JAR':path,'파일SHA256':digest,'파일크기':len(content),'배포파일링크':'https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/'+quote(path,safe='/'),'검증범위':scope,'선택시참고':'마을·개인상점 설치기로 전체 백업 후 함께 업데이트. 운영 적용 전 기존 서버 복제본에서 확인.','선정상태':'최신 업데이트 준비 · 이번 운영 미적용'})
 if name=='KoreanMenu':row['기능']='쉬프트+F 마을·땅 거래·개인상점·도감·농장 입구';row['연결대상']=list(dict.fromkeys(row['연결대상']+['KoreanCivic']))
data['문서버전']='1.2.0';data['최신통합설치']=base+'도시생활-마을상점-1.0.0.zip';data['설명']='기존 46종 선택 검토와 별도 연결 모듈은 유지. 신규 KoreanCivic와 경제·메뉴·도시팩 업데이트는 마을상점모듈에 구분. 이번 새 버전은 클라우드 검증·설치 파일 준비 상태, Windows 운영 미적용.'
data['마을상점모듈']={'공식이름':'KoreanCivic','한글이름':'마을·필지 매매 / 개인상점','버전':'1.0.0','저장소JAR':base+'KoreanCivic-1.0.0.jar','파일SHA256':report['artifacts']['KoreanCivic-1.0.0.jar'],'필수의존성':['KoreanEconomy 2.0.3','Vault'],'메뉴연결':'KoreanMenu 2.2.0 / CityTextures 1.2.0','기능':'마을 설립·인접 확장·분양·땅 재판매·물품 보관형 개인상점·판매대금','검증범위':scope,'설치안내':'개발자료/배포도구/마을상점/README.md','설치상태':'배포 준비 · 이번 Windows 운영 미적용'}
file.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
columns=list(dict.fromkeys(k for row in data['플러그인'] for k in row))
with (folder/'플러그인목록.csv').open('w',encoding='utf-8-sig',newline='') as out:
 writer=csv.DictWriter(out,fieldnames=columns,lineterminator="\n");writer.writeheader()
 for row in data['플러그인']:writer.writerow({k:' · '.join(v) if isinstance(v,list) else v for k,v in row.items()})
text='# 마을·개인상점 추가 구성\n\n[설치 ZIP](../'+data['최신통합설치']+') · [설치·이용·설정](../civic-update/README.md) · [거래·검증](../civic-update/거래·검증.md)\n\n기존 46종 후보 검토·KoreanServerBridge는 그대로 두고 KoreanCivic를 추가합니다. 이번 운영 서버 적용은 아직 수행하지 않았습니다.\n\n| 플러그인 | 버전 | 검증한 JAR |\n| --- | --- | --- |\n'
for name,version in report['versions'].items():text+='| '+name+' | '+version+' | [다운로드](../'+base+name+'-'+version+'.jar) |\n'
text+='\n마을·필지·개인상점의 자체 화면·이용자 명령은 한글입니다. 신규 외부 경매장·NPC 엔진·별도 화폐는 설치하지 않습니다. 기존 상자 상점·NPC 상점·개인 보호·기록 플러그인은 유지합니다. 통합팩 파일은 기존 배포와 동일합니다.\n'
(folder/'마을·개인상점.md').write_text(text)
print('PASS exact 46 review IDs and bridge retained; six current artifacts verified; civic module recorded separately')
