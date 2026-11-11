#!/usr/bin/env python3
"""Produce a coherent installer ZIP only after recorded checks and real API builds."""
from pathlib import Path
import os,subprocess,tempfile,zipfile,json,hashlib,io,xml.etree.ElementTree as ET,re
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'releases/tycoon-2.0';BASE=Path('/workspace/toolchains');JDK=BASE/'jdk-25.0.5.0.3/bin';VENDOR=ROOT/'survival-pack/vendor'
def sha(data):return hashlib.sha256(data).hexdigest()
xml=BASE/'tycoon-integration/build/test-results/test/TEST-kr.minecraft.survival.economy.TycoonIntegrationTest.xml'
tests=ET.parse(xml).getroot();assert tests.get('failures')=='0' and tests.get('errors')=='0' and tests.get('skipped')=='0'
assert int(tests.get('tests'))>=26
assert xml.stat().st_mtime>=max(p.stat().st_mtime for module in ['korean-economy','korean-menu'] for p in (ROOT/module/'src').rglob('*.java') if 'integrationTest' in p.parts or 'main' in p.parts),'Run integration tests after source edits'
domain=json.loads((ROOT/'korean-economy/build-domain/test-results.json').read_text());installer=json.loads((OUT/'installer-validation.json').read_text());setup=json.loads((ROOT/'korean-pack-setup/build/test-results.json').read_text());translator=(ROOT/'korean-commands/build/test-results.txt').read_text()
provenance=json.loads((OUT/'build-provenance.json').read_text())
payload={}
for artifact in provenance['artifacts']:
    p=OUT/artifact['jar'];assert sha(p.read_bytes())==artifact['sha256'];payload[p.name]=p.read_bytes()
    for rel,digest in artifact['source_sha256'].items():assert sha((ROOT/rel).read_bytes())==digest,'Rebuild modified source: '+rel
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None;assert re.search(r'(?m)^version: 2\.0\.0$',z.read('plugin.yml').decode())
        if p.name.startswith('KoreanPackSetup'):assert all(n in z.namelist() for n in ['defaults/Essentials.yml','defaults/QuickShop.yml','locales/Essentials.properties','locales/QuickShop.yml'])
        assert not any(n.startswith(('org/bukkit/','io/papermc/','org/mockbukkit/','net/milkbowl/')) for n in z.namelist())
for artifact in json.loads((VENDOR/'sources.json').read_text()):
    if artifact['file'] in ['Vault.jar','QuickShop-Hikari-6.3.0.4.jar']:
        data=(VENDOR/artifact['file']).read_bytes();assert sha(data)==artifact['sha256'];payload[artifact['file']]=data

with tempfile.TemporaryDirectory(prefix='tycoon-packaging-') as tmp:
    temp=Path(tmp);classes=temp/'classes';classes.mkdir()
    subprocess.run([str(JDK/'javac'),'--release','21','-encoding','UTF-8','-d',str(classes),str(ROOT/'tools/installer/PackInstaller.java')],check=True)
    subprocess.run([str(JDK/'jar'),'--create','--file',str(OUT/'설치도구.jar'),'--main-class','PackInstaller','-C',str(classes),'.'],check=True)
    smoke=temp/'EmbeddedDbSmoke.java';smoke.write_text('''import java.nio.file.*;import java.util.*;import kr.minecraft.survival.economy.domain.*;
public class EmbeddedDbSmoke {public static void main(String[] a)throws Exception {Path f=Path.of(a[0]);UUID id=UUID.randomUUID();String rid;try(StateStore db=new StateStore(f)){db.player(id,"Packaged");rid=db.reserve(id,"TEST","packaged-driver",0,"",null).id();db.snapshot(Path.of(a[1]));}try(StateStore db=new StateStore(f)){if(!db.receipt(rid).state().equals("REVIEW"))throw new AssertionError("restart journal");}if(!StateStore.validSnapshot(Path.of(a[1])))throw new AssertionError("snapshot");System.out.println("PASS: embedded SQLite driver and restart journal from production JAR");}}''')
    jar=OUT/'KoreanEconomy-2.0.0.jar';subprocess.run([str(JDK/'javac'),'--release','21','-cp',str(jar),'-d',str(temp),str(smoke)],check=True)
    cp=os.pathsep.join(map(str,[temp,jar,VENDOR/'tycoon/slf4j-api.jar',VENDOR/'tycoon/slf4j-simple.jar']))
    subprocess.run([str(JDK/'java'),'--enable-native-access=ALL-UNNAMED','-cp',cp,'EmbeddedDbSmoke',str(temp/'test.db'),str(temp/'snapshot.db')],check=True)

