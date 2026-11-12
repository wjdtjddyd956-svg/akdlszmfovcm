from pathlib import Path
import json,shutil,subprocess,zipfile,argparse
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());bundle=ROOT/'releases/plugin-integration/1.0.0';work=ROOT/'.build/integration-installer-tests';work.mkdir(exist_ok=True)
fixture=work/'server';plugins=fixture/'plugins';plugins.mkdir(parents=True,exist_ok=True)
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);a=p.parse_args();actual=a.server
for f in (actual/'plugins').glob('*.jar'):shutil.copy2(f,plugins/f.name)
(fixture/'server.properties').write_text('online-mode=true\n',encoding='ascii')
for name in ['ServerStop.cmd','ServerStart.cmd']:(fixture/name).write_text('@echo off\necho unexpected > "%~dp0UNEXPECTED-STOP"\nexit /b 1\n',encoding='ascii')
(fixture/'life-tools').mkdir(exist_ok=True);(fixture/'life-tools/Run-LifeServer.ps1').write_text("throw 'test stub must not run'\n",encoding='ascii')
def run(expected,word):
    result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(bundle/'Install-PluginIntegration.ps1'),'-ServerPath',str(fixture),'-CheckOnly'],capture_output=True,encoding='utf-8',errors='replace')
    assert (result.returncode==0)==expected,(result.stdout,result.stderr)
    assert word in result.stdout+result.stderr,(word,result.stdout,result.stderr)
    assert not (fixture/'UNEXPECTED-STOP').exists()
    assert not (fixture/'tycoon-server-backups').exists()
checks=[]
run(True,'검사 통과');checks.append('valid exact-version preflight; no stop, write or backup')
first=next(plugins.glob('*.jar'));duplicate=plugins/'duplicate.jar';shutil.copy2(first,duplicate)
run(False,'중복');duplicate.unlink();checks.append('duplicate plugin identity rejected before stop')
(plugins/'TAB').mkdir(exist_ok=True);config=plugins/'TAB/config.yml';config.write_text('operator-custom: keep\n',encoding='utf-8');original=config.read_bytes()
run(False,'기존 설정');assert config.read_bytes()==original;config.unlink();checks.append('custom configuration preserved and conflict rejected')
with zipfile.ZipFile(plugins/'LuckPerms-old.jar','w') as z:z.writestr('plugin.yml','name: LuckPerms\nversion: 0.0.1\n')
run(False,'SHA-256');(plugins/'LuckPerms-old.jar').unlink();checks.append('different installed build rejected rather than downgraded')
target=bundle/'vendor-downloads/LuckPerms-Bukkit-5.5.91.jar';original=target.read_bytes()
try:
    target.write_bytes(original+b'corrupt test byte');run(False,'SHA-256')
finally:target.write_bytes(original)
checks.append('corrupt incoming artifact rejected before any server action')
report={'kind':'Windows PowerShell preflight on synthetic fixture, not gameplay','cases':checks,'unexpected_stop':False,'private_server_files_published':False}
(bundle/'installer-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print('PASS',len(checks),'installer preflight failure checks')
