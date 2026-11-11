#!/usr/bin/env python3
"""Package original generated atlases into real Minecraft assets and a cosmetic bridge.

Image operations only extract, normalize and size atlas cells for the game;
the source artwork is preserved in art/ and is never overwritten.
"""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import io,json,hashlib,zipfile,subprocess,os,shutil,html,base64

ROOT=Path(__file__).resolve().parents[2];HERE=ROOT/'city-resourcepack';OUT=ROOT/'releases/city-resourcepack';BUILD=HERE/'build'
if 'version: 1.0.0\n' not in (ROOT/'city-textures/src/main/resources/plugin.yml').read_text():
    raise SystemExit('이 스크립트는 기존 도시팩 1.0 재현용입니다. 최신 메뉴 통합팩은 interface-update/build.py로 빌드하세요.')
OUT.mkdir(parents=True,exist_ok=True);BUILD.mkdir(exist_ok=True)
CAT=json.loads((HERE/'catalog.json').read_text());ITEMS=CAT['items'];BLOCKS=CAT['blocks'];BASE=Path(os.environ.get('TYCOON_TOOLCHAINS','/workspace/toolchains'))
def png(image):
    buffer=io.BytesIO();image.save(buffer,format='PNG',optimize=True);return buffer.getvalue()
def cell(sheet,index,columns,rows):
    image=Image.open(HERE/'art'/sheet).convert('RGBA');col=index%columns;row=index//columns
    return image.crop((round(col*image.width/columns),round(row*image.height/rows),round((col+1)*image.width/columns),round((row+1)*image.height/rows)))
