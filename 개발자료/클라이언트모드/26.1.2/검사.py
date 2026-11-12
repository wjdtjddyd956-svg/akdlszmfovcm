#!/usr/bin/env python3
from pathlib import Path
import json,zipfile,hashlib,re,sys
from 호환검사 import match,closure,inspect
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());HERE=Path(__file__).resolve().parent;OUT=ROOT/'설치파일/05_클라이언트모드/26.1.2';CACHE=ROOT/'.build/클라이언트모드-26.1.2';checks=0

def check(v):
 global checks
 checks+=1
 assert v

def sha(p,kind='sha256'):return hashlib.new(kind,p.read_bytes()).hexdigest()
for version,predicate,expected in [('26.1.2','~26.1',True),('26.1.2','~26.1-',True),('26.1.2','>=26.1 <26.2',True),('26.2','~26.1',False),('26.1.2','1.20.1',False),('0.9.2','~0.5.11',False),('0.19.5','>=0.18.4',True),('26.1.2','>1.26-',True),('26.1.2',['1.20.1','~26.1'],True),('26.1.2','26.1.x',True)]:check(match(version,predicate)==expected)
manifest=json.loads((OUT/'모드목록.json').read_text());check(len(manifest['모드'])==24);base=[x for x in manifest['모드'] if x['상태']=='기본구성'];check(len(base)==6)
for row in manifest['모드']:
 if row['식별자']!='visuality' and row['식별자'] not in {x['식별자'] for x in base}:check(row['상태'] not in ['기본구성','필수모드대기'])
check(next(x for x in manifest['모드'] if x['식별자']=='indium')['상태']=='제외')
check(next(x for x in manifest['모드'] if x['식별자']=='sodium')['상태']=='공식버전확인_다운로드대기')
files=[CACHE/'받은모드'/x['공식파일'] for x in base];result=closure(files);check(not result['errors']);check(result['dependency_checks']==129)
for row in base:
 file=CACHE/'받은모드'/row['공식파일'];check(sha(file)==row['SHA256']);check(sha(file,'sha512')==row['SHA512']);check(re.search('[가-힣]',row['한글파일']) is not None)
 d=inspect(file);check(d['modules'][row['식별자']]['version']==row['확보버전'])
try:closure(files+[files[0]])
except ValueError:check(True)
else:check(False)
visuality=next(x for x in manifest['모드'] if x['식별자']=='visuality');check(closure(files+[CACHE/'받은모드'/visuality['공식파일']])['errors']==['visuality → cloth-config 필요'])
baseline=json.loads((HERE/'언어검증-기준.json').read_text())['언어'];count=0
with zipfile.ZipFile(OUT/'모드한글팩-26.1.2-1.0.0.zip') as z:
 check(z.testzip() is None);meta=json.loads(z.read('pack.mcmeta'));check(meta['pack']['min_format']==[84,0] and meta['pack']['max_format']==[84,0]);allkeys=set()
 for path in z.namelist():
  if not path.startswith('assets/'):continue
  check(path.endswith('/lang/ko_kr.json'));ns=path.split('/')[1];values=json.loads(z.read(path));check(all(isinstance(v,str) and v for v in values.values()));allkeys.update(values);count+=len(values)
  en=baseline.get(ns,{}).get('영문',{})
  for key,value in values.items():
   if key.startswith('modmenu.nameTranslation.'):continue
   check(key in en);check(re.findall(r'%(?:\d+\$)?[\d.]*[sdf]',value)==re.findall(r'%(?:\d+\$)?[\d.]*[sdf]',en[key]));check(re.findall(r'<[^>]+>',value)==re.findall(r'<[^>]+>',en[key]))
 for ns,data in baseline.items():
  path='assets/'+ns+'/lang/ko_kr.json';supplement=json.loads(z.read(path)) if path in z.namelist() else {};check(not(set(data['영문'])-set(data['기본한국어키'])-set(supplement)))
 check(count==363)
with zipfile.ZipFile(OUT/'패브릭-26.1.2-한글기본6종.mrpack') as z:
 index=json.loads(z.read('modrinth.index.json'));check(index['dependencies']=={'minecraft':'26.1.2','fabric-loader':'0.19.5'});check(len(index['files'])==6);check(not any(p.endswith('.jar') for p in z.namelist()))
 for file in index['files']:
  row=next(x for x in base if file['path']=='mods/'+x['한글파일']);check(file['hashes']['sha512']==row['SHA512']);check(file['hashes']['sha1']==row['SHA1']);check(file['downloads']==[row['다운로드주소']]);check(file['env']=={'client':'required','server':'unsupported'})
 check(z.read('overrides/resourcepacks/모드한글팩-26.1.2-1.0.0.zip')==(OUT/'모드한글팩-26.1.2-1.0.0.zip').read_bytes());check('lang:ko_kr' in z.read('overrides/options.txt').decode())
check((OUT/'모드받기.ps1').read_bytes().startswith(b'\xef\xbb\xbf'));check('chcp 65001' in (OUT/'모드받기.cmd').read_text())
report={'검사수':checks,'기본모드수':6,'다운로드모드수':7,'대상버전':'26.1.2','로더':'Fabric 0.19.5','필수의존조건':129,'한국어키수':count,'서식인수와텍스트태그보존':True,'포함JAR와중복모드검사':True,'MRPACK_공식URL과해시':True,'모든24종완료':False,'게임실행검사':False}
(OUT/'정적검사.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('PASS',checks,'compatibility, translation, format, MRPACK and provenance checks')
