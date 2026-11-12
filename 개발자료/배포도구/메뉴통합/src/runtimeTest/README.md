# 실제 Paper UI 회귀 검사

`UIProbe.java`는 localhost127.0.0.1:25566, offline-mode 복제본에만 켜지는 RCON 전용 계측입니다. 운영 서버/설치 ZIP의 plugins에 넣지 마세요. 실제 API238로 컴파일해 `main: UIProbe`, `depend: [KoreanCodex, CityTextures]`, `commands: {uiprobe: {}}`로 테스트용 JAR를 만듭니다. 기존 CodexProbe/FarmProbe도 복제본에서만 사용합니다.

JS 환경: `UI_ISOLATED_TEST_COPY=YES`, `UI_TEST_MODULES`(mineflayer 설치 폴더), `UI_TEST_RCON_SCRIPT`(개인 RCON 클라이언트 경로), `UI_TEST_STATE`(검사 결과 JSON 경로). 암호와 RCON 클라이언트 파일은 공개 소스에 포함하지 않습니다. RCON은4KB 초과 응답을 이어 읽고 별도 list 응답으로 종료 경계를 확인해야 합니다.

`node ui-runtime.cjs features` → 서버 정상 종료/재시작 → `after` → `limits` 순서입니다. 원본 복제본의 합성 CodexFull80종 완성 fixture를 재사용하며, 새 복제본에서는 먼저 구성하세요. 재실행은 새로운 합성 계정 또는 초기 복제본이 필요합니다. GP의 기존 IP제한을 낮추지 않고 계정을 순차 종료합니다.

팩 요청/거절은 실제 패킷입니다. 성공/실패 상태 재현 일부는 UIProbe 이벤트 주입이며 다운로드/렌더 검사로 주장하지 않습니다. 별도로 Minecraft26.3 실제 클라이언트에서 쉬프트+F부터 등록·진행률·업적·효과·복귀를 클릭해 확인했습니다. 버전26.1 봇은 테스트 전용 어댑터를 통해 실제 Paper26.3에 접속하며 운영 서버에는 어댑터를 추가하지 않습니다.
