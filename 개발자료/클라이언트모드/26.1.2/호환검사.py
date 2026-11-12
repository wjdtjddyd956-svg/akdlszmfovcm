"""Inspect Fabric metadata without executing mods; this is not a Minecraft launch test."""
from pathlib import Path
import json,zipfile,io,re
TARGET='26.1.2';LOADER='0.19.5'
def number(v):
 v=str(v).split('+',1)[0];m=re.match(r'^(\d+)(?:\.(\d+))?(?:\.(\d+))?',v)
 if not m:raise ValueError('숫자 버전으로 판정할 수 없음: '+v)
 return tuple(int(x or 0) for x in m.groups())
def match(version,predicate):
 if isinstance(predicate,list):return any(match(version,p) for p in predicate)
 if predicate in ['*','']:return True
 if '||' in predicate:return any(match(version,p.strip()) for p in predicate.split('||'))
 actual=number(version)
 for part in predicate.split():
  m=re.fullmatch(r'(>=|<=|>|<|=|~|\^)?(\d+(?:\.(?:\d+|[xX*])){0,2}(?:-[A-Za-z0-9.-]*)?(?:\+[A-Za-z0-9.-]+)?)',part)
  if not m:raise ValueError('지원하지 않는 버전 조건: '+part)
  op,raw=m.groups();op=op or '=';expected=number(raw)
  if op=='~':
   count=len(raw.split('-')[0].split('.'));upper=(expected[0]+1,0,0) if count==1 else (expected[0],expected[1]+1,0)
   ok=expected<=actual<upper
  elif op=='^':
   upper=(expected[0]+1,0,0) if expected[0] else (0,expected[1]+1,0) if expected[1] else (0,0,expected[2]+1)
   ok=expected<=actual<upper
  elif '*' in raw or 'x' in raw.lower():
   prefix=[]
   for x in raw.split('.'):
    if x.lower() in ['x','*']:break
    prefix.append(int(x))
   ok=actual[:len(prefix)]==tuple(prefix)
  else:ok={'>=':actual>=expected,'<=':actual<=expected,'>':actual>expected,'<':actual<expected,'=':actual==expected}[op]
  if not ok:return False
 return True

def inspect(file):
 modules={};languages={};licenses={};localizations=[];max_major=0
 def visit(z,origin):
  nonlocal max_major
  assert z.testzip() is None
  if 'fabric.mod.json' not in z.namelist():raise ValueError('Fabric 모드가 아님: '+origin)
  meta=json.loads(z.read('fabric.mod.json').decode('utf-8-sig'));id=meta['id']
  if id in modules and modules[id]['version']!=meta['version']:raise ValueError('묶인 모드 버전 중복: '+id)
  modules[id]=meta
  for name in z.namelist():
   if name.endswith('.class'):
    b=z.read(name);assert b[:4]==b'\xca\xfe\xba\xbe';max_major=max(max_major,int.from_bytes(b[6:8],'big'))
   if re.fullmatch(r'assets/[^/]+/lang/(en_us|ko_kr)\.json',name):
    value=json.loads(z.read(name).decode('utf-8-sig'));assert all(isinstance(x,str) for x in value.values());languages[name]=value;localizations.append({'module_id':id,'version':meta['version'],'path':name,'value':value})
   if 'license' in name.lower() and not name.endswith('/') and not name.endswith('.class') and len(z.read(name))<100000:licenses[id+'!'+name]=z.read(name)
  for nested in meta.get('jars',[]):
   with zipfile.ZipFile(io.BytesIO(z.read(nested['file']))) as child:visit(child,origin+'!'+nested['file'])
 with zipfile.ZipFile(file) as z:visit(z,Path(file).name)
 return {'modules':modules,'languages':languages,'licenses':licenses,'max_class_major':max_major,'localizations':localizations}

def closure(files):
 providers={'minecraft':TARGET,'fabricloader':LOADER,'java':'25'};data=[];chosen={};roots=set()
 for f in files:
  d=inspect(f);assert d['max_class_major']<=69,'Java 25보다 새로운 바이트코드';data.append(d)
  root_id=next(iter(d['modules']))
  if root_id in roots:raise ValueError('최상위 모드 중복: '+root_id)
  roots.add(root_id)
  for id,meta in d['modules'].items():
   # Fabric resolves jar-in-jar candidates by semantic version; build metadata does not change precedence.
   if id not in chosen or number(meta['version'])>number(chosen[id]['version']):chosen[id]=meta
 for id,meta in chosen.items():
  providers[id]=meta['version']
  for alias in meta.get('provides',[]):providers[alias]=meta['version']
 errors=[];checked=0
 for id,meta in chosen.items():
  for dependency,predicate in meta.get('depends',{}).items():
   checked+=1
   if dependency not in providers:errors.append(id+' → '+dependency+' 필요')
   elif not match(providers[dependency],predicate):errors.append(id+' → '+dependency+' '+str(predicate)+' 조건 불일치')
  for dependency,predicate in meta.get('breaks',{}).items():
   if dependency in providers and match(providers[dependency],predicate):errors.append(id+' ↔ '+dependency+' 충돌')
 return {'providers':providers,'dependency_checks':checked,'errors':errors,'inspections':data,'roots':sorted(roots)}
