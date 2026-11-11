#!/usr/bin/env python3
"""Build the one-time configuration helper, using real SnakeYAML for config tests."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent/'korean-menu/tools'))
from compile_fixtures import write
BUILD=ROOT/'build'
if BUILD.exists():shutil.rmtree(BUILD)
for name in ['fixture-src','fixtures','main','test','libs']:(BUILD/name).mkdir(parents=True)
snake=ROOT.parent/'survival-pack/vendor/setup/snakeyaml-2.2.jar'
assert snake.exists(),'Download https://repo.maven.apache.org/maven2/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar into survival-pack/vendor/setup/'
write(BUILD/'fixture-src')
stub=BUILD/'fixture-src/org/bukkit/plugin/java/JavaPlugin.java'
stub.write_text(stub.read_text().replace('public void onEnable()','public void onLoad(){} public void onEnable()'))
compiler=['java','--module','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-encoding','UTF-8']
def compile(source,dest,cp=None):
    subprocess.run(compiler+(['-cp',str(cp)] if cp else [])+['-d',str(dest)]+[str(p) for p in source.rglob('*.java')],check=True)
compile(BUILD/'fixture-src',BUILD/'fixtures')
cp=str(BUILD/'fixtures')+os.pathsep+str(snake)
compile(ROOT/'src/main/java',BUILD/'main',cp)
shutil.copytree(ROOT/'src/main/resources',BUILD/'main',dirs_exist_ok=True)
with zipfile.ZipFile(ROOT.parent/'survival-pack/vendor/EssentialsX-2.22.0.jar') as z:
    defaults={'defaults/Essentials.yml':z.read('config.yml')}
    defaults['licenses/Essentials.txt']=z.read('META-INF/LICENSE.txt')
with zipfile.ZipFile(ROOT.parent/'survival-pack/vendor/QuickShop-Hikari-6.3.0.4.jar') as z:
    defaults['defaults/QuickShop.yml']=z.read('config.yml')
    defaults['licenses/QuickShop.md']=z.read('license.md')
with zipfile.ZipFile(ROOT.parent/'releases/survival-pack/EconomySurvival-Korean-Pack-1.1.0.zip') as z:
    defaults['locales/Essentials.properties']=z.read('한글설정/Essentials/messages_ko.properties')
    defaults['locales/QuickShop.yml']=z.read('한글설정/QuickShop-Hikari/overrides/ko_kr/messages.yml')
for name,data in defaults.items():
    p=BUILD/'main'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
cp+=os.pathsep+str(BUILD/'main')
compile(ROOT/'src/test/java',BUILD/'test',cp)
test=subprocess.run(['java','-cp',cp+os.pathsep+str(BUILD/'test'),'kr.minecraft.survival.setup.SetupTest'],capture_output=True,text=True)
if test.returncode:
    print(test.stdout);print(test.stderr);test.check_returncode()
print(test.stdout,end='')
report={'assertions':int(re.search(r'PASS: (\d+)',test.stdout).group(1)),'yaml_engine':'SnakeYAML 2.2 (real)','filesystem_tested':True,'actual_paper_runtime_tested':False}
(BUILD/'test-results.json').write_text(json.dumps(report,indent=2)+'\n')
print('Configuration tests only; use tools/build_tycoon.py for production JARs')