report={'version':'2.0.0','target':'Paper 26.3 / Java 25','production_compile':'real Paper API from unmodified df120fd source','domain':domain,'mockbukkit':{'version':'26.3:4.120.0','test_cases':int(tests.get('tests')),'failures':0,'real_essentials_provider':False,'real_player_nbt_saves':False},'setup':setup,'translator_assertions':int(re.search(r'PASS: (\d+)',translator).group(1)),'installer':installer,'production_embedded_sqlite_smoke_passed':True,'actual_paper_server_booted':False,'minecraft_client_connected':False,'windows_launcher_executed':False,'user_windows_server_applied':False,'official_essentials_dev_jar_included':False,'essentials_dev_download':'reuse installed compatible core/Spawn; otherwise official CI at Windows install time','resourcepack_client_visual_tested':False,'legacy12_sqlite_migration_tested':True,'existing_gp_prism_backup_commands_preserved':True,'gp_prism_runtime_2_0_tested':False,'previous_server_version':'1.2.0, user confirmed installed'}
(OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
sources=io.BytesIO()
with zipfile.ZipFile(sources,'w',zipfile.ZIP_DEFLATED) as z:
    for module in ['korean-economy','korean-menu','korean-commands','korean-pack-setup','tools','tycoon']:
        for p in sorted((ROOT/module).rglob('*')):
            rel=p.relative_to(ROOT)
            if p.is_file() and not any(part.startswith('build') and part not in ['build.py','build.sh','build_exact_paper_api.py','build_tycoon.py','build_tycoon_resourcepack.py'] for part in rel.parts[:-1]) and '__pycache__' not in rel.parts and p.suffix not in ['.class','.jar','.pyc']:
                z.write(p,rel)
    z.write(ROOT/'TYCOON-WORKPLAN.md','TYCOON-WORKPLAN.md')

zip_path=OUT/'생활타이쿤-통합설치-2.0.0.zip'
with zipfile.ZipFile(zip_path,'w',zipfile.ZIP_DEFLATED) as z:
    for name,data in payload.items():z.writestr('plugins/'+name,data)
    z.writestr('plugins/SHA256.properties','\n'.join(name+'='+sha(data) for name,data in payload.items())+'\n')
    z.writestr('설치도구.jar',(OUT/'설치도구.jar').read_bytes())
    z.writestr('설치하기.cmd',b'@echo off\r\ncd /d "%~dp0"\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"\r\npause\r\n')
    z.writestr('install.ps1',(ROOT/'tools/installer/설치.ps1').read_text(encoding='utf-8-sig').replace('\r\n','\n').replace('\n','\r\n').encode('utf-8-sig'))
    z.writestr('EssentialsX-개발빌드받기.ps1',(ROOT/'survival-pack/EssentialsX-개발빌드받기.ps1').read_text(encoding='utf-8-sig').replace('\r\n','\n').replace('\n','\r\n').encode('utf-8-sig'))
    for p in (ROOT/'tycoon').glob('*'):
        if p.is_file():z.writestr(p.name,p.read_bytes())
    for name in ['validation.json','build-provenance.json','생활타이쿤-리소스팩-26.3.zip']:z.write(OUT/name,name)
    z.writestr('소스/자체플러그인-2.0.0-source.zip',sources.getvalue())
    for p in (VENDOR/'upstream-source').glob('*.zip'):z.write(p,'소스/'+p.name)
    z.write(VENDOR/'sources.json','소스/official-binaries.json');z.write(VENDOR/'upstream-source/sources.json','소스/official-sources.json')
with zipfile.ZipFile(zip_path) as z:
    assert z.testzip() is None
    assert 'plugins/EssentialsX-2.22.0.jar' not in z.namelist()
    for name,data in payload.items():assert sha(z.read('plugins/'+name))==sha(data)
(OUT/'SHA256SUMS.txt').write_text('\n'.join(sha(p.read_bytes())+'  '+p.name for p in sorted(OUT.iterdir()) if p.suffix in ['.jar','.zip'])+'\n')
print('Verified installer ZIP:',zip_path,'bytes:',zip_path.stat().st_size)
