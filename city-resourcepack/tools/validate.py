#!/usr/bin/env python3
from pathlib import Path
from PIL import Image
import io,json,zipfile,hashlib,re,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'releases/city-resourcepack';CAT=json.loads((ROOT/'city-resourcepack/catalog.json').read_text());checks=0
def check(condition,message):
    global checks
    checks+=1
    if not condition:raise AssertionError(message)
def check_pack(file,with_items,with_blocks):
    with zipfile.ZipFile(file) as z:
        names=z.namelist();check(z.testzip() is None,'ZIP integrity');check(len(names)==len(set(names)),'no duplicate ZIP entries');check('pack.mcmeta' in names,'metadata at ZIP root')
        metadata=json.loads(z.read('pack.mcmeta'));check(metadata['pack']['min_format']==[97,1] and metadata['pack']['max_format']==[97,1],'Java 26.3 resource format 97.1')
        check(all(not n.startswith('/') and '..' not in Path(n).parts for n in names),'safe ZIP paths')
        if with_items:
            for entry in CAT['items']:
                key=entry['id'];definition=json.loads(z.read(f'assets/tycoon/items/{key}.json'));check(definition['model']=={'type':'minecraft:model','model':'tycoon:item/'+key},'item model reference '+key)
                model=json.loads(z.read(f'assets/tycoon/models/item/{key}.json'));check(model['parent']=='minecraft:item/generated','native generated parent');check(model['textures']['layer0']=='tycoon:item/'+key,'texture reference')
                image=Image.open(io.BytesIO(z.read(f'assets/tycoon/textures/item/{key}.png')));check(image.size==(64,64) and image.mode=='RGBA','64px RGBA icon');alpha=image.getchannel('A');check(alpha.getextrema()[0]==0 and alpha.getextrema()[1]>=250,'real alpha and near-opaque visible icon');bbox=alpha.getbbox();check(bbox[0]>=3 and bbox[1]>=3 and bbox[2]<=61 and bbox[3]<=61,'transparent safe margins')
            check(len([n for n in names if n.startswith('assets/tycoon/items/')])==32,'exactly 32 item definitions')
        if with_blocks:
            for entry in CAT['blocks']:
                image=Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/'+entry['id']+'.png')));check(image.size==(128,128),'128px city block');check(image.mode=='RGB','opaque block texture')
        else:check(not any(n.startswith('assets/minecraft/') for n in names),'base pack does not override vanilla assets')
        return names
base=OUT/'CityEconomy-26.3-1.0.0.zip';check_pack(base,True,False);check_pack(OUT/'CityBuild-26.3-1.0.0.zip',False,True);check_pack(OUT/'CityEconomy-Complete-26.3-1.0.0.zip',True,True)
with zipfile.ZipFile(ROOT/'releases/tycoon-2.0/생활타이쿤-리소스팩-26.3.zip') as old:
    previous={Path(n).stem for n in old.namelist() if n.startswith('assets/tycoon/items/') and n.endswith('.json')}
check(previous.issubset({e['id'] for e in CAT['items']}),'all 10 existing dish model IDs preserved')
with zipfile.ZipFile(OUT/'CityTextures-1.0.0.jar') as jar:
    check(jar.testzip() is None,'bridge JAR integrity');check(not any(n.startswith(('org/','io/','net/')) for n in jar.namelist()),'no bundled server or test API')
    config=jar.read('config.yml').decode();check(hashlib.sha1(base.read_bytes()).hexdigest() in config,'request SHA-1 matches exact ZIP');check('자동요청: false' in config,'optional delivery default')
    catalog=jar.read('icons.tsv').decode().splitlines();check(len(catalog)==32,'32 bridge catalog items');check({row.split('\t')[0] for row in catalog}=={e['id'] for e in CAT['items']},'bridge IDs match actual assets')
test_file=Path('/workspace/toolchains/city-textures-tests/build/test-results/test/TEST-kr.minecraft.survival.citytextures.CityTexturesTest.xml');tests=ET.parse(test_file).getroot();check(tests.get('failures')=='0' and tests.get('errors')=='0' and tests.get('skipped')=='0','all bridge tests passed')
check(test_file.stat().st_mtime>=max(p.stat().st_mtime for p in (ROOT/'city-textures/src').rglob('*.java')),'tests are current')
browser=json.loads((OUT/'browser-validation.json').read_text());check(browser['passed'],'offline browser specimen tests passed')
report={'pack_checks':checks,'bridge_mockbukkit_cases':int(tests.get('tests')),'mockbukkit_version':'26.3:4.120.0','actual_core_menu_tested':'KoreanMenu 2.0 source','legacy_1_2_name_mapping':'compatibility names included; no 1.2 runtime loaded here','download_api_test':'captured Player.addResourcePack parameters, no real Minecraft download','browser':browser,'actual_minecraft_client_rendered':False,'actual_windows_server_installed':False,'original_atlas_images_retained':True}
(OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('PASS:',checks,'resource/package checks;',tests.get('tests'),'MockBukkit cases; offline browser passed')
