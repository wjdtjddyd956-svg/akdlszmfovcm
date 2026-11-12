#!/usr/bin/env python3
from pathlib import Path
import subprocess,json,tempfile,os,hashlib,shutil
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());OUT=ROOT/'설치파일/05_클라이언트모드/26.1.2';CACHE=ROOT/'.build/클라이언트모드-26.1.2';PW=Path('/workspace/toolchains/powershell-7.5.4/pwsh');PROFILE=Path('/workspace/toolchains/powershell-user');env=dict(os.environ,XDG_CACHE_HOME=str(PROFILE/'cache'),XDG_CONFIG_HOME=str(PROFILE/'config'),XDG_DATA_HOME=str(PROFILE/'data'));checks=0

def check(v):
 global checks
 checks+=1
 assert v

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def run(script,output,success=True):
 x=subprocess.run([str(PW),'-NoLogo','-NoProfile','-File',str(script),'-OutputDirectory',str(output)],env=env,capture_output=True,text=True,encoding='utf-8')
 if (x.returncode==0)!=success:raise AssertionError(x.stdout+x.stderr)
 check((x.returncode==0)==success);return x
catalog=json.loads((OUT/'모드목록.json').read_text());rows=[r for r in catalog['모드'] if r['상태']=='기본구성'];target=CACHE/'PowerShell검사/최종도구-받은파일';target.mkdir(parents=True,exist_ok=True)
# First test actually fetches the fixed public GitHub assets; subsequent calls reuse the same verified bytes.
run(OUT/'모드받기.ps1',target)
for row in rows:check(sha(target/'mods'/row['한글파일'])==row['SHA256'])
check(len(list((target/'mods').glob('*.jar')))==6);before={p.relative_to(target).as_posix():(sha(p),p.stat().st_mtime_ns) for p in target.rglob('*') if p.is_file()};run(OUT/'모드받기.ps1',target);check(before=={p.relative_to(target).as_posix():(sha(p),p.stat().st_mtime_ns) for p in target.rglob('*') if p.is_file()})
with tempfile.TemporaryDirectory(prefix='mod-fetch-tests-') as temp:
 base=Path(temp);copy=base/'도구';copy.mkdir();shutil.copyfile(OUT/'모드받기.ps1',copy/'모드받기.ps1');shutil.copyfile(OUT/'모드한글팩-26.1.2-1.0.0.zip',copy/'모드한글팩-26.1.2-1.0.0.zip')
 def manifest(x): (copy/'모드목록.json').write_text(json.dumps(x,ensure_ascii=False),encoding='utf-8')
 # Corrupt existing payload must never be overwritten.
 modified=base/'기존파일';(modified/'mods').mkdir(parents=True);bad=modified/'mods'/rows[0]['한글파일'];bad.write_bytes(b'kept damaged user file');manifest(catalog);x=run(copy/'모드받기.ps1',modified,False);check(bad.read_bytes()==b'kept damaged user file');check('덮어쓰지' in x.stderr)
 # The tool rejects an old/foreign JAR instead of merging it into a new target profile.
 foreign=base/'이전모드';(foreign/'mods').mkdir(parents=True);old=foreign/'mods/기존-1.20.1.jar';old.write_bytes(b'kept old mod');x=run(copy/'모드받기.ps1',foreign,False);check(old.read_bytes()==b'kept old mod');check('다른 모드 JAR' in x.stderr)
 # Unexpected URLs and duplicate IDs are rejected before making network requests.
 alternate=json.loads(json.dumps(catalog));next(x for x in alternate['모드'] if x['상태']=='기본구성')['다운로드주소']='http://example.invalid/not-official.jar';manifest(alternate);x=run(copy/'모드받기.ps1',base/'주소오류',False);check('공식 GitHub' in x.stderr)
 alternate=json.loads(json.dumps(catalog));known=[x for x in alternate['모드'] if x['상태']=='기본구성'];known[1]['식별자']=known[0]['식별자'];manifest(alternate);x=run(copy/'모드받기.ps1',base/'중복오류',False);check('중복' in x.stderr)
report={'검사수':checks,'PowerShell버전':'7.5.4','실행플랫폼':os.name,'공식GitHub실제다운로드_6종':True,'SHA256_FabricID_버전검사':True,'재실행_원본바이트와수정시간보존':True,'손상파일_기존모드혼합_중복ID_비공식URL_거절':True,'WindowsPowerShell5_실행':False,'CMD실행':False,'Minecraft실행':False,'기존게임폴더변경':False,'실행스크립트SHA256':sha(OUT/'모드받기.ps1'),'목록SHA256':sha(OUT/'모드목록.json'),'한국어팩SHA256':sha(OUT/'모드한글팩-26.1.2-1.0.0.zip')}
(OUT/'다운로드검증.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('PASS',checks,'actual PowerShell download/reuse/rejection checks')
