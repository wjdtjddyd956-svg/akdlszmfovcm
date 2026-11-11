#!/usr/bin/env python3
"""Produce the current menu/codex/city/farm update; keep historical releases immutable."""
from pathlib import Path
import subprocess,os,json,zipfile,shutil,hashlib,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];BASE=Path('/workspace/toolchains');OUT=ROOT/'releases/ui-update';WORK=ROOT/'.build/interface-release';OUT.mkdir(parents=True,exist_ok=True);WORK.mkdir(parents=True,exist_ok=True)
JDK=BASE/'jdk-25.0.5.0.3/bin';API=BASE/'paper-api-build/build/libs/paper-api-26.3-df120fd-local.jar';DEPS=BASE/'paper-api-build/build/deps'
def run(args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def zipped(path,entries):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   i=zipfile.ZipInfo(name,(2026,11,11,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o644<<16;z.writestr(i,data)
suites={}
for key,folder,cls,modules in [('codex','korean-codex-tests','kr.minecraft.survival.codex.CodexTest',['korean-codex','korean-menu']),('textures','city-textures-tests','kr.minecraft.survival.citytextures.CityTexturesTest',['city-textures','korean-menu']),('farming','farming-tests','kr.minecraft.survival.farming.FarmingTest',['korean-farming','korean-codex','korean-menu','city-textures']),('economy','tycoon-integration','kr.minecraft.survival.economy.TycoonIntegrationTest',['korean-economy','korean-menu'])]:
 xml=BASE/folder/'build/test-results/test'/('TEST-'+cls+'.xml');r=ET.parse(xml).getroot();assert all(r.get(k)=='0' for k in ['errors','failures','skipped']),r.attrib
 assert xml.stat().st_mtime>=max(p.stat().st_mtime for m in modules for p in (ROOT/m/'src').rglob('*.java')),'Run final tests: '+key
 suites[key]={'tests':int(r.get('tests')),'failures':0,'skipped':0}
run(['python3',ROOT/'interface-resourcepack/tools/build.py'])
pack=OUT/'CityEconomy-Interface-26.3-1.2.0.zip';ui_sha1=hashlib.sha1(pack.read_bytes()).hexdigest();farm_sha1=hashlib.sha1((ROOT/'releases/farming-update/CityEconomy-Farming-26.3-1.1.0.zip').read_bytes()).hexdigest();city_sha1=hashlib.sha1((ROOT/'releases/city-resourcepack/CityEconomy-26.3-1.0.0.zip').read_bytes()).hexdigest()
# Unmodified existing economic core: verify both artifact and current source against its release record.
core=ROOT/'releases/farming-update/KoreanEconomy-2.0.2.jar';proof=json.loads((core.parent/'validation.json').read_text());assert sha(core)==proof['artifacts'][core.name]
for name,digest in proof['source_sha256'].items():
 if name.startswith('korean-economy/'):assert sha(ROOT/name)==digest,name
shutil.copyfile(core,OUT/core.name);artifacts=[OUT/core.name]
cp=os.pathsep.join(map(str,[API,DEPS/'*',core]))
for module,name,version in [('korean-menu','KoreanMenu','2.1.0'),('city-textures','CityTextures','1.1.0'),('korean-farming','KoreanFarming','1.0.1')]:
 src=ROOT/module/'src/main';assert 'version: '+version+'\n' in (src/'resources/plugin.yml').read_text();classes=WORK/module;shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True)
 extra=os.pathsep.join(map(str,[ROOT/'releases/korean-plugins/worldguard-bukkit-7.0.21-ko.jar',ROOT/'releases/korean-plugins/worldedit-bukkit-7.4.6-ko.jar']))
 run([JDK/'javac','--release','21','-encoding','UTF-8','-cp',cp+os.pathsep+extra,'-d',classes,*sorted((src/'java').rglob('*.java'))])
 entries={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
 for p in (src/'resources').rglob('*'):
  if p.is_file():entries[p.relative_to(src/'resources').as_posix()]=p.read_bytes().replace(b'@UI_PACK_SHA1@',ui_sha1.encode()).replace(b'@CITY_PACK_SHA1@',city_sha1.encode()).replace(b'@FARM_PACK_SHA1@',farm_sha1.encode())
 entries['LICENSE-Interface.txt']=(ROOT/'interface-resourcepack/LICENSE.txt').read_bytes();jar=OUT/(name+'-'+version+'.jar');zipped(jar,entries);artifacts.append(jar)
codexout=WORK/'codex';run(['python3',ROOT/'korean-codex/build.py'],env=dict(os.environ,CODEX_OUTPUT=str(codexout)))
for name in ['KoreanCodex-1.2.0.jar','아이템별보너스.md']:shutil.copyfile(codexout/name,OUT/name)
shutil.copyfile(codexout/'validation.json',OUT/'codex-validation.json');artifacts.append(OUT/'KoreanCodex-1.2.0.jar')
for jar in artifacts:
 with zipfile.ZipFile(jar) as z:
  assert z.testzip() is None
  assert not any(n.startswith(('org/bukkit/','io/papermc/','org/mockbukkit/','net/milkbowl/','com/sk89q/')) for n in z.namelist())
  assert not any('Probe' in n or 'Test' in n for n in z.namelist() if n.startswith('kr/'))
  for n in z.namelist():
   if n.startswith('kr/') and n.endswith('.class'):assert int.from_bytes(z.read(n)[6:8],'big')==65
for jar in [artifacts[0],artifacts[-1]]:
 with zipfile.ZipFile(jar) as z:assert 'org/sqlite/native/Windows/x86_64/sqlitejdbc.dll' in z.namelist()
with zipfile.ZipFile(pack) as z:
 assert z.testzip() is None
 for n in z.namelist():
  if n.endswith('.json'):
   model=json.loads(z.read(n))
   if '/items/' in n:
    ns,path=model['model']['model'].split(':');assert f'assets/{ns}/models/{path}.json' in z.namelist()
# Retain all previous farming textures/models, except the intentional pack header/icon.
with zipfile.ZipFile(pack) as current,zipfile.ZipFile(ROOT/'releases/farming-update/CityEconomy-Farming-26.3-1.1.0.zip') as previous:
 for n in previous.namelist():
  if n.startswith('assets/'):assert current.read(n)==previous.read(n),n
run(['python3',ROOT/'interface-resourcepack/tools/preview.py']);run(['python3',ROOT/'interface-resourcepack/tools/test_preview.py'])
installer=WORK/'installer';installer.mkdir(exist_ok=True);run([JDK/'javac','--release','21','-encoding','UTF-8','-d',installer,ROOT/'tools/installer/PackInstaller.java']);contents={p.relative_to(installer).as_posix():p.read_bytes() for p in installer.rglob('*.class')};contents['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMain-Class: PackInstaller\r\n\r\n';zipped(OUT/'InterfaceInstaller.jar',contents);run(['python3',ROOT/'interface-update/test_installer.py'])
report={'versions':{'KoreanMenu':'2.1.0','KoreanCodex':'1.2.0','CityTextures':'1.1.0','KoreanFarming':'1.0.1','KoreanEconomy':'2.0.2'},'target':'Paper 26.3 df120fd / Java 25+','api_sha256':sha(API),'production_bytecode':'Java 21, actual Paper API requires Java 25+','mockbukkit':'26.3:4.120.0','suites':suites,'total_tests':sum(x['tests'] for x in suites.values()),'new_ui_icons':24,'codex_entries':85,'codex_achievements':13,'registration_reward_database_rules_changed':False,'existing_economy_core_reused_verified':True,'existing_city_and_farm_models_retained_byte_for_byte':True,'packaged_codex_native_sqlite_restart_snapshot_smoke':True,'windows_sqlite_dll_included':True,'preview':'real MockBukkit inventory snapshots rendered into HTML/PNG; not a native client screenshot','windows_server_applied':False,'native_minecraft_client_visual_or_click_tested':False,'real_player_nbt_disk_tested':False,'test_substitutions':['Player NBT save counting/fault injection','MockBukkit entity default visibility property','Missing MockBukkit BLOCK_BREAK_SPEED attribute initialized to base 1','Resource-pack request parameters/status simulated; actual downloads not sent by MockBukkit'],'ten_player_load_tested':False,'source_sha256':{p.relative_to(ROOT).as_posix():sha(p) for m in ['korean-menu','korean-codex','city-textures','korean-farming'] for p in (ROOT/m/'src/main').rglob('*') if p.is_file()},'artifacts':{p.name:sha(p) for p in artifacts},'ui_pack_sha1':ui_sha1}
(OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');shutil.copyfile(ROOT/'interface-update/README.md',OUT/'설치·사용안내.md')
bundle={'plugins/'+p.name:p.read_bytes() for p in artifacts};bundle['plugins/SHA256.properties']=''.join(p.name+'='+sha(p)+'\n' for p in artifacts).encode('ascii')
for p in OUT.iterdir():
 if p.suffix in ['.md','.json','.png','.html']:bundle[p.name]=p.read_bytes()
for name in ['CityEconomy-Interface-26.3-1.2.0.zip','CityEconomy-Interface-Complete-26.3-1.2.0.zip']:bundle['resourcepacks/'+name]=(OUT/name).read_bytes()
bundle['InterfaceInstaller.jar']=(OUT/'InterfaceInstaller.jar').read_bytes();bundle['Install-Interface.ps1']=(ROOT/'tools/installer/Install-Interface.ps1').read_text().replace('\n','\r\n').encode('utf-8-sig');bundle['Install-Interface.cmd']=(ROOT/'tools/installer/Install-Interface.cmd').read_text().replace('\n','\r\n').encode('ascii');bundle['LICENSE-Interface.txt']=(ROOT/'interface-resourcepack/LICENSE.txt').read_bytes();bundle['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(bundle.items())).encode()
archive=OUT/'도시생활-메뉴통합-1.2.0.zip';zipped(archive,bundle)
(OUT/'SHA256SUMS.txt').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in [*artifacts,pack,OUT/'CityEconomy-Interface-Complete-26.3-1.2.0.zip',OUT/'InterfaceInstaller.jar',archive]))
print('Built',archive);print('PASS all suites, zero skips:',suites)
