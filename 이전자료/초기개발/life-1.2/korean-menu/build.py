#!/usr/bin/env python3
"""Build Java 21 plugin and run offline event tests; no network or extra dependencies."""
import os
import json
import re
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'tools'))
from compile_fixtures import write

BUILD = ROOT / 'build'
if BUILD.exists():
    shutil.rmtree(BUILD)
for name in ('fixture-src', 'fixtures', 'main', 'test', 'libs'):
    (BUILD / name).mkdir(parents=True)
COMPILER = ['java', '--module', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '21', '-target', '21', '-encoding', 'UTF-8']
def compile_dir(source, dest, cp=None):
    cmd = COMPILER + (['-cp', str(cp)] if cp else []) + ['-d', str(dest)]
    subprocess.run(cmd + [str(p) for p in source.rglob('*.java')], check=True)

write(BUILD / 'fixture-src')
compile_dir(BUILD / 'fixture-src', BUILD / 'fixtures')
compile_dir(ROOT / 'src/main/java', BUILD / 'main', BUILD / 'fixtures')
cp = os.pathsep.join(str(BUILD / p) for p in ('fixtures', 'main'))
compile_dir(ROOT / 'src/test/java', BUILD / 'test', cp)
test = subprocess.run(['java', '-cp', cp + os.pathsep + str(BUILD / 'test'), 'kr.minecraft.survival.menu.MenuEventTest'], check=True, capture_output=True, text=True)
print(test.stdout, end='')
(BUILD / 'test-results.json').write_text(json.dumps({'assertions': int(re.search(r'PASS: (\d+)', test.stdout).group(1)), 'type': 'offline fixtures'}) + '\n')
if os.environ.get('BUKKIT_API_JAR'):
    shutil.rmtree(BUILD / 'main')
    (BUILD / 'main').mkdir()
    compile_dir(ROOT / 'src/main/java', BUILD / 'main', os.environ['BUKKIT_API_JAR'])
    print('Compiled against real API:', os.environ['BUKKIT_API_JAR'])
else:
    print('Offline signature compilation only; real Paper runtime test has not been run.')

version = re.search(r'^version: (\S+)$', (ROOT / 'src/main/resources/plugin.yml').read_text(encoding='utf-8'), re.MULTILINE).group(1)
jar = BUILD / f'libs/KoreanMenu-{version}.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted((BUILD / 'main').rglob('*.class')):
        z.write(p, p.relative_to(BUILD / 'main'))
    for p in sorted((ROOT / 'src/main/resources').rglob('*')):
        if p.is_file(): z.write(p, p.relative_to(ROOT / 'src/main/resources'))
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    assert not any(p.startswith('org/bukkit/') for p in z.namelist())
print('Built', jar)
