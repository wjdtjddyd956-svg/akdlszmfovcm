"""Restore reproducible public inputs from pinned, official GitHub files."""
from pathlib import Path
import json,urllib.request,hashlib,zipfile

def prepare(root,here,cache,out):
 cache.mkdir(parents=True,exist_ok=True);target=cache/'받은모드';target.mkdir(exist_ok=True)
 if not (cache/'받은모드-분석.json').exists() or not (cache/'모드목록-확정.json').exists():
  fixed=json.loads((here/'공식배포-고정목록.json').read_text())['모드'];items=[]
  for row in fixed:
   file=target/row['공식파일']
   if not file.exists():
    req=urllib.request.Request(row['다운로드주소'],headers={'User-Agent':'Korean-Minecraft-Mod-Pack/1.0'})
    with urllib.request.urlopen(req,timeout=60) as response:data=response.read()
    assert hashlib.sha256(data).hexdigest()==row['SHA256'];file.write_bytes(data)
   data=file.read_bytes();assert hashlib.sha256(data).hexdigest()==row['SHA256']
   with zipfile.ZipFile(file) as z:meta=json.loads(z.read('fabric.mod.json'))
   assert meta['id']==row['식별자'] and meta['version']==row['확보버전']
   items.append({'id':row['식별자'],'filename':file.name,'url':row['다운로드주소'],'sha256':row['SHA256'],'sha512':row['SHA512'],'size':len(data),'metadata':meta})
  (cache/'받은모드-분석.json').write_text(json.dumps([x for x in items if x['id']!='modmenu'],ensure_ascii=False,indent=2)+'\n')
  (cache/'모드목록-확정.json').write_text(json.dumps(next(x for x in items if x['id']=='modmenu'),ensure_ascii=False,indent=2)+'\n')
 if not (cache/'업로드-분석.json').exists():
  source=json.loads((out/'모드목록.json').read_text())['모드']
  safe=[{'uploaded_filename':x['업로드원본'],'metadata':{'id':x['식별자'],'contact':{'sources':x.get('공식자료')}}} for x in source]
  (cache/'업로드-분석.json').write_text(json.dumps(safe,ensure_ascii=False,indent=2)+'\n')
 if not (cache/'sodium-en.response').exists():
  baseline=json.loads((here/'언어검증-기준.json').read_text());url=baseline['소듐영문주소']
  with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Korean-Minecraft-Mod-Pack/1.0'}),timeout=30) as response:data=response.read()
  assert hashlib.sha256(data).hexdigest()==baseline['소듐원문SHA256'];(cache/'sodium-en.response').write_bytes(data)