def icon(entry,size=64):
    original=cell(entry['sheet'],entry['cell'],4,4)
    if entry.get('trim_right'):original=original.crop((0,0,original.width-entry['trim_right'],original.height))
    bbox=original.getchannel('A').getbbox();assert bbox,'Empty atlas cell'
    sprite=original.crop(bbox);sprite.thumbnail((round(size*0.88),round(size*0.88)),Image.Resampling.NEAREST)
    # thumbnail does not enlarge, which is fine for native 64px output.
    canvas=Image.new('RGBA',(size,size));canvas.alpha_composite(sprite,((size-sprite.width)//2,(size-sprite.height)//2));return canvas
def pack_zip(path,entries):
    with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,data)
def metadata(description):return json.dumps({'pack':{'description':description,'min_format':[97,1],'max_format':[97,1]}},ensure_ascii=False,indent=2).encode()
def json_bytes(data):return json.dumps(data,ensure_ascii=False,indent=2).encode()
sprites={entry['id']:icon(entry) for entry in ITEMS}
tiles={}
for entry in BLOCKS:
    tile=cell('blocks-atlas.png',entry['cell'],4,2);tile=tile.crop((2,2,tile.width-2,tile.height-2));tiles[entry['id']]=tile.convert('RGB').resize((128,128),Image.Resampling.NEAREST)
common={'LICENSE.txt':(HERE/'LICENSE.txt').read_bytes()}
items=dict(common);items['pack.mcmeta']=metadata('시티 이코노미 | 도시 경제 아이콘 32종 · 64x · Java 26.3')
items['pack.png']=png(sprites['shop'].resize((256,256),Image.Resampling.NEAREST))
for entry in ITEMS:
    key=entry['id'];items[f'assets/tycoon/items/{key}.json']=json_bytes({'model':{'type':'minecraft:model','model':'tycoon:item/'+key}})
    items[f'assets/tycoon/models/item/{key}.json']=json_bytes({'parent':'minecraft:item/generated','gui_light':'front','textures':{'layer0':'tycoon:item/'+key}})
    items[f'assets/tycoon/textures/item/{key}.png']=png(sprites[key])
building=dict(common);building['pack.mcmeta']=metadata('시티 빌드 | 도시 건축 재질 8종 · 128x · Java 26.3');building['pack.png']=items['pack.png']
for entry in BLOCKS:building[f'assets/minecraft/textures/block/{entry["id"]}.png']=png(tiles[entry['id']])
combined={**items,**building};combined['pack.mcmeta']=metadata('시티 이코노미 전체 | 아이콘 32종 + 건축 재질 8종 · Java 26.3')
client=OUT/'CityEconomy-26.3-1.0.0.zip';pack_zip(client,items);pack_zip(OUT/'CityBuild-26.3-1.0.0.zip',building);pack_zip(OUT/'CityEconomy-Complete-26.3-1.0.0.zip',combined)
sha1=hashlib.sha1(client.read_bytes()).hexdigest()

# Korean specimen board, not a screenshot of a running Minecraft client.
font_dir=Path('/usr/share/fonts/opentype/noto');regular=font_dir/'NotoSansCJK-Regular.ttc';bold=font_dir/'NotoSansCJK-Bold.ttc'
def font(size,heavy=False):return ImageFont.truetype(str(bold if heavy else regular),size,index=1)
board=Image.new('RGB',(1536,1224),'#101c24');draw=ImageDraw.Draw(board)
draw.rounded_rectangle((40,30,218,67),radius=12,fill='#237c7c');draw.text((58,35),'JAVA 26.3 · 1.0',font=font(18,True),fill='#edf8f3')
draw.text((40,82),'시티 이코노미',font=font(48,True),fill='#f1f4ee');draw.text((42,149),'도시의 일상으로 바꾸는 생활 경제 리소스팩',font=font(22),fill='#91aeb8')
for i,entry in enumerate(ITEMS):
    row=i//8;col=i%8;y=234+row*170+(40 if i>=16 else 0);x=40+col*182
    if i==0:draw.text((40,200),'요리 · 직업 · 경제',font=font(21,True),fill='#4ac5b5')
    if i==16:draw.text((40,575),'도시 메뉴',font=font(21,True),fill='#4ac5b5')
    draw.rounded_rectangle((x,y,x+166,y+155),radius=14,fill='#1b2c35',outline='#2a454e')
    sprite=sprites[entry['id']].resize((112,112),Image.Resampling.NEAREST);board.paste(sprite,(x+27,y+4),sprite)
    label=entry['label'];label_font=font(16 if len(label)>8 else 18);bounds=draw.textbbox((0,0),label,font=label_font);draw.text((x+(166-bounds[2])/2,y+120),label,font=label_font,fill='#e4ece8')
draw.text((40,971),'선택 건축팩 · 128x 재질 8종',font=font(21,True),fill='#4ac5b5')
for i,entry in enumerate(BLOCKS):
    x=40+i*182;board.paste(tiles[entry['id']].resize((128,128),Image.Resampling.NEAREST),(x+18,1010));draw.text((x+10,1143),entry['label'],font=font(17),fill='#e4ece8')
draw.text((40,1192),'에셋 구성 미리보기 · 실제 게임 화면 캡처가 아닙니다.  |  아이콘 64×64 / 건축 128×128',font=font(16),fill='#8da4af')
board.save(OUT/'미리보기.png',optimize=True)

repeat=Image.new('RGB',(1200,760),'#13232a');rd=ImageDraw.Draw(repeat);rd.text((24,18),'도시 건축 재질 · 반복 확인',font=font(30,True),fill='#e8efeb')
for i,entry in enumerate(BLOCKS):
    x=20+(i%4)*298;y=82+(i//4)*330
    for dy in range(2):
        for dx in range(2):repeat.paste(tiles[entry['id']],(x+dx*128,y+dy*128))
    rd.text((x,y+263),entry['label'],font=font(18,True),fill='#d6e9e3');rd.text((x,y+288),entry['id'],font=font(13),fill='#8eaab5')
repeat.save(OUT/'건축재질-반복확인.png',optimize=True)

# An offline interactive specimen; all image data is embedded and no network is used.
cards=[]
for entry in ITEMS:
    data=base64.b64encode(png(sprites[entry['id']])).decode();cards.append(f'<article data-group="{entry["group"]}"><img src="data:image/png;base64,{data}" alt="{entry["label"]}"><b>{entry["label"]}</b><small>{entry["id"]}</small></article>')
page='''<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>시티 이코노미 미리보기</title>
<style>*{box-sizing:border-box}body{margin:0;background:#101c24;color:#e8efeb;font:16px 'Malgun Gothic',sans-serif}main{max-width:1180px;margin:50px auto;padding:24px}h1{font-size:44px;margin:8px 0}p{color:#a0bac2;line-height:1.7}.tag{color:#45c8b6}nav{display:flex;gap:8px;flex-wrap:wrap;margin:30px 0}button{border:1px solid #39515b;border-radius:8px;color:inherit;background:#1b3039;padding:10px 16px;cursor:pointer}button.active{background:#237c7c}#cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:16px}article{background:#1b2c35;border:1px solid #2a454e;border-radius:14px;text-align:center;padding:16px}img{display:block;width:128px;height:128px;image-rendering:pixelated;margin:auto}b,small{display:block;margin:10px 0}small{color:#8fa6b1;font-size:11px}body.light article{background:#e4e5de;color:#203039}article[hidden]{display:none}</style>
<main><span class="tag">JAVA 26.3 · 64×64 아이콘</span><h1>시티 이코노미</h1><p>현실적인 도시·생활 경제 디자인 32종. 이것은 에셋 미리보기이며 실제 게임 화면 캡처가 아닙니다.</p><nav id="filters">'''
for group in ['전체','요리','직업','경제','성장','안내','메뉴','조작']:page+=f'<button data-filter="{group}"'+(' class="active"' if group=='전체' else '')+'>'+group+'</button>'
page+='''<button id="theme">밝은 바탕 비교</button></nav><section id="cards">'''+''.join(cards)+'''</section><p>적용: 클라이언트 리소스팩 선택 → /도시팩 켜기. 보조 플러그인은 서버 plugins 폴더에 넣으세요.</p></main><script>
document.querySelectorAll('[data-filter]').forEach(button=>button.onclick=()=>{document.querySelectorAll('[data-filter]').forEach(b=>b.classList.toggle('active',b===button));document.querySelectorAll('article').forEach(card=>card.hidden=button.dataset.filter!=='전체'&&card.dataset.group!==button.dataset.filter)});document.querySelector('#theme').onclick=()=>document.body.classList.toggle('light');</script></html>'''
(OUT/'미리보기.html').write_text(page,encoding='utf-8')

# Generate the bridge's catalog from the same source used by the Minecraft models.
module=ROOT/'city-textures';resources=module/'src/main/resources';resources.joinpath('icons.tsv').write_text(''.join(e['id']+'\t'+e['label']+'\t'+e['base']+'\n' for e in ITEMS))
classes=module/'build/classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True)
jdk=Path(os.environ.get('TYCOON_JDK',str(BASE/'jdk-25.0.5.0.3')));api=Path(os.environ.get('PAPER_API_JAR',str(BASE/'paper-api-build/build/libs/paper-api-26.3-df120fd-local.jar')))
cp=os.pathsep.join([str(api),str(BASE/'paper-api-build/build/deps/*')])
subprocess.run([str(jdk/'bin/javac'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',str(classes)]+[str(p) for p in (module/'src/main/java').rglob('*.java')],check=True)
jar_entries={str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')}
for p in resources.rglob('*'):
    if p.is_file():jar_entries[str(p.relative_to(resources))]=p.read_bytes().replace(b'@CITY_PACK_SHA1@',sha1.encode())
bridge=OUT/'CityTextures-1.0.0.jar';pack_zip(bridge,jar_entries)
(OUT/'자동배포-설정예시.yml').write_bytes(jar_entries['config.yml'])
(OUT/'SHA1.txt').write_text('\n'.join(hashlib.sha1(p.read_bytes()).hexdigest()+'  '+p.name for p in [client,OUT/'CityBuild-26.3-1.0.0.zip',OUT/'CityEconomy-Complete-26.3-1.0.0.zip'])+'\n')
report={'version':CAT['version'],'minecraft':'26.3','resource_format':[97,1],'icon_count':len(ITEMS),'block_count':len(BLOCKS),'icon_resolution':64,'block_resolution':128,'base_sha1':sha1,'source_atlases':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (HERE/'art').glob('*.png')},'bridge_compile':'unmodified actual Paper 26.3 df120fd local API, Java 25 compiler, release 21','real_game_client_visual_verified':False}
(OUT/'build-info.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('Built resource packs, bridge and previews:',OUT)
