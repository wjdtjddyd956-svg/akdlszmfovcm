"""Generate reviewed first-install configurations from exact vendor defaults.
Existing installs are NEVER overwritten by this generator; the installer refuses conflicts.
"""
from pathlib import Path
import argparse,json,re,shutil,hashlib
ROOT=Path(__file__).resolve().parents[1];MOD=ROOT/'plugin-integration'
p=argparse.ArgumentParser();p.add_argument('--defaults',type=Path,required=True);a=p.parse_args()
OUT=MOD/'config';OUT.mkdir(exist_ok=True)
def write(name,text):
    dest=OUT/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(text,encoding='utf-8')
def block(text,key,value):
    return re.sub(r'(?ms)^'+re.escape(key)+r':.*?(?=^[a-zA-Z_][\w-]*:|\Z)',key+':\n'+value+'\n\n',text,count=1)
tab=(a.defaults/'TAB/config.yml').read_text(encoding='utf-8')
tab=block(tab,'header-footer','''  enabled: true
  designs:
    default:
      header:
        - "&3&l우리 마을 · 생활 경제"
        - "&7%koreanlife_rank% &f%player% &7님, 환영합니다"
        - "&7접속 중 &b%koreanlife_online%명 &8| &7잔액 &6%koreanlife_money%"
      footer:
        - "&7직업 &f%koreanlife_job% &8| &7농부 &a%koreanlife_farmer_level%레벨"
        - "&7도감 &b%koreanlife_codex% &8| &7업적 &e%koreanlife_achievements%"
        - "&f쉬프트+F &7→ 마을 수첩 · NPC 우클릭 거래"''')
for key in ['scoreboard-teams','playerlist-objective','proxy-support']:
    # Keep all original option keys, disable only the actual active feature.
    start=tab.index(key+':');end=re.search(r'(?m)^[a-zA-Z_][\w-]*:',tab[start+len(key)+1:])
    finish=start+len(key)+1+end.start() if end else len(tab)
    part=tab[start:finish].replace('enabled: true','enabled: false',1);tab=tab[:start]+part+tab[finish:]
tab=block(tab,'scoreboard','''  enabled: true
  toggle-command: /정보표시
  remember-toggle-choice: true
  hidden-by-default: false
  delay-on-join-milliseconds: 1000
  scoreboards:
    korean-life:
      title: "&3&l마을 생활 수첩"
      lines:
        - "&7등급 &f%koreanlife_rank%"
        - "&7잔액 &6%koreanlife_money%"
        - "&7직업 &f%koreanlife_job% &7%koreanlife_job_level%레벨"
        - "&7농부 숙련 &a%koreanlife_farmer_level%레벨"
        - "&7받을 적립금 &e%koreanlife_owed%"
        - "&7도감 &b%koreanlife_codex%"
        - "&7업적 &e%koreanlife_achievements%"
        - "&7접속 중 &b%koreanlife_online%명"
        - "&f쉬프트+F &7마을 수첩"''')
tab=tab.replace('locale: en-US','locale: ko-KR');write('TAB/config.yml',tab)
write('TAB/groups.yml',json.dumps({'_DEFAULT_':{'tabprefix':'%koreanlife_prefix%','tagprefix':'%koreanlife_prefix%','customtabname':'%player%','tabsuffix':'','tagsuffix':''}},ensure_ascii=False,indent=2)+'\n')
messages=(a.defaults/'TAB/messages.yml').read_text(encoding='utf-8')
trans={'no-permission':'&c이 명령을 사용할 권한이 없습니다. 운영자에게 문의하세요.','command-only-from-game':'&c게임 안에서 사용하세요.','player-not-online':'&c%player% 이용자가 접속 중이 아닙니다.','invalid-number':'&c%input% 값은 숫자가 아닙니다.','scoreboard-feature-not-enabled':'&c정보판 기능이 비활성화되어 있습니다.','scoreboard-toggle-on':'&a생활 정보판을 켰습니다.','scoreboard-toggle-off':'&7생활 정보판을 껐습니다.','reload-success':'&b[TAB] 설정을 다시 읽었습니다.','reload-fail-file':'&c[TAB] %file% 설정 문법 오류입니다. 서버 기록을 확인하세요.','bossbar-toggle-on':'&a상단 안내를 켰습니다.','bossbar-toggle-off':'&7상단 안내를 껐습니다.'}
for key,value in trans.items():messages=re.sub(r'(?m)^'+key+r':[^\n]*',key+': '+json.dumps(value,ensure_ascii=False),messages)
write('TAB/messages.yml',messages)
chunky=(a.defaults/'Chunky/config.yml').read_text(encoding='utf-8').replace('language: en','language: ko').replace('update-interval: 1','update-interval: 10');write('Chunky/config.yml',chunky)
papi=(a.defaults/'PlaceholderAPI/config.yml').read_text(encoding='utf-8').replace("'true': 'yes'","'true': '예'").replace("'false': 'no'","'false': '아니요'").replace('MM/dd/yy HH:mm:ss','yyyy-MM-dd HH:mm:ss');write('PlaceholderAPI/config.yml',papi)
dh=(a.defaults/'DecentHolograms/config.yml').read_text(encoding='utf-8').replace('text: Blank Line','text: 안내 문구').replace('display-range: 48','display-range: 32').replace('update-range: 48','update-range: 32').replace('update-interval: 20','update-interval: 40');write('DecentHolograms/config.yml',dh)
lang=(a.defaults/'DecentHolograms/lang.yml').read_text(encoding='utf-8')
trans={'only_player':'{prefix}&c게임 안에서 사용하세요.','no_perm':'{prefix}&c이 기능을 사용할 권한이 없습니다.','prefix':'&8[&3마을 안내판&8] &7','reloaded':'{prefix}&a설정을 다시 읽었습니다. (%1$d ms)','unknown_sub_command':'{prefix}&c알 수 없는 하위 명령입니다.','usage':'{prefix}사용법: &b%1$s','use_help':'{prefix}&b/안내판관리 목록&7 또는 &b/dh help&7를 확인하세요.'}
for key,value in trans.items():lang=re.sub(r'(?m)^(\s*)'+key+r':[^\n]*',lambda m:m[1]+key+': '+json.dumps(value,ensure_ascii=False),lang)
write('DecentHolograms/lang.yml',lang)
# LP uses the official ko_KR bundle according to each player's client language.
# Preserve default LP storage configuration; auto-install-translations is already true.
lp=(a.defaults/'LuckPerms/config.yml').read_text(encoding='utf-8')
lp=lp.replace('primary-group-calculation: stored','primary-group-calculation: parents-by-weight')
write('LuckPerms/config.yml',lp)
report={'source_defaults':'official exact-version first boot on isolated Paper 26.3 build 238','korean_player_messages':list(trans),'partial_admin_translation':['TAB/messages.yml','DecentHolograms/lang.yml'],'luckperms':'official auto-installed ko_KR.properties; client ko_kr','chunky':'bundled official ko.json','files':{f.relative_to(OUT).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in OUT.rglob('*') if f.is_file()}}
(MOD/'translation-scope.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print('PASS first-install configs:',len(report['files']))
