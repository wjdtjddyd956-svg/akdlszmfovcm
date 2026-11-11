# 한글 플러그인 전체 정리 — 46종

Windows · Paper 26.3 · 약 10명 생활 경제 서버 기준입니다. 이용자 안내·설치 순서·기능 설명은 한국어로 정리하고 공식 이름은 검색과 버전 확인을 위해 함께 표시합니다.

**이 목록은 전체 설치 지시가 아닙니다.** 기존 구성 16종 / 추가 후보 14종 / 콘텐츠 확장 11종 / 접속 확장 4종 / 대체 후보 1종입니다. 최신 메뉴 통합 배포는 클라우드 자동 검증 결과이며 이번 작업에서 Windows 서버 설치 상태를 조회하거나 변경하지 않았습니다.

- [한글 정리·기존 자체 플러그인 묶음 ZIP](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/plugin-catalog/%ED%95%9C%EA%B8%80%ED%94%8C%EB%9F%AC%EA%B7%B8%EC%9D%B8-%EC%A0%95%EB%A6%AC%EB%AC%B6%EC%9D%8C-1.0.0.zip)
- [설치 순서와 기능 선택](설치순서.md) · [연결·한글화 현황](연결·한글화.md) · [의존성·검증 범위](의존성·검증.md)
- [엑셀용 전체 목록](플러그인목록.csv) · [구조화된 전체 목록](플러그인목록.json) · [자료 링크 확인 기록](자료링크검증.json)
- [최신 메뉴·도감·농장 통합 설치 파일](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/ui-update/%EB%8F%84%EC%8B%9C%EC%83%9D%ED%99%9C-%EB%A9%94%EB%89%B4%ED%86%B5%ED%95%A9-1.2.0.zip) · [사용법](https://github.com/wjdtjddyd956-svg/akdlszmfovcm/blob/main/interface-update/README.md)

## 정리 묶음에 실제로 들어 있는 것

기존에 제작·배포한 메뉴 통합 설치 자료를 그대로 포함합니다. `설치자료/도시생활UI/Install-Interface.cmd`는 기존 생활서버 2.0.1/2.0.2를 전체 백업 후 5개 JAR로 업데이트하는 설치기입니다. 추가 후보를 일괄 설치하지 않습니다.

| 실제 포함 파일 | 버전 | 역할 |
| --- | --- | --- |
| KoreanEconomy | 2.0.2 | 기존 화폐·NPC 상점·직업·요리·임무 |
| KoreanMenu | 2.1.0 | 쉬프트+F 기능별 메뉴·도감·농장 입구 |
| KoreanCodex | 1.2.0 | 정품 1개 소비 영구 등록·85종·13업적·작은 보너스 |
| KoreanFarming | 1.0.1 | 딸기·토마토·옥수수·쌀·오이 재배·수확·요리·판매 |
| CityTextures | 1.1.0 | 도시 아이콘·메뉴·도감·능력치 전용 아이콘과 팩 요청 |
| KoreanCommands | 2.0.0 | 생활 명령·월드에딧·영역 보호 등 기존 기능 한글 연결 |
| KoreanPackSetup | 2.0.0 | 기존 생활 서버의 한글 설정·초기 구성 보조 |

앞의 5개는 `설치자료/도시생활UI/plugins/`, Commands·Setup은 `설치자료/기존한글보조/`에 있습니다. 통합 리소스팩·설치기·안내·검증 결과도 포함합니다. 외부 플러그인과 추가 후보의 신규 JAR는 이 묶음에 넣지 않았으며 아래 공식 자료에서 확인합니다. 저장소에 이미 있는 외부 JAR는 개별 링크로 구분합니다.

## 기존 구성 — 16종

| 한글 이름 · 공식 이름 | 기능 | 우선순위 | 버전·파일 | 공식 자료 |
| --- | --- | --- | --- | --- |
| 한글 생활 경제 · **KoreanEconomy** | 기존 화폐·NPC 상점·직업·요리·임무 | 유지 | [2.0.2 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/ui-update/KoreanEconomy-2.0.2.jar) | [공식 자료](https://github.com/wjdtjddyd956-svg/akdlszmfovcm) |
| 마을 수첩 · **KoreanMenu** | 쉬프트+F 기능별 메뉴·도감·농장 입구 | 유지 | [2.1.0 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/ui-update/KoreanMenu-2.1.0.jar) | [공식 자료](https://github.com/wjdtjddyd956-svg/akdlszmfovcm) |
| 아이템 도감·업적 · **KoreanCodex** | 정품 1개 소비 영구 등록·85종·13업적·작은 보너스 | 유지 | [1.2.0 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/ui-update/KoreanCodex-1.2.0.jar) | [공식 자료](https://github.com/wjdtjddyd956-svg/akdlszmfovcm) |
| 도시 농장 · **KoreanFarming** | 딸기·토마토·옥수수·쌀·오이 재배·수확·요리·판매 | 유지 | [1.0.1 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/ui-update/KoreanFarming-1.0.1.jar) | [공식 자료](https://github.com/wjdtjddyd956-svg/akdlszmfovcm) |
| 도시 메뉴·리소스팩 · **CityTextures** | 도시 아이콘·메뉴·도감·능력치 전용 아이콘과 팩 요청 | 유지 | [1.1.0 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/ui-update/CityTextures-1.1.0.jar) | [공식 자료](https://github.com/wjdtjddyd956-svg/akdlszmfovcm) |
| 한글 명령 연결 · **KoreanCommands** | 생활 명령·월드에딧·영역 보호 등 기존 기능 한글 연결 | 유지 | [2.0.0 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/tycoon-2.0.1/KoreanCommands-2.0.0.jar) | [공식 자료](https://github.com/wjdtjddyd956-svg/akdlszmfovcm) |
| 생활 서버 설정 보조 · **KoreanPackSetup** | 기존 생활 서버의 한글 설정·초기 구성 보조 | 유지 | [2.0.0 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/tycoon-2.0.1/KoreanPackSetup-2.0.0.jar) | [공식 자료](https://github.com/wjdtjddyd956-svg/akdlszmfovcm) |
| 기본 생활·화폐 · **EssentialsX** | 잔액·송금·집·이동 요청 | 유지 | 2.22.1-dev+28-f8724e2 사용 기록 / 신규 JAR 미포함 | [공식 자료](https://github.com/EssentialsX/Essentials) |
| 스폰 관리 · **EssentialsXSpawn** | 스폰 지정·이동·접속 위치 | 유지 | 2.22.1-dev+28-f8724e2 사용 기록 / 신규 JAR 미포함 | [공식 자료](https://github.com/EssentialsX/Essentials) |
| 경제 연결 · **Vault** | 플러그인 간 경제 기능 연결 | 유지 | [1.7.3-b131 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/survival-pack/vendor/Vault.jar) | [공식 자료](https://github.com/MilkBowl/Vault) |
| 유저 상자 상점 · **QuickShop-Hikari** | 플레이어가 상자로 만드는 구매·판매 상점 | 유지 | [6.3.0.4 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/survival-pack/vendor/QuickShop-Hikari-6.3.0.4.jar) | [공식 자료](https://github.com/QuickShop-Community/QuickShop-Hikari) |
| 개인 땅 보호 · **GriefPrevention** | 개인 구역·친구 건축·상자 권한 | 유지 | 16.18.8 사용 기록 / 신규 JAR 미포함 | [공식 자료](https://github.com/GriefPrevention/GriefPrevention) |
| 맵·건축 편집 · **WorldEdit** | 건축 편집·스키매틱 불러오기 | 유지 | [7.4.6+7648-00664b0 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/korean-plugins/worldedit-bukkit-7.4.6-ko.jar) | [공식 자료](https://github.com/EngineHub/WorldEdit) |
| 관리 구역 보호 · **WorldGuard** | 스폰·관리 구역·지역 규칙 | 유지 | [7.0.21+2424-da30a99 JAR](https://raw.githubusercontent.com/wjdtjddyd956-svg/akdlszmfovcm/main/releases/korean-plugins/worldguard-bukkit-7.0.21-ko.jar) | [공식 자료](https://github.com/EngineHub/WorldGuard) |
| 활동 기록·복구 · **Prism** | 블록·상자 등 활동 조회와 운영자 복구 | 유지 | 4.5 사용 기록 / 신규 JAR 미포함 | [공식 자료](https://modrinth.com/plugin/oLV8Vvxy) |
| 아이템 데이터 보조 · **NBTAPI** | 아이템 데이터 처리가 필요한 플러그인의 보조 라이브러리 | 유지 | 2.16.1 사용 기록 / 신규 JAR 미포함 | [공식 자료](https://github.com/tr7zw/Item-NBT-API) |

## 추가 후보 — 14종

| 한글 이름 · 공식 이름 | 기능 | 우선순위 | 버전·파일 | 공식 자료 |
| --- | --- | --- | --- | --- |
| 권한·등급 관리 · **LuckPerms** | 유저·운영자·등급별 기능 권한 | 높음 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/LuckPerms/LuckPerms) |
| 지형 미리 생성 · **Chunky** | 야생 탐험 시 새 청크 생성 부하 완화 | 높음 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/pop4959/Chunky) |
| 렉 원인 분석 · **spark** | CPU·틱·느린 플러그인 분석 | 높음 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/lucko/spark) |
| 정보 표시 연결 · **PlaceholderAPI** | 잔액·접속자 수 등 표시값 연결 | 높음 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/PlaceholderAPI/PlaceholderAPI) |
| 탭 목록·안내판 · **TAB** | 탭 목록·이름·사이드바에 서버 정보 표시 | 보통 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/NEZNAMY/TAB) |
| 공중 안내문 · **DecentHolograms** | NPC 이름·상점 안내·마을 공중 안내판 | 보통 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/DecentSoftware-eu/DecentHolograms) |
| 야생 랜덤 이동 · **BetterRTP** | 야생의 적절한 위치로 랜덤 이동 | 보통 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://www.spigotmc.org/resources/betterrtp.36081/) |
| 여러 월드 관리 · **Multiverse-Core** | 로비·야생·건축 월드 분리 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/Multiverse/Multiverse-Core) |
| 접속·활동 통계 · **Plan** | 접속 시간·이용자 활동 통계 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/plan-player-analytics/Plan) |
| 디스코드 연결 · **DiscordSRV** | 게임 채팅·접속 알림과 디스코드 연동 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/DiscordSRV/DiscordSRV) |
| 핵 탐지 · **GrimAC** | 이동·전투 등 이상 행동 탐지 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/GrimAnticheat/Grim) |
| 개별 가방 복구 · **InventoryRollbackPlus** | 운영자용 인벤토리 복구 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/TechnicallyCoded/Inventory-Rollback-Plus) |
| 패킷 기능 보조 · **PacketEvents** | 요구하는 플러그인의 패킷 기능 지원 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/retrooper/packetevents) |
| 패킷 기능 보조 · **ProtocolLib** | 요구하는 플러그인의 패킷 기능 지원 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/dmulloy2/ProtocolLib) |

## 콘텐츠 확장 — 11종

| 한글 이름 · 공식 이름 | 기능 | 우선순위 | 버전·파일 | 공식 자료 |
| --- | --- | --- | --- | --- |
| NPC 연출 · **Citizens** | 외형·동선·상호작용 등 NPC 연출 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/CitizensDev/Citizens2) |
| 스토리·연속 임무 · **BetonQuest** | NPC 대화·분기·스토리 퀘스트 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/BetonQuest/BetonQuest) |
| NPC 임무 제작 · **BeautyQuests** | NPC와 연결하는 퀘스트 콘텐츠 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/SkytAsul/BeautyQuests) |
| 커스텀 콘텐츠 · **ItemsAdder** | 아이템·블록·가구 등 커스텀 콘텐츠 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://itemsadder.devs.beer/) |
| 커스텀 콘텐츠 · **Nexo** | 아이템·블록·가구 등 커스텀 콘텐츠 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://docs.nexomc.com/) |
| 커스텀 콘텐츠 · **Oraxen** | 리소스팩 기반 커스텀 아이템·블록 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/oraxen/oraxen) |
| 움직이는 3D 모델 · **ModelEngine** | 커스텀 모델과 애니메이션 연출 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://mythiccraft.io/) |
| 커스텀 몹·보스 · **MythicMobs** | 사냥터·보스·스킬 콘텐츠 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://mythiccraft.io/) |
| GUI 경매장 · **AuctionHouse** | 경매·위탁 판매 방식의 유저 거래 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://www.spigotmc.org/resources/auctionhouse.61836/) |
| 이벤트 보상 상자 · **ExcellentCrates** | 이벤트 상자·보상 콘텐츠 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/nulli0n/ExcellentCrates-spigot) |
| 아이템 교환 상인 · **Shopkeepers** | 아이템 교환형 주민 상점 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/Shopkeepers/Shopkeepers) |

