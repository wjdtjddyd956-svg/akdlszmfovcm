#!/usr/bin/env python3
from pathlib import Path
from PIL import Image
import json,io,hashlib,zipfile
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());HERE=ROOT/'개발자료/리소스팩/메뉴팩';OUT=ROOT/'releases/ui-update';OUT.mkdir(parents=True,exist_ok=True)
CAT=json.loads((HERE/'catalog.json').read_text());sheet=Image.open(HERE/'art/ui-atlas.png').convert('RGBA')
def js(x):return json.dumps(x,ensure_ascii=False,indent=2).encode()
def png(x):
 b=io.BytesIO();x.save(b,format='PNG',optimize=True);return b.getvalue()
def zipped(path,entries):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
with zipfile.ZipFile(ROOT/'releases/farming-update/CityEconomy-Farming-26.3-1.1.0.zip') as old:entries={n:old.read(n) for n in old.namelist()}
for index,(id,label,base) in enumerate(CAT):
 col=index%6;row=index//6;cell=sheet.crop((round(col*sheet.width/6),round(row*sheet.height/4),round((col+1)*sheet.width/6),round((row+1)*sheet.height/4)));bbox=cell.getchannel('A').point(lambda a:255 if a>32 else 0).getbbox();assert bbox;cell=cell.crop(bbox);cell.thumbnail((58,58),Image.Resampling.LANCZOS);icon=Image.new('RGBA',(64,64));icon.alpha_composite(cell,((64-cell.width)//2,(64-cell.height)//2))
 entries[f'assets/tycoon/textures/item/ui/{id}.png']=png(icon)
 entries[f'assets/tycoon/models/item/ui/{id}.json']=js({'parent':'minecraft:item/generated','gui_light':'front','textures':{'layer0':f'tycoon:item/ui/{id}'}})
 entries[f'assets/tycoon/items/ui/{id}.json']=js({'model':{'type':'minecraft:model','model':f'tycoon:item/ui/{id}'}})
entries['pack.mcmeta']=js({'pack':{'description':'도시 생활 수첩 | 메뉴·도감 UI + 도시 아이콘 + 농작물 5종 · Java 26.3','min_format':[97,1],'max_format':[97,1]}});entries['pack.png']=entries['assets/tycoon/textures/item/ui/codex.png'];entries['LICENSE-Interface.txt']=(HERE/'LICENSE.txt').read_bytes()
pack=OUT/'CityEconomy-Interface-26.3-1.2.0.zip';zipped(pack,entries)
with zipfile.ZipFile(ROOT/'releases/city-resourcepack/CityBuild-26.3-1.0.0.zip') as old:complete={**entries,**{n:old.read(n) for n in old.namelist() if n.startswith('assets/')}}
complete['pack.mcmeta']=js({'pack':{'description':'도시 생활 수첩 전체 | UI·농장·도시 건축 8종 · Java 26.3','min_format':[97,1],'max_format':[97,1]}});zipped(OUT/'CityEconomy-Interface-Complete-26.3-1.2.0.zip',complete)
report={'version':'1.2.0','minecraft':'26.3','resource_format':[97,1],'new_ui_icons':24,'existing_city_icons':32,'farm_item_icons':15,'crop_growth_models':20,'base_sha1':hashlib.sha1(pack.read_bytes()).hexdigest(),'complete_sha1':hashlib.sha1((OUT/'CityEconomy-Interface-Complete-26.3-1.2.0.zip').read_bytes()).hexdigest(),'original_atlas_sha256':hashlib.sha256((HERE/'art/ui-atlas.png').read_bytes()).hexdigest(),'native_client_visual_tested':False}
(OUT/'resourcepack-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('Built interface pack',report['base_sha1'])
