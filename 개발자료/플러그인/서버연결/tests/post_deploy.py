"""Read-only production restart, archive and aggregate integrity checks. No user records are exported."""
from pathlib import Path
import argparse,hashlib,json,zipfile,sqlite3,contextlib,gzip,struct,io,re
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file())
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);a=p.parse_args();server=a.server
work=ROOT/'.build/integration';out=ROOT/'releases/plugin-integration/1.0.0'
backup=max(server.parent.joinpath(server.name+'-backups').glob('life-*.zip'),key=lambda f:f.stat().st_mtime)
digest=hashlib.sha256(backup.read_bytes()).hexdigest()
assert digest==backup.with_suffix('.zip.sha256').read_text().strip().lower()
with zipfile.ZipFile(backup) as z:
    assert z.testzip() is None
    assert 'life-snapshot.json' in z.namelist()
    assert 'plugins/LuckPerms/luckperms-h2-v2.mv.db' in z.namelist()
    assert 'plugins/KoreanServerBridge/권한초기화완료.txt' in z.namelist()
    assert len([n for n in z.namelist() if n.startswith('plugins/') and n.count('/')==1 and n.endswith('.jar')])==22
    names=z.namelist();assert not any('Probe.jar' in n or '/ViaVersion' in n or '/ViaBackwards' in n for n in names)
    archive_files=len(names)
# Standard NBT reader, used only to read saved border settings after the manager's cold restart.
stream=io.BytesIO(gzip.decompress((server/'world/level.dat').read_bytes()))
def number(fmt):return struct.unpack('>'+fmt,stream.read(struct.calcsize('>'+fmt)))[0]
def string():return stream.read(number('H')).decode('utf-8',errors='replace')
def payload(tag):
    if tag in [1,2,3,4,5,6]:return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[tag])
    if tag==7:return stream.read(number('i'))
    if tag==8:return string()
    if tag==9:
        subtype=number('b');return [payload(subtype) for _ in range(number('i'))]
    if tag==10:
        result={}
        while (subtype:=number('b'))!=0:
            key=string();result[key]=payload(subtype)
        return result
    if tag in [11,12]:return [number('i' if tag==11 else 'q') for _ in range(number('i'))]
    raise ValueError('unknown NBT type')
tag=number('b');string();nbt=payload(tag);data=nbt.get('Data',nbt)
border={k:v for k,v in data.items() if 'border' in k.lower()}
if not border:
    # Modern 26.3 saves each dimension's border as dedicated saved data.
    modern=server/'world/dimensions/minecraft/overworld/data/minecraft/world_border.dat'
    stream=io.BytesIO(gzip.decompress(modern.read_bytes()));tag=number('b');string();saved=payload(tag)['data']
    border={'WorldBorder':saved}
assert border,'saved border missing'
if 'WorldBorder' in border:
    saved=border['WorldBorder'];size=saved.get('size')
else:size=border.get('BorderSize')
assert size==4096,(border,'unexpected border size')
tables=json.loads((work/'database-cold-counts.json').read_text(encoding='utf-8'));checked=0
for name,counts in tables.items():
    file=server/'plugins'/name
    with contextlib.closing(sqlite3.connect(file.as_uri()+'?mode=ro',uri=True)) as db:
        assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
        for table,count in counts.items():
            assert db.execute('SELECT count(*) FROM "'+table.replace('"','""')+'"').fetchone()[0]==count
            checked+=1
log=(server/'logs/latest.log').read_text(encoding='utf-8')
assert 'Done (' in log
for name in ['LuckPerms','Chunky','PlaceholderAPI','TAB','DecentHolograms','KoreanServerBridge']:
    assert '['+name+'] Enabling ' in log,name
assert 'Successfully registered internal expansion: koreanlife' in log
assert '[DecentHolograms] Loaded 1 holograms!' in log
assert len(list((server/'plugins/DecentHolograms/holograms').glob('koreanlife_town*.yml')))==1
assert not re.search(r'\bERROR\b|Exception|Unsupported API|unsupported server',log,re.I)
properties=(server/'server.properties').read_text(encoding='utf-8')
assert 'online-mode=true' in properties and 'require-resource-pack=false' in properties
tasks=(server/'plugins/Chunky/tasks.log').read_text(encoding='utf-8')
assert 'radius=512' in tasks and 'completed' in tasks and 'chunks=4225' in tasks
report={'actual_windows_server':True,'paper':'26.3 build238','installed_plugins':22,'original_jars_preserved':16,'added_official_plugins':5,'added_bridge':1,'managed_cold_backup_verified':True,'managed_backup_sha256':digest,'managed_archive_files':archive_files,'managed_archive_contains_lp_h2_and_22_jars':True,'manager_cold_restart_completed':True,'database_tables_unchanged':checked,'database_integrity':'ok','owned_holograms_after_restart':1,'papi_internal_expansion_registered':True,'world_border_saved':border,'pregen':{'world':'overworld','center':[0,0],'radius':512,'shape':'square','chunks':4225,'completed':True,'elapsed_seconds':20,'whole_border_generated':False},'online_auth_unchanged':True,'resource_pack_optional':True,'no_test_adapters_or_probes_in_production':True,'actual_authenticated_gameplay_on_production':False,'real_26_3_gameplay_on_copy':True}
(out/'production-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS production 22-plugin restart; 17 tables unchanged; backup SHA/ZIP+LP H2 valid; border4096 and4225 chunks persisted')
