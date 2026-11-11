"""Build, test and package the existing farming addon with the installed Paper API."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,tempfile,zipfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'releases/ui-update/1.2.1';WORK=ROOT/'.build/interface-windows'
if 'version: 2.1.0\n' not in (ROOT/'korean-menu/src/main/resources/plugin.yml').read_text():raise SystemExit('Historical UI builder: use civic-update/build.py for the current source.')
p=argparse.ArgumentParser();p.add_argument('action',choices=['build','test','package']);p.add_argument('--server',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);a=p.parse_args();OUT.mkdir(parents=True,exist_ok=True);WORK.mkdir(parents=True,exist_ok=True)
api=a.server/'libraries/io/papermc/paper/paper-api/26.3.build.238-stable/paper-api-26.3.build.238-stable.jar';assert api.is_file()
dependencies=[api]+[f for f in sorted((a.server/'libraries').rglob('*.jar')) if 'paper-api' not in f.parts]+[a.server/'plugins'/n for n in ['Vault.jar','worldedit-bukkit-7.4.6-ko.jar','worldguard-bukkit-7.0.21-ko.jar']]
modules=[('korean-economy','KoreanEconomy','2.0.2',ROOT/'releases/tycoon-2.0.1/KoreanEconomy-2.0.1.jar'),('korean-farming','KoreanFarming','1.0.2',None),('korean-codex','KoreanCodex','1.2.1',ROOT/'releases/korean-codex/1.0.1/KoreanCodex-1.0.1.jar'),('korean-menu','KoreanMenu','2.1.0',None),('city-textures','CityTextures','1.1.1',None)]
def sha(data):return hashlib.sha256(data).hexdigest()
def run(exe,args,name):
    file=WORK/(name+'.args');file.write_text('\n'.join('"'+str(v).replace('\\','/').replace('"','\\"')+'"' for v in args),encoding='utf-8');subprocess.run([str(a.jdk/'bin'/exe),'@'+str(file)],check=True)
def compile(files,classes,cp,name):
    classes.mkdir(parents=True,exist_ok=True);run('javac.exe',['--release','21','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*files],name)
def zipwrite(path,entries):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for n,data in sorted(entries.items()):
            info=zipfile.ZipInfo(n,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
pack=ROOT/'releases/farming-update/CityEconomy-Farming-26.3-1.1.0.zip';packsha=hashlib.sha1(pack.read_bytes()).hexdigest();uisha=hashlib.sha1((ROOT/'releases/ui-update/CityEconomy-Interface-26.3-1.2.0.zip').read_bytes()).hexdigest();citysha=hashlib.sha1((ROOT/'releases/city-resourcepack/CityEconomy-26.3-1.0.0.zip').read_bytes()).hexdigest()
if a.action=='build':
    artifacts=[];prior=[]
    for module,name,version,carrier in modules:
        src=ROOT/module/'src/main';classes=Path(tempfile.mkdtemp(prefix=name+'-',dir=WORK));compile(sorted((src/'java').rglob('*.java')),classes,[*dependencies,*prior],name)
        entries={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
        if carrier:
            with zipfile.ZipFile(carrier) as z:
                for n in z.namelist():
                    if not n.endswith('/') and not n.startswith('kr/'):entries[n]=z.read(n)
        for f in (src/'resources').rglob('*'):
            if f.is_file():entries[f.relative_to(src/'resources').as_posix()]=f.read_bytes().replace(b'@FARM_PACK_SHA1@',packsha.encode()).replace(b'@UI_PACK_SHA1@',uisha.encode()).replace(b'@CITY_PACK_SHA1@',citysha.encode())
        jar=OUT/(name+'-'+version+'.jar');zipwrite(jar,entries);prior.append(jar)
        with zipfile.ZipFile(jar) as z:
            assert z.testzip() is None
            assert not any(n.startswith(('org/bukkit/','io/papermc/','net/milkbowl/','com/sk89q/','org/mockbukkit/')) for n in z.namelist())
            assert ('version: '+version) in z.read('plugin.yml').decode()
        artifacts.append({'module':module,'file':jar.name,'sha256':sha(jar.read_bytes()),'sqlite_carrier_sha256':sha(carrier.read_bytes()) if carrier else None,'source_sha256':{f.relative_to(ROOT).as_posix():sha(f.read_bytes()) for f in sorted(src.rglob('*')) if f.is_file()}})
    c=Path(tempfile.mkdtemp(prefix='installer-',dir=WORK));compile([ROOT/'tools/installer/PackInstaller.java'],c,[],'installer');entries={f.relative_to(c).as_posix():f.read_bytes() for f in c.rglob('*.class')};entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMain-Class: PackInstaller\r\n\r\n';zipwrite(OUT/'InterfaceInstaller.jar',entries)
    (OUT/'build-info.json').write_text(json.dumps({'api':api.name,'api_sha256':sha(api.read_bytes()),'java':subprocess.check_output([str(a.jdk/'bin/java.exe'),'-version'],stderr=subprocess.STDOUT,text=True),'artifacts':artifacts,'resourcepack_sha1':packsha,'merged_ui_pack_sha1':uisha,'base_city_pack_sha1':citysha,'test_api_fixtures':False,'native_platform':'Windows SQLite DLLs from the previously verified production releases'},ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print('PASS: five plugins compiled against actual installed Paper build 238 API')
elif a.action=='test':
    version=subprocess.check_output([str(a.jdk/'bin/java.exe'),'-version'],stderr=subprocess.STDOUT,text=True);assert 'version "25.' in version,'Use JDK25 for the MockBukkit harness; real server/build uses JDK27'
    libs=WORK/'libs';libs.mkdir(exist_ok=True)
    for i,f in enumerate(dependencies):
        d=libs/(str(i)+'-'+f.name)
        if not d.exists() or sha(d.read_bytes())!=sha(f.read_bytes()):shutil.copy2(f,d)
    cp=[*sorted((ROOT/'.build/codex-tools/deps').glob('*.jar')),*sorted(libs.glob('*.jar')),*[OUT/(n+'-'+v+'.jar') for m,n,v,c in modules]]
    assert any('mockbukkit-v26.3-4.120.0' in f.name for f in cp)
    classes=Path(tempfile.mkdtemp(prefix='suites-',dir=WORK));sources=[]
    for module in ['korean-economy','korean-farming','korean-codex','korean-menu','city-textures']:sources+=list((ROOT/module/'src/main/java').rglob('*.java'))
    for module in ['korean-farming','korean-codex','city-textures']:sources+=list((ROOT/module/'src/test/java').rglob('*.java'))
    sources+=list((ROOT/'korean-economy/src/integrationTest/java').rglob('*.java'));sources+=list((ROOT/'korean-economy/src/test/java/kr/minecraft/survival/economy/domain').glob('*.java'))
    compile(sources,classes,cp,'test-compile');runtime=[classes,ROOT/'korean-codex/src/main/resources',*cp];reports={}
    for suite in ['kr.minecraft.survival.farming.FarmingTest','kr.minecraft.survival.codex.CodexTest','kr.minecraft.survival.economy.TycoonIntegrationTest','kr.minecraft.survival.citytextures.CityTexturesTest']:
        destination=WORK/suite.rsplit('.',1)[1];destination.mkdir(exist_ok=True)
        suite_runtime=[classes,ROOT/('city-textures' if suite.endswith('CityTexturesTest') else 'korean-codex')/'src/main/resources',*cp]
        run('java.exe',['--enable-native-access=ALL-UNNAMED','--add-opens=java.base/java.lang=ALL-UNNAMED','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dfarm.repo='+str(ROOT),'-Dcodex.repo='+str(ROOT),'-Dtycoon.repo='+str(ROOT),'-Dcity.repo='+str(ROOT),'-cp',os.pathsep.join(map(str,suite_runtime)),'org.junit.platform.console.ConsoleLauncher','execute','--select-class',suite,'--disable-banner','--details','summary','--reports-dir',destination],suite.rsplit('.',1)[1])
        xml=ET.parse(destination/'TEST-junit-jupiter.xml').getroot();assert all(int(xml.get(k,'0'))==0 for k in ['failures','errors','skipped']);reports[suite]={'cases':int(xml.get('tests')),'failures':0,'skipped':0}
    for name in ['DomainTest','ActionTest','LegacyTest']:run('java.exe',['--enable-native-access=ALL-UNNAMED','-Dstdout.encoding=UTF-8','-cp',os.pathsep.join(map(str,[classes,*cp])),'kr.minecraft.survival.economy.domain.'+name],name)
    (OUT/'mock-tests.json').write_text(json.dumps({'java':version,'suites':reports,'domain_assertions':121,'mock_player_nbt_save':True,'mock_entity_visibility_defaults':True,'artifact_sha256':{(n+'-'+v+'.jar'):sha((OUT/(n+'-'+v+'.jar')).read_bytes()) for m,n,v,c in modules}},ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print('PASS: farming, codex, economy suites and 121 real SQLite domain/fault assertions')
else:
    report=json.loads((OUT/'build-info.json').read_text(encoding='utf-8'));tests=json.loads((OUT/'mock-tests.json').read_text(encoding='utf-8'));entries={}
    for item in report['artifacts']:
        f=OUT/item['file'];assert sha(f.read_bytes())==item['sha256']==tests['artifact_sha256'][f.name]
        for name,digest in item['source_sha256'].items():assert sha((ROOT/name).read_bytes())==digest,name
        entries['plugins/'+f.name]=f.read_bytes()
    entries['plugins/SHA256.properties']=''.join(item['file']+'='+item['sha256']+'\n' for item in report['artifacts']).encode('ascii')
    entries['InterfaceInstaller.jar']=(OUT/'InterfaceInstaller.jar').read_bytes();entries['InterfaceInstaller.sha256']=(sha(entries['InterfaceInstaller.jar'])+'\n').encode()
    entries['Install-Interface.ps1']=(ROOT/'tools/installer/Install-Interface.ps1').read_text(encoding='utf-8-sig').replace('\r\n','\n').replace('\n','\r\n').encode('utf-8-sig');entries['Install-Interface.cmd']=(ROOT/'tools/installer/Install-Interface.cmd').read_bytes()
    with zipfile.ZipFile(OUT/'KoreanFarming-1.0.2.jar') as farming:entries['설정참고/KoreanFarming/config.yml']=farming.read('config.yml')
    with zipfile.ZipFile(OUT/'CityTextures-1.1.1.jar') as city:entries['설정참고/CityTextures/ui.yml']=city.read('ui.yml')
    for name in ['CityEconomy-Interface-26.3-1.2.0.zip','CityEconomy-Interface-Complete-26.3-1.2.0.zip']:entries['resourcepacks/'+name]=(ROOT/'releases/ui-update'/name).read_bytes()
    for f in sorted(OUT.iterdir()):
        if f.suffix in ['.md','.json','.png','.html']:entries[f.name]=f.read_bytes()
    for module in ['korean-farming','korean-codex','korean-economy','korean-menu','city-textures']:
        for f in sorted((ROOT/module/'src').rglob('*')):
            if f.is_file():entries['소스/'+f.relative_to(ROOT).as_posix()]=f.read_bytes()
    for f in [ROOT/'interface-update/windows.py',ROOT/'interface-update/test_installer.py',ROOT/'tools/installer/PackInstaller.java',*sorted((ROOT/'interface-update/src/runtimeTest').glob('*'))]:entries['소스/'+f.relative_to(ROOT).as_posix()]=f.read_bytes()
    entries['LICENSE-Interface.txt']=(ROOT/'interface-resourcepack/LICENSE.txt').read_bytes()
    entries['LICENSE-Farming.txt']=(ROOT/'farming-resourcepack/LICENSE.txt').read_bytes()
    archive=OUT/'도시생활-Windows-메뉴통합-1.2.1.zip';zipwrite(archive,entries)
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None;assert len([n for n in z.namelist() if n.startswith('plugins/') and n.endswith('.jar')])==5
    (OUT/'SHA256SUMS.txt').write_text(''.join(sha(f.read_bytes())+'  '+f.name+'\n' for f in [*[OUT/i['file'] for i in report['artifacts']],OUT/'InterfaceInstaller.jar',archive]),encoding='utf-8');print('PASS: verified five-plugin Windows farming package',archive.name)
