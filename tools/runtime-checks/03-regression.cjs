if(process.env.TYCOON_ISOLATED_TEST_COPY!=='YES')throw Error('Only run against an isolated Paper copy on 127.0.0.1:25566');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const path = require('node:path');
const mineflayer = require('mineflayer');
const { Vec3 } = require('node:module').createRequire(require.resolve('mineflayer'))('vec3');
const rconScript = path.join(__dirname, 'rcon.cjs');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const rcon = (...commands) => execFileSync(process.execPath, [rconScript, ...commands], { encoding: 'utf8' }).replace(/§./g, '');
const bots = [];
let passed = 0;
function ok(label, data = '') { ++passed; console.log('PASS', label, data); }
async function until(test, label, ms = 8000) {
    const started = Date.now();
    do {
        const result = test();
        if (result) return result;
        await delay(100);
    } while (Date.now() - started < ms);
    throw Error('Timed out: ' + label);
}
async function join(username) {
    const bot = mineflayer.createBot({ host:'127.0.0.1', port:25566, version:'26.1', username, auth:'offline', viewDistance:'tiny', physicsEnabled:false });
    const write=bot._client.write.bind(bot._client);
    bot._client.write=(name,packet)=>['position','position_look','look','flying','vehicle_move','player_input','tick_end'].includes(name)
        ? undefined : write(name,packet);
    bots.push(bot);
    bot.lines = [];
    bot.on('messagestr', m => { bot.lines.push(m); });
    bot.on('error', e => console.error(username, e.message));
    bot.on('kicked', reason => console.error(username, 'kicked', reason));
    await until(() => bot.entity?.position && bot.health > 0, username + ' spawn', 12000);
    await delay(700);
    return bot;
}
async function tp(bot, x, y, z, yaw=0, pitch=0) {
    rcon(`minecraft:tp ${bot.username} ${x} ${y} ${z} ${yaw} ${pitch}`);
    await until(() => bot.entity.position.distanceTo(new Vec3(x,y,z)) < 1.5, 'teleport');
    await delay(300);
}
function amount(bot, name) { return bot.inventory.items().filter(item => item.name === name).reduce((total,item) => total + item.count, 0); }
function balance(bot) {
    const text = rcon('essentials:balance ' + bot.username);
    const match = text.match(/([\d,]+)원/);
    if (!match) throw Error('Unexpected balance: ' + text);
    return Number(match[1].replaceAll(',', ''));
}
function close(bot) { if (bot.currentWindow) bot.closeWindow(bot.currentWindow); }
async function command(bot, text, pause=400) { bot.chat(text); await delay(pause); }
async function menu(bot, text) {
    close(bot);
    await command(bot,text);
    return until(() => bot.currentWindow, text);
}
async function npc(bot, position) {
    close(bot);
    const target = await until(() => Object.values(bot.entities).find(entity => entity.position.distanceTo(position) < 0.7 &&
        !['PackTestA','PackTestB'].includes(entity.username) && entity.name !== 'item'), 'NPC at ' + position);
    await bot.lookAt(target.position.offset(0,1.3,0), true);
    await bot.activateEntity(target);
    return until(() => bot.currentWindow, 'NPC window at ' + position);
}
async function click(bot, slot, button=0, mode=0) {
    await bot.clickWindow(slot,button,mode);
    await delay(350);
}
function blockClick(bot, target, direction=1) {
    bot.action = (bot.action || 0) + 1;
    bot._client.write('block_place', { location:target, direction, hand:'main_hand',
        cursorX:0.5,cursorY:direction===1?0.999:0.5,cursorZ:0.5,insideBlock:false,worldBorderHit:false,sequence:bot.action });
}
async function claimCorner(bot, block, x, z) {
    const dx=block.x+0.5-x, dz=block.z+0.5-z;
    await tp(bot,x,100,z,-Math.atan2(dx,dz)*180/Math.PI,Math.atan2(1.62,Math.hypot(dx,dz))*180/Math.PI);
    const shovel = await until(() => bot.inventory.items().find(item => item.name === 'golden_shovel'), 'golden shovel');
    await bot.equip(shovel,'hand');
    const target = await until(() => bot.blockAt(block), 'claim corner block');
    blockClick(bot,target.position);
    await delay(600);
}


