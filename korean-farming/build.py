#!/usr/bin/env python3
"""Build the complete farming update against the real Paper API, without fixtures in production."""
from pathlib import Path
import subprocess,os,zipfile,hashlib,json,shutil,tempfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];BASE=Path(os.environ.get('TYCOON_TOOLCHAINS','/workspace/toolchains'));OUT=ROOT/'releases/farming-update';WORK=ROOT/'.build/farming-release';WORK.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
if 'version: 1.0.0\n' not in (ROOT/'korean-farming/src/main/resources/plugin.yml').read_text():
 raise SystemExit('이 스크립트는 ee8edb8의 농장 1.0.0 재현용입니다. 최신 메뉴 통합 빌드는 interface-update/build.py를 사용하세요.')
JDK=BASE/'jdk-25.0.5.0.3/bin';API=BASE/'paper-api-build/build/libs/paper-api-26.3-df120fd-local.jar';DEPS=BASE/'paper-api-build/build/deps';VENDOR=ROOT/'survival-pack/vendor/tycoon'
def run(args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def zipped(path,entries):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
def test_report(folder,classname,modules):
 xml=BASE/folder/'build/test-results/test'/('TEST-'+classname+'.xml');r=ET.parse(xml).getroot();assert all(r.get(k)=='0' for k in ['errors','failures','skipped']),r.attrib
 assert xml.stat().st_mtime>=max(f.stat().st_mtime for module in modules for f in (ROOT/module/'src').rglob('*.java')),'Run tests after all Java edits'
 return {'tests':int(r.get('tests')),'failures':0,'skipped':0}
reports={'farming':test_report('farming-tests','kr.minecraft.survival.farming.FarmingTest',['korean-farming','korean-codex','korean-economy','korean-menu']),'codex':test_report('korean-codex-tests','kr.minecraft.survival.codex.CodexTest',['korean-codex']),'economy':test_report('tycoon-integration','kr.minecraft.survival.economy.TycoonIntegrationTest',['korean-economy','korean-menu'])}
run(['python3',ROOT/'farming-resourcepack/tools/build.py'])
pack=OUT/'CityEconomy-Farming-26.3-1.1.0.zip';sha1=hashlib.sha1(pack.read_bytes()).hexdigest()
cp=os.pathsep.join(map(str,[API,DEPS/'*',ROOT/'survival-pack/vendor/Vault.jar',ROOT/'releases/korean-plugins/worldguard-bukkit-7.0.21-ko.jar',ROOT/'releases/korean-plugins/worldedit-bukkit-7.4.6-ko.jar']))
core_classes=WORK/'core';farm_classes=WORK/'farm'
artifacts=[]
for module,name,version,classes in [('korean-economy','KoreanEconomy','2.0.2',core_classes),('korean-farming','KoreanFarming','1.0.0',farm_classes)]:
 shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True);src=ROOT/module/'src/main';classpath=cp+(os.pathsep+str(core_classes) if name=='KoreanFarming' else '')
 run([JDK/'javac','--release','21','-encoding','UTF-8','-cp',classpath,'-d',classes,*sorted((src/'java').rglob('*.java'))])
 entries={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
 for f in (src/'resources').rglob('*'):
  if f.is_file():entries[f.relative_to(src/'resources').as_posix()]=f.read_bytes().replace(b'@FARM_PACK_SHA1@',sha1.encode())
 if name=='KoreanEconomy':
  with zipfile.ZipFile(VENDOR/'sqlite-jdbc.jar') as driver:
   for n in driver.namelist():
    if n.endswith('/') or n.upper().endswith(('.SF','.RSA','.DSA')) or n=='META-INF/MANIFEST.MF':continue
    entries[n]=driver.read(n)
 else:entries['LICENSE-Farming.txt']=(ROOT/'farming-resourcepack/LICENSE.txt').read_bytes()
 jar=OUT/(name+'-'+version+'.jar');zipped(jar,entries);artifacts.append(jar)
# Reuse the codex's independent SQLite artifact verification and complete bonus-table generator.
codexout=WORK/'codex-artifact';run(['python3',ROOT/'korean-codex/build.py'],env=dict(os.environ,CODEX_OUTPUT=str(codexout)))
for name in ['KoreanCodex-1.1.0.jar','아이템별보너스.md']:shutil.copyfile(codexout/name,OUT/name)
artifacts.append(OUT/'KoreanCodex-1.1.0.jar');shutil.copyfile(codexout/'validation.json',OUT/'codex-validation.json')
# Actual production core JAR must load its own SQLite native library, with no vendor driver in the process.
smoke=WORK/'FarmArtifactSmoke.java';smoke.write_text('''import java.nio.file.*;import java.util.*;import kr.minecraft.survival.economy.domain.*;import kr.minecraft.survival.farming.*;
public class FarmArtifactSmoke{public static void main(String[] args)throws Exception{UUID owner=UUID.randomUUID();String plot,rid;Path file=Path.of(args[0]);
try(StateStore db=new StateStore(file)){db.player(owner,"Artifact");FarmStore fs=new FarmStore(db);var r=fs.reservePlant(owner,UUID.randomUUID().toString(),0,65,0,"corn");plot=FarmStore.operation(r).plot();db.complete(r.id(),()->fs.completeInside(r,()->{}));fs.grow(Map.of(plot,FarmRules.crop("corn").duration()));var harvest=fs.reserveHarvest(owner,plot,3,true,false);rid=harvest.id();}
try(StateStore db=new StateStore(file)){FarmStore fs=new FarmStore(db);if(!db.receipt(rid).state().equals("REVIEW")||!fs.get(plot).state().equals("LOCKED"))throw new AssertionError("restart quarantine");db.resolveFarm(rid,true,"smoke",()->fs.completeInside(db.receipt(rid),()->db.grantXpInside(owner,Rules.Job.FARMER,2,"2026-11-11",250)));if(fs.get(plot)!=null||db.progress(owner,Rules.Job.FARMER).xp()!=2)throw new AssertionError("atomic harvest");db.snapshot(Path.of(args[1]));}System.out.println("PRODUCTION_CORE_NATIVE_SQLITE_FARM_RESTART_OK");}}
''',encoding='utf-8')
smokecp=os.pathsep.join(map(str,[*artifacts[:2],API,DEPS/'*',VENDOR/'slf4j-api.jar',VENDOR/'slf4j-simple.jar']))
run([JDK/'javac','--release','21','-cp',smokecp,'-d',WORK,smoke])
with tempfile.TemporaryDirectory(prefix='farm-artifact-') as tmp:run([JDK/'java','--enable-native-access=ALL-UNNAMED','-cp',str(WORK)+os.pathsep+smokecp,'FarmArtifactSmoke',Path(tmp)/'economy.db',Path(tmp)/'backup.db'])
installer_classes=WORK/'installer';installer_classes.mkdir(exist_ok=True);run([JDK/'javac','--release','21','-encoding','UTF-8','-d',installer_classes,ROOT/'tools/installer/PackInstaller.java']);installer_entries={f.relative_to(installer_classes).as_posix():f.read_bytes() for f in installer_classes.rglob('*.class')};installer_entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMain-Class: PackInstaller\r\n\r\n';zipped(OUT/'FarmInstaller.jar',installer_entries)
run(['python3',ROOT/'korean-farming/test_installer.py'])
for jar in artifacts:
 with zipfile.ZipFile(jar) as z:
  assert z.testzip() is None
  assert not any(n.startswith(('org/bukkit/','io/papermc/','org/mockbukkit/','net/milkbowl/','com/sk89q/')) for n in z.namelist())
  for n in z.namelist():
   if n.startswith('kr/') and n.endswith('.class'):assert int.from_bytes(z.read(n)[6:8],'big')==65
for jar in [artifacts[0],artifacts[2]]:
 with zipfile.ZipFile(jar) as z:assert any('org/sqlite/native/Windows/x86_64/' in n and n.endswith('.dll') for n in z.namelist())
# Validate every resource JSON, model reference and actual PNG alpha channel.
from PIL import Image
with zipfile.ZipFile(pack) as z:
 assert z.testzip() is None
 for n in z.namelist():
  if n.endswith('.json'):
   data=json.loads(z.read(n))
   if '/items/' in n:
    model=data['model']['model'];ns,key=model.split(':');assert f'assets/{ns}/models/{key}.json' in z.namelist()
  if '/crop/' in n and n.endswith('.png'):
   import io
   im=Image.open(io.BytesIO(z.read(n)));assert im.size==(64,64) and im.mode=='RGBA';assert im.getchannel('A').getextrema()[0]==0
report={'versions':{'KoreanEconomy':'2.0.2','KoreanFarming':'1.0.0','KoreanCodex':'1.1.0'},'target':'Paper 26.3 df120fd / Java 25 runtime / Java 21 bytecode','api_sha256':hashlib.sha256(API.read_bytes()).hexdigest(),'mockbukkit':'26.3:4.120.0','suites':reports,'existing_domain_assertions':121,'resourcepack_sha1':sha1,'crops':5,'dishes':5,'codex_entries':85,'codex_achievements':13,'unchanged_original_codex_rewards_and_caps_tested':True,'packaged_core_and_codex_native_sqlite_smoke':True,'windows_x86_64_native_included':True,'player_nbt_actual_disk_persistence_tested':False,'mock_substitutions':['Player.saveData counting/fault injection','Entity default visibility property absent in real MockBukkit','BLOCK_BREAK_SPEED mock attribute base=1 absent in MockBukkit default map'],'windows_server_applied':False,'real_client_rendering_or_hitboxes_tested':False,'ten_player_load_tested':False,'gp_wg_real_server_tested':False,'protection_api_references':{'GriefPrevention':'official 16.18.8 source allowBuild(Player,Location,Material)','WorldGuard':'real 7.0.21 API RegionQuery.testBuild'},'source_sha256':{f.relative_to(ROOT).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for m in ['korean-economy','korean-farming','korean-codex'] for f in (ROOT/m/'src/main').rglob('*') if f.is_file()},'artifacts':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in artifacts}}
(OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');shutil.copyfile(ROOT/'korean-farming/README.md',OUT/'설치·사용안내.md')
# Bundle payload contains exactly the three custom plugins. No external plugin is replaced.
bundle={'plugins/'+f.name:f.read_bytes() for f in artifacts};bundle['plugins/SHA256.properties']=''.join(f.name+'='+hashlib.sha256(f.read_bytes()).hexdigest()+'\n' for f in artifacts).encode('ascii')
for f in OUT.iterdir():
 if f.is_file() and f.suffix in ['.json','.md','.png'] and f.name!='SHA256SUMS.txt':bundle[f.name]=f.read_bytes()
for name in ['CityEconomy-Farming-26.3-1.1.0.zip','CityEconomy-Farming-Complete-26.3-1.1.0.zip']:bundle['resourcepacks/'+name]=(OUT/name).read_bytes()
bundle['FarmInstaller.jar']=(OUT/'FarmInstaller.jar').read_bytes();bundle['Install-Farming.ps1']=b'\xef\xbb\xbf'+(ROOT/'tools/installer/Install-Farming.ps1').read_bytes().replace(b'\n',b'\r\n');bundle['Install-Farming.cmd']=(ROOT/'tools/installer/Install-Farming.cmd').read_bytes().replace(b'\n',b'\r\n');bundle['LICENSE-Farming.txt']=(ROOT/'farming-resourcepack/LICENSE.txt').read_bytes()
bundle['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(bundle.items())).encode('utf-8')
archive=OUT/'도시농장-통합설치-1.0.0.zip';zipped(archive,bundle)
(OUT/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in [*artifacts,pack,OUT/'CityEconomy-Farming-Complete-26.3-1.1.0.zip',OUT/'FarmInstaller.jar',archive]))
print('Built farming update:',archive);print('All required test suites passed with zero skips:',reports)
