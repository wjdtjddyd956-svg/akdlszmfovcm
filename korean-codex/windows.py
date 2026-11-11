"""Windows build/test/package using the installed Paper build 238 API, never fixtures."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, tempfile, zipfile, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1];MODULE=ROOT/'korean-codex';OUT=ROOT/'releases/korean-codex/1.0.1'
p=argparse.ArgumentParser();p.add_argument('action',choices=['build','test','package','probe']);p.add_argument('--server',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);p.add_argument('--deps',type=Path,default=ROOT/'.build/codex-tools/deps');a=p.parse_args()
WORK=ROOT/'.build/codex-windows';WORK.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
api=a.server/'libraries/io/papermc/paper/paper-api/26.3.build.238-stable/paper-api-26.3.build.238-stable.jar'
assert api.is_file(),'Installed Paper build 238 API required'
serverdeps=[api]+[f for f in sorted((a.server/'libraries').rglob('*.jar')) if 'paper-api' not in f.parts]
carrier=ROOT/'releases/korean-codex/KoreanCodex-1.0.0.jar';jar=OUT/'KoreanCodex-1.0.1.jar'
def sha(b):return hashlib.sha256(b).hexdigest()
def run(exe,args,name):
    f=WORK/(name+'.args');f.write_text('\n'.join('"'+str(v).replace('\\','/').replace('"','\\"')+'"' for v in args),encoding='utf-8')
    subprocess.run([str(a.jdk/'bin'/exe),'@'+str(f)],check=True)
def compile_sources(files,dest,cp,name):
    dest.mkdir(parents=True,exist_ok=True);run('javac.exe',['--release','21','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',dest,*files],name)
if a.action=='build':
    expected=next(line.split()[0] for line in (carrier.parent/'SHA256SUMS.txt').read_text().splitlines() if line.endswith(carrier.name))
    assert sha(carrier.read_bytes())==expected,'Original SQLite dependency carrier changed'
    classes=Path(tempfile.mkdtemp(prefix='classes-',dir=WORK));compile_sources(sorted((MODULE/'src/main/java').rglob('*.java')),classes,serverdeps,'production')
    entries={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
    for f in (MODULE/'src/main/resources').rglob('*'):
        if f.is_file():entries[f.relative_to(MODULE/'src/main/resources').as_posix()]=f.read_bytes()
    with zipfile.ZipFile(carrier) as old:
        for n in old.namelist():
            if n.startswith(('org/sqlite/','META-INF/versions/','META-INF/services/')) or n.startswith(('LICENSE','META-INF/LICENSE','META-INF/NOTICE','META-INF/maven/org.xerial/')):
                if n.startswith('org/sqlite/native/') and not n.startswith('org/sqlite/native/Windows/'):continue
                if not n.endswith('/'):entries[n]=old.read(n)
    entries['LICENSE-KoreanCodex.txt']=(MODULE/'LICENSE.txt').read_bytes()
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in sorted(entries.items()):
            i=zipfile.ZipInfo(name,(2026,11,11,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o644<<16;z.writestr(i,data)
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        assert 'org/sqlite/native/Windows/x86_64/sqlitejdbc.dll' in z.namelist()
        assert not any(n.startswith(('org/bukkit/','io/papermc/','org/mockbukkit/')) for n in z.namelist())
    report={'version':'1.0.1','api':api.name,'api_sha256':sha(api.read_bytes()),'java':subprocess.check_output([str(a.jdk/'bin/java.exe'),'-version'],stderr=subprocess.STDOUT,text=True),'fixture_api':False,'sqlite_dependency_carrier_sha256':expected,'platform':'Windows; original SQLite DLLs and licenses preserved','jar_sha256':sha(jar.read_bytes()),'source_sha256':{f.relative_to(ROOT).as_posix():sha(f.read_bytes()) for f in sorted((MODULE/'src/main').rglob('*')) if f.is_file()}}
    (OUT/'build-info.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print('PASS: production build with installed real Paper API')
elif a.action=='test':
    test_java=subprocess.check_output([str(a.jdk/'bin/java.exe'),'-version'],stderr=subprocess.STDOUT,text=True)
    if not re.search(r'version "25(?:\.|\")',test_java):raise SystemExit('MockBukkit test harness requires the validated JDK 25 (--jdk). Production build and real Paper tests separately support the installed JDK 27.')
    assert (a.deps/'mockbukkit-v26.3-4.120.0.jar').is_file(),'Resolve test-pom.xml dependencies first'
    # java.exe's Windows argument-file reader can use the legacy native encoding.
    # Keep runtime library paths ASCII and short without changing the installed server.
    libs=WORK/'paper-libs';libs.mkdir(exist_ok=True)
    for i,f in enumerate(serverdeps):
        dest=libs/(str(i)+'-'+f.name)
        if not dest.exists() or sha(dest.read_bytes())!=sha(f.read_bytes()):shutil.copy2(f,dest)
    classes=Path(tempfile.mkdtemp(prefix='tests-',dir=WORK));cp=[*sorted(a.deps.glob('*.jar')),*sorted(libs.glob('*.jar')),jar]
    sources=[*sorted((MODULE/'src/main/java').rglob('*.java')),*sorted((MODULE/'src/test/java').rglob('*.java')),*sorted((ROOT/'korean-menu/src/main/java').rglob('*.java'))]
    compile_sources(sources,classes,cp,'tests-compile');reportdir=WORK/'test-results';reportdir.mkdir(exist_ok=True)
    run('java.exe',['--enable-native-access=ALL-UNNAMED','--add-opens=java.base/java.lang=ALL-UNNAMED','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dcodex.repo='+str(ROOT),'-cp',os.pathsep.join(map(str,[classes,MODULE/'src/main/resources',*cp])),'org.junit.platform.console.ConsoleLauncher','execute','--select-class','kr.minecraft.survival.codex.CodexTest','--disable-banner','--details','summary','--reports-dir',reportdir],'tests-run')
    suites=[ET.parse(f).getroot() for f in reportdir.glob('TEST-*.xml')];count=sum(int(s.get('tests','0')) for s in suites);assert count>=33
    assert all(int(s.get(k,'0'))==0 for s in suites for k in ['failures','errors','skipped'])
    (OUT/'mock-tests.json').write_text(json.dumps({'cases':count,'failures':0,'skipped':0,'test_java':test_java,'mockbukkit':'26.3:4.120.0','real_sqlite':True,'player_saves':'test double: counts and injected failures','production_jar_sha256':sha(jar.read_bytes())},indent=2)+'\n',encoding='utf-8')
    print('PASS:',count,'MockBukkit cases with real SQLite; player saves are test doubles')
elif a.action=='probe':
    classes=Path(tempfile.mkdtemp(prefix='probe-',dir=WORK));compile_sources([MODULE/'src/runtimeTest/CodexProbe.java'],classes,serverdeps,'probe-compile')
    with zipfile.ZipFile(WORK/'CodexProbe.jar','w') as z:
        z.write(classes/'CodexProbe.class','CodexProbe.class');z.writestr('plugin.yml',"name: CodexProbe\nversion: test-only\nmain: CodexProbe\napi-version: '26.3'\ndepend: [KoreanCodex]\ncommands:\n  codexprobe:\n    permission: codexprobe.never\npermissions:\n  codexprobe.never:\n    default: op\n")
    print('Built isolated-test-only probe:',WORK/'CodexProbe.jar')
else:
    report=json.loads((OUT/'build-info.json').read_text(encoding='utf-8'));assert sha(jar.read_bytes())==report['jar_sha256']
    for name,digest in report['source_sha256'].items():assert sha((ROOT/name).read_bytes())==digest,name
    tests=json.loads((OUT/'mock-tests.json').read_text());assert tests['production_jar_sha256']==report['jar_sha256']
    classes=Path(tempfile.mkdtemp(prefix='catalog-',dir=WORK));compile_sources([MODULE/'src/test/java/kr/minecraft/survival/codex/CatalogReport.java'],classes,[jar],'catalog-compile')
    result=subprocess.check_output([str(a.jdk/'bin/java.exe'),'--enable-native-access=ALL-UNNAMED','-Dstdout.encoding=UTF-8','-cp',os.pathsep.join(map(str,[classes,jar])),'kr.minecraft.survival.codex.CatalogReport',str(classes/'test.db')],encoding='utf-8')
    assert 'PACKAGED_SQLITE_OK' in result
    rows=[x.split('\t')[1:] for x in result.splitlines() if x.startswith('ENTRY\t')];awards=[x.split('\t')[1:] for x in result.splitlines() if x.startswith('AWARD\t')];assert len(rows)==80 and len(awards)==12
    table='# 아이템별 보너스 — 1.0.1\n\n모두 기본 아이템 1개 소비, UUID별 1회 등록. 같은 분야 일반 대비 고급 2배·희귀 4배·전설 8배. 드래곤 알은 제외합니다. 넉백 저항은 %p입니다.\n\n| 분야 | 아이템 | 희귀도 | 효과 |\n|---|---|---|---|\n'+''.join('| '+' | '.join(row)+' |\n' for row in rows)
    table+='\n## 업적 추가 보너스\n\n| 업적 | 목표 | 효과 |\n|---|---|---|\n'+''.join('| '+' | '.join(row)+' |\n' for row in awards)
    (OUT/'아이템별보너스.md').write_text(table,encoding='utf-8')
    archive=OUT/'KoreanCodex-Windows-설치-1.0.1.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        z.write(jar,'plugins/'+jar.name);z.writestr('plugins/SHA256.properties',jar.name+'='+report['jar_sha256']+'\n')
        z.write(ROOT/'releases/tycoon-2.0/설치도구.jar','전체백업도구.jar')
        z.writestr('전체백업도구.sha256',sha((ROOT/'releases/tycoon-2.0/설치도구.jar').read_bytes())+'\n')
        z.writestr('설치하기.cmd',b'@echo off\r\ncd /d "%~dp0"\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"\r\npause\r\n')
        z.writestr('install.ps1',(MODULE/'install.ps1').read_text(encoding='utf-8-sig').replace('\r\n','\n').replace('\n','\r\n').encode('utf-8-sig'))
        for f in sorted(OUT.iterdir()):
            if f.suffix in ['.md','.json','.png']:z.write(f,f.name)
        for f in sorted((MODULE/'src').rglob('*')):
            if f.is_file():z.write(f,'소스/korean-codex/'+f.relative_to(MODULE).as_posix())
        for name in ['windows.py','install.ps1','test-pom.xml','LICENSE.txt']:z.write(MODULE/name,'소스/korean-codex/'+name)
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None
    (OUT/'SHA256SUMS.txt').write_text('\n'.join(sha(f.read_bytes())+'  '+f.name for f in [jar,archive])+'\n',encoding='utf-8');print('PASS: Windows package, 80-row generated bonus table, SQLite restart and snapshot')
