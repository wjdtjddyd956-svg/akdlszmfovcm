#!/usr/bin/env python3
"""Exercise Korean payload mapping with the unchanged production Java installer."""
from pathlib import Path
import json,tempfile,zipfile,hashlib,subprocess,shutil,sqlite3,os
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());OUT=ROOT/'설치파일/01_마을상점';JAVA=Path('/workspace/toolchains/jdk-25.0.5.0.3/bin/java');checks=0

def check(value):
 global checks
 checks+=1
 assert value

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
with tempfile.TemporaryDirectory(prefix='korean-payload-') as temp:
 base=Path(temp);bundle=base/'한글 설치 파일';bundle.mkdir()
 with zipfile.ZipFile(OUT/'도시생활-마을상점-한글설치-1.0.0.zip') as z:
  check(z.testzip() is None);z.extractall(bundle)
  for line in z.read('파일검증.sha256').decode().splitlines():
   digest,name=line.split('  ',1);check(hashlib.sha256(z.read(name)).hexdigest()==digest)
 manifest=json.loads((bundle/'설치목록.json').read_text());check(len(manifest['플러그인'])==6)
 # Same fixed ASCII staging contract used by the PowerShell wrapper, exercised against the real Java installer.
 stage=base/'임시 설치 준비';stage.mkdir();lines=[]
 for entry in manifest['플러그인']:
  source=bundle/entry['파일'];check(sha(source)==entry['SHA256']);check(any('\uac00'<=c<='\ud7a3' for c in source.stem));target=stage/entry['설치파일명'];shutil.copyfile(source,target);lines.append(target.name+'='+sha(target))
 (stage/'SHA256.properties').write_text('\n'.join(lines)+'\n',encoding='ascii')
 tool=bundle/'설치도구.jar';check(sha(tool)==manifest['설치도구']['SHA256'])
 def run(*args,success=True):
  r=subprocess.run([str(JAVA),'-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-jar',str(tool),*map(str,args)],text=True,encoding='utf-8',capture_output=True)
  if (r.returncode==0)!=success:raise AssertionError(r.stdout+r.stderr)
  check((r.returncode==0)==success);return r
 server=base/'기존 한글 서버';plugins=server/'plugins';plugins.mkdir(parents=True);(server/'server.properties').write_text('online-mode=true\n');world=server/'world';world.mkdir();(world/'session.lock').touch();(world/'월드데이터.mca').write_bytes(b'keep world bytes')
 for old,new in [('releases/ui-update/1.2.1/KoreanEconomy-2.0.2.jar','이전경제.jar'),('개발자료/배포도구/경제야생/vendor/Vault.jar','Vault.jar')]:shutil.copyfile(ROOT/old,plugins/new)
 dbfile=plugins/'KoreanEconomy/생활경제.db';dbfile.parent.mkdir()
 with sqlite3.connect(dbfile) as db:db.execute('CREATE TABLE keep_data(value TEXT)');db.execute("INSERT INTO keep_data VALUES('keep economy, farm and title data')")
 (plugins/'Essentials').mkdir();(plugins/'Essentials/잔액과집.yml').write_text('잔액: 123456\n집: 유지\n')
 preserved={p.relative_to(server).as_posix():sha(p) for p in server.rglob('*') if p.is_file() and p.suffix!='.jar' and p.name!='session.lock'}
 run('마을설치',server,stage,success=False)
 run('마을설치',server,stage,'--서버종료확인')
 for entry in manifest['플러그인']:check(sha(plugins/entry['설치파일명'])==entry['SHA256'])
 for name,digest in preserved.items():check(sha(server/name)==digest)
 backup=next((server/'tycoon-server-backups').glob('*.zip'));run('검증',backup)
 mtimes={p.name:p.stat().st_mtime_ns for p in plugins.glob('*.jar')};run('마을설치',server,stage,'--서버종료확인');check(mtimes=={p.name:p.stat().st_mtime_ns for p in plugins.glob('*.jar')})
 old=next(stage.glob('*.jar'));old.write_bytes(b'broken');current={p.name:sha(p) for p in plugins.glob('*.jar')};run('마을설치',server,stage,'--서버종료확인',success=False);check(current=={p.name:sha(p) for p in plugins.glob('*.jar')})
 run('복원',server,backup,'--서버종료확인');check((plugins/'이전경제.jar').is_file());check(not (plugins/'KoreanCivic-1.0.0.jar').exists())
 for name,digest in preserved.items():check(sha(server/name)==digest)
 # Encoding checks are separate from executing Windows PowerShell/cmd.
 check((bundle/'설치하기.ps1').read_bytes().startswith(b'\xef\xbb\xbf'));check('chcp 65001' in (bundle/'설치하기.cmd').read_text());check('설치하기.ps1' in (bundle/'설치하기.cmd').read_text())
report={'검사수':checks,'한글ZIP_해시검증':True,'한글JAR_원본동일':True,'실제Java설치기_설치_재설치_손상거절_백업_복원':True,'한글서버경로_기존DB_월드_잔액파일보존':True,'PowerShell_실행':False,'CMD_Windows_실행':False,'Windows_운영서버_적용':False,'설명':'PowerShell 실행기 자체는 미실행. 한글 파일의 임시 원래 이름 준비 방식과 실제 Java 설치기·파일 시스템을 검사함.','플랫폼':os.name}
(OUT/'설치준비검증.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PASS',checks,'Korean bundle / actual Java installer / backup and restore checks')
