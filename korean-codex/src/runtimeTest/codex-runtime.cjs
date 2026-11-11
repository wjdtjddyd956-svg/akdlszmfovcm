// Only for the isolated Paper copy, with test-only ViaVersion/ViaBackwards and CodexProbe.
if(process.env.CODEX_ISOLATED_TEST_COPY!=='YES')throw Error('Isolated Paper copy required');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const req=process.env.CODEX_TEST_MODULES?require('node:module').createRequire(path.join(process.env.CODEX_TEST_MODULES,'package.json')):require;
const mineflayer=req('mineflayer'),{execFileSync}=require('node:child_process');
const rconScript=process.env.CODEX_TEST_RCON_SCRIPT;if(!rconScript)throw Error('Private test RCON script path required');
const rcon=(...c)=>execFileSync(process.execPath,[rconScript,...c],{encoding:'utf8'}).replace(/§./g,'');
const delay=ms=>new Promise(r=>setTimeout(r,ms));const bots=[];let checks=0;
const ok=m=>{checks++;console.log('PASS',m)};
async function until(f,label,ms=12000){let end=Date.now()+ms;while(Date.now()<end){let v=f();if(v)return v;await delay(60)}throw Error('Timeout: '+label)}
async function join(name){rcon('whitelist add '+name,'deop '+name);let b=mineflayer.createBot({host:'127.0.0.1',port:25566,version:'26.1',username:name,auth:'offline',viewDistance:'tiny',physicsEnabled:false});const write=b._client.write.bind(b._client);b._client.write=(n,p)=>['position','position_look','look','flying','vehicle_move','player_input','tick_end'].includes(n)?undefined:write(n,p);bots.push(b);b.lines=[];b.on('messagestr',s=>b.lines.push(s));b.on('error',e=>console.log('client error',e.message));await until(()=>b.entity?.position&&b.health>0,name+' spawn');await delay(250);return b;}
function close(b){if(b.currentWindow)b.closeWindow(b.currentWindow)}
async function send(b,s){const wait=1100-(Date.now()-(b.lastCommand||0));if(wait>0)await delay(wait);b.chat(s);b.lastCommand=Date.now()}
async function cmd(b,s){await send(b,s);await delay(250)}
async function menu(b,s){close(b);await send(b,s);const expected=s==='/도감 등록'?/도감 등록/:s==='/메뉴'?/마을 수첩/:s==='/업적'?/수집 업적/:/도감/;await until(()=>b.currentWindow&&expected.test(JSON.stringify(b.currentWindow.title)),s);return b.currentWindow;}
async function click(b,slot){await b.clickWindow(slot,0,0);await delay(180)}
const amount=(b,n)=>b.inventory.items().filter(i=>i.name===n).reduce((a,i)=>a+i.count,0);
const attrs=b=>Object.fromEntries(rcon('codexprobe attrs '+b.username).trim().split(/\s+/).filter(s=>s.includes('=')).map(s=>s.split('=')));
function near(a,b){assert.ok(Math.abs(Number(a)-Number(b))<0.000001,`${a} != ${b}`)}
async function give(b,material,count=3){close(b);rcon('codexprobe prepare '+b.username+' '+material+' '+count);b.setQuickBarSlot(0);await delay(220)}
async function register(b,material,count=3){await give(b,material,count);await menu(b,'/도감 등록');await click(b,30);close(b);}
async function features(){
 const b=await join('CodexCheck3');await menu(b,'/메뉴');await delay(150);assert.equal(b.currentWindow.slots[20].name,'book');assert.equal(b.currentWindow.slots[26].name,'golden_chestplate');await click(b,20);assert.match(JSON.stringify(b.currentWindow.title),/아이템 도감/);close(b);ok('existing Korean menu injected in blank slots; opens codex without replacing KoreanMenu');
 rcon('codexprobe external '+b.username,'minecraft:item replace entity '+b.username+' armor.chest with minecraft:iron_chestplate');const start=attrs(b);assert.equal(start.speed_potion,'true');near(start.ARMOR,6);
 await give(b,'oak_log');await menu(b,'/도감 등록');assert.equal(amount(b,'oak_log'),3);await click(b,30);close(b);assert.equal(amount(b,'oak_log'),2);const once=attrs(b);assert.equal(once.own_MOVE,'1');near(once.base_MOVE,start.base_MOVE);ok('confirm consumes one actual plain item and adds one transient speed modifier');
 await cmd(b,'/도감 등록');assert.equal(amount(b,'oak_log'),2);near(attrs(b).MOVE,once.MOVE);ok('re-register cannot consume again or stack modifiers');
 await give(b,'wheat');await menu(b,'/도감 등록');await click(b,32);close(b);assert.equal(amount(b,'wheat'),3);ok('cancel consumes nothing');
 rcon('codexprobe injure '+b.username);await menu(b,'/도감 등록');await Promise.allSettled([b.clickWindow(30,0,0),b.clickWindow(30,0,0)]);await delay(350);close(b);assert.equal(amount(b,'wheat'),2);assert.ok(Number(attrs(b).HEALTH)>21);near(attrs(b).hp,11);ok('simultaneous confirm clicks consume only one; health gain does not heal');
 for(let i=0;i<3;i++){await cmd(b,'/도감효과 끄기');await cmd(b,'/도감효과 켜기')}near(attrs(b).hp,11);assert.equal(attrs(b).speed_potion,'true');near(attrs(b).ARMOR,6);ok('toggling cannot heal and preserves existing potion, armor and external modifiers');
 for(const kind of ['named','enchanted','damaged','data','model']){close(b);rcon('codexprobe special '+b.username+' '+kind);await delay(220);await menu(b,'/도감 등록');await click(b,30);close(b);assert.equal(b.inventory.items().reduce((n,i)=>n+i.count,0),1);ok('custom item preserved: '+kind)}
 rcon('codexprobe special '+b.username+' mixed');await delay(220);await menu(b,'/도감 등록');await click(b,30);close(b);assert.equal(amount(b,'diamond'),3);assert.ok(JSON.stringify(b.inventory.slots[36]).includes('보존할 기념품'));ok('mixed inventory consumes one plain diamond and preserves named diamond');
 await give(b,'elytra',1);await menu(b,'/도감 등록');const text=JSON.stringify(b.currentWindow.slots[22]);assert.ok(text.includes('1개')&&text.includes('전설'));await click(b,32);close(b);assert.equal(amount(b,'elytra'),1);await menu(b,'/도감 등록');await click(b,30);close(b);assert.equal(amount(b,'elytra'),0);ok('legendary elytra confirmation shows actual cost and reward; cancel/confirm honored');
 let n=b.lines.length;await cmd(b,'/도감관리 거래');await cmd(b,'/도감관리 백업');assert.ok(b.lines.slice(n).filter(x=>x.includes('운영자 전용')).length===2);ok('ordinary player denied administration in Korean');
 await cmd(b,'/도감효과 끄기');let off=attrs(b);assert.equal(off.own_MOVE,'0');assert.equal(off.speed_potion,'true');near(off.HEALTH,21);near(off.ARMOR,6);near(off.MOVE,start.MOVE);await cmd(b,'/도감효과 켜기');near(attrs(b).MOVE,once.MOVE);ok('only codex modifiers removed; external attribute base and potion remain');
 await give(b,'nether_star',2);await register(b,'nether_star',2);assert.equal(amount(b,'nether_star'),1);assert.equal(attrs(b).own_DAMAGE,'1');ok('nether star registers once with legendary reward');
 console.log('FEATURE_ATTRIBUTES',JSON.stringify(attrs(b)));
}
async function full(){
 const b=await join('CodexFull');await cmd(b,'/도감효과 켜기');rcon('codexprobe injure '+b.username);let catalog=fs.readFileSync(path.join(__dirname,'../main/resources/catalog.tsv'),'utf8').trim().split(/\r?\n/).map(l=>l.split('\t'));
 for(let i=Number(process.env.CODEX_FULL_START||0);i<catalog.length;i++){await register(b,catalog[i][5].toLowerCase(),1);assert.equal(amount(b,catalog[i][5].toLowerCase()),0);if((i+1)%20===0)console.log('REGISTERED',i+1)}
 const a=attrs(b),caps={MOVE:.03,MINING:.06,HEALTH:2,DAMAGE:.5,ARMOR:1,ATTACK_SPEED:.02,KNOCKBACK:.02,LUCK:.5};for(const [k,v]of Object.entries(caps)){near(a['amount_'+k],v);assert.equal(a['own_'+k],'1')}near(a.HEALTH,22);near(a.MINING,1.06);near(a.hp,11);ok('80 real GUI registrations reach all eight exact caps without healing');
 await menu(b,'/업적');for(const s of [10,11,12,13,14,15,19,20,21,22,23,24])assert.equal(b.currentWindow.slots[s].name,'gold_ingot');close(b);ok('all twelve achievements automatically completed in real Paper');
 rcon('minecraft:execute in minecraft:the_nether run minecraft:tp CodexFull 0 120 0');await delay(350);for(const k of Object.keys(caps))assert.equal(attrs(b)['own_'+k],'1');rcon('minecraft:execute in minecraft:overworld run minecraft:tp CodexFull 28 100 28');await delay(250);near(attrs(b).MINING,1.06);ok('dimension changes do not stack codex bonuses');
 let died=false;b.once('death',()=>{died=true});rcon('essentials:kill CodexFull');await until(()=>died,'death');await until(()=>b.health>0,'respawn');await delay(350);for(const k of Object.keys(caps))assert.equal(attrs(b)['own_'+k],'1');near(attrs(b).HEALTH,22);ok('death and actual respawn restore one modifier per stat');
 await cmd(b,'/도감효과 끄기');assert.equal(attrs(b).own_HEALTH,'0');near(attrs(b).HEALTH,20);ok('personal disable removes complete bonuses before restart');
}
async function failure(){const b=await join('CodexFault');await give(b,'oak_log');rcon('codexprobe fail on');try{await menu(b,'/도감 등록');await click(b,30);close(b);assert.equal(amount(b,'oak_log'),2);assert.equal(attrs(b).own_MOVE,'0');let n=b.lines.length;await menu(b,'/도감 등록');await click(b,30);close(b);assert.equal(amount(b,'oak_log'),2);assert.ok(b.lines.slice(n).some(x=>x.includes('중지')||x.includes('확인')));assert.ok(rcon('도감관리 거래').includes('CONSUMED_SAVED'));ok('actual SQLite abort after player NBT save leaves review; no auto consume or award');}finally{rcon('codexprobe fail off')}}
async function review(){const b=await join('CodexFault');try{assert.equal(amount(b,'oak_log'),2);assert.equal(attrs(b).own_MOVE,'0');let n=b.lines.length;await menu(b,'/도감 등록');await click(b,30);close(b);assert.equal(amount(b,'oak_log'),2);assert.ok(b.lines.slice(n).some(x=>x.includes('중지')||x.includes('확인')));assert.ok(rcon('도감관리 거래').includes('CONSUMED_SAVED'));ok('actual SQLite abort after player NBT save leaves review; no auto consume or award');}finally{rcon('codexprobe fail off')}}
async function after(){
 const b=await join('CodexCheck3'),full=await join('CodexFull'),fault=await join('CodexFault');const a=attrs(b);assert.equal(a.own_MOVE,'1');assert.equal(a.own_HEALTH,'1');assert.equal(a.speed_potion,'true');near(a.ARMOR,6);ok('registered bonuses, external equipment/potion survive real server restart without stacking');
 let f=attrs(full);assert.equal(f.own_HEALTH,'0');near(f.HEALTH,20);await cmd(full,'/도감효과 켜기');f=attrs(full);near(f.HEALTH,22);for(const k of ['MOVE','MINING','HEALTH','DAMAGE','ARMOR','ATTACK_SPEED','KNOCKBACK','LUCK'])assert.equal(f['own_'+k],'1');ok('80 registrations and disabled preference persist; enabling restores only one modifier');
 await menu(full,'/업적');for(const s of [10,11,12,13,14,15,19,20,21,22,23,24])assert.equal(full.currentWindow.slots[s].name,'gold_ingot');close(full);ok('all twelve achievements persist across restart');
 assert.equal(amount(fault,'oak_log'),2);await cmd(fault,'/도감 등록');assert.equal(amount(fault,'oak_log'),2);assert.equal(attrs(fault).own_MOVE,'0');let log=rcon('도감관리 거래');const rid=log.match(/[0-9a-f]{8}-[0-9a-f-]{27}/)?.[0];assert.ok(rid);rcon('도감관리 완료 '+rid+' 확인');await delay(200);assert.equal(amount(fault,'oak_log'),2);assert.equal(attrs(fault).own_MOVE,'1');ok('review survives restart; operator completion grants record without further consumption');
 rcon('codexprobe orphan CodexFault');await give(fault,'wheat');await menu(fault,'/도감 등록');await click(fault,30);close(fault);assert.equal(amount(fault,'wheat'),3);assert.ok(rcon('도감관리 거래').includes('저장 표식 확인 대기'));rcon('도감관리 표식해제 CodexFault 확인');assert.equal(amount(fault,'wheat'),3);await menu(fault,'/도감 등록');await click(fault,30);close(fault);assert.equal(amount(fault,'wheat'),2);ok('unknown saved marker blocks registration until explicit operator acknowledgement');
 assert.ok(rcon('도감관리 백업').includes('백업 완료'));ok('operator SQLite snapshot succeeds after real registrations');
}
const actions={features,full,failure,review,after};
(actions[process.argv[2]]||(()=>{throw Error('features|full|failure|after required')}))().then(()=>{console.log('TOTAL',checks);for(const b of bots)b.quit();setTimeout(()=>process.exit(0),400)}).catch(e=>{console.error(e.stack);for(const b of bots){console.error(b.username,b.lines.slice(-6));b.quit()}setTimeout(()=>process.exit(1),500)});
