#!/usr/bin/env python3
"""Offline installer integration using the actual released JARs, on a disposable server tree."""
from pathlib import Path
import contextlib,tempfile,subprocess,shutil,zipfile,hashlib,json,sqlite3,os
ROOT=Path(__file__).resolve().parents[1];OUT=Path(os.environ.get('CIVIC_RELEASE_DIR',str(ROOT/'releases/civic-update/1.0.0')));JAVA=Path(os.environ.get('UI_JAVA','/workspace/toolchains/jdk-25.0.5.0.3/bin/java'));assertions=0
def check(value):
 global assertions
 assertions+=1
 assert value
with tempfile.TemporaryDirectory(prefix='ui-installer-') as temp:
 base=Path(temp);server=base/'한글 서버 폴더';plugins=server/'plugins';plugins.mkdir(parents=True);(server/'server.properties').write_text('online-mode=true\n');world=server/'world';world.mkdir();(world/'session.lock').touch();(world/'region.mca').write_bytes(b'kept-world-test-data');(plugins/'Essentials').mkdir();(plugins/'Essentials/userdata.yml').write_text('money: 123456\nhome: keep\n')
 for name,source in {'기존경제-임의이름.jar':ROOT/'releases/tycoon-2.0.1/KoreanEconomy-2.0.1.jar','기존도감.jar':ROOT/'releases/korean-codex/1.0.1/KoreanCodex-1.0.1.jar','Vault.jar':Path(os.environ.get('UI_VAULT',str(ROOT/'survival-pack/vendor/Vault.jar'))),'이전메뉴.jar':ROOT/'releases/tycoon-2.0.1/KoreanMenu-2.0.0.jar','이전도시팩.jar':ROOT/'releases/city-resourcepack/CityTextures-1.0.0.jar'}.items():shutil.copyfile(source,plugins/name)
 (plugins/'CityTextures').mkdir();(plugins/'CityTextures/config.yml').write_text('자동요청: false\n팩-주소: https://example.com/kept.zip\n')
 for data in ['KoreanEconomy/생활경제.db','KoreanCodex/도감.db']:
  file=plugins/data;file.parent.mkdir(parents=True,exist_ok=True)
  with contextlib.closing(sqlite3.connect(file)) as db:db.execute('CREATE TABLE keep_data(id TEXT PRIMARY KEY,value TEXT)');db.execute('INSERT INTO keep_data VALUES(?,?)',('existing','keep-me'));db.commit()
 before={p.relative_to(server).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in server.rglob('*') if p.is_file() and p.suffix!='.jar' and p.name!='session.lock'}
 payload=base/'payload';payload.mkdir();names=['KoreanEconomy-2.0.3.jar','KoreanFarming-1.0.2.jar','KoreanCodex-1.2.1.jar','KoreanMenu-2.2.0.jar','CityTextures-1.2.0.jar','KoreanCivic-1.0.0.jar'];hashes=''
 for name in names:shutil.copyfile(OUT/name,payload/name);hashes+=name+'='+hashlib.sha256((payload/name).read_bytes()).hexdigest()+'\n'
 (payload/'SHA256.properties').write_text(hashes)
 def run(*args,ok=True):
  r=subprocess.run([str(JAVA),'-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-jar',str(OUT/'CivicInstaller.jar'),*map(str,args)],capture_output=True,text=True,encoding='utf-8');
  if (r.returncode==0)!=ok:raise AssertionError(r.stdout+r.stderr)
  check((r.returncode==0)==ok)
  return r
 run('마을설치',server,payload,ok=False);check((plugins/'기존경제-임의이름.jar').is_file())
 # Verify actual session file locking prevents writes.
 with (world/'session.lock').open('rb+') as lock:
  if os.name=='nt':
   import msvcrt;msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
  else:
   import fcntl;fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
  run('마을설치',server,payload,'--서버종료확인',ok=False)
 run('마을설치',server,payload,'--서버종료확인')
 check(not (plugins/'기존경제-임의이름.jar').exists());check(not (plugins/'기존도감.jar').exists());check(len(list(plugins.glob('*.jar')))==7)
 for name in names:check(hashlib.sha256((plugins/name).read_bytes()).digest()==hashlib.sha256((OUT/name).read_bytes()).digest())
 for path,digest in before.items():check(hashlib.sha256((server/path).read_bytes()).hexdigest()==digest)
 backup=next((server/'tycoon-server-backups').glob('*.zip'));run('검증',backup)
 with zipfile.ZipFile(backup) as z:check('plugins/기존경제-임의이름.jar' in z.namelist());check('plugins/KoreanCodex/도감.db' in z.namelist());check('world/region.mca' in z.namelist())
 prior_mtimes={p.name:p.stat().st_mtime_ns for p in plugins.glob('*.jar')}
 run('마을설치',server,payload,'--서버종료확인');check(len(list(plugins.glob('*.jar')))==7);check(prior_mtimes=={p.name:p.stat().st_mtime_ns for p in plugins.glob('*.jar')})
 # Corrupt payload must leave all existing JARs byte-for-byte unchanged.
 installed={p.name:p.read_bytes() for p in plugins.glob('*.jar')};(payload/names[1]).write_bytes(b'corrupt');run('마을설치',server,payload,'--서버종료확인',ok=False);check(installed=={p.name:p.read_bytes() for p in plugins.glob('*.jar')});shutil.copyfile(OUT/names[1],payload/names[1])
 # Original whole-server backup restores the real old JARs and original data.
 run('복원',server,backup,'--서버종료확인');check((plugins/'기존경제-임의이름.jar').is_file());check(not (plugins/'KoreanFarming-1.0.2.jar').exists())
 for path,digest in before.items():check(hashlib.sha256((server/path).read_bytes()).hexdigest()==digest)
 # Unsupported newer core is rejected before any file/data change.
 with zipfile.ZipFile(plugins/'기존경제-임의이름.jar','w') as z:z.writestr('plugin.yml','name: KoreanEconomy\nversion: 9.0.0\n')
 prior={p.name:p.read_bytes() for p in plugins.glob('*.jar')};run('마을설치',server,payload,'--서버종료확인',ok=False);check(prior=={p.name:p.read_bytes() for p in plugins.glob('*.jar')})
report={'assertions':assertions,'actual_release_jars_used':True,'real_filesystem_sqlite_bytes_hashes_locks_backup_restore_tested':True,'keeps_economy_codex_data':True,'keeps_existing_city_config':True,'repeat_install_does_not_duplicate_jars':True,'corruption_and_newer_version_rejected_before_mutation':True,'platform':os.name,'powershell_or_windows_launcher_tested':False,'real_minecraft_server_started':False}
(OUT/'installer-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('PASS',assertions,'civic installer checks against actual artifact files')
