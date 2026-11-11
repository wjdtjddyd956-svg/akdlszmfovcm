#!/usr/bin/env python3
"""Build against the real Paper API, package SQLite, and run the packaged DB smoke test."""
from pathlib import Path
import os,subprocess,shutil,zipfile,hashlib,json,tempfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];MODULE=ROOT/'korean-codex';BASE=Path(os.environ.get('TYCOON_TOOLCHAINS','/workspace/toolchains'));OUT=Path(os.environ.get('CODEX_OUTPUT',str(ROOT/'releases/korean-codex-1.1')));OUT.mkdir(parents=True,exist_ok=True)
JDK=Path(os.environ.get('TYCOON_JDK',str(BASE/'jdk-25.0.5.0.3')))/'bin';API=Path(os.environ.get('PAPER_API_JAR',str(BASE/'paper-api-build/build/libs/paper-api-26.3-df120fd-local.jar')));DEPS=BASE/'paper-api-build/build/deps';VENDOR=ROOT/'survival-pack/vendor/tycoon'
TEST=BASE/'korean-codex-tests/build/test-results/test/TEST-kr.minecraft.survival.codex.CodexTest.xml';tests=ET.parse(TEST).getroot();assert all(tests.get(k)=='0' for k in ['failures','errors','skipped']) and int(tests.get('tests'))>=26,'Run test.py first'
assert TEST.stat().st_mtime>=max(p.stat().st_mtime for p in (MODULE/'src').rglob('*.java')),'Run tests after source edits'
classes=MODULE/'build/classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True)
sources=list((MODULE/'src/main/java').rglob('*.java'));cp=os.pathsep.join([str(API),str(DEPS/'*')]);subprocess.run([str(JDK/'javac'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',str(classes)]+[str(p) for p in sources],check=True)
def zip_write(path,entries):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
entries={str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')}
for p in (MODULE/'src/main/resources').rglob('*'):
    if p.is_file():entries[str(p.relative_to(MODULE/'src/main/resources'))]=p.read_bytes()
entries['LICENSE-KoreanCodex.txt']=(MODULE/'LICENSE.txt').read_bytes()
with zipfile.ZipFile(VENDOR/'sqlite-jdbc.jar') as driver:
    for n in driver.namelist():
        if n.endswith('/') or n.upper().endswith(('.SF','.RSA','.DSA')) or n=='META-INF/MANIFEST.MF':continue
        assert n not in entries;entries[n]=driver.read(n)
jar=OUT/'KoreanCodex-1.1.0.jar';zip_write(jar,entries)
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    assert not any(n.startswith(('org/bukkit/','io/papermc/','org/mockbukkit/','net/milkbowl/','org/slf4j/')) for n in z.namelist())
    assert any('org/sqlite/native/Windows/x86_64/' in n and n.endswith('.dll') for n in z.namelist())
with tempfile.TemporaryDirectory(prefix='codex-artifact-') as tmp:
    temp=Path(tmp);smoke=temp/'CodexArtifact.java';smoke.write_text('''package kr.minecraft.survival.codex;
import java.nio.file.*;import java.util.*;
public class CodexArtifact {public static void main(String[] args)throws Exception {
Catalog c=new Catalog(CodexArtifact.class.getResourceAsStream("/catalog.tsv"));
for(Catalog.Category category:c.categories)for(Catalog.Entry e:category.entries())System.out.println(String.join("\\t","ENTRY",category.name(),e.name(),e.rarity(),e.material(),category.stat().label,category.stat().format(c.entryUnits(e))));
for(Catalog.Achievement a:c.achievements){List<String> parts=new ArrayList<>();for(Catalog.Stat s:Catalog.Stat.values())if(a.reward().getOrDefault(s,0)>0)parts.add(s.label+" "+s.format(a.reward().get(s)));System.out.println(String.join("\\t","AWARD",a.name(),a.description(),String.join(", ",parts)));}
for(Catalog.Stat s:Catalog.Stat.values())if(c.bonuses(c.entries.keySet()).get(s)!=s.cap)throw new AssertionError("cap "+s);
UUID id=UUID.randomUUID();String pending;Path file=Path.of(args[0]);try(CodexStore db=new CodexStore(file)){db.player(id,"Artifact");var r=db.reserve(id,"oak_log",0,2,"before","after");db.finish(r.id(),c,false);db.enabled(id,false);pending=db.reserve(id,"diamond",0,2,"b","a").id();db.snapshot(Path.of(args[1]));}
try(CodexStore db=new CodexStore(file)){if(!db.registered(id).equals(Set.of("oak_log"))||db.enabled(id)||!db.receipt(pending).state().equals("REVIEW"))throw new AssertionError("persistence");}
System.out.println("PACKAGED_SQLITE_OK");}}
''',encoding='utf-8')
    subprocess.run([str(JDK/'javac'),'--release','21','-encoding','UTF-8','-cp',str(jar),'-d',str(temp),str(smoke)],check=True)
    # No external SQLite JAR: this proves the actual production artifact contains the driver/native library.
    run_cp=os.pathsep.join(map(str,[temp,jar,VENDOR/'slf4j-api.jar',VENDOR/'slf4j-simple.jar']))
    result=subprocess.run([str(JDK/'java'),'--enable-native-access=ALL-UNNAMED','-cp',run_cp,'kr.minecraft.survival.codex.CodexArtifact',str(temp/'test.db'),str(temp/'snapshot.db')],check=True,capture_output=True,text=True)
    assert 'PACKAGED_SQLITE_OK' in result.stdout
    lines=result.stdout.splitlines();items=[line.split('\t')[1:] for line in lines if line.startswith('ENTRY\t')];awards=[line.split('\t')[1:] for line in lines if line.startswith('AWARD\t')];assert len(items)==85 and len(awards)==13
table='# 아이템별 등록 보너스\n\n같은 분야 안에서 고급 2배·희귀 4배·전설 8배입니다. 기본 아이템 또는 정품 농장 수확물 1개를 소비하며 영구 등록은 한 번입니다.\n\n| 분야 | 아이템 | 희귀도 | 등록 효과 |\n| --- | --- | --- | --- |\n'
table+=''.join('| '+category+' | '+name+' | '+rarity+' | '+stat+' '+value+' |\n' for category,name,rarity,material,stat,value in items)
table+='\n## 업적 추가 보상\n\n| 업적 | 목표 | 추가 효과 |\n| --- | --- | --- |\n'+''.join('| '+' | '.join(row)+' |\n' for row in awards)
(OUT/'아이템별보너스.md').write_text(table,encoding='utf-8');shutil.copyfile(MODULE/'README.md',OUT/'설치·사용안내.md')
report={'version':'1.1.0','target':'Paper 26.3 / Java 25','real_api_compile':'unmodified actual Paper df120fd API source build','collection_entries':85,'achievements':13,'small_bonus_stats':8,'rarity_multipliers':[1,2,4,8],'consumption':'one plain item or canonical custom farm item per permanent UUID entry','mockbukkit_cases':int(tests.get('tests')),'mockbukkit_version':'26.3:4.120.0','test_failures':0,'test_skipped':0,'sqlite':'real SQLite JDBC 3.51.1.0, file persistence and fault cases','production_jar_sqlite_native_smoke':True,'windows_native_driver_included':True,'player_nbt_saves_tested':False,'player_nbt_test_substitute':'save call counting and injected failure','mining_attribute_mock_preparation':'real MockBukkit AttributeInstanceMock with vanilla base 1, absent from its default map','real_minecraft_client_or_native_combat_mining_tested':False,'windows_server_applied':False,'caps':{'movement_percent':3,'mining_percent':6,'health_hp':2,'attack_damage':0.5,'armor':1,'attack_speed_percent':2,'knockback_resistance_percentage_points':2,'luck':0.5},'source_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (MODULE/'src/main').rglob('*') if p.is_file()}}
(OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
bundle={'plugins/'+jar.name:jar.read_bytes(),'설치·사용안내.md':(OUT/'설치·사용안내.md').read_bytes(),'아이템별보너스.md':(OUT/'아이템별보너스.md').read_bytes(),'validation.json':(OUT/'validation.json').read_bytes(),'LICENSE.txt':(MODULE/'LICENSE.txt').read_bytes()}
for p in MODULE.rglob('*'):
    if p.is_file() and 'build' not in p.relative_to(MODULE).parts and '__pycache__' not in p.parts:bundle['소스/'+str(p.relative_to(ROOT))]=p.read_bytes()
bundle['SHA256SUMS.txt']='\n'.join(hashlib.sha256(data).hexdigest()+'  '+name for name,data in sorted(bundle.items())).encode()
archive=OUT/'KoreanCodex-설치묶음-1.1.0.zip';zip_write(archive,bundle)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
(OUT/'SHA256SUMS.txt').write_text('\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name for p in [jar,archive])+'\n')
print('Built',jar,'and',archive);print('PASS: 85 entries, 13 awards, 8 exact caps, packaged native SQLite restart/snapshot smoke')
