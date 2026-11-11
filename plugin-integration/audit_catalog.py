"""Keep all original 46 IDs, record the reviewed selection without treating the catalog as an install list."""
from pathlib import Path
import json,csv
ROOT=Path(__file__).resolve().parents[1]
file=ROOT/'plugin-catalog/플러그인목록.json';data=json.loads(file.read_text(encoding='utf-8'))
assert len(data['플러그인'])==46
reasons={
'spark':'Paper 자체 제공 확인. 별도 JAR를 중복 설치하지 않음.',
'betterrtp':'보류. 무료 3.6.13 배포 안내의 테스트 버전은 1.20까지. 26.3 검증 배포판 미확보. 공식 파일 다운로드·실행 준비는 자동 승인 검토 차단(세부 사유 없음). 공개 소스는 오래된 Spigot·보호 API 의존 및 저장소 DNS 문제. EssentialsX 이동도 비용 청구 순서 검증이 남아 야생 버튼은 준비 상태만 안내.',
'multiverse':'기존 월드·차원 구조를 유지. 별도 월드 추가 요청과 검증 전까지 보류.',
'plan':'10인 규모에서 별도 분석 DB와 웹 서비스를 추가하지 않음. 필요 시 내장 spark로 진단.',
'discordsrv':'외부 봇·토큰·메시지 연동을 별도로 요청하지 않았으므로 설치하지 않음.',
'grim':'26.3 정확한 배포판·패킷 의존성·농장/NPC 오탐 검증 전까지 보류.',
'inventory_restore':'판매·도감 소비 후 과거 가방만 되돌리면 복제 위험. 도입 보류. 복원 시 경제 영수증·도감 거래·농사 거래와 함께 대조.',
'packetevents':'선정한 5종에는 필수 아님. 불필요한 패킷 계층을 추가하지 않음.',
'protocollib':'선정한 5종에는 필수 아님. PacketEvents와 일괄 설치하지 않음.',
'citizens':'기존 자체 NPC를 유지. 중복 NPC·추가 비용·배포 조건을 만들지 않음.',
'betonquest':'기존 UUID 기반 임무·납품·중복 수령 방지를 유지. 중복 임무 엔진 설치 안 함.',
'beautyquests':'기존 임무 화면·NPC 기능과 중복. BetonQuest와 함께 설치하지 않음.',
'itemsadder':'기존 통합 리소스팩·정품 PDC·5종 작물 구현 유지. 유료 파일 재배포 안 함.',
'nexo':'기존 통합 리소스팩·작물 모델 유지. ItemsAdder/Oraxen과 중복 설치 안 함.',
'oraxen':'기존 통합 리소스팩·작물 모델 유지. 유료 파일 재배포 안 함.',
'modelengine':'현재 요구 기능에 필요 없음. 별도 모델 엔진·구매 파일 추가 안 함.',
'mythicmobs':'현재 요구 기능에 필요 없음. 몬스터·전투 시스템 확장 보류.',
'auctionhouse':'기존 QuickShop·NPC 거래를 유지. 별도 경매의 정산·중복 지급 검증 전까지 보류.',
'excellentcrates':'요청하지 않은 상자 보상·확률형 콘텐츠는 추가하지 않음.',
'shopkeepers':'기존 사람형/주민형 NPC 상점과 중복. 별도 주민 거래 엔진 추가 안 함.',
'viaversion':'복제 서버의 26.1 자동 클라이언트 연결에만 5.12.2 사용. 운영 서버·설치 ZIP에는 미포함.',
'viabackwards':'복제 서버의 자동 검사에만 5.12.2 사용. ViaVersion과 함께 테스트 전용, 운영 미설치.',
'geyser':'Bedrock 접속 요청이 없어 보류. 현재 Java 인증·UUID 유지.',
'floodgate':'Bedrock 인증 확장 요청이 없어 보류. Geyser 없이 무조건 설치하지 않음.',
'coreprotect':'기존 Prism 4.5와 기록을 유지. 중복 기록 엔진·자동 기록 이전을 도입하지 않음.'
}
selected={'luckperms':'5.5.91','chunky':'1.5.66','placeholderapi':'2.12.3','tab':'6.2.1','holograms':'2.10.2'}
existing={'korean_codex':'1.2.1','korean_farming':'1.0.2','city_textures':'1.1.1'}
for i,row in enumerate(data['플러그인']):
    key=row['식별자']
    if i<16:
        row['선정상태']='기존 운영 구성 유지'
        row['선택시참고']='실제 Windows 서버 설치 파일 확인. 기존 데이터·설정·JAR 유지.'
        row['검증범위']='실제 Windows 설치 식별자·버전·SHA-256·시작 로그 확인. 기존 16개 JAR 유지. 새 조합의 일반 이용자 연결은 실제 Paper 복제 서버와 일부 실제 게임 확인. 10인 전체 플레이 미검증.'
        if key=='griefprevention':row['한글화상태']='기존 한글 45문구 유지 + 영문 187문구 추가 번역. 메시지 키·치환값·보호/권한/데이터 유지. 내부 정규식·작성자 값 유지.'
        if key in existing:row['버전']=existing[key]
        if key in ['korean_codex','korean_farming','city_textures','korean_economy','korean_menu']:
            row['저장소JAR']='releases/ui-update/1.2.1/'+row['공식이름']+'-'+row['버전']+'.jar'
    elif key in selected:
        row['선정상태']='선정 · 실제 Paper 복제 서버 검증'
        row['버전']=selected[key];row['선택시참고']='공식 다운로드 파일명·SHA-256은 releases/plugin-integration/1.0.0/versions.json 참조. 외부 JAR는 ZIP에서 재배포하지 않고 고정 다운로드.'
        row['검증범위']='실제 Paper 26.3 build238 활성화·일반 이용자 표시/권한·재시작 검사. 자동 클라이언트와 실제 게임 확인은 검증결과.md 구분.'
        row['한글화상태']= {'luckperms':'공식 ko_KR 번들·주민/단골/운영자 등급·한국어 권한관리 연결. 고급 명령은 클라이언트 언어 설정 영향.','chunky':'공식 ko.json·한국어 별칭. 경계 설정 후 제한 영역 생성.','placeholderapi':'자체 koreanlife 확장으로 기존 Vault·직업·도감 실데이터 연결. 원래 관리자 도구는 일부 영문.','tab':'실제 값으로 한국어 탭 머리말·꼬리말·사이드바·개인 전환. 자주 쓰는 오류 번역, 고급 관리자 문구 일부 영문.','holograms':'기존 NPC와 별개 고정 소유 이름의 마을 안내판 1개. 이용자 오류와 안내 한글, 고급 관리자 문구 일부 영문.'}[key]
    else:
        row['선정상태']='내장 제공' if key=='spark' else '추가 설치 안 함 · 보류/중복 제외'
        row['선택시참고']=reasons[key]
        row['검증범위']='이번 운영 설치 대상이 아님. 공식 링크 응답을 26.3 실행 검증으로 간주하지 않음.'
