import { generate } from 'selfsigned';
import { randomBytes } from 'node:crypto';
import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { isIP } from 'node:net';
import { root } from '../src/config.mjs';
import { hash } from '../src/policy.mjs';
const host=process.argv[2];
if(!host || !isIP(host)) throw Error('Supply this PC’s LAN IPv4 address: npm run setup -- 192.168.x.x');
mkdirSync(root+'private',{recursive:true,mode:0o700});
if(existsSync(root+'private/server.json') && !process.argv.includes('--rotate')) throw Error('Already configured. Use --rotate to revoke the old phone connection and create a new one.');
const cert=await generate([{name:'commonName',value:host}],{keySize:2048,algorithm:'sha256',days:365,
  extensions:[{name:'basicConstraints',cA:true},{name:'keyUsage',digitalSignature:true,keyEncipherment:true,keyCertSign:true},
    {name:'extKeyUsage',serverAuth:true},{name:'subjectAltName',altNames:[{type:7,ip:host},{type:7,ip:'127.0.0.1'}]}]});
const token=randomBytes(32).toString('base64url');
for(const [name,value] of Object.entries({
  'tls-key.pem':cert.private,'tls-cert.pem':cert.cert,
  'server.json':JSON.stringify({bind:'0.0.0.0',port:8443,tokenHash:hash(token),sessionSeconds:300,dailySeconds:1800,startsPerHour:8},null,2),
  'kinform-connection.json':JSON.stringify({version:1,url:`wss://${host}:8443/voice`,token,certificate:cert.cert},null,2)
})) writeFileSync(root+'private/'+name,value,{mode:0o600});
console.log('Created private/kinform-connection.json. Transfer privately to your phone; never commit or publish it. Restart the backend after rotating.');
