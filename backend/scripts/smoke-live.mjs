// Explicit opt-in live handshake only: no microphone, personal memories or transcripts.
import {loadConfig} from '../src/config.mjs';
import {createBackend} from '../src/relay.mjs';
import {Admission} from '../src/policy.mjs';
import {readFileSync} from 'node:fs';
import {root} from '../src/config.mjs';
import {once} from 'node:events';
import WebSocket from 'ws';
if(!process.argv.includes('--confirm-live'))throw Error('Pass --confirm-live to allow a short billable provider connection.');
const config=loadConfig(), pairing=JSON.parse(readFileSync(root+'private/kinform-connection.json','utf8'));
const backend=createBackend(config,{admission:new Admission(null,{sessionSeconds:10})});
backend.server.listen(0,'127.0.0.1');await once(backend.server,'listening');
const ws=new WebSocket(`wss://127.0.0.1:${backend.server.address().port}/voice`,{ca:config.cert,headers:{Authorization:'Bearer '+pairing.token}});
let passed=false;
const timeout=setTimeout(()=>{ws.terminate();},25000);
ws.on('open',()=>ws.send(JSON.stringify({type:'start',character:{id:'handshake-test',name:'Greg',body:'Rock',personality:'Dry humor.',boundaries:'No external actions.',memoryMode:'OFF',memories:[]}})));
ws.on('message',(raw,binary)=>{
  if(binary)return;
  const e=JSON.parse(raw.toString());
  if(e.type==='ready'){passed=true;console.log('PASS: authenticated TLS relay received session.started from GPT-Live. No microphone audio sent.');ws.send('{"type":"stop"}');}
  if(e.type==='error')console.log('Live handshake failed with sanitized code: '+e.code);
});
ws.on('error',()=>console.log('Live handshake transport failed. No credentials logged.'));
await new Promise(resolve=>ws.on('close',resolve));clearTimeout(timeout);await backend.close();
process.exitCode=passed?0:1;
