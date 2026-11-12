# 한글 생활 경제 서버 묶음 1.1

> **Paper 26.3 수정 안내:** 실제 서버에서 EssentialsX 2.22.0의 미지원 메시지가 확인되었습니다. 26.3에서는 본체·스폰을 공식 개발 빌드로 교체해야 합니다. JAR만 복사했을 때 빠졌던 설정은 [수정 플러그인 안내](../../플러그인/경제설정/README.md)를 따르세요. 기본 묶음 1.1 ZIP만으로 이 호환성 문제가 해결되지는 않습니다.

10명 규모 경제 야생 서버용 구성입니다. EarthMC 서버의 공개 경제·상점 가이드에서 유저 생산·상자 거래·재고·가격·직원 관리 방식을 확인하고 소규모 서버에 맞게 직접 만들었습니다. 서버에 직접 로그인해 플레이하지 않았습니다. [참고 자료와 설계 차이](참고서버.md)를 확인할 수 있습니다.

## 구성

| 포함 JAR | 기능 |
| --- | --- |
| KoreanMenu 1.1.0 | 쉬프트+F 한글 메뉴, 상점·보상·집·스폰 연결 |
| KoreanCommands 1.1.0 | 돈·송금·집·이동·거래소·건축·땅 보호 한글 별칭 |
| KoreanEconomy 1.0.0 | 서버 매입/판매 상점, 일일 보상, 한글 경제 안내 |
| EssentialsX 2.22.0 | 잔액·송금·집·이동의 실제 처리 |
| EssentialsXSpawn 2.22.0 | 스폰 저장·이동 |
| Vault 1.7.3 | 경제 기능 간 잔액 공유 |
| QuickShop-Hikari 6.3.0.4 | 유저 상자 상점 |

한글 채팅 기능·영타 변환·클라이언트 입력 모드는 포함하지 않습니다. 기존 채팅 처리도 변경하지 않습니다. 맵·WorldEdit·WorldGuard는 기존 것을 유지하세요.

## 설치 및 기존 버전 교체

1. 서버를 `stop`으로 종료하고 서버 폴더를 백업합니다.
2. 기존 `KoreanMenu-1.0.0.jar`와 `KoreanCommands-1.0.x.jar`를 서버 `plugins`에서 꺼냅니다. 같은 플러그인은 버전이 달라도 JAR를 하나만 남겨야 합니다. 플레이어 데이터·설정 폴더는 지우지 않습니다.
3. ZIP `plugins` 안 JAR 7개를 서버의 `plugins`에 넣습니다. 동일한 EssentialsX·Vault·QuickShop이 이미 있으면 중복 추가하지 않습니다.
4. EssentialsX·QuickShop을 처음 설치한다면 ZIP `신규설정` 안 두 폴더를 서버의 `plugins`에 복사합니다. 기존 설정이 있으면 덮어쓰지 않습니다.
5. 서버를 한 번 시작하고 다시 `stop`으로 종료해 새 플러그인의 기본 설정을 생성합니다.
6. ZIP을 푼 위치에서 PowerShell을 열고 아래 명령으로 한글 설정만 적용합니다. 서버 경로는 자신의 실제 경로로 바꿉니다.

```powershell
powershell -NoProfile -File .\한글설정적용.ps1 -ServerDirectory 'C:\내서버'
```

스크립트는 원래 설정을 `korean-settings-backup-날짜`에 백업하고 언어 항목·번역 파일을 적용합니다. 잔액·집·가격·땅 데이터는 변경하지 않습니다. 실행 정책으로 차단되면 아래 수동 방법을 사용하세요. 관리자 권한이나 시스템 실행 정책 변경은 필요하지 않습니다.

7. 서버를 시작하고 운영자가 `/스폰설정`을 한 번 실행합니다. `/메뉴`, `/돈`, `/상점`, `/일일보상`을 일반 유저 계정으로도 확인합니다.

### 한글 설정 수동 적용

서버가 꺼진 상태에서 ZIP `한글설정` 안 폴더를 서버의 `plugins`에 복사합니다. 기존 같은 이름의 번역 파일은 백업합니다. 기존 `config.yml`에는 아래 항목만 병합합니다.

Essentials:

```yaml
locale: ko
per-player-locale: false
currency-symbol: '원'
currency-symbol-suffix: true
```

QuickShop-Hikari:

```yaml
game-language: ko_kr
enabled-languages:
  - ko_kr
use-crowdin-ota: false
```

메뉴·직접 만든 경제 기능·주요 생활 명령과 도움말은 한글입니다. 공식 한국어 번역에서 부족했던 상점 안내 142개와 Essentials 사용법을 추가로 덮어씁니다. 외부 플러그인의 개발자 진단·관리자 고급 기능·새 버전의 문구까지 모두 번역한 것은 아닙니다. 플러그인 이름·아이템 식별자·권한 키는 호환성을 위해 원래 값을 씁니다.

