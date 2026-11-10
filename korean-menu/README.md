# KoreanMenu

경제 야생 서버에서 쉬프트+F 또는 `/메뉴`로 여는 한글 메뉴 1.1입니다. `/kmenu`도 기존 별칭으로 유지합니다. F키를 변경했다면 **쉬프트+보조 손 교체 키**를 사용합니다.

잔액, 송금 안내, 서버 상점, 일일 보상, 유저 거래소 안내, 땅 보호 도움말, 집 저장 확인·이동, 스폰 이동을 제공합니다. 서버 상점·보상은 KoreanEconomy, 한글 생활 명령은 KoreanCommands에 연결합니다. 직업·퀘스트는 포함하지 않습니다. 명령은 클릭한 플레이어 권한으로 실행하며 기존 요금·대기 시간을 따릅니다. 기본 집 이름은 내부적으로 `default`입니다.

`koreanmenu.use`는 기본적으로 모두에게 허용합니다. `koreanmenu.basics`는 기본 잔액·송금·집·스폰·TPA 및 일반 유저 상점 생성 권한만 기본 허용합니다. 돈 생성·관리자 상점·타인 땅 관리·운영자 권한을 주지 않습니다. 권한 관리 플러그인에서 기본 권한을 거부해 서버 정책에 맞게 제한할 수 있습니다.

## 빌드와 검증

```bash
python3 korean-menu/build.py
```

기본 빌드는 Java 21과 로컬 최소 API 시그니처 fixture를 사용합니다. 실제 서버 모의 구현이 아니며 fixture는 JAR에 포함하지 않습니다. 검사 후 `build/libs/KoreanMenu-1.1.0.jar`를 생성합니다. 공식 Paper 소스에서 InventoryView 등이 인터페이스임을 확인했습니다.

실제 Bukkit/Paper API JAR를 확보한 환경에서는 다음 방식으로 추가 컴파일할 수 있습니다. API의 전이 의존성이 필요하면 클래스 경로에 함께 지정합니다.

```bash
BUKKIT_API_JAR='/path/to/paper-api.jar:/path/to/dependencies/*' python3 korean-menu/build.py
```

현재 환경에서는 Paper Maven 저장소 접근이 차단되어 실제 API JAR로 컴파일하거나 Paper 26.3 서버에서 실행하지 못했습니다. 오프라인 검사는 실제 서버 호환성을 보장하지 않습니다. 설치 뒤 보조 손 아이템 유지, 메뉴 클릭·드래그, 집 저장/이동, 스폰 이동을 먼저 확인하세요.

## 설치

서버를 `stop`으로 종료한 뒤 JAR를 `plugins`에 넣고 다시 시작합니다. 기존 WorldEdit·WorldGuard·KoreanCommands를 교체할 필요가 없습니다. 메뉴 자체는 연결 플러그인 없이도 열리고, 없는 기능은 안내를 표시합니다.
