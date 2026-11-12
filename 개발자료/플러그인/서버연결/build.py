"""Build only the bridge, using the actual production Paper API and published vendor artifacts."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile,shutil
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());MOD=ROOT/'개발자료/플러그인/서버연결';OUT=ROOT/'releases/plugin-integration/1.0.0';WORK=ROOT/'.build/integration'
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);p.add_argument('--runtime-probe',action='store_true');a=p.parse_args()
OUT.mkdir(parents=True,exist_ok=True);WORK.mkdir(parents=True,exist_ok=True)
api=a.server/'libraries/io/papermc/paper/paper-api/26.3.build.238-stable/paper-api-26.3.build.238-stable.jar';assert api.is_file()
deps=[api,*[f for f in (a.server/'libraries').rglob('*.jar') if 'paper-api' not in f.parts],*list((ROOT/'.build/catalog-vendor').glob('*.jar')),*list((a.server/'plugins').glob('Vault*.jar')),*list((a.server/'plugins').glob('KoreanEconomy*.jar')),*list((a.server/'plugins').glob('KoreanCodex*.jar'))]
classes=WORK/'classes'
assert classes.resolve().parent==WORK.resolve()
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
src=sorted((MOD/'src/main/java').rglob('*.java'))
args=['--release','21','-encoding','UTF-8','-cp',os.pathsep.join(map(str,deps)),'-d',str(classes),*map(str,src)]
argfile=WORK/'compile.args';argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in args),encoding='utf-8')
subprocess.run([str(a.jdk/'bin/javac.exe'),'@'+str(argfile)],check=True)
jar=OUT/'KoreanServerBridge-1.0.0.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
    for f in [*classes.rglob('*.class'),*(MOD/'src/main/resources').rglob('*')]:
        if f.is_file():
            base=classes if f.suffix=='.class' else MOD/'src/main/resources'
            info=zipfile.ZipInfo(f.relative_to(base).as_posix(),(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,f.read_bytes())
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
(OUT/'build-info.json').write_text(json.dumps({'api':api.name,'api_sha256':sha(api),'jar_sha256':sha(jar),'java':subprocess.check_output([str(a.jdk/'bin/java.exe'),'-version'],stderr=subprocess.STDOUT,text=True),'sources':{f.relative_to(ROOT).as_posix():sha(f) for f in (MOD/'src/main').rglob('*') if f.is_file()},'bundled_api_classes':False},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS actual Paper 26.3 build 238 compilation:',jar.name,sha(jar))
if a.runtime_probe:
    probeClasses=WORK/'probe';probeClasses.mkdir(exist_ok=True)
    args=['--release','21','-encoding','UTF-8','-cp',os.pathsep.join(map(str,deps)),'-d',str(probeClasses),str(MOD/'tests/BridgeProbe.java')]
    argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in args),encoding='utf-8')
    subprocess.run([str(a.jdk/'bin/javac.exe'),'@'+str(argfile)],check=True)
    with zipfile.ZipFile(WORK/'BridgeProbe.jar','w',zipfile.ZIP_DEFLATED) as z:
        z.write(probeClasses/'BridgeProbe.class','BridgeProbe.class')
        z.writestr('plugin.yml','name: BridgeProbe\nversion: test-only\nmain: BridgeProbe\napi-version: "26.3"\ndepend: [KoreanServerBridge, TAB]\ncommands:\n  bridgeprobe: {}\n')
    print('Isolated runtime probe built outside release payload')