## 플레이 흐름

1. 신규 유저는 1000원으로 출발합니다. 기존 유저의 잔액은 바뀌지 않습니다. 시작 잔액은 `신규설정`을 사용했을 때 적용됩니다.
2. 밀·당근·감자를 재배하거나 조약돌·원목·석탄·철·다이아몬드를 모읍니다.
3. `/상점`에서 생산물을 팔거나 재료를 삽니다. 좌클릭 1개, 쉬프트 클릭 16개, 우클릭 64개입니다. 화면 아래에서 구매/판매 모드를 선택합니다.
4. `/일일보상`으로 한국 날짜 기준 하루 한 번 200원을 받습니다. 시간대·가격·보상은 `plugins/KoreanEconomy/config.yml`에서 바꾸고 `/경제설정 새로고침`으로 반영합니다.
5. `/거래소 생성 가격`으로 유저 상점을 만듭니다. 상자를 바라보고 `/거래소 가격 금액`, `/거래소 수량 개수`, `/거래소 직원 추가 닉네임`을 사용합니다.

서버 상점의 판매가는 구매가보다 낮습니다. 단순 구매·되팔기로 돈이 늘어나지 않습니다. 특수 이름·마법·설명이 붙은 아이템은 취급하지 않고, 장비칸·보조 손은 거래에서 제외합니다. 창작·관전자 모드에서는 거래를 막습니다.

보상은 재시작 후에도 받은 날짜를 기억합니다. 처리 중 중단된 기록은 중복 지급을 막기 위해 운영자 확인을 요구합니다. 운영자는 `보상기록`을 임의 삭제하지 말고 로그와 실제 잔액부터 확인해야 합니다.

[한글 명령어 안내](한글명령어.md)를 확인하세요. 명령은 실행한 사람의 권한·수수료·대기 시간을 따릅니다. 일반 유저에게 운영자 권한을 주지 않습니다.

## 땅 보호·복구·권한 관리

LuckPerms(권한), GriefPrevention(땅 보호), CoreProtect(도난·파괴 기록/복구)는 추천 추가 구성입니다. 배포 사이트가 이 환경에서 차단되어 해당 JAR 3개는 포함하지 않습니다. 한글 별칭은 실제 JAR가 활성화되었을 때만 작동합니다. 기존 WorldGuard 보호는 그대로 사용합니다.

ZIP 폴더에서 다음 명령으로 공식 Modrinth 배포처를 조회해 추가 JAR를 받을 수 있습니다.

```powershell
powershell -NoProfile -File .\Download-Extras.ps1 -MinecraftVersion 26.3
```

입력한 게임 버전을 명시한 정식 릴리스만 선택하고 SHA-512를 검사합니다. 실패 목록을 확인하세요. 성공한 파일은 ZIP을 푼 위치의 `plugins`에 추가되므로 서버를 종료한 뒤 서버의 `plugins`로 복사합니다. Windows에서 스크립트 실행은 미검증입니다.

- LuckPerms: https://luckperms.net/download / https://modrinth.com/plugin/luckperms
- GriefPrevention: https://modrinth.com/plugin/griefprevention (공식 16.18.8은 26.3+ 지원 명시)
- CoreProtect: https://modrinth.com/plugin/coreprotect / https://github.com/PlayPro/CoreProtect

## 버전과 검증 범위

대상은 알려주신 Paper 26.3이며 실제 서버 접속 검사는 수행하지 못했습니다. EssentialsX 2.22.0 공식 지원 목록은 26.1.2·1.21.11 등이며 26.3은 미확인입니다. QuickShop 릴리스는 26.x 호환성 개선을 언급합니다. CoreProtect 공개 v24.1은 26.2 지원을 명시하며 26.3은 별도 확인해야 합니다. 기존 월드를 이전 서버 버전으로 열지 마세요.

검증 범위는 JAR 손상, 메타데이터·필수 의존 관계, YAML, 번역 키·치환 변수 보존, 명령어 변환, 메뉴·거래·보상 오프라인 동작입니다. Vault API는 공식 JAR를 사용하고 Bukkit/Paper는 로컬 최소 시그니처 fixture로 컴파일했습니다. fixture는 배포 JAR에 넣지 않습니다. 테스트 통과가 실제 서버 호환성을 보장하지는 않습니다.

`sources.json`에는 공식 JAR 원본 URL·버전·해시를 기록했고, `upstream-source`에는 해당 버전 소스를 넣었습니다. 공식 JAR는 수정하지 않았습니다.

```bash
python3 -m pip install -r 개발자료/배포도구/경제야생/requirements.txt
python3 개발자료/배포도구/경제야생/fetch_vendor.py
bash 개발자료/플러그인/한글명령어/build.sh
python3 개발자료/플러그인/마을수첩/build.py
python3 개발자료/플러그인/생활경제/build.py
python3 개발자료/배포도구/경제야생/build_pack.py
```
