import https from 'node:https';
import {CustomVoices} from './custom-voices.mjs';
import {MemoryCommands} from './memory.mjs';
import WebSocket, { WebSocketServer } from 'ws';
import { authenticated, sessionConfig, Admission } from './policy.mjs';

export function createBackend(config, {connectUpstream, admission, customVoices} = {}) {
  const voices=customVoices || new CustomVoices(config);
  const gate = admission || new Admission(config.ledger, config);
  const server = https.createServer({key:config.key, cert:config.cert, minVersion:'TLSv1.2'}, (req,res) => {
    if(req.url==='/voices') {void voices.handle(req,res);return;}
    res.writeHead(req.url === '/health' ? 200 : 404, {'Content-Type':'application/json','Cache-Control':'no-store'});
    res.end(req.url === '/health' ? '{"service":"kinform","version":2}' : '{}');
  });
  server.headersTimeout=10000; server.requestTimeout=10000;
  const wss = new WebSocketServer({noServer:true, maxPayload:16384, perMessageDeflate:false});
  const attempts = new Map();
  const rejection = (socket,status) => socket.end(`HTTP/1.1 ${status}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
  server.on('upgrade',(req,socket,head) => {
    const ip = req.socket.remoteAddress, now=Date.now();
    for (const [k,v] of attempts) if (now-v.time>60000) attempts.delete(k);
    if (!attempts.has(ip) && attempts.size>=1024) return rejection(socket,'429 Too Many Requests');
    const count=attempts.get(ip)||{time:now,n:0}; count.n++; attempts.set(ip,count);
    if (count.n>20) return rejection(socket,'429 Too Many Requests');
    if (req.url!=='/voice' || req.headers.origin || !authenticated(req.headers.authorization, config.tokenHash)) return rejection(socket,'401 Unauthorized');
    if (wss.clients.size>=2) return rejection(socket,'429 Too Many Requests');
    wss.handleUpgrade(req,socket,head,client=>wss.emit('connection',client));
  });
  wss.on('connection', client => {
    let upstream, reserved=false, started=false, closing=false, released=false, lastAudio=Date.now(), bytes=0, byteWindow=Date.now();
    let startTimer, durationTimer, closeTimer, memoryTimer, memoryCommands, memoryDelegation;
    let sessionRequested=false,confirmedClosed=false,startedAt=0;
    const send = data => { if(client.readyState===WebSocket.OPEN) client.send(JSON.stringify(data)); };
    const release = () => { if(reserved && !released) {
      released=true;
      try{gate.release(confirmedClosed || !sessionRequested ? {usedSeconds:startedAt?Math.ceil((Date.now()-startedAt)/1000):0}:undefined);}
      catch{console.error('Usage settlement could not be persisted; reservation retained.');gate.release();}
    } };
    const finalize = () => {clearTimeout(startTimer); clearTimeout(durationTimer); clearTimeout(closeTimer); clearInterval(heartbeat); release();};
    const finish = (code='ended') => {
      if(closing) return; closing=true; clearTimeout(startTimer); clearTimeout(durationTimer); clearInterval(heartbeat);
      clearTimeout(memoryTimer);memoryCommands?.clear();
      send({type:'ended', code});
      if(upstream?.readyState===WebSocket.OPEN) {
        upstream.send(JSON.stringify({type:'session.close'}));
        closeTimer=setTimeout(()=>{upstream.terminate(); finalize();},15000);
      } else { upstream?.terminate(); finalize(); }
      if(client.readyState===WebSocket.OPEN) client.close(1000,'Session ended');
    };
    const fail = code => {send({type:'error',code}); finish(code);};
    startTimer=setTimeout(()=>fail('start_timeout'),10000);
    const heartbeat=setInterval(()=>{
      if(started && Date.now()-lastAudio>15000) finish('audio_timeout');
      if(client.readyState===WebSocket.OPEN) client.ping();
    },5000);
    client.on('error',()=>finish('connection_lost'));
    client.on('close',()=>finish('connection_lost'));
    client.on('message',(data,isBinary)=>{
      if(closing) return;
      if(isBinary) {
        if(!started) return fail('audio_before_ready');
        if(!data.length || data.length>4800 || data.length%2) return fail('invalid_audio');
        const now=Date.now(); if(now-byteWindow>=1000){bytes=0; byteWindow=now;}
        bytes+=data.length; if(bytes>96000) return fail('audio_rate_limit');
        lastAudio=now;
        if(upstream.bufferedAmount>96000) return fail('network_too_slow');
        upstream.send(JSON.stringify({type:'session.input_audio.append', audio:data.toString('base64')}));
        return;
      }
      try {
        const message=JSON.parse(data.toString());
        if(message.type==='stop') return finish();
        if(message.type==='memory_result') {
          if(!started || !memoryCommands || Object.keys(message).some(k=>!['type','id','status'].includes(k)))return fail('unsupported_command');
          const result=memoryCommands.acknowledge(message);
          if(!result)return fail('invalid_memory_result');
          clearTimeout(memoryTimer);
          upstream.send(JSON.stringify({type:'session.commentary.append',delegation_id:memoryDelegation,content:result}));
          return;
        }
        if(upstream || reserved) return fail('unsupported_command');
        const session=sessionConfig(message,id=>voices.has(id));
        memoryCommands=new MemoryCommands(message.memoryProtocol===1 && message.character.memoryMode!=='OFF',message.character.id);
        gate.reserve(); reserved=true;
        upstream=connectUpstream ? connectUpstream() : new WebSocket('wss://api.openai.com/v1/live/sessions',{
          headers:{Authorization:`Bearer ${config.apiKey}`, 'OpenAI-Safety-Identifier':config.tokenHash},
          handshakeTimeout:15000, maxPayload:2*1024*1024, perMessageDeflate:false, followRedirects:false
        });
        clearTimeout(startTimer); startTimer=setTimeout(()=>fail('provider_timeout'),20000);
        // Count startup toward the hard duration cap, not just time after ready.
        durationTimer=setTimeout(()=>finish('session_limit'),gate.allowedSeconds*1000);
        upstream.on('open',()=> { if(closing) upstream.terminate(); else {sessionRequested=true;upstream.send(JSON.stringify({type:'session.start',session}));} });
        upstream.on('unexpected-response',(_req,res)=>{res.resume(); fail(res.statusCode===401?'provider_auth_failed':res.statusCode===429?'provider_quota':'provider_unavailable');});
        upstream.on('error',error=>{
          const allowed=['EPERM','EACCES','ENOTFOUND','ECONNREFUSED','ECONNRESET','ETIMEDOUT','CERT_HAS_EXPIRED','UNABLE_TO_VERIFY_LEAF_SIGNATURE'];
          console.error(JSON.stringify({event:'provider_connection_failed',code:allowed.includes(error.code)?error.code:'UNCLASSIFIED'}));
          fail('provider_connection_failed');
        });
        upstream.on('close',()=>{if(!closing) finish('provider_closed'); finalize();});
        upstream.on('message', raw => {
          try {
            const event=JSON.parse(raw.toString());
            if(event.type==='session.closed') { confirmedClosed=true;upstream.close(); if(!closing) finish(); finalize(); return; }
            if(closing) return;
            if(event.type==='session.started' && !started) {started=true;startedAt=Date.now(); lastAudio=Date.now(); clearTimeout(startTimer); send({type:'ready',sampleRate:24000, maxSeconds:gate.allowedSeconds});}
            else if(event.type==='session.output_audio.delta' && started) {
              if(typeof event.delta!=='string') return fail('invalid_provider_audio');
              const audio=Buffer.from(event.delta,'base64');
              if(audio.length>96000 || audio.length%2 || client.bufferedAmount>96000) return fail('playback_overflow');
              if(client.readyState===WebSocket.OPEN) client.send(audio,{binary:true});
            } else if(event.type==='session.input_transcript.delta') {
              memoryCommands.transcript(event);
            } else if(event.type==='session.delegation.created') {
              const result=memoryCommands.delegate();
              if(result.save) {
                memoryDelegation=event.delegation.id;send(result.save);
                memoryTimer=setTimeout(()=>{
                  memoryCommands.clear();
                  if(!closing && upstream.readyState===WebSocket.OPEN)upstream.send(JSON.stringify({type:'session.commentary.append',delegation_id:memoryDelegation,content:'The phone did not confirm before the timeout. Saving is uncertain; ask the user to check this character\'s memory list before retrying. Do not claim success.'}));
                },15000);
              } else upstream.send(JSON.stringify({type:'session.commentary.append',delegation_id:event.delegation.id,content:result.say}));
            } else if(event.type==='error') fail('provider_rejected_session');
            // No transcript/audio logging. Only a bounded ephemeral input buffer is used for explicit memory commands.
          } catch { fail('invalid_provider_message'); }
        });
      } catch(e) {
        const publicCodes=['invalid_start','invalid_character','invalid_memory','character_too_large','busy','hourly_limit','daily_limit'];
        fail(publicCodes.includes(e.message)?e.message:'invalid_request');
      }
    });
  });
  return {server,wss,close:async()=>{
    for(const c of wss.clients)c.close(1001,'Server stopping');
    await new Promise(resolve=>server.close(resolve));
  }};
}
