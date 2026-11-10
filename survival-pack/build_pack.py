#!/usr/bin/env python3
"""Bundle unchanged upstream JARs, Korean defaults and our menu; validate inputs."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile
import yaml

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
VENDOR = ROOT / 'vendor'
STAGE = ROOT / 'build/economy-survival-korean'
RELEASE = REPO / 'releases/survival-pack'
if STAGE.exists(): shutil.rmtree(STAGE)
(STAGE / 'plugins').mkdir(parents=True)
RELEASE.mkdir(parents=True, exist_ok=True)
sources = json.loads((VENDOR / 'sources.json').read_text())
metadata = []
for row in sources:
    src = VENDOR / row['file']
    assert hashlib.sha256(src.read_bytes()).hexdigest() == row['sha256'], src
    with zipfile.ZipFile(src) as z:
        assert z.testzip() is None
        data = yaml.safe_load(z.read('plugin.yml'))
        metadata.append({'name': data['name'], 'version': str(data['version']),
                         'dependencies': data.get('depend', []), 'file': src.name})
    shutil.copyfile(src, STAGE / 'plugins' / src.name)

menu = REPO / 'korean-menu/build/libs/KoreanMenu-1.0.0.jar'
assert menu.is_file(), 'Run python3 korean-menu/build.py first'
with zipfile.ZipFile(menu) as z:
    assert z.testzip() is None
    assert not any(n.startswith('org/bukkit/') for n in z.namelist())
    data = yaml.safe_load(z.read('plugin.yml'))
    assert data['permissions']['koreanmenu.use']['default'] is True
    assert data['permissions']['koreanmenu.basics']['default'] is True
    children = data['permissions']['koreanmenu.basics']['children']
    assert not any('*' in n or 'admin' in n or 'eco.' in n for n in children)
metadata.append({'name': 'KoreanMenu', 'version': '1.0.0', 'dependencies': [], 'file': menu.name})
shutil.copyfile(menu, STAGE / 'plugins' / menu.name)
shutil.copyfile(menu, RELEASE / menu.name)
names = {p['name'] for p in metadata}
for p in metadata:
    assert set(p['dependencies']) <= names, p

with zipfile.ZipFile(VENDOR / 'EssentialsX-2.22.0.jar') as z:
    config = z.read('config.yml').decode('utf-8')
    assert 'messages_ko.properties' in z.namelist()
    changes = {'teleport-delay': '3', 'teleport-cooldown': '10', 'starting-balance': '1000',
               'currency-symbol': "'원'", 'currency-symbol-suffix': 'true', 'min-money': '0',
               'spawn-if-no-home': 'false'}
    for key, value in changes.items():
        config, count = re.subn(r'^' + re.escape(key) + r':.*$', key + ': ' + value, config, flags=re.M)
        assert count == 1, key
    config, count = re.subn(r'^#locale: en$', 'locale: ko', config, flags=re.M)
    assert count == 1
    parsed = yaml.safe_load(config)
    assert parsed['locale'] == 'ko' and parsed['starting-balance'] == 1000
    dest = STAGE / '신규설정/Essentials/config.yml'
    dest.parent.mkdir(parents=True)
    dest.write_text(config, encoding='utf-8')
with zipfile.ZipFile(VENDOR / 'QuickShop-Hikari-6.3.0.4.jar') as z:
    config = z.read('config.yml').decode('utf-8').replace('\r\n', '\n')
    assert 'lang/ko-KR/messages.yml' in z.namelist()
    config, n = re.subn(r'^game-language:.*$', 'game-language: ko_kr', config, flags=re.M)
    assert n == 1
    config, n = re.subn(r'^use-crowdin-ota: true$', 'use-crowdin-ota: false', config, flags=re.M)
    assert n == 1
    config, n = re.subn(r'^  display-items: true$', '  display-items: false', config, flags=re.M)
    assert n == 1
    parsed = yaml.safe_load(config)
    assert parsed['game-language'] == 'ko_kr'
    dest = STAGE / '신규설정/QuickShop-Hikari/config.yml'
    dest.parent.mkdir(parents=True)
    dest.write_text(config, encoding='utf-8')

shutil.copyfile(ROOT / 'README.md', STAGE / '설치안내.md')
# BOM makes Korean strings and paths readable in Windows PowerShell 5.1.
(STAGE / 'Download-Extras.ps1').write_text((ROOT / 'Download-Extras.ps1').read_text(), encoding='utf-8-sig')
upstream = json.loads((VENDOR / 'upstream-source/sources.json').read_text())
for row in upstream:
    src = VENDOR / row['file']
    assert hashlib.sha256(src.read_bytes()).hexdigest() == row['sha256']
    dest = STAGE / row['file']
    dest.parent.mkdir(exist_ok=True)
    shutil.copyfile(src, dest)
(STAGE / 'sources.json').write_text(json.dumps({'jars': sources, 'source_archives': upstream,
    'menu_source': 'https://github.com/wjdtjddyd956-svg/akdlszmfovcm/tree/main/korean-menu'}, indent=2) + '\n')
validation = {'included_plugins': metadata, 'jar_integrity': 'passed', 'yaml': 'passed',
              'hard_dependencies': 'passed', 'korean_menu_fixture_assertions': 29,
              'actual_paper_runtime_tested': False, 'actual_windows_installer_tested': False,
              'target_minecraft': '26.3', 'all_plugins_confirmed_for_target': False,
              'extra_jars_not_included': ['LuckPerms', 'GriefPrevention', 'CoreProtect']}
(STAGE / 'validation.json').write_text(json.dumps(validation, ensure_ascii=False, indent=2) + '\n')
checksums = ''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.relative_to(STAGE).as_posix() + '\n'
                    for p in sorted(STAGE.rglob('*')) if p.is_file())
(STAGE / 'SHA256SUMS.txt').write_text(checksums)
dest = RELEASE / 'EconomySurvival-Korean-Pack-1.0.0.zip'
with zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted(STAGE.rglob('*')):
        if p.is_file(): z.write(p, p.relative_to(STAGE))
with zipfile.ZipFile(dest) as z: assert z.testzip() is None
for name in ['validation.json', 'sources.json', '설치안내.md']:
    shutil.copyfile(STAGE / name, RELEASE / name)
(RELEASE / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n'
    for p in [dest, RELEASE / menu.name]))
print(json.dumps(validation, ensure_ascii=False, indent=2))
print('Created', dest, dest.stat().st_size, 'bytes')
