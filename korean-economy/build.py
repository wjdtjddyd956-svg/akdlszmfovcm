#!/usr/bin/env python3
"""Compile against the real Vault economy API plus offline Bukkit signature fixtures."""
from pathlib import Path
import os
import json
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
vault=ROOT.parent/'survival-pack/vendor/Vault.jar'
assert vault.is_file(),'Run survival-pack/fetch_vendor.py first'
compiler=['java','--module','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-encoding','UTF-8']
def compile(source,dest,cp=None):
    subprocess.run(compiler+(['-cp',str(cp)] if cp else [])+['-d',str(dest)]+[str(p) for p in source.rglob('*.java')],check=True)
write(BUILD/'fixture-src')
compile(BUILD/'fixture-src',BUILD/'fixtures')
api=os.pathsep.join([str(BUILD/'fixtures'),str(vault)])
compile(ROOT/'src/main/java',BUILD/'main',api)
cp=api+os.pathsep+str(BUILD/'main')
compile(ROOT/'src/test/java',BUILD/'test',cp)
test=subprocess.run(['java','-cp',cp+os.pathsep+str(BUILD/'test'),'kr.minecraft.survival.economy.EconomyTest'],check=True,capture_output=True,text=True)
print(test.stdout,end='')
(BUILD/'test-results.json').write_text(json.dumps({'assertions':int(re.search(r'PASS: (\d+)',test.stdout).group(1)), 'type':'real Vault API, offline Bukkit fixtures'})+'\n')
if os.environ.get('BUKKIT_API_JAR'):
    shutil.rmtree(BUILD/'main');(BUILD/'main').mkdir()
    compile(ROOT/'src/main/java',BUILD/'main',os.environ['BUKKIT_API_JAR']+os.pathsep+str(vault))
jar=BUILD/'libs/KoreanEconomy-1.0.0.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
    for path in (BUILD/'main').rglob('*.class'):z.write(path,path.relative_to(BUILD/'main'))
    for path in (ROOT/'src/main/resources').rglob('*'):
        if path.is_file():z.write(path,path.relative_to(ROOT/'src/main/resources'))
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    assert not any(n.startswith(('org/','net/')) for n in z.namelist())
print('Built',jar,'; Paper runtime not tested')