async function run(){
 rcon('op PackTestA','deop TycoonPatch','deop PackTestB');const a=await join('PackTestA'),b=await join('TycoonPatch'),miner=await join('PackTestB');
 const before=balance(b),target=balance(a);await command(b,'/송금 PackTestA 1',900);assert.equal(balance(b),before-1);assert.equal(balance(a),target+1);ok('Korean payment transfers real Vault money between online users');
 const noMoney=balance(b);await command(b,'/돈관리 지급 TycoonPatch 999999');assert.equal(balance(b),noMoney);ok('ordinary player cannot generate money');
 await tp(b,29.5,100,29.5);rcon('minecraft:setblock 30 99 29 minecraft:farmland[moisture=7]','minecraft:setblock 30 100 29 minecraft:wheat[age=7]');await delay(350);await b.equip(b.inventory.items().find(i=>i.name==='wheat'),'hand');await b.dig(b.blockAt(new Vec3(30,100,29)),true);await menu(b,'/직업');await click(b,40);close(b);assert.equal(balance(b),noMoney+2);await menu(b,'/직업');await click(b,40);close(b);assert.equal(balance(b),noMoney+2);ok('farmer accrual from actual harvest, payout once');
 await command(b,'/집저장');await tp(b,27.5,100,28.5);await command(b,'/집',1000);assert.ok(b.entity.position.distanceTo(new Vec3(29.5,100,29.5))<1);await command(b,'/스폰',1000);assert.ok(b.entity.position.distanceTo(new Vec3(29.5,100,29.5))>5);ok('Korean home save, home and spawn teleport');
 await menu(miner,'/직업');assert.match(JSON.stringify(miner.currentWindow.slots[4]),/광부/);close(miner);ok('legacy 1.2 selected miner imported');
 await tp(miner,27.5,100,23.5,180,35);rcon('minecraft:give PackTestB minecraft:diamond_pickaxe 1','minecraft:setblock 27 100 22 minecraft:iron_ore','minecraft:setblock 28 100 22 minecraft:air');await delay(400);await miner.equip(miner.inventory.items().find(i=>i.name==='diamond_pickaxe'),'hand');await miner.dig(miner.blockAt(new Vec3(27,100,22)),true);
 await tp(miner,28.5,100,23.5,180,59);assert.match(rcon('lifetest ore PackTestB 28 100 22'),/ore-accepted=true/);await delay(350);await miner.dig(miner.blockAt(new Vec3(28,100,22)),true);const income=balance(miner);await menu(miner,'/직업');await click(miner,40);close(miner);assert.equal(balance(miner),income+3);ok('natural ore rewards; real Bukkit placed-ore event excluded');
 rcon('minecraft:fill 100 99 100 112 99 112 minecraft:stone','minecraft:setblock 104 100 104 minecraft:chest');await tp(a,105.5,100,104.5);if(!rcon('lifetest permissions PackTestA 104 100 104').includes('owner=PackTestA'))assert.match(rcon('lifetest claim PackTestA 100 100 100'),/claim-created=true/);
 await tp(b,106.5,100,104.5,90,30);let n=b.lines.length;await command(b,'/땅보호 해제 확인');assert.match(b.lines.slice(n).join(' '),/내 땅/);assert.match(rcon('lifetest permissions TycoonPatch 104 100 104'),/build=false chest=false owner=PackTestA/);try{await Promise.race([b.dig(b.blockAt(new Vec3(104,100,104)),true),delay(1400)])}catch{};assert.match(rcon('minecraft:data get block 104 100 104 id'),/minecraft:chest/);ok('real GP claim ownership and foreign block protection');
 await command(a,'/땅보호 상자초대 TycoonPatch');assert.match(rcon('lifetest permissions TycoonPatch 104 100 104'),/build=false chest=true/);rcon('minecraft:item replace block 104 100 104 container.0 with minecraft:diamond 1','lifetest chest TycoonPatch 104 100 104');await until(()=>b.currentWindow,'server opened chest');await click(b,0,0,1);close(b);await command(a,'/땅보호 회수 TycoonPatch');assert.match(rcon('lifetest permissions TycoonPatch 104 100 104'),/build=false chest=false/);ok('Korean GP chest trust and revoke (API-opened chest, real inventory click)');
 await tp(a,27.5,100,23.5);n=a.lines.length;await command(a,'/기록조회 PackTestB 15m 16 블록',1400);console.log('LOOKUP',a.lines.slice(n));await command(a,'/복구 미리보기 PackTestB 15m 16 블록',1600);await command(a,'/복구 적용',1500);assert.match(rcon('minecraft:execute if block 27 100 22 minecraft:iron_ore'),/test passed/i);ok('Prism block lookup preview apply restored real ore');
 await tp(a,105.5,100,104.5);n=a.lines.length;await command(a,'/기록조회 TycoonPatch 15m 16 상자',1400);await command(a,'/복구 미리보기 TycoonPatch 15m 16 상자',1700);await command(a,'/복구 적용',1500);console.log('CHEST',a.lines.slice(n));assert.match(rcon('minecraft:data get block 104 100 104 Items'),/diamond/);ok('Prism chest item remove recorded and restored');rcon('deop PackTestA');console.log('TOTAL',passed);
}
run().then(()=>{for(const b of bots)b.quit();setTimeout(()=>process.exit(0),500)}).catch(e=>{console.error(e);for(const b of bots){console.error(b.username,b.lines.slice(-8));b.quit()}setTimeout(()=>process.exit(1),500)});
