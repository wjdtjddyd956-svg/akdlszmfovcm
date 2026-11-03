# KoreanCommands

WorldEdit과 WorldGuard의 주요 명령어를 한글로 입력할 수 있게 해 주는 Paper 플러그인입니다.
영문 명령어를 실행 직전에 원래 명령어로 변환하므로 기존 플러그인 파일을 다시 수정하지 않습니다.

## 설치

1. 서버를 `stop`으로 종료합니다.
2. WorldEdit과 WorldGuard가 설치된 서버의 `plugins` 폴더에 `KoreanCommands-1.0.0.jar`를 넣습니다.
3. 서버를 다시 실행합니다. `/reload`는 사용하지 마세요.
4. `/한글명령어`로 간단한 사용 예를 확인합니다.

## 예시

```text
//도끼                         → //wand
//채우기 돌                    → //set stone
//채우기 50%돌,50%흙           → //set 50%stone,50%dirt
//바꾸기 흙 잔디블록           → //replace dirt grass_block
/영역 생성 마을               → /rg define 마을
/영역 정보 마을               → /rg info 마을
/영역 설정 마을 피브이피 거부 → /rg flag 마을 pvp deny
```

명령어에 사용되는 블록 이름 전체를 번역한 것은 아닙니다. 등록되지 않은 블록 ID와 플래그는 영문으로 입력하면 됩니다.
기존 영문 명령어도 그대로 사용할 수 있습니다.
한글 명령어, 블록 이름, 영역 플래그는 입력 중 Tab 키로 자동완성할 수 있습니다.

## 빌드

JDK 21 이상에서 다음 명령을 실행합니다.

```bash
./build.sh
```

빌드 결과는 `build/libs/KoreanCommands-1.0.0.jar`에 생성됩니다. `tools/stubs`의 최소 API 정의는 컴파일에만 사용되며 결과 JAR에는 포함되지 않습니다.
