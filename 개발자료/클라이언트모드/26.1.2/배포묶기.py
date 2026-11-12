#!/usr/bin/env python3
from pathlib import Path
import json,zipfile,hashlib,re,urllib.parse
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());OUT=ROOT/'설치파일/05_클라이언트모드/26.1.2';CACHE=ROOT/'.build/클라이언트모드-26.1.2';SHARED=Path('/workspace/shared');SHARED.mkdir(exist_ok=True)

def digest(data):return hashlib.sha256(data).hexdigest()
def zipped(path,entries):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   i=zipfile.ZipInfo(name,(2026,11,13,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o644<<16;z.writestr(i,data)
manifest=json.loads((OUT/'모드목록.json').read_text());stat=json.loads((OUT/'정적검사.json').read_text());download=json.loads((OUT/'다운로드검증.json').read_text());assert stat['검사수']>1000 and download['공식GitHub실제다운로드_6종'];assert download['실행스크립트SHA256']==digest((OUT/'모드받기.ps1').read_bytes());assert download['목록SHA256']==digest((OUT/'모드목록.json').read_bytes());assert download['한국어팩SHA256']==digest((OUT/'모드한글팩-26.1.2-1.0.0.zip').read_bytes())
entries={p.relative_to(OUT).as_posix():p.read_bytes() for p in OUT.rglob('*') if p.is_file() and '받은파일' not in p.parts and p.name not in {'.gitignore','README.md','파일검증.sha256'} and (p.suffix!='.zip' or p.name.startswith('모드한글팩-'))}
assert not any(n.endswith('.jar') for n in entries)
entries['파일검증.sha256']=''.join(digest(data)+'  '+name+'\n' for name,data in sorted(entries.items())).encode('utf-8')
archive=OUT/'패브릭-26.1.2-한글준비-1.0.0.zip';zipped(archive,entries)
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 for line in z.read('파일검증.sha256').decode().splitlines():
  sha,name=line.split('  ',1);assert digest(z.read(name))==sha
 for name in ['설치안내.md','확인대기목록.md']:
  for link in re.findall(r'\]\(([^\n)]+)\)',z.read(name).decode()):
   if not re.match(r'\w+:|#',link):assert urllib.parse.unquote(link) in z.namelist(),(name,link)
# Private copy of the seven actually downloaded original JARs for the user, kept outside Git.
private=dict(entries)
for row in manifest['모드']:
 if row['상태'] not in ['기본구성','필수모드대기']:continue
 file=CACHE/'받은모드'/row['공식파일'];data=file.read_bytes();assert digest(data)==row['SHA256'];prefix='mods/' if row['상태']=='기본구성' else '추가파일-필수모드대기/';private[prefix+row['한글파일']]=data
private['먼저읽기.md']='# 실제로 받은 26.1.2 Fabric 모드\n\n이 ZIP에는 공식 주소에서 실제로 받은 JAR 7개가 들어 있습니다.\n\n1. **mods**의 기본 6개를 새 Minecraft 26.1.2 Fabric 프로필의 mods 폴더에 넣습니다.\n2. **resourcepacks**의 한국어 팩을 게임의 resourcepacks 폴더에 넣고, 게임 언어를 한국어로 바꾼 뒤 팩을 켭니다.\n3. **추가파일-필수모드대기**의 Visuality는 Cloth Config를 확보하기 전까지 mods에 넣지 마세요.\n4. 기존 1.20.1 모드는 따로 보관하세요. 새 파일과 섞지 않습니다.\n\nJava 25 이상이 필요합니다. 검사한 로더 기준은 Fabric Loader 0.19.5입니다.\n기본 구성: Fabric API / ShulkerBoxTooltip / AppleSkin / EntityCulling / Not Enough Animations / Mod Menu.\n\nSodium은 공식 26.1.2 버전을 확인하고 설정 문구를 번역했지만 본체를 아직 받지 못했습니다.\nIndium은 새 Sodium과 함께 쓰지 않습니다. 나머지 미확인 항목은 확인대기목록.md에 있습니다.\n24종 전체가 준비된 상태는 아닙니다. 실제 Minecraft 실행은 미검증입니다.\n\n모든 JAR의 내용은 원본과 같고 이름만 한글로 바꿨습니다.\nGitHub 공개 묶음은 원본 JAR를 직접 받는 도구와 MRPACK 방식으로 제공합니다.\n기존 게임·서버 데이터는 변경하지 않았습니다.\n'.encode('utf-8')
private['파일검증.sha256']=''.join(digest(data)+'  '+name+'\n' for name,data in sorted(private.items()) if name!='파일검증.sha256').encode('utf-8')
personal=SHARED/'마인크래프트-26.1.2-확보7종-한글.zip';zipped(personal,private)
with zipfile.ZipFile(personal) as z:
 assert z.testzip() is None and len([n for n in z.namelist() if n.endswith('.jar')])==7
 for line in z.read('파일검증.sha256').decode().splitlines():
  sha,name=line.split('  ',1);assert digest(z.read(name))==sha
public=[archive,OUT/'모드한글팩-26.1.2-1.0.0.zip',OUT/'패브릭-26.1.2-한글기본6종.mrpack'];(OUT/'파일검증.sha256').write_text(''.join(digest(f.read_bytes())+'  '+f.name+'\n' for f in public),encoding='utf-8')
print('PASS public download bundle and checksums:',archive.relative_to(ROOT));print('Private seven original JARs:',personal)
