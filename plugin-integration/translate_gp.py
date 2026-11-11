from pathlib import Path
import argparse,re,json,hashlib
MOD=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);a=p.parse_args()
original=a.source.read_bytes();text=original.decode('utf-8-sig');translations=json.loads((MOD/'gp-korean.json').read_text(encoding='utf-8'))
changed=[];problems=[]
for key,value in translations.items():
    pattern=r'(?ms)(^  '+re.escape(key)+r':\r?\n)    Text:(.*?)(?=^    Notes:|^  \w+:|\Z)'
    match=re.search(pattern,text)
    if not match:problems.append(key+':missing');continue
    old=match[2];before=set(re.findall(r'\{\d+\}',old));after=set(re.findall(r'\{\d+\}',value))
    if before!=after:problems.append(key+': placeholders '+str(before)+' != '+str(after));continue
    text=text[:match.start()]+match[1]+'    Text: '+json.dumps(value,ensure_ascii=False)+'\n'+text[match.end():];changed.append(key)
if problems:raise SystemExit('\n'.join(problems))
text='\n'.join(line.rstrip() for line in text.splitlines())+'\n'
target=MOD/'config/GriefPreventionData/messages.yml';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text,encoding='utf-8')
(MOD/'gp-overlay.json').write_text(json.dumps({'path':'GriefPreventionData/messages.yml','originalSha256':hashlib.sha256(original).hexdigest(),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'translated_keys':changed,'preserved_internal_values':['TrappedChatKeyword','HowToClaimRegex','BookAuthor'],'source_version':'16.18.8'},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS original keys and format placeholders preserved:',len(changed))
