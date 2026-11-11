# 한글 생활 경제 야생 서버 1.2.0

Windows 11의 기존 Paper 26.3 서버를 10인용으로 올리는 설치판입니다. 서버의 기존 월드, 잔액, 집, NPC, WorldGuard 지역, QuickShop 상점과 보상 데이터 폴더를 그대로 사용합니다. 기본 Java 힙은 512MiB~2GiB, 시야 거리는 8청크, 시뮬레이션 거리는 5청크입니다.

## Windows에서 설치

1. 서버 콘솔에 `stop`을 입력하고 창이 닫힐 때까지 기다리세요. 관리 스크립트로 켜 둔 서버라면 `ServerStop.cmd`를 실행하세요.
2. 설치 ZIP을 서버 폴더 **밖**에 풀고, 압축을 푼 폴더에서 PowerShell을 여세요.
3. 아래에서 서버 경로를 자신의 경로로 바꾸고 실행하세요. 서버 폴더에는 기존 Paper JAR, `server.properties`, `plugins`가 있어야 합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Install-LifePack.ps1 -ServerDirectory 'C:\내서버'
```

설치 프로그램은 ZIP 내부 체크섬과 공식 의존 JAR를 확인합니다. 없거나 버전이 다른 의존 JAR는 공식 배포처에서 받습니다. 다운로드에 실패하면 기존 JAR·설정 교체 전에 중단합니다. 설치 직전의 월드와 플러그인 데이터는 서버 옆 `-backups` 폴더에 ZIP으로 저장합니다.

4. 서버 폴더의 `ServerStart.cmd`로 시작하세요. 이 방식으로 시작해야 자동 백업과 `/백업 지금`을 사용할 수 있습니다. Paper와 호환되는 Java 25 이상이 필요합니다. Java가 여러 개면 `life-tools/backup.json`의 `javaExe`에 Java의 전체 경로를 넣으세요.
5. 스폰 근처의 원하는 자리에 서서 운영자 계정으로 다음 명령을 하나씩 사용하세요. 기존 잡화상인이 이미 있다면 그 NPC를 계속 쓸 수 있습니다.

```text
/상인 생성 잡화상인 전체
/상인 생성 임무안내 일일임무
```

서로 다른 안전한 지점에 배치하고 일반 계정으로 우클릭을 확인하세요. 스폰 보호가 상호작용을 막는 곳에서는 보호 경계 밖의 접근 가능한 자리를 사용하세요. 다른 운영자가 직접 관리할 서버에서도 기존 스폰의 WorldGuard 규칙은 그대로 적용됩니다.

### 교체하는 파일

| 대상 | 적용 |
| --- | --- |
| KoreanEconomy 1.0/1.1 | `KoreanEconomy-1.2.0.jar` |
| KoreanMenu 1.1 | `KoreanMenu-1.2.0.jar` |
| EssentialsX, EssentialsXSpawn | 공식 빌드 1833, 둘 다 `2.22.1-dev+28-f8724e2` |
| 땅 보호와 기록 | GriefPrevention 16.18.8, Prism 4.5, NBT-API 2.16.1 추가 |
| KoreanCommands, KoreanPackSetup | 기존 설치를 사용하며 없을 때 ZIP에 든 JAR 추가 |
| WorldEdit, WorldGuard, QuickShop, Vault | 기존 설치를 사용 |

설치 프로그램은 플러그인 이름을 기준으로 이전 JAR를 백업합니다. 새 유저 시작 잔액은 1,000원입니다. 이미 생성된 계정의 잔액은 자동 지급 대상에서 제외됩니다. 기존 KoreanEconomy 가격·일일 보상은 유지되며 새 항목이 기본값으로 병합됩니다.

## 가격·보상·보호 설정

- `plugins/KoreanEconomy/config.yml`의 `상점`, `직업`, `임무`, `보상-시간대`를 수정할 수 있습니다. `/경제설정 새로고침`으로 적용합니다. 매입 한도는 품목별·유저별로 한국 시간 자정에 초기화되며, 0은 해당 품목의 매입을 중지합니다. 판매가는 구매가보다 높게 설정할 수 없습니다.
- 직업은 하루에 한 번 선택·변경할 수 있습니다. 적립금은 직업 메뉴의 금괴에서 받습니다. 하루 보상 한도는 직업 사이에 공유되고, 레벨 진행도는 직업별로 남습니다. 정식 플레이로 설치한 광석 및 피스톤으로 이동한 광석은 보상과 임무에 포함되지 않습니다. 설치 이전에 이미 배치된 광석과 운영 도구가 직접 배치한 광석은 플레이어 설치 기록이 없을 수 있습니다.
- 임무는 안내 NPC에서 수락하고 완료합니다. `/임무`는 어디서나 상태를 표시합니다. 밀 납품은 기본 아이템을 가방에서 회수합니다. 성공·한도·수령 기록은 UUID로 저장합니다. 전원 강제 종료 등으로 Vault와 서버 데이터 사이 결과가 불명확해지면 해당 플레이어의 새 생활 거래를 잠급니다. 운영자가 로그와 잔액·아이템을 확인한 후 `/경제거래 UUID 확인|완료|취소`로 기록만 정리합니다. 진행 중 활동은 평상시 2초 간격 및 정상 종료 시 저장합니다.
- 보호는 기본 오버월드에서 적용됩니다. 금 삽으로 대각선 두 점을 지정하고 막대기로 경계를 확인하세요. GriefPrevention에서 WorldGuard 건축 제한을 적용합니다. 땅 설정과 한국어 핵심 메시지는 최초 설치에 포함되며, 기존 GriefPrevention 설정·땅 데이터는 보존합니다.
- Prism은 기본 SQLite를 사용합니다. 첫 시작 시 Prism 자체의 런타임 의존성 다운로드가 필요할 수 있습니다. 블록 설치·파괴와 플레이어 상자 입출고는 기록됩니다. 블록 미리보기는 현재 월드를 바꾸지 않으며, 상자 내역은 조회로 먼저 확인합니다. Prism은 꺼낸 아이템의 상자 복구를 지원하고, 넣은 아이템 제거는 기록 조회 범위입니다. 도난된 물건이 아직 플레이어 가방에 있다면 운영자가 별도로 처리하세요. [Prism 기록·복구 범위](https://docs.prism-mc.org/actions/)

## 자동 백업과 복원

`life-tools/backup.json` 기본값은 12시간마다, 최근 7개 보관, 서버 옆 `-backups` 폴더입니다. `intervalHours`는 주기, `keep`은 개수, `directory`는 저장 위치, `warningSeconds`는 재시작 사전 안내 시간입니다. 변경 후 `ServerStop.cmd`와 `ServerStart.cmd`로 관리자도 다시 시작하세요.

백업은 `save-all flush`와 `stop`을 실행하고 Paper의 종료와 월드 잠금 해제를 확인한 다음, 닫힌 월드와 플러그인 데이터를 임시 스냅샷에 복사합니다. 복사 후 Paper를 다시 시작하며 ZIP 압축과 체크섬 계산은 별도 Windows 프로세스에서 진행됩니다. 복사와 재기동 동안 잠시 접속이 끊깁니다. `/백업 상태`, `life-control/backup.log`에서 결과를 확인하세요. Java/Paper JAR와 라이브러리 캐시 대신 월드, 플러그인 JAR·데이터, 서버 설정을 백업합니다. ZIP과 옆의 `.sha256` 파일을 함께 보관하세요.

복원 전 서버를 정상 종료하고 서버 폴더에서 아래를 실행하세요.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\life-tools\Restore-LifeBackup.ps1 -Archive 'C:\백업\life-서버ID-날짜.zip'
```

복원은 체크섬과 월드 목록을 검증하고, 교체되는 기존 파일을 서버 옆 `life-before-restore-...` 폴더에 보관합니다. 복원한 백업과 동일한 Paper 26.3 서버로 시작하세요. 운영 서버 복원 전에 복사본에서 먼저 확인하면 됩니다.

[한글 명령어표](명령어.md) · [검증 결과](검증.md)
