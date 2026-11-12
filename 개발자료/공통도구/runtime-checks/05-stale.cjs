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



async function run(){const b=await join('TycoonPatch');await tp(b,39.5,100,28.5);await b.equip(b.inventory.items().find(i=>i.name==='diamond_pickaxe'),'hand');await menu(b,'/강화');rcon('minecraft:item replace entity TycoonPatch hotbar.0 with minecraft:iron_pickaxe');await delay(600);b.setQuickBarSlot(0);await delay(300);const stale=balance(b);await click(b,31);close(b);assert.equal(balance(b),stale);ok('changing held tool after opening blocks stale enhancement without charge');await npc(b,new Vec3(38.5,100,25.5));await tp(b,50.5,100,30.5);const far=balance(b);await click(b,10);close(b);assert.equal(balance(b),far);ok('moving away from NPC invalidates trade');console.log('TOTAL',passed)}
run().then(()=>{for(const b of bots)b.quit();setTimeout(()=>process.exit(0),500)}).catch(e=>{console.error(e);for(const b of bots){console.error(b.lines.slice(-8));b.quit()}setTimeout(()=>process.exit(1),500)});