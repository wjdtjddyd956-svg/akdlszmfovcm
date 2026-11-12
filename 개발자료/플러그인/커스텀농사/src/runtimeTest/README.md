# 실제 Paper 회귀 검사

`FarmProbe.java`는 외부 서비스가 없는 `127.0.0.1:25566`, offline-mode 테스트 복제본에서만 켜지는 RCON 전용 계측입니다. 운영 서버에 설치하지 마세요. 같은 폴더의 JS는 테스트 전용 ViaVersion/ViaBackwards를 통해 26.1 프로토콜 봇으로 **실제 Paper 26.3 build 238**에 접속합니다. 26.3 네이티브 클라이언트 화면 검사는 별도입니다.

컴파일 의존성: 실제 설치된 Paper API238/libraries + 배포 KoreanEconomy2.0.2/Farming1.0.1. `main: FarmProbe`, `api-version: '26.3'`, `depend: [KoreanFarming]`, `commands: {farmprobe: {}}` 플러그인 서술자와 JAR로 묶어 테스트 복제본에만 설치합니다. 도감 효과 확인에는 저장소 `개발자료/플러그인/아이템도감/src/runtimeTest/CodexProbe.java`도 사용합니다.

환경 변수: `FARM_ISOLATED_TEST_COPY=YES`, `FARM_TEST_MODULES`(mineflayer 설치 폴더), `FARM_TEST_RCON_SCRIPT`(개인 테스트 RCON 클라이언트 파일), `FARM_TEST_STATE`(재시작 전 스냅샷 JSON 경로). RCON 암호·실제 사용자 데이터는 공개 소스나 배포 ZIP에 포함하지 않습니다.

순서: `node farm-runtime.cjs features`, `faults`, 테스트 서버 정상 종료/재시작, `after`, `protection`. 기존 테스트 복제본의 일반상인26.5,100,25.5 / 요리상인38.5,100,25.5와 WorldGuard 스폰 보호 및 GP104,100,104 타인 땅 fixture가 전제입니다. 새 복제본에서는 해당 fixture를 먼저 만드세요. 재실행 때에는 새 합성 계정이나 초기 복제본을 사용해야 합니다.

`features`는 실제 10초 성장 타이머의 물/빛 조건을 검사합니다. 모든 작물의 긴 대기·레시피 해금은 테스트 전용 progress/XP 조정으로 가속하므로 정상 시간 전체를 기다린 플레이 검증과 다릅니다. `faults`는 SQLite 트리거로 최종 저장만 중단해 실제 플레이어 NBT에 저장된 아이템과 REVIEW 거래의 재시작 동작을 확인합니다. `protection`은 실제 WorldGuard/GP API와 타인 수확 거절, 청크 언로드를 검사합니다. 청크 언로드 검사 중에는 **모든** 접속 플레이어가 작물의 시야 거리 밖에 있어야 합니다.
