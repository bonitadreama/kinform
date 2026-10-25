// Paid synthetic-audio protocol check, without phone writes or personal data.
import WebSocket from 'ws';
import {readFileSync} from 'node:fs';
import {loadConfig} from '../src/config.mjs';
import {sessionConfig} from '../src/policy.mjs';
import {MemoryCommands} from '../src/memory.mjs';
if(!process.argv.includes('--confirm-live'))throw Error('Explicit --confirm-live required');
function pcm(name){const b=readFileSync(new URL('../../build/memory-test/'+name+'.wav',import.meta.url));for(let i=12;i+8<=b.length;){let n=b.readUInt32LE(i+4);if(b.toString('ascii',i,i+4)==='data')return b.subarray(i+8,i+8+n);i+=8+n+(n%2);}throw Error('WAV data missing');}
const request=pcm('request'),confirm=pcm('confirm'),config=loadConfig();
const character={id:'synthetic',name:'Greg',body:'Rock',personality:'Dry wit.',boundaries:'No external actions.',memoryMode:'ASK',memories:[]};
const memory=new MemoryCommands(true,character.id);
const ws=new WebSocket('wss://api.openai.com/v1/live/sessions',{headers:{Authorization:'Bearer '+config.apiKey},handshakeTimeout:10000});
let interval,stream=request,position=0,startedAt=0,proposedAt=0,confirmed=false,passed=false,closing=false;
const send=e=>ws.send(JSON.stringify(e));
function close(){if(closing)return;closing=true;clearInterval(interval);if(ws.readyState===WebSocket.OPEN)send({type:'session.close'});else ws.terminate();}
const timeout=setTimeout(close,40000),hard=setTimeout(()=>ws.terminate(),50000);
ws.on('open',()=>send({type:'session.start',session:sessionConfig({type:'start',memoryProtocol:1,character})}));
ws.on('message',raw=>{
 const e=JSON.parse(raw.toString());
 if(e.type==='session.started'){
   startedAt=Date.now();interval=setInterval(()=>{
     if(proposedAt && !confirmed && Date.now()-proposedAt>10000){stream=confirm;position=0;confirmed=true;}
     let frame=Buffer.alloc(960);
     if(Date.now()-startedAt>1500 && position<stream.length){stream.copy(frame,0,position,position+960);position+=960;}
     if(ws.readyState===WebSocket.OPEN)send({type:'session.input_audio.append',audio:frame.toString('base64')});
   },20);
 }
 if(e.type==='session.input_transcript.delta')memory.transcript(e);
 if(e.type==='session.delegation.created'){
   const result=memory.delegate();
   if(result.save){passed=confirmed && /purple/i.test(result.save.text);console.log('Confirmed memory write request received: '+passed+' (no phone write performed)');close();}
   else{if(memory.pending){proposedAt=Date.now();console.log('Memory proposal created from synthetic speech.');}
     send({type:'session.commentary.append',delegation_id:e.delegation.id,content:result.say});}
 }
 if(e.type==='error'){console.log('Provider error; test stopped.');close();}
 if(e.type==='session.closed')ws.close();
});
ws.on('error',()=>console.log('Transport failed; no credentials logged.'));
await new Promise(r=>ws.on('close',r));clearInterval(interval);clearTimeout(timeout);clearTimeout(hard);
console.log(passed?'PASS: spoken request, proposal, and separate spoken confirmation.':'FAIL: voice memory protocol not completed within test window.');
process.exitCode=passed?0:1;
