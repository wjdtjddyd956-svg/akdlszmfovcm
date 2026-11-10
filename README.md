# Minecraft RPG 서버 자료

소규모 Paper 기반 RPG 서버를 만들면서 사용하는 설정과 결과물을 보관합니다.

## 플러그인 한글화

`releases/korean-plugins/`에 WorldEdit 7.4.6 및 WorldGuard 7.0.21 한국어 수정본과 설치 안내가 있습니다.

- `WorldEdit-WorldGuard-Korean.zip`: 두 플러그인과 안내 문서를 묶은 파일
- `worldedit-bukkit-7.4.6-ko.jar`: WorldEdit 한국어 수정본
- `worldguard-bukkit-7.0.21-ko.jar`: WorldGuard 한국어 수정본
- `설치안내.txt`: 적용법, 번역 범위, 검증 결과

공식 배포판이 아닌 수정본이므로 적용 전에 서버와 기존 플러그인을 백업해야 합니다.

## 한글 명령어

`korean-commands/`에는 WorldEdit과 WorldGuard의 주요 명령어를 한글로 입력할 수 있게 해 주는 Paper 플러그인 소스가 있습니다.
설치용 JAR과 안내 문서는 `releases/korean-commands/`에 있습니다.

```text
//채우기 돌                    → //set stone
/영역 생성 마을               → /rg define 마을
/영역 설정 마을 피브이피 거부 → /rg flag 마을 pvp deny
```

## 현실 경제 야생 스폰

`economy-spawn/`에는 중앙광장, 시청, 은행, 직업소, 유저 상점 10칸, 초보 농장과 야생 출구가 포함된 현대 소도시형 스폰 생성기가 있습니다.
설치용 WorldEdit 스키매틱은 `releases/economy-spawn/`에 있습니다.
`v3_handbuilt`판은 굽은 길·하천·돌다리·경사지붕·목재 골조·불규칙 농장으로 다시 설계한 고품질 버전입니다.

## 경제 야생 플러그인과 쉬프트+F 메뉴

`korean-menu/`에는 직접 만든 한글 메뉴와 검사가 있습니다. `korean-economy/`에는 같은 Vault 잔액으로 거래하는 서버 상점과 일일 보상이 있습니다.
`survival-pack/`에는 공식 EssentialsX·EssentialsXSpawn·Vault·QuickShop JAR의 배포 묶음 생성기와 한글 신규 설정, 추가 플러그인 다운로드 스크립트가 있습니다.
설치 ZIP과 단독 메뉴 JAR는 `releases/survival-pack/`에 있습니다. Paper 26.3 전체 호환성 및 실제 서버 실행은 미검증이며, 추천 추가 플러그인 LuckPerms·GriefPrevention·CoreProtect JAR는 ZIP에 포함되지 않습니다. 상세 범위는 해당 설치 안내를 참고하세요.
최신 묶음은 `EconomySurvival-Korean-Pack-1.1.0.zip`이며 한글 채팅 기능은 포함하지 않습니다. EarthMC 공개 가이드를 참고한 [설계](survival-pack/참고서버.md)와 [한글 명령어 안내](survival-pack/한글명령어.md)를 함께 제공합니다.

Paper 26.3에서 EssentialsX 2.22.0 미지원 메시지가 실제 확인되었습니다. 본체·스폰은 공식 26.3 지원 개발 빌드로 교체해야 합니다. [26.3 수정 안내](korean-pack-setup/README.md)와 `releases/survival-pack/Paper26.3-Economy-Fix-1.0.0.zip`에는 기존 데이터를 유지하는 1회 설정 적용 JAR와 공식 개발 빌드 다운로드 스크립트가 있습니다. 차단된 공식 개발 JAR 자체는 포함하지 않습니다.

상점의 최신 버전은 NPC 우클릭 방식입니다. `NPC-Shop-Korean-Update-1.1.0.zip`의 `KoreanEconomy-1.1.0.jar`로 기존 1.0.0 JAR를 교체하세요. `/상인 생성 농부 농산물`처럼 한글 명령으로 사람형·주민형 NPC를 배치할 수 있습니다. [NPC 상점 안내](survival-pack/NPC상점-설치안내.md)를 확인하세요. 현재 소스에서 NPC 교체 파일을 만들 때는 `python3 korean-economy/build.py` 후 `python3 survival-pack/build_npc_update.py`를 사용합니다. 기본 묶음 1.1 ZIP은 NPC 업데이트 이전 배포본입니다.
