#!/usr/bin/env python3
from pathlib import Path
import json
import os
import re
import shutil
import subprocess

ROOT=Path(__file__).resolve().parent
TOOLS=Path(os.environ.get('TYCOON_JDK','/workspace/toolchains/jdk-25.0.5.0.3'))
JAVA=str(TOOLS/'bin/java') if (TOOLS/'bin/java').exists() else 'java'
JAVAC=str(TOOLS/'bin/javac') if (TOOLS/'bin/javac').exists() else 'javac'
BUILD=ROOT/'build-domain'
if BUILD.exists():shutil.rmtree(BUILD)
BUILD.mkdir()
sources=list((ROOT/'src/main/java/kr/minecraft/survival/economy/domain').glob('*.java'))+list((ROOT/'src/test/java/kr/minecraft/survival/economy/domain').glob('*.java'))
subprocess.run([JAVAC,'--release','21','-encoding','UTF-8','-d',str(BUILD)]+[str(p) for p in sources],check=True)
deps=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file())/'개발자료/배포도구/경제야생/vendor/tycoon'
cp=os.pathsep.join([str(BUILD)]+[str(deps/n) for n in ['sqlite-jdbc.jar','slf4j-api.jar','slf4j-simple.jar']])
results={}
for name in ['DomainTest','ActionTest','LegacyTest']:
    run=subprocess.run([JAVA,'--enable-native-access=ALL-UNNAMED','-cp',cp,'kr.minecraft.survival.economy.domain.'+name],capture_output=True,text=True)
    if run.returncode:print(run.stdout);print(run.stderr);run.check_returncode()
    print(run.stdout,end='');results[name]=int(re.search(r'PASS: (\d+)',run.stdout).group(1))
(BUILD/'test-results.json').write_text(json.dumps({'assertions':sum(results.values()),'suites':results,'real_sqlite':True})+'\n')
