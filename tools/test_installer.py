#!/usr/bin/env python3
"""Real file/ZIP/hash/restore tests. Fixture plugin metadata is never a runtime validation."""
from pathlib import Path
import subprocess,tempfile,zipfile,hashlib,json,fcntl
ROOT=Path(__file__).resolve().parents[1];JAVA=Path('/workspace/toolchains/jdk-25.0.5.0.3/bin/java');JAVAC=JAVA.with_name('javac');OUT=ROOT/'releases/tycoon-2.0'
assertions=0
def check(value):
 global assertions
 assertions+=1
 assert value
with tempfile.TemporaryDirectory(prefix='tycoon-installer-') as tmp:
 base=Path(tmp);classes=base/'classes';classes.mkdir()
 subprocess.run([str(JAVAC),'--release','21','-encoding','UTF-8','-d',str(classes),str(ROOT/'tools/installer/PackInstaller.java')],check=True)
 def run(*args,ok=True):
  r=subprocess.run([str(JAVA),'-cp',str(classes),'PackInstaller',*map(str,args)],text=True,capture_output=True)
  check((r.returncode==0)==ok)
  if ok and r.returncode:raise RuntimeError(r.stdout+r.stderr)
  return r
 server=base/'한글 서버 폴더';server.mkdir();(server/'server.properties').write_text('online-mode=true\n');plugins=server/'plugins';plugins.mkdir();data=plugins/'Essentials';data.mkdir();(data/'userdata.yml').write_text('money: 123456\nhome: keep\n');world=server/'world';world.mkdir();(world/'region.mca').write_bytes(b'original world data');(world/'session.lock').touch()
 run('백업',server,'--서버종료확인');backup=next((server/'tycoon-server-backups').glob('*.zip'));run('검증',backup)
 with zipfile.ZipFile(backup) as z:check('world/session.lock' not in z.namelist());check('.tycoon-install.lock' not in z.namelist())
 (data/'userdata.yml').write_text('changed');(server/'extra.txt').write_text('remove on complete restore');run('복원',server,backup,'--서버종료확인');check((data/'userdata.yml').read_text().startswith('money: 123456'));check(not (server/'extra.txt').exists());check((world/'region.mca').read_bytes()==b'original world data')
 bad=base/'bad.zip'
 with zipfile.ZipFile(bad,'w') as z:z.writestr('../escape.txt',b'bad');z.writestr('TYCOON-SHA256.properties','../escape.txt='+hashlib.sha256(b'bad').hexdigest())
 run('복원',server,bad,'--서버종료확인',ok=False);check(not (base/'escape.txt').exists())
 corrupt=base/'corrupt.zip'
 with zipfile.ZipFile(corrupt,'w') as z:z.writestr('test.txt',b'bad');z.writestr('TYCOON-SHA256.properties','test.txt='+'0'*64)
 run('검증',corrupt,ok=False)
 (world/'session.lock').touch()
 with (world/'session.lock').open('rb+') as lock:
  fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);run('백업',server,'--서버종료확인',ok=False)
 payload=base/'payload';payload.mkdir();hashes=[]
 names=['KoreanEconomy','KoreanMenu','KoreanCommands','KoreanPackSetup','Vault','Essentials','EssentialsSpawn','QuickShop-Hikari']
 for name in names:
  f=payload/(name+'-test.jar')
  with zipfile.ZipFile(f,'w') as z:
   z.writestr('plugin.yml','name: '+name+'\nversion: test-fixture-only\n')
   if name=='Essentials':z.writestr('com/earth2me/essentials/utils/VersionUtil.class',b'test-fixture-v26_3_R01')
  hashes.append(f.name+'='+hashlib.sha256(f.read_bytes()).hexdigest())
 (payload/'SHA256.properties').write_text('\n'.join(hashes))
 # Existing jar has a different filename; replacement is by actual plugin name.
 with zipfile.ZipFile(plugins/'old-random.jar','w') as z:z.writestr('plugin.yml','name: KoreanMenu\nversion: old\n')
 run('설치',server,payload,'--서버종료확인');check(not (plugins/'old-random.jar').exists());check(len(list(plugins.glob('*.jar')))==8);check((data/'userdata.yml').read_text().startswith('money: 123456'));check((world/'region.mca').read_bytes()==b'original world data')
 run('설치',server,payload,'--서버종료확인');check(len(list(plugins.glob('*.jar')))==8)
 legacy=data.parent/'KoreanEconomy'/'생활기록';legacy.mkdir(parents=True,exist_ok=True);pending=legacy/'pending-user.properties';pending.write_text('pending.id=unresolved\njob.owed=99\n');run('설치',server,payload,'--서버종료확인',ok=False);check(pending.read_text()=='pending.id=unresolved\njob.owed=99\n');pending.unlink()
 spawn=payload/'EssentialsSpawn-test.jar';original_spawn=spawn.read_bytes()
 with zipfile.ZipFile(spawn,'w') as z:z.writestr('plugin.yml','name: EssentialsSpawn\nversion: different-release-version\n')
 current_manifest=(payload/'SHA256.properties').read_text();(payload/'SHA256.properties').write_text('\n'.join(line if not line.startswith(spawn.name+'=') else spawn.name+'='+hashlib.sha256(spawn.read_bytes()).hexdigest() for line in current_manifest.splitlines()));run('설치',server,payload,'--서버종료확인',ok=False);spawn.write_bytes(original_spawn);(payload/'SHA256.properties').write_text(current_manifest)
 # Corrupt input cannot touch any installed plugins.
 before={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in plugins.glob('*.jar')};(payload/'KoreanMenu-test.jar').write_bytes(b'corrupt');run('설치',server,payload,'--서버종료확인',ok=False);check(before=={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in plugins.glob('*.jar')})
 run('설치',server,payload,ok=False)
print(f'PASS: {assertions} installer assertions (real filesystem, hashes, locks, complete restore; fixture plugin metadata only)')
(OUT/'installer-validation.json').write_text(json.dumps({'assertions':assertions,'real_filesystem':True,'real_hash_validation':True,'linux_session_lock_tested':True,'windows_launcher_tested':False,'fixture_plugin_metadata_only':True},indent=2)+'\n')
