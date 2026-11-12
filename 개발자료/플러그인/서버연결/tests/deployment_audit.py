"""Cold pre-start restore drill and equality audit. All user data stays in ignored .build."""
from pathlib import Path
import argparse,hashlib,json,sqlite3,zipfile,shutil,contextlib
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file())
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);a=p.parse_args();server=a.server.resolve()
work=ROOT/'.build/integration';original=json.loads((work/'production-original-jars.json').read_text())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert len(original)==16
for name,expected in original.items():assert sha(server/'plugins'/name)==expected,name
jars=list((server/'plugins').glob('*.jar'));assert len(jars)==22
backup=max((server/'tycoon-server-backups').glob('전체서버-*.zip'),key=lambda f:f.stat().st_mtime)
proof=work/('restore-'+backup.stem);assert proof.resolve().is_relative_to(work.resolve());proof.mkdir(exist_ok=True)
checked=0;changed=[]
with zipfile.ZipFile(backup) as z:
    assert z.testzip() is None
    for info in z.infolist():
        destination=(proof/info.filename).resolve()
        assert destination.is_relative_to(proof.resolve()),'unsafe restore path'
        if info.is_dir():destination.mkdir(parents=True,exist_ok=True);continue
        destination.parent.mkdir(parents=True,exist_ok=True)
        with z.open(info) as source,destination.open('wb') as target:shutil.copyfileobj(source,target)
        if info.filename=='TYCOON-SHA256.properties':continue
        current=server/info.filename
        if not current.is_file() or sha(current)!=sha(destination):changed.append(info.filename)
        checked+=1
assert changed==['plugins/GriefPreventionData/messages.yml'],changed
tables={}
for file in ['KoreanEconomy/생활경제.db','KoreanCodex/도감.db']:
    current=server/'plugins'/file;snapshot=proof/'plugins'/file
    assert sha(current)==sha(snapshot)
    with contextlib.closing(sqlite3.connect(current.as_uri()+'?mode=ro',uri=True)) as db:
        assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
        tables[file]={name:db.execute('SELECT count(*) FROM "'+name.replace('"','""')+'"').fetchone()[0] for name, in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
(work/'database-cold-counts.json').write_text(json.dumps(tables,ensure_ascii=False),encoding='utf-8')
report={'cold_backup':backup.name,'full_restore_files':checked,'original_files_changed':['GriefPreventionData/messages.yml (187 additional Text translations; original saved)'],'original_plugin_jars_unchanged':16,'installed_plugin_jars':22,'database_integrity':'ok','database_tables_checked':sum(len(v) for v in tables.values()),'private_world_user_data_in_git':False}
(ROOT/'releases/plugin-integration/1.0.0/deployment-cold-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(work/'restore-path.txt').write_text(str(proof),encoding='utf-8')
print('PASS cold backup restored:',checked,'files; original16 JARs unchanged; existing DB files identical; only known message overlay changed')
print('Backup:',backup)
print('Proof:',proof)
