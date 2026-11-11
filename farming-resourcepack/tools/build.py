#!/usr/bin/env python3
"""Extract original generated atlases, build merged 26.3 item/crop packs and an asset preview.
Pillow processing crops atlas cells and resizes them; original artwork is preserved.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json,io,zipfile,hashlib
ROOT=Path(__file__).resolve().parents[2];HERE=ROOT/'farming-resourcepack';OUT=ROOT/'releases/farming-update';OUT.mkdir(parents=True,exist_ok=True)
CROPS=['strawberry','tomato','corn','rice','cucumber'];NAMES=['딸기','토마토','옥수수','쌀','오이'];DISHES=['strawberry_jam','tomato_pasta','corn_soup','rice_bowl','cucumber_salad'];DNAMES=['수제 딸기잼','토마토 파스타','옥수수 크림수프','도시락 밥','오이 토마토 샐러드'];HEIGHTS=[.65,1.3,1.7,1.1,1.4]
def png(im):
 b=io.BytesIO();im.save(b,format='PNG',optimize=True);return b.getvalue()
def js(data):return json.dumps(data,ensure_ascii=False,indent=2).encode()
def write_zip(path,entries):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
def metadata(text):return js({'pack':{'description':text,'min_format':[97,1],'max_format':[97,1]}})
def cell(image,col,row,rows):return image.crop((round(col*image.width/5),round(row*image.height/rows),round((col+1)*image.width/5),round((row+1)*image.height/rows)))
with zipfile.ZipFile(ROOT/'releases/city-resourcepack/CityEconomy-26.3-1.0.0.zip') as z:entries={n:z.read(n) for n in z.namelist()}
entries['pack.mcmeta']=metadata('시티 이코노미 농장 | 도시 아이콘 32종 + 농작물 5종 · Java 26.3');entries['LICENSE-Farming.txt']=(HERE/'LICENSE.txt').read_bytes()
itemsheet=Image.open(HERE/'art/items-atlas.png').convert('RGBA');plantsheet=Image.open(HERE/'art/growth-atlas.png').convert('RGBA');sprites={};growth={}
for row,ids in enumerate([CROPS,[c+'_seed' for c in CROPS],DISHES]):
 for col,key in enumerate(ids):
  raw=cell(itemsheet,col,row,3);bbox=raw.getchannel('A').getbbox();assert bbox
  raw=raw.crop(bbox);raw.thumbnail((58,58),Image.Resampling.NEAREST);icon=Image.new('RGBA',(64,64));icon.alpha_composite(raw,((64-raw.width)//2,(64-raw.height)//2));sprites[key]=icon
  entries[f'assets/tycoon/items/farm/{key}.json']=js({'model':{'type':'minecraft:model','model':f'tycoon:item/farm/{key}'}})
  entries[f'assets/tycoon/models/item/farm/{key}.json']=js({'parent':'minecraft:item/generated','gui_light':'front','textures':{'layer0':f'tycoon:item/farm/{key}'}})
  entries[f'assets/tycoon/textures/item/farm/{key}.png']=png(icon)
# Source atlas rows end at the ground lines, not at equally spaced centres.
# Explicit cell bands retain the original growth sizing and prevent ripe leaf clipping.
bands=[(0,.2545),(.2545,.4634),(.4634,.7009),(.7009,1)]
for col,crop in enumerate(CROPS):
 cells=[]
 for low,high in bands:
  raw=plantsheet.crop((round(col*plantsheet.width/5),round(low*plantsheet.height),round((col+1)*plantsheet.width/5),round(high*plantsheet.height)))
  bbox=raw.getchannel('A').getbbox();assert bbox;cells.append(raw.crop(bbox))
 scale=min(60/max(c.width for c in cells),60/max(c.height for c in cells))
 for stage,raw in enumerate(cells):
  raw=raw.resize((max(1,round(raw.width*scale)),max(1,round(raw.height*scale))),Image.Resampling.NEAREST);sprite=Image.new('RGBA',(64,64));sprite.alpha_composite(raw,((64-raw.width)//2,63-raw.height));growth[(crop,stage)]=sprite
  key=f'{crop}_stage_{stage}';texture=f'tycoon:block/crop/{key}';height=round(HEIGHTS[col]*16,3)
  elements=[]
  for angle in [-45,45]:
   elements.append({'from':[1,0,8],'to':[15,height,8],'rotation':{'origin':[8,0,8],'axis':'y','angle':angle,'rescale':False},'shade':False,'faces':{side:{'uv':[0,0,16,16],'texture':'#plant','tintindex':-1} for side in ['north','south']}})
  entries[f'assets/tycoon/items/crop/{key}.json']=js({'model':{'type':'minecraft:model','model':f'tycoon:block/crop/{key}'}})
  entries[f'assets/tycoon/models/block/crop/{key}.json']=js({'ambientocclusion':False,'textures':{'plant':texture,'particle':texture},'elements':elements})
  entries[f'assets/tycoon/textures/block/crop/{key}.png']=png(sprite)
entries['pack.png']=png(sprites['strawberry'].resize((256,256),Image.Resampling.NEAREST))
base=OUT/'CityEconomy-Farming-26.3-1.1.0.zip';write_zip(base,entries)
with zipfile.ZipFile(ROOT/'releases/city-resourcepack/CityBuild-26.3-1.0.0.zip') as z:
 complete={**entries,**{n:z.read(n) for n in z.namelist() if n.startswith('assets/')}}
complete['pack.mcmeta']=metadata('시티 이코노미 농장 전체 | 농작물 5종 + 도시 건축 8종 · Java 26.3');write_zip(OUT/'CityEconomy-Farming-Complete-26.3-1.1.0.zip',complete)
fontbase=Path('/usr/share/fonts/opentype/noto');font=lambda size:ImageFont.truetype(str(fontbase/'NotoSansCJK-Regular.ttc'),size,index=1)
board=Image.new('RGB',(1300,1240),'#101f27');draw=ImageDraw.Draw(board);draw.text((35,22),'도시 농장 · 딸기 / 토마토 / 옥수수 / 쌀 / 오이',font=font(32),fill='#e8f1e6');draw.text((35,78),'씨앗 → 재배 → 수확 → 요리 · NPC 거래 · 영구 도감',font=font(21),fill='#68ccbc')
for col,crop in enumerate(CROPS):
 x=30+col*250;draw.text((x+72,130),NAMES[col],font=font(24),fill='#edf3e6')
 for row,key in enumerate([crop,crop+'_seed',DISHES[col]]):
  y=180+row*175;draw.rounded_rectangle((x,y,x+235,y+164),radius=12,fill='#1b3039');sprite=sprites[key].resize((128,128),Image.Resampling.NEAREST);board.paste(sprite,(x+53,y),sprite);label=[NAMES[col],NAMES[col]+' 씨앗',DNAMES[col]][row];draw.text((x+25,y+128),label,font=font(19),fill='#d7e9e2')
 draw.text((x+18,735),'새싹 → 잎 → 개화 → 수확',font=font(17),fill='#70ccbb')
 for stage in range(4):
  sprite=growth[(crop,stage)].resize((112,112),Image.Resampling.NEAREST);xx=x+(stage%2)*118;yy=790+(stage//2)*165;draw.rounded_rectangle((xx,yy,xx+112,yy+145),radius=8,fill='#1b3039');board.paste(sprite,(xx,yy+2),sprite);draw.text((xx+28,yy+116),['새싹','잎','개화','수확'][stage],font=font(18),fill='#d9e8e0')
draw.text((30,1155),'에셋 구성 미리보기이며 실제 게임 화면 캡처가 아닙니다.',font=font(21),fill='#8aa9b4');draw.text((30,1190),'Paper 26.3 · 4단계 성장 · 64×64 텍스처 · 서버 플러그인 + 클라이언트 리소스팩',font=font(18),fill='#8aa9b4');board.save(OUT/'농작물-미리보기.png',optimize=True)
report={'minecraft':'26.3','resource_format':[97,1],'farm_item_icons':15,'crop_growth_models':20,'existing_city_icons':32,'optional_building_textures':8,'base_sha1':hashlib.sha1(base.read_bytes()).hexdigest(),'source_art_sha256':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (HERE/'art').glob('*.png')},'real_game_visual_tested':False}
(OUT/'resourcepack-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False,indent=2))
