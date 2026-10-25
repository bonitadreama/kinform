// Explicit developer smoke test: no microphone, no user context, no credential output.
import {readFileSync} from 'node:fs';
import {parseEnv} from 'node:util';
import WebSocket from 'ws';
const {OPENAI_API_KEY}=parseEnv(readFileSync(new URL('../.env.local',import.meta.url),'utf8'));
const session=JSON.parse(readFileSync(new URL('../../build/live-tests/session-start.json',import.meta.url),'utf8').replace(/^\uFEFF/,''));
let started=false,closed=false;
const ws=new WebSocket('wss://api.openai.com/v1/live/sessions',{headers:{Authorization:`Bearer ${OPENAI_API_KEY}`},handshakeTimeout:15000,maxPayload:262144,followRedirects:false});
const timeout=setTimeout(()=>{console.log('Handshake did not finalize in time');ws.terminate();process.exitCode=1;},25000);
ws.on('open',()=>ws.send(JSON.stringify(session)));
ws.on('message',raw=>{
  const e=JSON.parse(raw);
  if(e.type==='session.started'){started=true;console.log('Android-generated session configuration accepted');ws.send(JSON.stringify({type:'session.close'}));}
  if(e.type==='session.closed'){closed=true;console.log('Provider confirmed session closed');clearTimeout(timeout);ws.close();}
  if(e.type==='error'){console.log('Provider rejected session configuration');ws.send(JSON.stringify({type:'session.close'}));process.exitCode=1;}
});
ws.on('error',()=>{console.log('Network or provider connection failed');process.exitCode=1;});
ws.on('close',()=>{clearTimeout(timeout);if(!started || !closed)process.exitCode=1;});
