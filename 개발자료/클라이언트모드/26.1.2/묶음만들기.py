#!/usr/bin/env python3
"""Build a Korean overlay, a six-mod import pack and verified official-download tools."""
from pathlib import Path
import sys,json,hashlib,zipfile,io,shutil,urllib.parse,csv,re
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());HERE=Path(__file__).resolve().parent;OUT=ROOT/'설치파일/05_클라이언트모드/26.1.2';CACHE=ROOT/'.build/클라이언트모드-26.1.2';OUT.mkdir(parents=True,exist_ok=True)
from 호환검사 import closure,inspect
from 준비 import prepare
prepare(ROOT,HERE,CACHE,OUT)

def sha(data,kind='sha256'):return hashlib.new(kind,data).hexdigest()
def zipped(path,entries):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   i=zipfile.ZipInfo(name,(2026,11,13,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o644<<16;z.writestr(i,data)

def bjson(x):return (json.dumps(x,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
rows=json.loads((CACHE/'받은모드-분석.json').read_text())+[json.loads((CACHE/'모드목록-확정.json').read_text())]
lookup={r['id']:r for r in rows};names=json.loads((HERE/'한글표시이름.json').read_text())
oldrows=json.loads((CACHE/'업로드-분석.json').read_text());oldlookup={r['metadata']['id']:r for r in oldrows}
order=['punchy_moves','punchy','shulkerboxtooltip','sodium','visuality','voicechat','xaeroworldmap','xaerominimap','appleskin','citresewn','cloth-config','eatinganimationid','entity_model_features','entity_texture_features','entityculling','fabric-api','effective','ferritecore','indium','inventoryhud','jei','iris','modmenu','notenoughanimations']
files={'fabric-api':'패브릭API-0.155.3.jar','shulkerboxtooltip':'셜커상자미리보기-5.4.3.jar','visuality':'입자효과-0.7.13.jar','appleskin':'음식회복량-3.0.10.jar','entityculling':'엔티티최적화-1.12.0.jar','notenoughanimations':'추가동작-1.12.7.jar','modmenu':'모드목록-18.0.2.jar'}
base_ids=[id for id in lookup if id!='visuality'];basefiles=[CACHE/'받은모드'/lookup[id]['filename'] for id in base_ids];solved=closure(basefiles);assert not solved['errors'],solved['errors']
allcheck=closure([CACHE/'받은모드'/r['filename'] for r in rows]);assert allcheck['errors']==['visuality → cloth-config 필요']
contacts={'sodium':'https://github.com/CaffeineMC/sodium/releases/tag/mc26.1.2-0.9.2','effective':'https://github.com/Ladysnake/Effective','punchy_moves':'https://github.com/punchy-guys/punchy-wiki','indium':'https://github.com/comp500/Indium#since-sodium-060-indium-is-no-longer-required'}
modlist=[]
for id in order:
 old=oldlookup.get(id,{});meta=old.get('metadata',{});contact=meta.get('contact',{}) or {};url=contacts.get(id) or contact.get('sources') or contact.get('source') or contact.get('homepage') or contact.get('issues')
 record={'식별자':id,'한글이름':names[id],'업로드원본':old.get('uploaded_filename','effective-2.3.2-1.20.1.jar'),'상태':'추가확인대기','이유':'Modrinth API·파일 CDN 접속이 현재 클라우드에서 차단되어 정확한 26.1.2 배포를 확인하지 못함. 미지원이라고 판정한 것이 아님.','공식자료':url}
 if id in lookup:
  row=lookup[id];path=CACHE/'받은모드'/row['filename'];data=path.read_bytes();assert sha(data)==row['sha256'];assert sha(data,'sha512')==row['sha512']
  record.update({'상태':'기본구성' if id!='visuality' else '필수모드대기','이유':'공식 GitHub JAR 확보·Fabric 메타데이터가 26.1.2를 허용함. 실제 게임 실행은 미검증.' if id!='visuality' else '공식 JAR은 확보했으나 필수 Cloth Config를 아직 받지 못해 기본 구성에서 제외함.','확보버전':row['metadata']['version'],'한글파일':files[id],'공식파일':row['filename'],'다운로드주소':row['url'],'SHA256':sha(data),'SHA512':sha(data,'sha512'),'SHA1':sha(data,'sha1'),'크기':len(data),'게임버전조건':row['metadata'].get('depends',{}).get('minecraft','공식 배포 파일이 26.1.2 명시'),'필수조건':row['metadata'].get('depends',{}),'라이선스':row['metadata'].get('license'),'바이너리변경':False})
 elif id=='indium':record.update({'상태':'제외','이유':'제작자가 Sodium 0.6.0 이상에서 불필요하고 비호환이라고 안내함. 구버전을 새 Sodium에 넣지 않음.'})
 elif id=='sodium':record.update({'상태':'공식버전확인_다운로드대기','이유':'mc26.1.2-0.9.2 공식 릴리스 확인. 배포 사이트 접속 차단으로 본체를 받지 못함. 해당 공식 태그의 영어 언어 파일 105개를 번역함.','확인버전':'0.9.2+mc26.1.2'})
 modlist.append(record)
catalog={'대상버전':'26.1.2','로더':'Fabric','로더검사기준':'0.19.5','자바최소버전':25,'확인일':'2026-11-13','전체요청수':24,'받은원본수':7,'기본구성수':6,'한글화방식':'JAR 원본은 그대로 유지. 파일명과 Mod Menu 표시 이름은 한글, 언어 파일은 추가 리소스팩으로 보충.','Modrinth접속':'현재 실행 환경 차단·환경 설정 초안 저장됨·아직 미적용','모드':modlist}
(OUT/'모드목록.json').write_bytes(bjson(catalog));(HERE/'공식배포-고정목록.json').write_bytes(bjson({'대상버전':'26.1.2','모드':[x for x in modlist if x['식별자'] in lookup]}))
with (OUT/'모드목록.csv').open('w',encoding='utf-8-sig',newline='') as f:
 fields=['한글이름','식별자','상태','확보버전','한글파일','이유','공식자료'];w=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore',lineterminator='\n');w.writeheader();w.writerows(modlist)
# Add only missing translations, preserving every upstream language file and all original JARs.
translations=json.loads((HERE/'보충번역.json').read_text());translations['sodium']=json.loads((HERE/'소듐번역.json').read_text())
translations.setdefault('modmenu',{}).update({'modmenu.nameTranslation.'+id:name for id,name in names.items()})
langsource={};builtin={};baseline={};licensefiles={}
for row in rows:
 d=inspect(CACHE/'받은모드'/row['filename'])
 for lang in d['localizations']:
  if lang['module_id']!='visuality' and solved['providers'].get(lang['module_id'])!=lang['version']:continue
  path,values=lang['path'],lang['value'];ns=path.split('/')[1]
  if path.endswith('/en_us.json'):langsource.setdefault(ns,{}).update(values)
  else:builtin.setdefault(ns,{}).update(values)
 for name,data in d['licenses'].items():licensefiles[name]=data
sodium_en=json.loads((CACHE/'sodium-en.response').read_text());langsource['sodium']=sodium_en
for ns,en in langsource.items():
 addon=translations.get(ns,{});missing=set(en)-set(builtin.get(ns,{}))-set(addon);assert not missing,(ns,missing)
 for key,value in addon.items():
  if key.startswith('modmenu.nameTranslation.'):continue
  assert key in en,(ns,key)
  fmt=lambda s:re.findall(r'%(?:\d+\$)?[\d.]*[sdf]',s)
  assert fmt(value)==fmt(en[key]),(ns,key)
  assert re.findall(r'<[^>]+>',value)==re.findall(r'<[^>]+>',en[key]),(ns,key,'서식 태그')
 baseline[ns]={'영문':en,'기본한국어키':sorted(builtin.get(ns,{}))}
(HERE/'언어검증-기준.json').write_bytes(bjson({'출처':'공식 GitHub JAR 언어 파일과 Sodium mc26.1.2-0.9.2 공식 태그','소듐영문주소':'https://raw.githubusercontent.com/CaffeineMC/sodium/mc26.1.2-0.9.2/common/src/main/resources/assets/sodium/lang/en_us.json','소듐원문SHA256':sha((CACHE/'sodium-en.response').read_bytes()),'언어':baseline}))
packentries={'pack.mcmeta':bjson({'pack':{'pack_format':84,'min_format':[84,0],'max_format':[84,0],'description':'26.1.2 Fabric 모드 한국어 보충 · 공식 JAR 유지'}})}
for ns,values in translations.items():packentries['assets/'+ns+'/lang/ko_kr.json']=bjson(values)
packentries['번역안내.txt']='기존 모드 JAR와 공식 한국어 파일은 변경하지 않습니다. 한국어를 선택하고 이 팩을 위쪽에 적용하세요. 확보한 모드의 부족한 번역과 Sodium 0.9.2 공식 태그의 문구를 보충합니다. 전체 24종 본체를 포함하는 팩은 아닙니다.\n'.encode()
pack=OUT/'모드한글팩-26.1.2-1.0.0.zip';zipped(pack,packentries)
# Modrinth/Prism import downloads original JARs directly from their authors, rather than rehosting binaries.
packmods=[]
for record in modlist:
 if record['상태']!='기본구성':continue
 packmods.append({'path':'mods/'+record['한글파일'],'hashes':{'sha1':record['SHA1'],'sha512':record['SHA512']},'env':{'client':'required','server':'unsupported'},'downloads':[record['다운로드주소']],'fileSize':record['크기']})
index={'formatVersion':1,'game':'minecraft','versionId':'26.1.2-ko-base-1.0.0','name':'26.1.2 한글 기본 모드 · 확인된 6종','summary':'요청한 24종 중 확인된 6종의 기본 구성. 나머지와 Visuality는 확인대기목록을 참고하세요.','files':packmods,'dependencies':{'minecraft':'26.1.2','fabric-loader':'0.19.5'}}
mrpack={'modrinth.index.json':bjson(index),'overrides/resourcepacks/'+pack.name:pack.read_bytes(),'overrides/options.txt':('lang:ko_kr\nresourcePacks:["vanilla","file/'+pack.name+'"]\n').encode(),'overrides/모드확인안내.txt':'요청한 24종 전체가 아니라 공식 확인된 기본 6종입니다. Visuality는 Cloth Config 확보 전까지 제외했습니다. 게임 실행·셰이더·타 모드 조합은 미검증입니다.\n'.encode()}
zipped(OUT/'패브릭-26.1.2-한글기본6종.mrpack',mrpack)
# Retain third-party notices verbatim; license text is not translated or changed.
lic=OUT/'이용허가';lic.mkdir(exist_ok=True)
for old in lic.glob('원문-*.txt'):old.unlink()
license_index=[];license_hashes={}
for name,data in sorted(licensefiles.items()):
 digest=sha(data)
 if digest not in license_hashes:
  filename=f'원문-{len(license_hashes)+1:02d}.txt';(lic/filename).write_bytes(data);license_hashes[digest]=filename
 license_index.append({'파일':license_hashes[digest],'원문위치':name,'SHA256':digest})
(lic/'이용허가목록.json').write_bytes(bjson(license_index))
notice='# 원본 모드와 이용 허가\n\nGitHub 공개 묶음에는 모드 JAR를 넣지 않습니다. MRPACK과 다운로드 도구가 제작자의 공식 GitHub에서 원본을 직접 받습니다. 라이선스 문서는 원문 그대로 보관합니다.\n\n| 모드 | 버전 | 라이선스 | 공식 원본 |\n| --- | --- | --- | --- |\n'
for x in modlist:
 if x['식별자'] in lookup:notice+='| '+x['한글이름']+' | '+x['확보버전']+' | '+str(x['라이선스'])+' | [공식 다운로드]('+x['다운로드주소']+') |\n'
notice+='\nSodium 번역 기준은 [mc26.1.2-0.9.2 공식 소스](https://github.com/CaffeineMC/sodium/tree/mc26.1.2-0.9.2)입니다. 새 번역은 별도 언어 리소스팩으로 제공하고 바이너리를 바꾸지 않습니다.\n'
(OUT/'이용허가·출처.md').write_text(notice)
for file in ['모드받기.ps1','모드받기.cmd']:
 data=(HERE/file).read_text().replace('\n','\r\n').encode('utf-8-sig' if file.endswith('.ps1') else 'utf-8');(OUT/file).write_bytes(data)
for file in ['설치안내.md','확인대기목록.md']:
 if (HERE/file).exists():
  text=(HERE/file).read_text()
  def local_link(m):
   target=m.group(2)
   if re.match(r'\w+:|#',target):return m.group(0)
   resolved=(HERE/urllib.parse.unquote(target)).resolve()
   try:relative=resolved.relative_to(OUT).as_posix()
   except ValueError:return m.group(0)
   return m.group(1)+relative+m.group(3)
  (OUT/file).write_text(re.sub(r'(\]\()([^\n)]+)(\))',local_link,text))
count=sum(len(x) for x in translations.values());verified={'확인일':'2026-11-13','요청모드수':24,'공식JAR다운로드':7,'기본구성':6,'Visuality':'Cloth Config가 없어 기본 구성 제외','번역키수':count,'부족한한국어보충':sum(len(x) for x in json.loads((HERE/'보충번역.json').read_text()).values()),'소듐공식태그번역':105,'모드표시이름':24,'검사한영문언어키':sum(len(x) for x in langsource.values()),'필수의존조건검사수':solved['dependency_checks'],'기본구성의존성누락':solved['errors'],'실제Minecraft실행':False,'셰이더검사':False,'전체24종한글화완료':False,'Sodium본체다운로드':False,'Modrinth접속':'프록시 403 차단. 환경 설정 초안 저장, 실행 환경 미적용.','파일명변경':'JAR 바이트 변경 없음','바이트코드':'기본 구성 Java 25 호환 범위'}
(OUT/'검증결과.json').write_bytes(bjson(verified))
# A private, local full archive for this user's downloaded copies; never add third-party JARs to GitHub.
private=ROOT/'.build/클라이언트모드-26.1.2/받은모드-한글이름';private.mkdir(exist_ok=True)
for x in modlist:
 if x['식별자'] in lookup:shutil.copyfile(CACHE/'받은모드'/x['공식파일'],private/x['한글파일'])
print('PASS',count,'Korean keys, 6-mod dependency closure, original 7 hashes, official-source MRPACK and download tools')