## 접속 확장 — 4종

| 한글 이름 · 공식 이름 | 기능 | 우선순위 | 버전·파일 | 공식 자료 |
| --- | --- | --- | --- | --- |
| 자바 버전 접속 연결 · **ViaVersion** | 서로 다른 자바 클라이언트 버전 접속 보조 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/ViaVersion/ViaVersion) |
| 이전 자바 버전 접속 · **ViaBackwards** | 지원되는 이전 클라이언트 접속 보조 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/ViaVersion/ViaBackwards) |
| 베드락 접속 연결 · **Geyser** | 베드락 이용자의 서버 참여 보조 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/GeyserMC/Geyser) |
| 베드락 계정 연결 · **Floodgate** | Geyser와 함께 베드락 계정 연결 보조 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/GeyserMC/Floodgate) |

## 대체 후보 — 1종

| 한글 이름 · 공식 이름 | 기능 | 우선순위 | 버전·파일 | 공식 자료 |
| --- | --- | --- | --- | --- |
| 활동 기록·복구 대안 · **CoreProtect** | 블록·상자 등 기록과 복구 | 선택 | 미선정 / 신규 JAR 미포함 | [공식 자료](https://github.com/PlayPro/CoreProtect) |

## 한글화와 호환성의 기준

자체 플러그인의 한국어 메뉴·명령과 기존 설정 자료를 보존합니다. **추가 후보는 한글 설명만 작성했으며 내부 문구·설정·새 한글 명령 별칭·자체 도감/농장 표시값 연결을 완료한 상태가 아닙니다.** [연결·한글화 현황](연결·한글화.md) · [의존성·검증 범위](의존성·검증.md)에 항목별로 구분했습니다. 아직 없는 한글 명령을 사용할 수 있다고 안내하지 않습니다.

공식 자료의 HTTP 정상 응답은 자료 주소 확인입니다. Paper 26.3 지원·실제 플레이·완전 한글화 검증을 의미하지 않습니다. 추가 후보의 버전은 미선정이며 설치 전에 공식 지원 범위와 의존성을 확인해야 합니다. 현재 메뉴 통합 배포의 자동 테스트 132개와 설치 검사 37개는 해당 배포의 범위이고 신규 후보를 검증한 숫자가 아닙니다.

## 먼저 추가할 순서

LuckPerms → 월드 경계를 정하고 Chunky → PlaceholderAPI 연결 범위 결정 → TAB·DecentHolograms 한국어 표시 → BetterRTP의 야생 입장 연결 순서입니다. `/spark`가 이미 제공되면 추가 JAR 없이 사용합니다.

EssentialsX 2.22.0은 오류 이력으로 최신 설치 목록에서 제외합니다. 기존 호환 본체·Spawn 쌍과 Vault·보호·기록·상점 데이터는 유지합니다. 인벤토리 복구·새 경매장·핵 탐지·베드락 접속은 기존 소비/판매/능력치/계정과의 연결 검증이 필요합니다.
