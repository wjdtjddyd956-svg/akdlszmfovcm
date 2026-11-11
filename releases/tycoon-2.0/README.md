# 생활 타이쿤 2.0.0

[통합 설치 ZIP 다운로드](https://github.com/wjdtjddyd956-svg/akdlszmfovcm/raw/refs/heads/main/releases/tycoon-2.0/생활타이쿤-통합설치-2.0.0.zip)

기존 생활서버 1.2.0용 업그레이드입니다. NPC 상점·직업 적립을 유지하고 요리 10종·강화·새 임무·한글 메뉴를 추가합니다. 기존 GriefPrevention·Prism·전체 자동 백업을 사용하며 직업·임무·매입 기록을 한 번 가져옵니다.

1. `ServerStop.cmd`로 기존 서버와 백업 관리자를 종료합니다.
2. ZIP을 서버 폴더 바깥에 풀고 `설치하기.cmd`를 실행합니다.
3. 실제 서버 폴더를 입력합니다. 파일 검증과 전체 백업 후 교체하며, 이후 기존 `ServerStart.cmd`로 실행합니다.

[자세한 설치·운영 안내](../../tycoon/시작안내.md) · [검증 범위](../../tycoon/개발검증.md) · [검증 결과 JSON](validation.json)

실제 Paper API 컴파일, MockBukkit 통합 31개, 실제 SQLite 도메인·이전 121개, 설치·복원 25개 검사 통과. 새 2.0은 Windows·실제 서버 접속을 이 환경에서 검증하거나 사용자 PC에 원격 적용한 상태가 아닙니다. 1.2 운영 검증과 구분합니다. EssentialsX는 기존 26.3 지원 같은 버전을 재사용하고 필요할 때만 공식 CI에서 받습니다.

수동 설치용 JAR 4개와 선택 리소스팩도 이 폴더에 있습니다. 단독 JAR보다 전체 백업·검증을 포함한 통합 설치를 권장합니다. 체크섬은 `SHA256SUMS.txt`입니다.
