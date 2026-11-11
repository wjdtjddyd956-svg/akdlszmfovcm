"""Compile against the installed, real Paper API. Requires an existing 2.0 release.

The existing release is a verified dependency/resource carrier; its plugin classes
are removed and rebuilt. No Bukkit fixture or downloaded server is used.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--server', type=Path, required=True)
p.add_argument('--jdk', type=Path, required=True)
p.add_argument('--output', type=Path, default=ROOT / 'releases/tycoon-2.0.1')
p.add_argument('--api-version', default='26.3.build.238-stable')
a = p.parse_args()
api = list((a.server / 'libraries/io/papermc/paper/paper-api' / a.api_version).glob('*.jar'))
if len(api) != 1:
    raise SystemExit('Expected one installed Paper API JAR; select a clean server copy.')
deps = api + [f for f in sorted((a.server / 'libraries').rglob('*.jar')) if 'paper-api' not in f.parts]
deps += [a.server / 'plugins/Vault.jar']
work = ROOT / '.build/windows-tycoon'
work.mkdir(parents=True, exist_ok=True)
a.output.mkdir(parents=True, exist_ok=True)
report = {'api': str(api[0]), 'api_sha256': hashlib.sha256(api[0].read_bytes()).hexdigest(),
          'java': subprocess.check_output([str(a.jdk/'bin/java.exe'), '-version'], stderr=subprocess.STDOUT, text=True),
          'artifacts': [], 'fixture_api': False, 'native_platform': 'Windows (SQLite original DLLs, all Windows architectures)'}
for module, name in [('korean-economy', 'KoreanEconomy'), ('korean-menu', 'KoreanMenu'),
                     ('korean-commands', 'KoreanCommands'), ('korean-pack-setup', 'KoreanPackSetup')]:
    src = ROOT / module / 'src/main'
    classes = work / module
    # New per-build directory avoids retaining classes removed from source.
    import tempfile
    classes = Path(tempfile.mkdtemp(prefix=module+'-', dir=work))
    files = sorted((src/'java').rglob('*.java'))
    args = ['--release', '21', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, deps)),
            '-d', str(classes), *map(str, files)]
    argfile = work / (module+'.args')
    argfile.write_text('\n'.join('"'+s.replace('\\', '/').replace('"', '\\"')+'"' for s in args), encoding='utf-8')
    subprocess.run([str(a.jdk/'bin/javac.exe'), '@'+str(argfile)], check=True)
    version = '2.0.1' if name == 'KoreanEconomy' else '2.0.0'
    output = a.output / (name+'-'+version+'.jar')
    base = ROOT / 'releases/tycoon-2.0' / (name+'-2.0.0.jar')
    provenance = json.loads((base.parent/'build-provenance.json').read_text(encoding='utf-8'))
    expected = next(x['sha256'] for x in provenance['artifacts'] if x['module'] == module)
    if hashlib.sha256(base.read_bytes()).hexdigest() != expected:
        raise SystemExit('Dependency carrier hash mismatch: '+str(base))
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as out:
        for n in old.namelist():
            if n.startswith('org/sqlite/native/') and not n.startswith('org/sqlite/native/Windows/'):
                continue
            if not n.startswith('kr/') and not (src/'resources'/n).is_file():
                out.writestr(n, old.read(n))
        for f in sorted(classes.rglob('*.class')): out.write(f, f.relative_to(classes).as_posix())
        for f in sorted((src/'resources').rglob('*')):
            if f.is_file(): out.write(f, f.relative_to(src/'resources').as_posix())
    with zipfile.ZipFile(output) as check:
        assert check.testzip() is None
        assert not any(n.startswith(('org/bukkit/', 'io/papermc/', 'org/mockbukkit/')) for n in check.namelist())
    report['artifacts'].append({'module': module, 'file': output.name,
        'sha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'dependency_carrier_sha256': expected,
        'source_sha256': {str(f.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(f.read_bytes()).hexdigest()
                          for f in sorted(src.rglob('*')) if f.is_file()}})
(a.output/'windows-build.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print('PASS: four plugins compiled against installed Paper API:', api[0].name)
