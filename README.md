# Minecraft RPG 서버 자료

소규모 Paper 기반 RPG 서버를 만들면서 사용하는 설정과 결과물을 보관합니다.

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
