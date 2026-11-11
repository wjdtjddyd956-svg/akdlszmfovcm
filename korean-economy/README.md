# 한글 생활 타이쿤 2.0

Paper 26.3에서 기존 Vault 잔액을 공유하는 자체 플러그인입니다. NPC 상점·일일 보상에 농부/광부/낚시꾼, 요리 10종, 도구 강화, 일일 임무, 청크 땅 보호, 기록 조회·일반 블록 복구·SQLite 백업을 통합했습니다. 한글 채팅 변환 기능은 없습니다.

[설치·명령어·운영 안내](../tycoon/시작안내.md) · [개발·검증 범위](../tycoon/개발검증.md) · [최신 배포](../releases/tycoon-2.0/)

생산 JAR는 사용자 서버와 같은 커밋의 실제 Paper 26.3 API로 컴파일합니다. `python3 korean-economy/build.py` 또는 `python3 tools/build_tycoon.py`를 사용하세요. 기존 fixture 기반 EconomyTest/NpcTest는 1.1 동작의 이력이며 2.0 생산 검증에 사용하지 않습니다. 새 도메인 검사와 실제 MockBukkit 통합 검사를 사용합니다.

기존 `KoreanEconomy` 플러그인 이름·NPC 영구 키·보상 기록을 유지합니다. 새 데이터는 `plugins/KoreanEconomy/생활경제.db`에 저장합니다. Vault 경제 저장소와 플레이어 NBT 사이의 원자성을 주장하지 않으며 불확실한 거래를 확인 대기로 남깁니다. Windows 서버에 원격 설치되거나 실제 플레이 접속이 검증된 상태는 아닙니다.
