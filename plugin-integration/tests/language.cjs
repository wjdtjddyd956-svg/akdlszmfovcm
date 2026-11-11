if(process.env.INTEGRATION_TEST_COPY!=='YES')throw Error('Isolated copy only');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const req=require('node:module').createRequire(path.resolve('.build/life-bot/package.json'));
for(const command of ['whitelist add IntLang1','deop IntLang1'])execFileSync(process.execPath,['.build/ui-rcon.cjs',command]);
let b=req('mineflayer').createBot({host:'127.0.0.1',port:25566,username:'IntLang1',version:'26.1',auth:'offline',physicsEnabled:false,viewDistance:'tiny'});
let lines=[];b.on('messagestr',s=>lines.push(s));b.on('resourcePack',(url,id)=>b._client.write('resource_pack_receive',{uuid:String(id),result:1}));
const write=b._client.write.bind(b._client);b._client.write=(n,p)=>['position','position_look','look','flying','vehicle_move','player_input','tick_end'].includes(n)?undefined:write(n,p);
setTimeout(()=>{try{assert.ok(lines.some(s=>s.includes('가방이 비어 있는 동안')));assert.ok(!lines.some(s=>s.includes("You're protected from attack")));fs.writeFileSync('releases/plugin-integration/1.0.0/language-runtime.json',JSON.stringify({actualServer:'Paper26.3build238',client:'automatic26.1 through adapters',checks:['actual GriefPrevention first-join PvP immunity message uses Korean Text value'],all_187_messages_executed:false},null,2));console.log('PASS actual GP join message Korean');b.quit();setTimeout(()=>process.exit(0),300)}catch(e){console.error(e,lines);b.quit();setTimeout(()=>process.exit(1),300)}},10000);
