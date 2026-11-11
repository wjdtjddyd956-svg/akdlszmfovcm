// Actual Paper 26.3 server + protocol adapter on isolated localhost; never production.
if(process.env.INTEGRATION_TEST_COPY!=='YES')throw Error('Isolated copy only');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const req=require('node:module').createRequire(path.resolve('.build/life-bot/package.json')),mineflayer=req('mineflayer');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));let b,checks=[];
const rcon=s=>execFileSync(process.execPath,['.build/ui-rcon.cjs',s],{encoding:'utf8',maxBuffer:2e6}).replace(/§./g,'').trim();
const pass=s=>{checks.push(s);console.log('PASS',s)};
async function until(f,n,ms=15000){let end=Date.now()+ms;while(Date.now()<end){let x=f();if(x)return x;await sleep(80)}throw Error('Timeout '+n)}
async function cmd(s){if(b.currentWindow)b.closeWindow(b.currentWindow);await sleep(1200);b.chat(s);await sleep(400)}
async function window(re){return until(()=>b.currentWindow&&re.test(JSON.stringify(b.currentWindow.title)),re.toString())}
async function click(slot){await b.clickWindow(slot,0,0);await sleep(400)}
function view(){return JSON.parse(rcon('uiprobe view '+b.username))}
function text(slot){let s=view().slots.find(s=>s.slot===slot);return [s?.name,...s?.lore||[]].join('\n')}
function parse(){return rcon('papi parse '+b.username+' %koreanlife_money%|%koreanlife_job%|%koreanlife_job_level%|%koreanlife_farmer_level%|%koreanlife_owed%|%koreanlife_codex%|%koreanlife_achievements%|%koreanlife_online%|%koreanlife_rank%')}
async function run(){
 const after=process.argv[2]==='after',name='IntTest2';rcon('whitelist add '+name);rcon('deop '+name);
 b=mineflayer.createBot({host:'127.0.0.1',port:25566,version:'26.1',username:name,auth:'offline',physicsEnabled:false,viewDistance:'tiny'});
 let write=b._client.write.bind(b._client);b._client.write=(n,p)=>['position','position_look','look','flying','vehicle_move','player_input','tick_end'].includes(n)?undefined:write(n,p);
 b.lines=[];b.packs=[];b.holos=[];b.scores=[];
 b.on('messagestr',s=>b.lines.push(s));b.on('resourcePack',(url,id)=>{b.packs.push(url);b._client.write('resource_pack_receive',{uuid:String(id),result:1})});
 b._client.on('scoreboard_score',p=>b.scores.push(JSON.stringify(p)));b._client.on('scoreboard_objective',p=>b.scores.push(JSON.stringify(p)));
 b.on('error',console.error);await until(()=>b.entity?.position&&b.health>0,'join');await sleep(4200);
 assert.equal(b.packs.length,1);pass('actual single merged pack request; client decline stays connected');
 if(process.argv[2]==='setup'){
  rcon('farmprobe prepare '+name);rcon('farmprobe level '+name+' 300');rcon('essentials:eco set '+name+' 4321.25');
  rcon('farmprobe ready '+name);rcon('farmprobe soil '+name+' 226 100 226');rcon('minecraft:tp '+name+' 226 101 227');
  for(const command of ['farmprobe hand '+name+' tomato_seed 1','farmprobe plant '+name+' tomato 226 100 226','farmprobe progress '+name+' tomato 999999','farmprobe harvest '+name+' tomato'])assert.match(rcon(command),/probe-ok/);
  rcon('codexprobe prepare '+name+' oak_log 2');await cmd('/도감 등록');await window(/1개 소비 확인/);await click(30);
 }
 await sleep(3000);let fields=parse();assert.match(fields,/4,321\.25원\|농부\|3\|3\|2원\|1\/85\|0\/13\|\d+\|주민/);pass('PAPI reads actual Vault balance4321.25, farmer level3, earned2, codex1/85, awards0/13, actual online count');
 assert.ok(b.scores.some(s=>s.includes('4,321.25'))||b.scores.some(s=>s.includes('마을 생활')));pass('actual TAB scoreboard protocol packets contain Korean title or real balance');
 await cmd('/메뉴');await window(/마을 수첩/);assert.match(text(47),/마을 안내·서버 정보/);await click(47);await window(/마을 안내 \|/);
 assert.match(text(19),/4,321\.25원/);assert.match(text(20),/농부.*\n.*3.*\n.*3.*\n.*2원/);assert.match(text(21),/1\/85/);pass('existing ShiftF menu slot47 opens same real values without replacing original buttons');
 let before=JSON.stringify(view());await b.clickWindow(19,1,0);await sleep(250);assert.equal(JSON.stringify(view()),before);pass('right click does not transfer GUI item or execute action');
 await click(45);await window(/마을 수첩/);await click(47);await window(/마을 안내 \|/);await click(21);await window(/아이템 도감/);pass('bridge to existing codex menu and return to main are connected');
 await cmd('/야생');assert.ok(b.lines.some(s=>s.includes('검증 대기')));let balance=parse().split('|')[0];assert.ok(balance.includes('4,321.25'));pass('pending wilderness action is honest Korean status, no money mutation');
 for(let command of ['/권한관리 등급 IntTest2 운영자','/지형생성 시작','/안내판관리 연결']){
  let n=b.lines.length;await cmd(command);assert.ok(b.lines.slice(n).some(s=>s.includes('운영자 전용')));
 }pass('non-OP denied all three administrative Korean aliases');
 let permission=JSON.parse(rcon('bridgeprobe '+name));assert.equal(permission.op,false);
 for(let node of ['koreaneconomy.admin','koreaneconomy.operations','essentials.eco','essentials.give','griefprevention.adminclaims','worldguard.region.bypass.world','luckperms.user.parent.add'])assert.equal(permission[node],false,node);
 pass('actual Bukkit permissions: normal rank has no economy creation, rollback, claim bypass or rank editing permission');
 await cmd('/정보표시');assert.ok(b.lines.some(s=>s.includes('정보판을 껐')));await cmd('/정보표시');assert.ok(b.lines.some(s=>s.includes('정보판을 켰')));pass('Korean scoreboard toggle works with real TAB and permission');
 let n=b.lines.length;await cmd('/dh list');assert.ok(b.lines.slice(n).some(s=>s.includes('권한')));pass('DecentHolograms actual permission-denial message is Korean');
 rcon('lp user '+name+' parent add resident');await sleep(3000);assert.match(parse(),/\|단골$/);
 assert.equal(JSON.parse(rcon('bridgeprobe '+name))['koreaneconomy.operations'],false);pass('resident rank changes real Korean display, inherits normal rights without operating rights');
 rcon('lp user '+name+' parent add admin');await sleep(3000);assert.match(parse(),/\|운영자$/);
 let admin=JSON.parse(rcon('bridgeprobe '+name));assert.equal(admin.op,false);assert.equal(admin['koreaneconomy.operations'],true);assert.equal(admin['essentials.eco'],false);assert.equal(admin['luckperms.user.parent.add'],false);
 n=b.lines.length;await cmd('/권한관리 등급 '+name+' 운영자');assert.ok(b.lines.slice(n).some(s=>s.includes('원래 플러그인의 권한')));
 pass('explicit admin rank has bounded operations rights, no OP, money creation, permission-edit or promotion bypass');
 rcon('lp user '+name+' parent remove admin');rcon('lp user '+name+' parent remove resident');await sleep(3000);assert.match(parse(),/\|주민$/);
 assert.equal(JSON.parse(rcon('bridgeprobe '+name))['koreaneconomy.operations'],false);pass('rank removal takes effect without rejoin and leaves baseline gameplay rights');
 rcon('안내판관리 연결');rcon('안내판관리 연결');
 assert.equal(fs.readdirSync('../.paper-economy-fix-test/plugins/DecentHolograms/holograms').filter(n=>n.startsWith('koreanlife_town')&&n.endsWith('.yml')).length,1);
 assert.equal(parse(),fields);pass('repeat linking and denied commands preserve exact profile values');
 const report={date:new Date().toISOString(),phase:after?'after-restart':'before-restart',server:'actual Paper 26.3 build238',client:'mineflayer26.1 through test-only ViaVersion/ViaBackwards5.12.2',actual_game_client:false,simulated:['farm resource-pack readiness for fixture setup'],checks,profile:parse(),packRequests:b.packs.length};
 fs.writeFileSync('releases/plugin-integration/1.0.0/runtime-'+(after?'after':'before')+'.json',JSON.stringify(report,null,2)+'\n');
 console.log('TOTAL',checks.length);b.quit();await sleep(600);
}
run().then(()=>process.exit(0)).catch(e=>{console.error(e.stack);console.error(b?.lines?.slice(-10));b?.quit();setTimeout(()=>process.exit(1),800)});
