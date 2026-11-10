# 소규모 경제 야생 플러그인 묶음

직접 만든 쉬프트+F 메뉴와 공식 무료 플러그인 4개를 제공합니다. 대상은 사용자가 알려준 Paper 26.3이며, 모든 플러그인의 해당 버전 호환성을 검증한 패키지는 아닙니다.

| 포함 파일 | 역할 | 버전 확인 결과 |
| --- | --- | --- |
| KoreanMenu 1.0.0 | 쉬프트+F 한글 메뉴 | 오프라인 이벤트 검사 완료, 서버 실행 미검증 |
| EssentialsX 2.22.0 | 돈·송금·집·TPA | 공식 지원 목록은 26.1.2 및 1.21.11 등, 26.3 지원 미확인 |
| EssentialsXSpawn 2.22.0 | 스폰 설정·이동 | EssentialsX와 동일 |
| Vault 1.7.3 | 경제 플러그인 연결 | 26.3 실행 미검증 |
| QuickShop-Hikari 6.3.0.4 | 상자 기반 유저 상점 | 릴리스 설명에서 26.x 호환성 개선 언급, 26.3 실행 미검증 |

LuckPerms(권한), GriefPrevention(땅 보호), CoreProtect(파괴·도난 기록/복구)는 추천 추가 플러그인입니다. 해당 배포처가 클라우드 환경에서 차단되어 **JAR가 포함되지 않습니다**. `Download-Extras.ps1`은 Windows에서 공식 Modrinth API를 조회해 입력한 마인크래프트 버전을 명시한 정식 릴리스만 받습니다. 지원 릴리스가 없거나 다운로드에 실패하면 오류로 알립니다. 땅 보호·복구 기능은 해당 JAR가 설치되고 활성화된 뒤에만 사용할 수 있습니다.

## 설치

1. 서버 콘솔에서 `stop`을 입력하고 서버 폴더를 백업합니다.
2. ZIP의 `plugins` 폴더 안 JAR를 서버의 `plugins` 폴더에 넣습니다. 같은 플러그인의 다른 버전이 이미 있으면 하나만 남깁니다. WorldEdit·WorldGuard는 이미 설치한 것을 유지합니다.
3. `신규설정`에는 한글·기본 경제 설정을 적용한 Essentials/QuickShop 설정이 있습니다. **그 플러그인을 처음 설치하는 경우만**, 안의 두 폴더를 서버의 `plugins`에 복사합니다. 기존 설정이 있으면 파일을 덮어쓰지 말고 아래 변경값을 병합합니다.
4. 서버를 시작해 `/plugins`와 콘솔 오류를 확인합니다. Paper 26.3에서 오류가 나면 서버를 종료하고 해당 JAR를 빼세요. 기존 월드를 이전 버전 서버로 열지 마세요.
5. 운영자가 스폰 장소에서 `/setspawn`을 한 번 실행합니다. `/kmenu` 또는 쉬프트+F로 메뉴를 엽니다. 다른 버전의 집 이름을 쓰던 경우 이 메뉴는 `default` 집만 사용합니다.

F키는 마인크래프트의 보조 손 교체 기본 키입니다. 보조 손 교체 키를 변경했다면 쉬프트와 변경한 키를 같이 누르세요. 일반 F키는 그대로 동작합니다.

## 추천 추가 플러그인 다운로드

ZIP을 서버 폴더 바깥에 풀고, 해당 폴더에서 PowerShell을 열어 실행합니다. 관리자 권한이나 시스템 실행 정책 변경은 필요하지 않습니다. 실행 정책에 의해 차단되면 공식 링크에서 직접 받으세요.

```powershell
powershell -NoProfile -File .\Download-Extras.ps1 -MinecraftVersion 26.3
```

다운로드 성공 시 ZIP을 푼 위치의 `plugins`에 JAR가 추가됩니다. 서버를 종료한 뒤 서버의 `plugins`로 복사하세요. 완료 메시지와 실패 목록을 확인해야 합니다. 스크립트는 정확한 버전을 지원한다고 명시된 정식 릴리스만 선택하며, 받은 파일의 SHA-512와 플러그인 메타데이터를 검사합니다. 실제 Windows 실행·외부 API 호출은 이 환경에서 검증하지 못했습니다.

- LuckPerms: https://luckperms.net/download / https://modrinth.com/plugin/luckperms
- GriefPrevention: https://modrinth.com/plugin/griefprevention (공식 GitHub 16.18.8 릴리스는 26.3+ 지원을 명시)
- CoreProtect: https://modrinth.com/plugin/coreprotect / https://github.com/PlayPro/CoreProtect

CoreProtect GitHub 최신 공개 릴리스 v24.1은 26.2 지원을 명시합니다. 26.3 지원은 별도 확인이 필요합니다. 무료 공개 배포만 사용하며 유료 전용 빌드는 포함하지 않습니다.

## 한글과 경제 설정

EssentialsX와 QuickShop JAR는 수정하지 않은 공식 파일이며 각각 내장 한국어 번역을 사용합니다. 일부 새 메시지·콘솔·설정 주석은 영어일 수 있습니다. KoreanMenu 화면과 자체 안내는 한글입니다.

신규 설정의 변경값:

- Essentials: `locale: ko`, 시작 잔액 1000, 통화 `원` 접미사, 최소 잔액 0, 이동 준비 3초, 재사용 10초, 집 미저장 시 스폰 대체 이동 끔.
- QuickShop: `game-language: ko_kr`, 전시 아이템 끔(추가 패킷 플러그인 없이 운영), 번역 자동 갱신 대신 내장 번역 사용.

일반 플레이어에게 필요한 기본 명령어 권한은 KoreanMenu의 `koreanmenu.basics`가 제공합니다. LuckPerms로 직접 거부한 권한이 있으면 별도 조정해야 합니다. 운영자 명령은 기본 플레이어에게 허용하지 않습니다.

시작 잔액은 최초 접속 시에만 지급됩니다. 지속적인 돈 공급은 운영자 매입 상점 등 경제 규칙을 추가해야 합니다. `/eco give 닉네임 금액`과 QuickShop 무한 상점은 운영자 전용이며, 상점 물가와 지급량은 운영자가 정합니다.

## 출처와 검증

`sources.json`에는 원본 JAR URL·버전·SHA-256이 있고 `SHA256SUMS.txt`로 묶음 파일을 확인할 수 있습니다. 배포 ZIP의 `upstream-source`에는 재배포하는 공식 플러그인의 해당 태그 소스 스냅샷을 포함합니다. 저작권·라이선스는 각 프로젝트 소스를 따릅니다.

```bash
python3 -m pip install -r survival-pack/requirements.txt
python3 survival-pack/fetch_vendor.py
python3 korean-menu/build.py
python3 survival-pack/build_pack.py
```

현재 검증 범위는 JAR ZIP 구조, 메타데이터·의존 관계, YAML 파싱, 메뉴 오프라인 이벤트 검사입니다. 실제 서버 접속 검사는 수행하지 않았습니다.
