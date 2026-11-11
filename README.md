# 한글 생활 타이쿤 서버

현재 Windows 업그레이드는 **생활타이쿤 2.0.1**, Paper 26.3 build 238용입니다. NPC 상점·직업·요리·강화·임무·기존 GriefPrevention 땅 보호·Prism 기록·Windows 백업과 쉬프트+F 한글 메뉴를 포함합니다.

- [기존 1.2/2.0용 Windows 설치 ZIP](releases/tycoon-2.0.1/생활타이쿤-Windows-업그레이드-2.0.1.zip)
- [설치·명령어·백업·복원 안내](releases/tycoon-2.0.1/설치·운영안내.md)
- [실제 Windows/Paper 검증 범위와 한계](releases/tycoon-2.0.1/검증결과.md)
- [JAR·체크섬·빌드 출처](releases/tycoon-2.0.1/)
- [이전 2.0.0 전체 설치 자료](releases/tycoon-2.0/)

2026-11-11 실제 Windows 서버가 1.2.0임을 확인하고, 기존 데이터의 전체 백업·복원 검증 후 2.0.1을 적용했습니다. 실제 Paper 복사본과 순정 26.3 클라이언트에서도 검증했습니다. 운영 서버의 로그인한 실제 이용자 플레이와 10명 동시 활동 부하 검증은 별도입니다. 설치된 호환 EssentialsX `2.22.1-dev+28-f8724e2` 본체·Spawn, GP·Prism·WorldGuard와 기존 백업 관리자를 유지했습니다. 새 ZIP은 이미 설치된 이 플러그인들을 재사용하며 미지원 버전으로 교체하지 않습니다.

현실적인 도시·경제풍의 [시티 이코노미 리소스팩](city-resourcepack/README.md)도 제공합니다. 요리·경제·직업·메뉴 아이콘 32종(64x), 선택 건축 재질 8종(128x), 기존 메뉴에 연결하는 CityTextures 보조 플러그인과 [미리보기](releases/city-resourcepack/미리보기.png)를 포함합니다. [전체 다운로드](releases/city-resourcepack/CityEconomy-Package-1.0.0.zip).

아래는 이전 RPG·경제 야생 자료의 이력입니다.

# 이전 서버 자료

소규모 Paper 기반 RPG 서버를 만들면서 사용하는 설정과 결과물을 보관합니다.

## 10인 한글 생활 경제 서버

Paper 26.3용 1.2.0은 NPC 상점에 매입 한도를 추가하고, 직업·일일 임무·땅 보호·기록 복구·자동 백업을 구성합니다.

- [설치 ZIP](releases/survival-pack/Korean-Life-Server-1.2.0.zip)
- [Windows 설치와 백업 복원](life-server/README.md) · [한글 명령어표](life-server/명령어.md) · [검증 결과](life-server/검증.md)
- 현재 배포본은 `life-server/Build-LifePack.ps1`로 실제 Paper 26.3 API에서 빌드하고 `life-server/Package-LifePack.ps1`로 패키징합니다. 예전 배포 파일은 해당 버전의 커밋에서 재빌드할 수 있습니다.

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
이전 묶음은 `EconomySurvival-Korean-Pack-1.1.0.zip`이며 한글 채팅 기능은 포함하지 않습니다. EarthMC 공개 가이드를 참고한 [설계](survival-pack/참고서버.md)와 [한글 명령어 안내](survival-pack/한글명령어.md)를 함께 제공합니다.

Paper 26.3에서 EssentialsX 2.22.0 미지원 메시지가 실제 확인되었습니다. 본체·스폰은 공식 26.3 지원 개발 빌드로 교체해야 합니다. [26.3 수정 안내](korean-pack-setup/README.md)와 `releases/survival-pack/Paper26.3-Economy-Fix-1.0.0.zip`에는 기존 데이터를 유지하는 1회 설정 적용 JAR와 공식 개발 빌드 다운로드 스크립트가 있습니다. 차단된 공식 개발 JAR 자체는 포함하지 않습니다.

이전 1.1 상점은 NPC 우클릭 방식입니다. `NPC-Shop-Korean-Update-1.1.0.zip`의 `KoreanEconomy-1.1.0.jar`로 기존 1.0.0 JAR를 교체하세요. `/상인 생성 농부 농산물`처럼 한글 명령으로 사람형·주민형 NPC를 배치할 수 있습니다. [NPC 상점 안내](survival-pack/NPC상점-설치안내.md)를 확인하세요. 이전 배포물의 소스/도구 사용법은 해당 버전 이력을 참고하세요.
