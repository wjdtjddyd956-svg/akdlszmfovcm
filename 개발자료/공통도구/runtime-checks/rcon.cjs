if(process.env.TYCOON_ISOLATED_TEST_COPY!=='YES')throw Error('Only run against an isolated Paper copy on 127.0.0.1:25566');
const net = require('node:net');
const fs = require('node:fs');
const path = require('node:path');
const password = fs.readFileSync(process.env.TYCOON_TEST_RCON_PASSWORD_FILE, 'utf8').trim();
const commands = process.argv.slice(2);
const socket = net.connect(25577, '127.0.0.1');
let buffer = Buffer.alloc(0), next = 0;
function send(id, type, content) {
    const data = Buffer.from(content, 'utf8');
    const frame = Buffer.alloc(14 + data.length);
    frame.writeInt32LE(frame.length - 4);
    frame.writeInt32LE(id, 4);
    frame.writeInt32LE(type, 8);
    data.copy(frame, 12);
    socket.write(frame);
}
socket.on('connect', () => send(1, 3, password));
socket.on('data', chunk => {
    buffer = Buffer.concat([buffer, chunk]);
    while (buffer.length >= 4 && buffer.length >= buffer.readInt32LE() + 4) {
        const size = buffer.readInt32LE() + 4;
        const id = buffer.readInt32LE(4);
        const type = buffer.readInt32LE(8);
        const body = buffer.subarray(12, size - 2).toString('utf8');
        buffer = buffer.subarray(size);
        if (id === -1) { socket.destroy(new Error('Authentication failed')); break; }
        if (id === 1 && type !== 2) continue;
        if (id > 1 && body) process.stdout.write(body + '\n');
        if (next < commands.length) send(next + 2, 2, commands[next++]);
        else socket.end();
    }
});
socket.setTimeout(10000, () => socket.destroy(new Error('RCON timed out')));
socket.on('error', error => { console.error(error.message); process.exitCode = 1; });