data['문서버전']='1.1.0'
data['최신통합설치']='releases/ui-update/1.2.1/도시생활-Windows-메뉴통합-1.2.1.zip'
data['추가기능설치']='releases/plugin-integration/1.0.0/생활서버-플러그인연결-1.0.0.zip'
data['설명']='46종의 선택 검토. 기존 16종 유지, 공식 추가 5종 선정, 별도 자체 연결 모듈 1종. 무조건 전체 설치하지 않음. 실제 적용·검증 범위는 plugin-integration/검증결과.md 참조.'
data['연결모듈']={'공식이름':'KoreanServerBridge','버전':'1.0.0','기능':'기존 저장 데이터를 읽는 koreanlife 표시 확장·한국어 연결·고정 안내판. 경제·NPC·도감 구현을 복제하지 않음.'}
file.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
columns=list(dict.fromkeys(k for row in data['플러그인'] for k in row))
with (file.parent/'플러그인목록.csv').open('w',encoding='utf-8-sig',newline='') as out:
    writer=csv.DictWriter(out,fieldnames=columns);writer.writeheader()
    for row in data['플러그인']:writer.writerow({k:' · '.join(v) if isinstance(v,list) else v for k,v in row.items()})
lines=['# 46종 선택 검토','', '기존 46개 ID를 유지합니다. 연결 모듈은 기존 기능을 읽고 연결하는 별도 보조 JAR입니다.','', '| 번호 | 플러그인 | 선택 | 이유 |','| --- | --- | --- | --- |']
for i,row in enumerate(data['플러그인'],1):
    lines.append(f"| {i} | {row['공식이름']} | {row['선정상태']} | {row['선택시참고'].replace('|','·')} |")
(ROOT/'plugin-integration/46종검토.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('PASS exact 46 IDs retained, existing16 + official5 + separate bridge')
