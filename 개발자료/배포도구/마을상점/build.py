#!/usr/bin/env python3
"""Build the civic update against the real Paper API; retain historical releases."""
from pathlib import Path
import subprocess,os,json,zipfile,shutil,hashlib,tempfile,xml.etree.ElementTree as ET
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());BASE=Path('/workspace/toolchains');OUT=Path(os.environ.get('CIVIC_OUTPUT',str(ROOT/'.build/마을상점-빌드')));WORK=ROOT/'.build/civic-release';JDK=BASE/'jdk-25.0.5.0.3/bin';API=BASE/'paper-api-build/build/libs/paper-api-26.3-df120fd-local.jar';DEPS=BASE/'paper-api-build/build/deps'
OUT.mkdir(parents=True,exist_ok=True);WORK.mkdir(parents=True,exist_ok=True)
def run(args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def sourcepath(name):
 first,sep,tail=name.partition('/')
 mapping=json.loads((ROOT/'저장소구조.json').read_text())['이전경로대응']
 return ROOT/(mapping.get(first,first)+sep+tail)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def zipped(path,entries):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   i=zipfile.ZipInfo(name,(2026,11,11,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o644<<16;z.writestr(i,data)
suites={}
for key,folder,classes,modules in [
 ('civic','civic-tests',['kr.minecraft.survival.civic.CivicTest','kr.minecraft.survival.civic.CivicStoreTest','kr.minecraft.survival.economy.ClaimGeometryTest'],['개발자료/플러그인/생활경제','개발자료/플러그인/마을상점','개발자료/플러그인/마을수첩','개발자료/플러그인/도시화면']),
 ('economy','tycoon-integration',['kr.minecraft.survival.economy.TycoonIntegrationTest'],['개발자료/플러그인/생활경제','개발자료/플러그인/마을수첩']),
 ('farming','farming-tests',['kr.minecraft.survival.farming.FarmingTest'],['개발자료/플러그인/생활경제','개발자료/플러그인/커스텀농사','개발자료/플러그인/아이템도감','개발자료/플러그인/마을수첩','개발자료/플러그인/도시화면']),
 ('codex','korean-codex-tests',['kr.minecraft.survival.codex.CodexTest'],['개발자료/플러그인/아이템도감','개발자료/플러그인/마을수첩']),
 ('textures','city-textures-tests',['kr.minecraft.survival.citytextures.CityTexturesTest'],['개발자료/플러그인/도시화면','개발자료/플러그인/마을수첩'])]:
  count=0
  for cls in classes:
   xml=BASE/folder/'build/test-results/test'/('TEST-'+cls+'.xml');result=ET.parse(xml).getroot();assert all(result.get(k)=='0' for k in ['errors','failures','skipped']),result.attrib
   assert xml.stat().st_mtime>=max(f.stat().st_mtime for m in modules for f in (ROOT/m/'src/main').rglob('*') if f.is_file()),'Run final tests after source edits: '+key
   count+=int(result.get('tests'))
  suites[key]={'tests':count,'failures':0,'skipped':0}
run(['python3',ROOT/'개발자료/플러그인/생활경제/test_domain.py'])
pack=ROOT/'releases/ui-update/CityEconomy-Interface-26.3-1.2.0.zip';uisha=hashlib.sha1(pack.read_bytes()).hexdigest();citysha=hashlib.sha1((ROOT/'releases/city-resourcepack/CityEconomy-26.3-1.0.0.zip').read_bytes()).hexdigest()
cp=os.pathsep.join(map(str,[API,DEPS/'*',ROOT/'개발자료/배포도구/경제야생/vendor/Vault.jar']));artifacts=[];versions={}
for module,name,version in [('개발자료/플러그인/생활경제','KoreanEconomy','2.0.3'),('개발자료/플러그인/마을수첩','KoreanMenu','2.2.0'),('개발자료/플러그인/도시화면','CityTextures','1.2.0'),('개발자료/플러그인/마을상점','KoreanCivic','1.0.0')]:
 src=ROOT/module/'src/main';assert 'version: '+version+'\n' in (src/'resources/plugin.yml').read_text();classes=WORK/module;shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True)
 run([JDK/'javac','--release','21','-encoding','UTF-8','-cp',cp,'-d',classes,*sorted((src/'java').rglob('*.java'))])
 entries={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
 for f in (src/'resources').rglob('*'):
  if f.is_file():entries[f.relative_to(src/'resources').as_posix()]=f.read_bytes().replace(b'@UI_PACK_SHA1@',uisha.encode()).replace(b'@CITY_PACK_SHA1@',citysha.encode())
 entries['LICENSE-Tycoon.txt']=(ROOT/'사용안내/생활타이쿤/LICENSE.txt').read_bytes()
 if name=='KoreanEconomy':
  with zipfile.ZipFile(ROOT/'개발자료/배포도구/경제야생/vendor/tycoon/sqlite-jdbc.jar') as driver:
   for n in driver.namelist():
    if not n.endswith('/') and n!='META-INF/MANIFEST.MF' and not n.upper().endswith(('.SF','.RSA','.DSA')):entries[n]=driver.read(n)
 jar=OUT/(name+'-'+version+'.jar');zipped(jar,entries);artifacts.append(jar);versions[name]=version
 if name=='KoreanEconomy':cp+=os.pathsep+str(jar)
# Unchanged farm/codex: verify the latest Windows release's complete source and artifact proofs.
prior=ROOT/'releases/ui-update/1.2.1';proof=json.loads((prior/'build-info.json').read_text())
for module,name,version in [('개발자료/플러그인/커스텀농사','KoreanFarming','1.0.2'),('개발자료/플러그인/아이템도감','KoreanCodex','1.2.1')]:
 record=next(r for r in proof['artifacts'] if r['file']==name+'-'+version+'.jar');old=prior/record['file'];assert sha(old)==record['sha256']
 with zipfile.ZipFile(prior/'도시생활-Windows-메뉴통합-1.2.1.zip') as published:
  for path,digest in record['source_sha256'].items():
   source=published.read('소스/'+path);assert hashlib.sha256(source).hexdigest()==digest,path
   assert sourcepath(path).read_bytes().replace(b'\r\n',b'\n')==source.replace(b'\r\n',b'\n'),path
 new=OUT/old.name;shutil.copyfile(old,new);artifacts.append(new);versions[name]=version
for jar in artifacts:
 with zipfile.ZipFile(jar) as z:
  assert z.testzip() is None
  assert not any(n.startswith(('org/bukkit/','io/papermc/','org/mockbukkit/','net/milkbowl/','com/sk89q/')) for n in z.namelist())
  for n in z.namelist():
   if n.startswith('kr/') and n.endswith('.class'):assert int.from_bytes(z.read(n)[6:8],'big')==65 and 'Test' not in n and 'Probe' not in n
for jar in [artifacts[0],artifacts[-1]]:
 with zipfile.ZipFile(jar) as z:assert 'org/sqlite/native/Windows/x86_64/sqlitejdbc.dll' in z.namelist()
# Packaged Core+addon, actual embedded SQLite/native driver, no external SQLite JAR.
with tempfile.TemporaryDirectory(prefix='civic-sqlite-') as tmp:
 temp=Path(tmp);smoke=temp/'CivicArtifact.java';smoke.write_text('''import java.nio.file.*;import java.util.*;import kr.minecraft.survival.economy.domain.*;import kr.minecraft.survival.civic.CivicStore;
public class CivicArtifact{public static void main(String[] args)throws Exception{Path file=Path.of(args[0]);UUID owner=UUID.randomUUID(),buyer=UUID.randomUUID();String pending;Map<UUID,Long> balance=new HashMap<>();balance.put(owner,10000000L);balance.put(buyer,10000000L);
try(var db=new StateStore(file)){db.player(owner,"Owner");db.player(buyer,"Buyer");db.meta("kept","unchanged");db.execute("DROP TABLE land_tags");db.execute("PRAGMA user_version=1");}
try(var db=new StateStore(file)){var store=new CivicStore(db);ActionEngine engine=new ActionEngine(db,new ActionEngine.Wallet(){public boolean credit(UUID p,long n){balance.put(p,balance.get(p)+n);return true;}public boolean debit(UUID p,long n){if(balance.get(p)<n)return false;balance.put(p,balance.get(p)-n);return true;}},e->{throw new AssertionError(e);});
var town=store.reserveTown(owner,"world",-1,0,"도시마을",1000000,64);if(engine.reserved(town,ActionEngine.Direction.DEBIT,ActionEngine.NO_ITEMS,()->store.completeInside(town)).status()!=ActionEngine.Status.DONE)throw new AssertionError();var plot=store.plots().getFirst();store.setPlotPrice(owner,plot.id(),50000);var sale=store.reservePlotBuy(buyer,plot.id(),50000);engine.reserved(sale,ActionEngine.Direction.DEBIT,ActionEngine.NO_ITEMS,()->store.completeInside(sale));if(!db.land("world",-1,0).owner().equals(buyer.toString())||store.credit(owner)!=50000)throw new AssertionError();var credit=store.reserveCollection(owner);engine.reserved(credit,ActionEngine.Direction.CREDIT,ActionEngine.NO_ITEMS,()->store.completeInside(credit));if(balance.get(owner)!=9050000||store.credit(owner)!=0)throw new AssertionError();pending=store.reserveExpansion(owner,store.towns().getFirst().id(),"world",0,0,250000,16).id();db.snapshot(Path.of(args[1]));}
try(var db=new StateStore(file)){var store=new CivicStore(db);if(!db.meta("kept").equals("unchanged")||!db.receipt(pending).state().equals("REVIEW")||store.plots().size()!=2)throw new AssertionError();db.resolveCivic(pending,false,"artifact",()->store.cancelInside(pending));if(store.plots().size()!=1||db.land("world",0,0)!=null||!StateStore.validSnapshot(Path.of(args[1])))throw new AssertionError();}System.out.println("PACKAGED_CIVIC_SQLITE_OK");}}''',encoding='utf-8')
 smoke_cp=os.pathsep.join(map(str,[artifacts[0],OUT/'KoreanCivic-1.0.0.jar',DEPS/'*',ROOT/'개발자료/배포도구/경제야생/vendor/tycoon/slf4j-simple.jar']))
 run([JDK/'javac','--release','21','-encoding','UTF-8','-cp',smoke_cp,'-d',temp,smoke]);result=run([JDK/'java','--enable-native-access=ALL-UNNAMED','-cp',str(temp)+os.pathsep+smoke_cp,'CivicArtifact',temp/'생활경제.db',temp/'검증백업.db'],capture_output=True,text=True);assert 'PACKAGED_CIVIC_SQLITE_OK' in result.stdout
 (OUT/'packaged-sqlite.json').write_text(json.dumps({'actual_production_core_and_civic_jars':True,'embedded_native_sqlite_only':True,'migration_title_transfer_payout_restart_review_and_snapshot':True,'platform':os.name,'result':result.stdout.strip()},ensure_ascii=False,indent=2)+'\n')
installer=WORK/'installer';shutil.rmtree(installer,ignore_errors=True);installer.mkdir();run([JDK/'javac','--release','21','-encoding','UTF-8','-d',installer,ROOT/'개발자료/공통도구/installer/PackInstaller.java']);entries={f.relative_to(installer).as_posix():f.read_bytes() for f in installer.rglob('*.class')};entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMain-Class: PackInstaller\r\n\r\n';zipped(OUT/'CivicInstaller.jar',entries);run(['python3',ROOT/'개발자료/배포도구/마을상점/test_installer.py'],env=dict(os.environ,CIVIC_RELEASE_DIR=str(OUT)))
run(['python3',ROOT/'개발자료/배포도구/마을상점/preview.py'])
report={'versions':versions,'target':'Paper 26.3 build238 df120fd / Java 25+','api_sha256':sha(API),'api_provenance':json.loads((BASE/'paper-api-build/provenance.json').read_text()),'bytecode':21,'mockbukkit':'26.3:4.120.0','suites':suites,'total_tests':sum(r['tests'] for r in suites.values()),'domain_assertions':json.loads((ROOT/'개발자료/플러그인/생활경제/build-domain/test-results.json').read_text())['assertions'],'embedded_sqlite_smoke':True,'farm_codex_reused_with_complete_source_and_artifact_proof':True,'reused_source_verification':'published ZIP source hashes checked exactly; current source equality checked after Windows CRLF normalization','resourcepack_unchanged_sha256':sha(pack),'core_schema':2,'civic_schema':1,'windows_server_applied':False,'real_gp_worldguard_event_combination_tested':False,'native_minecraft_client_play_tested':False,'actual_player_nbt_disk_tested':False,'ten_player_load_tested':False,'preview':'real MockBukkit inventory data and actual pack PNGs, not a native game screenshot','test_substitutions':['MockBukkit player NBT save counting/fault injection','Existing farming suite: missing entity visibility defaults and BLOCK_BREAK_SPEED attribute','Resource-pack status simulation, no native client download/renderer'],'source_sha256':{f.relative_to(ROOT).as_posix():sha(f) for m in ['개발자료/플러그인/생활경제','개발자료/플러그인/마을수첩','개발자료/플러그인/도시화면','개발자료/플러그인/마을상점','개발자료/플러그인/커스텀농사','개발자료/플러그인/아이템도감'] for f in (ROOT/m/'src/main').rglob('*') if f.is_file()},'artifacts':{f.name:sha(f) for f in artifacts}}
(OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
run(['python3',ROOT/'개발자료/배포도구/마을상점/package.py'])
print('PASS',report['total_tests'],'JUnit tests,',report['domain_assertions'],'domain assertions; packaged SQLite + installer checks');print(OUT/'도시생활-마을상점-1.0.0.zip')
