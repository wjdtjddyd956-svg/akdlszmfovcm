#!/usr/bin/env python3
"""Production compilation uses real Paper API; never packages fixture API classes."""
from pathlib import Path
import os, shutil, subprocess, zipfile, hashlib, json
ROOT=Path(__file__).resolve().parents[1]
BASE=Path(os.environ.get('TYCOON_TOOLCHAINS','/workspace/toolchains'))
JDK=Path(os.environ.get('TYCOON_JDK',str(BASE/'jdk-25.0.5.0.3')))
API=Path(os.environ.get('PAPER_API_JAR',str(BASE/'paper-api-build/build/libs/paper-api-26.3-df120fd-local.jar')))
DEPS=Path(os.environ.get('PAPER_API_DEPS',str(BASE/'paper-api-build/build/deps')))
OUT=ROOT/'releases/tycoon-2.0';OUT.mkdir(parents=True,exist_ok=True)
VENDOR=ROOT/'survival-pack/vendor'
reports=[]
for module,name in [('korean-economy','KoreanEconomy'),('korean-menu','KoreanMenu'),('korean-commands','KoreanCommands'),('korean-pack-setup','KoreanPackSetup')]:
    src=ROOT/module/'src/main';classes=ROOT/module/'build-real/classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True)
    cp=os.pathsep.join([str(API),str(DEPS/'*'),str(VENDOR/'Vault.jar')])
    subprocess.run([str(JDK/'bin/javac'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',str(classes)]+[str(p) for p in (src/'java').rglob('*.java')],check=True)
    jar=OUT/(name+'-2.0.0.jar')
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(classes.rglob('*.class')):z.write(p,p.relative_to(classes))
        for p in sorted((src/'resources').rglob('*')):
            if p.is_file():z.write(p,p.relative_to(src/'resources'))
        if name=='KoreanPackSetup':
            with zipfile.ZipFile(VENDOR/'EssentialsX-2.22.0.jar') as base:
                z.writestr('defaults/Essentials.yml',base.read('config.yml'))
                z.writestr('licenses/Essentials.txt',base.read('META-INF/LICENSE.txt'))
            with zipfile.ZipFile(VENDOR/'QuickShop-Hikari-6.3.0.4.jar') as base:
                z.writestr('defaults/QuickShop.yml',base.read('config.yml'))
                z.writestr('licenses/QuickShop.md',base.read('license.md'))
            with zipfile.ZipFile(ROOT/'releases/survival-pack/EconomySurvival-Korean-Pack-1.1.0.zip') as base:
                z.writestr('locales/Essentials.properties',base.read('한글설정/Essentials/messages_ko.properties'))
                z.writestr('locales/QuickShop.yml',base.read('한글설정/QuickShop-Hikari/overrides/ko_kr/messages.yml'))
        if name=='KoreanEconomy':
            with zipfile.ZipFile(VENDOR/'tycoon/sqlite-jdbc.jar') as sqlite:
                for n in sqlite.namelist():
                    if n.endswith('/') or n.upper().endswith(('.SF','.RSA','.DSA')) or n=='META-INF/MANIFEST.MF':continue
                    z.writestr(n,sqlite.read(n))
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        assert not any(n.startswith(('org/bukkit/','io/papermc/','net/milkbowl/')) for n in z.namelist())
    reports.append({'module':module,'jar':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'compile':'real Paper 26.3 API, commit df120fd, local unmodified API source build','source_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in src.rglob('*') if p.is_file()}})
(OUT/'build-provenance.json').write_text(json.dumps({'jdk':str(JDK),'api':str(API),'api_notes':json.loads((BASE/'paper-api-build/provenance.json').read_text()),'artifacts':reports},ensure_ascii=False,indent=2)+'\n')
print('Production JARs:',OUT)
