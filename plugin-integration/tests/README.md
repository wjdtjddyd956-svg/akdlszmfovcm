# 검사 재현 환경

운영 폴더로 테스트 클라이언트를 접속시키지 마세요. 실제 전체 백업의 별도 복원 사본을 localhost 127.0.0.1:25566, 온라인 인증 끔, 외부 접근 차단 상태로 사용했습니다. 시험 UUID·인벤토리·잔액 변경은 이 사본에만 적용합니다. 실제 운영 인증·포트·비밀번호를 저장소에 넣지 않습니다.

build.py --runtime-probe로 만든 BridgeProbe는 주소/포트/인증 상태를 검사하고 지정한 복제 서버에서만 활성화됩니다. 실제 API classpath로 컴파일하며 .build/integration에 생성합니다. 운영 ZIP에는 포함되지 않습니다. UIProbe, FarmProbe, CodexProbe는 기존 저장소의 runtimeTest 소스를 재사용합니다.

runtime.cjs/language.cjs는 로컬 .build/life-bot의 Mineflayer 4.39.0 / minecraft-protocol 1.68.0 / minecraft-data 3.119.0을 사용합니다. 실제 26.3 서버에 26.1 클라이언트를 연결하므로 ViaVersion/ViaBackwards 5.12.2를 사본에만 넣습니다. 비공개 .build/ui-rcon.cjs와 비공개 암호 파일은 시험 전용입니다. 환경 변수 INTEGRATION_TEST_COPY=YES가 없으면 클라이언트 검사를 시작하지 않습니다.

setup 단계는 새로운 합성 계정의 농부 숙련도·수확·돈·도감 기록을 준비합니다. before/after 단계는 준비한 상태를 검사합니다. 재실행할 때 실제 플레이어 이름을 사용하거나 데이터 초기화를 하지 마세요. 현재 실행 증거는 배포 폴더의 runtime-before.json과 runtime-after.json에 있습니다.

DisplayProfileTest는 실제 게임이 아닌 순수 Java 계약 검사입니다. installer.py는 --server에 읽기 전용 원본 JAR가 있는 경로를 받고 .build의 합성 서버 폴더에서 사전 검사를 합니다. 정상 종료 스텁이 실행되지 않았는지 검증합니다. 배포 도구 실제 전체 백업·복원은 별도 deployment_audit.py와 post_deploy.py 집계 증거로 구분합니다.

실제 클라이언트 화면 확인은 별도 -Xint 시험 프로필로 수행했습니다. 프로토콜 대역 검사, 리소스팩 준비 상태를 주입한 농사 시험 자료, 실제 게임 확인과 고정 이동 실수는 검증결과.md에 구분해 기록했습니다.
