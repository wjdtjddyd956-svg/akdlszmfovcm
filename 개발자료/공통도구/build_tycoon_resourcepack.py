#!/usr/bin/env python3
"""Original small pixel icons; no external textures or client mod required."""
from pathlib import Path
import json,zipfile,io
from PIL import Image,ImageDraw
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());OUT=ROOT/'releases/tycoon-2.0/생활타이쿤-리소스팩-26.3.zip'
IDS=['mill_bread','carrot_salad','roast_potato','mushroom_soup','pumpkin_pie','apple_jam','sweet_cookie','roast_cod','salmon_meal','golden_meal']
def png(which):
    image=Image.new('RGBA',(16,16));d=ImageDraw.Draw(image)
    if which in (0,2):
        d.rounded_rectangle((2,5,13,12),radius=3,fill='#61381e');d.rounded_rectangle((3,4,12,10),radius=3,fill='#e9b354');d.line((5,5,5,8),fill='#fff0a3');d.line((8,5,8,8),fill='#fff0a3');d.line((11,5,11,7),fill='#fff0a3')
    elif which in (1,3,9):
        d.ellipse((1,6,14,13),fill='#65412e');d.rectangle((4,10,11,13),fill='#a46b3b');d.ellipse((1,4,14,10),fill='#d7e0b6');d.ellipse((3,5,12,9),fill=['#71a83b','#71a83b','#71a83b','#bf8958'][min(which,3)]);
        for x,y in [(4,6),(8,5),(10,7),(6,8)]:d.rectangle((x,y,x+1,y+1),fill='#fa9d35' if which!=9 else '#ffd94a')
    elif which==4:
        d.polygon([(2,10),(11,3),(14,11),(5,14)],fill='#7b4021');d.polygon([(2,8),(11,2),(14,9),(5,12)],fill='#e8a441');d.line((4,8,10,4),fill='#fff0a0',width=2)
    elif which==5:
        d.rectangle((5,2,10,4),fill='#6b4932');d.rounded_rectangle((3,5,12,14),2,fill='#c8e9e3');d.rectangle((4,8,11,12),fill='#d74a36');d.rectangle((5,6,6,9),fill='#fff5d3')
    elif which==6:
        d.ellipse((2,3,13,14),fill='#9b562e');d.ellipse((2,2,13,12),fill='#e2a957');
        for x,y in [(5,4),(9,4),(4,8),(8,9),(11,7)]:d.rectangle((x,y,x+1,y+1),fill='#583528')
    else:
        d.ellipse((1,8,14,13),fill='#d7d7c3');d.polygon([(3,8),(10,4),(13,7),(6,11)],fill='#d67f62' if which==8 else '#b48449');d.line((5,8,10,6),fill='#ffdfaf');d.rectangle((11,9,13,10),fill='#7cac43')
    b=io.BytesIO();image.save(b,format='PNG');return b.getvalue()
with zipfile.ZipFile(OUT,'w',zipfile.ZIP_DEFLATED) as z:
    # Minecraft 26.3 resource format 97.1, from misode/mcmeta versions/data.json, 2026-11-11.
    z.writestr('pack.mcmeta',json.dumps({'pack':{'description':'생활 타이쿤 · 주방 요리 아이콘 10종','min_format':[97,1],'max_format':[97,1]}},ensure_ascii=False,indent=2))
    for i,id in enumerate(IDS):
        z.writestr('assets/tycoon/items/'+id+'.json',json.dumps({'model':{'type':'minecraft:model','model':'tycoon:item/'+id}}))
        z.writestr('assets/tycoon/models/item/'+id+'.json',json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'tycoon:item/'+id}}))
        z.writestr('assets/tycoon/textures/item/'+id+'.png',png(i))
    z.writestr('LICENSE.txt','Original pixel icons created for this project. MIT license.\n')
print(OUT)
