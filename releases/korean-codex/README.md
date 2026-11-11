# 아이템 도감·수집 업적 1.0.0

- [플러그인 JAR 다운로드](https://github.com/wjdtjddyd956-svg/akdlszmfovcm/raw/refs/heads/main/releases/korean-codex/KoreanCodex-1.0.0.jar)
- [설치 파일·보너스 표·설명서·소스 묶음](https://github.com/wjdtjddyd956-svg/akdlszmfovcm/raw/refs/heads/main/releases/korean-codex/KoreanCodex-설치묶음-1.0.0.zip)
- [설치·사용 안내](설치·사용안내.md) · [아이템 80종 실제 보너스](아이템별보너스.md) · [검증 결과](validation.json)

80종 도감·12개 업적. 기본 아이템 **1개 소비 후 영구 등록**하며 고급·희귀·전설은 같은 분야 일반 아이템의 2·4·8배 효과를 줍니다. 전체 완성 상한은 이동 3%, 채굴 6%, 체력 +2HP(하트 1칸), 공격력 +0.5, 방어력 +1, 공격 속도 2%, 넉백 저항 2%p, 행운 +0.5입니다.

기존 `ServerStop.cmd`로 서버와 백업 관리자를 종료하고 전체 백업 후 **JAR 한 개만 plugins에 추가**하세요. 다시 시작한 뒤 `/도감`, `/업적`, `/도감효과` 또는 쉬프트+F 메뉴에서 사용합니다. 기존 경제·땅·집·직업 자료를 유지하며 추가 모드·리소스팩은 필요하지 않습니다.

실제 Paper 26.3 API 컴파일, MockBukkit·SQLite 검사 26개와 배포 JAR에 내장한 SQLite의 재시작·백업 검사를 통과했습니다. MockBukkit의 플레이어 NBT 저장은 실패 주입으로 대체하고 누락된 채굴 기본 속성만 테스트에서 준비했습니다. **이 새 플러그인의 실제 Windows 설치·Minecraft 접속·전투/채굴 시간은 미검증**입니다.
