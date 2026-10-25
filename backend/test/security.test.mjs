import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {randomBytes} from 'node:crypto';
import {EventEmitter, once} from 'node:events';
import WebSocket from 'ws';
import {generate} from 'selfsigned';
import {authenticated,hash,sessionConfig,Admission} from '../src/policy.mjs';
import {createBackend} from '../src/relay.mjs';

const character={id:'greg-test',name:'Greg',body:'rock',personality:'Ancient, sarcastic, secretly affectionate.',boundaries:'No movement.',memoryMode:'ASK',memories:['Example user enjoys quiet mornings.']};
test('authentication rejects missing, malformed, and incorrect tokens',()=>{
  const token=randomBytes(32).toString('base64url');
  assert.equal(authenticated('Bearer '+token,hash(token)),true);
  for(const bad of [undefined,'','Basic '+token,'Bearer '+token+'x','Bearer '+randomBytes(32).toString('base64url')])assert.equal(authenticated(bad,hash(token)),false);
});
test('client cannot select provider, model, tools or inject arbitrary commands',()=>{
  for(const type of ['session.update','response.create','session.start'])assert.throws(()=>sessionConfig({type,character}));
  assert.throws(()=>sessionConfig({type:'start',character,model:'unbounded'}));
  assert.throws(()=>sessionConfig({type:'start',character:{...character,tools:[]}}));
  assert.throws(()=>sessionConfig({type:'start',character:{...character,name:'a'.repeat(100)}}));
  assert.throws(()=>sessionConfig({type:'start',character:{...character,memories:Array(31).fill('x')}}));
  const session=sessionConfig({type:'start',character});
  assert.equal(session.store,false);assert.equal(session.model,'gpt-live-1');assert.equal(session.delegation.type,'client');
});
test('memory off prevents saved facts reaching provider config',()=>{
  const session=sessionConfig({type:'start',character:{...character,memoryMode:'OFF'}});
  assert.equal(session.instructions.includes('Example user enjoys'),false);
  assert.equal(sessionConfig({type:'start',character}).instructions.includes('Example user enjoys'),true);
});
test('concurrency, rate and persisted conservative duration reservations',()=>{
  const dir=mkdtempSync(join(tmpdir(),'kinform-test-'));
  try{
    const file=join(dir,'usage.json'), now=Date.now();
    const gate=new Admission(file,{sessionSeconds:10,dailySeconds:50,startsPerHour:3});
    gate.reserve(now);assert.throws(()=>gate.reserve(now),/busy/);gate.release();
    const restarted=new Admission(file,{sessionSeconds:10,dailySeconds:50,startsPerHour:3});
    restarted.reserve(now+1);restarted.release();assert.throws(()=>restarted.reserve(now+2),/daily_limit/);
    const rate=new Admission(null,{sessionSeconds:10,dailySeconds:999,startsPerHour:1});
    rate.reserve(now);rate.release();assert.throws(()=>rate.reserve(now+1),/hourly_limit/);
  }finally{rmSync(dir,{recursive:true,force:true});}
});

test('TLS relay rejects unauthenticated connections and gates audio until provider ready',async()=>{
  const cert=await generate([{name:'commonName',value:'127.0.0.1'}],{keySize:2048,algorithm:'sha256',extensions:[{name:'basicConstraints',cA:true},{name:'subjectAltName',altNames:[{type:7,ip:'127.0.0.1'}]}]});
  const token=randomBytes(32).toString('base64url');let calls=0, upstream;
  class FakeProvider extends EventEmitter {
    constructor(){super();this.readyState=WebSocket.CONNECTING;this.bufferedAmount=0;this.sent=[];setImmediate(()=>{this.readyState=WebSocket.OPEN;this.emit('open');});}
    send(raw){const e=JSON.parse(raw);this.sent.push(e);if(e.type==='session.close')setImmediate(()=>this.emit('message',Buffer.from('{"type":"session.closed"}')));}
    close(){this.readyState=WebSocket.CLOSED;this.emit('close');}
    terminate(){this.close();}
  }
  const gate=new Admission(null);
  const app=createBackend({key:cert.private,cert:cert.cert,tokenHash:hash(token)}, {admission:gate,connectUpstream:()=>{calls++;if(calls===3)throw Error('connection unavailable');return upstream=new FakeProvider();}});
  app.server.listen(0,'127.0.0.1');await once(app.server,'listening');
  const url=`wss://127.0.0.1:${app.server.address().port}/voice`;
  try {
    const status=await new Promise(resolve=>{
      const bad=new WebSocket(url,{ca:cert.cert});bad.on('unexpected-response',(_r,res)=>{resolve(res.statusCode);res.resume();bad.terminate();});bad.on('error',()=>{});
    });
    assert.equal(status,401);assert.equal(calls,0);
    const early=new WebSocket(url,{ca:cert.cert,headers:{Authorization:'Bearer '+token}});await once(early,'open');
    const error=once(early,'message');early.send(Buffer.alloc(960));assert.equal(JSON.parse((await error)[0]).code,'audio_before_ready');await once(early,'close');assert.equal(calls,0);
    const valid=new WebSocket(url,{ca:cert.cert,headers:{Authorization:'Bearer '+token}});await once(valid,'open');
    valid.send(JSON.stringify({type:'start',memoryProtocol:1,character}));
    while(!upstream?.sent.length)await new Promise(r=>setTimeout(r,5));
    const ready=once(valid,'message');upstream.emit('message',Buffer.from('{"type":"session.started"}'));assert.equal(JSON.parse((await ready)[0]).type,'ready');
    valid.send(Buffer.alloc(960));
    while(!upstream.sent.some(e=>e.type==='session.input_audio.append'))await new Promise(r=>setTimeout(r,5));
    const output=once(valid,'message');upstream.emit('message',Buffer.from(JSON.stringify({type:'session.output_audio.delta',delta:Buffer.alloc(960).toString('base64')})));
    const [audio,binary]=await output;assert.equal(binary,true);assert.equal(audio.length,960);
    upstream.emit('message',Buffer.from(JSON.stringify({type:'session.input_transcript.delta',event_id:'t1',delta:'Remember this: I like moon rocks.'})));
    upstream.emit('message',Buffer.from(JSON.stringify({type:'session.delegation.created',delegation:{id:'d1',target:'client'}})));
    assert(upstream.sent.some(e=>e.type==='session.commentary.append' && e.content.includes('NOT saved')));
    upstream.emit('message',Buffer.from(JSON.stringify({type:'session.input_transcript.delta',event_id:'t2',delta:'Save that memory.'})));
    const proposed=once(valid,'message');
    upstream.emit('message',Buffer.from(JSON.stringify({type:'session.delegation.created',delegation:{id:'d2',target:'client'}})));
    const request=JSON.parse((await proposed)[0]);assert.equal(request.type,'memory_save');assert.equal(request.characterId,character.id);assert.equal(request.text,'I like moon rocks.');
    assert(!upstream.sent.some(e=>e.content?.includes('saved successfully')));
    valid.send(JSON.stringify({type:'memory_result',id:request.id,status:'saved'}));
    while(!upstream.sent.some(e=>e.content?.includes('saved successfully')))await new Promise(r=>setTimeout(r,5));
    const closed=once(valid,'close');valid.send('{"type":"session.update"}');await closed;
    assert(upstream.sent.some(e=>e.type==='session.close'));
    while(gate.active)await new Promise(r=>setTimeout(r,5));
    assert(Object.values(gate.state.days)[0]<315,'confirmed short call releases unused allowance');
    const failed=new WebSocket(url,{ca:cert.cert,headers:{Authorization:'Bearer '+token}});await once(failed,'open');
    const prior=Object.values(gate.state.days)[0];
    failed.send(JSON.stringify({type:'start',memoryProtocol:1,character}));
    while(calls<2 || !upstream.sent.length)await new Promise(r=>setTimeout(r,5));
    const disconnected=once(failed,'close');upstream.close();await disconnected;
    assert.equal(Object.values(gate.state.days)[0],prior+315,'unconfirmed provider disconnect retains reservation');
    const unavailable=new WebSocket(url,{ca:cert.cert,headers:{Authorization:'Bearer '+token}});await once(unavailable,'open');
    const unavailableClosed=once(unavailable,'close');
    unavailable.send(JSON.stringify({type:'start',memoryProtocol:1,character}));await unavailableClosed;
    assert.equal(Object.values(gate.state.days)[0],prior+315,'failure before sending session start refunds all reserved time');
  }finally{await app.close();}
});

test('settlement persists actual duration, refunds pre-start failure, and preserves rate limit',()=>{
  const dir=mkdtempSync(join(tmpdir(),'kinform-settlement-'));
  try{
    const file=join(dir,'usage.json'),now=Date.UTC(2026,9,16,23,59,59),day='2026-10-16';
    const gate=new Admission(file,{startsPerHour:3});
    gate.reserve(now);gate.release({usedSeconds:12.2});
    assert.equal(gate.state.days[day],13);
    gate.release({usedSeconds:0});assert.equal(gate.state.days[day],13);
    const next=new Admission(file,{startsPerHour:3});
    next.reserve(now);next.release({usedSeconds:0});assert.equal(next.state.days[day],13);
    next.reserve(now+2000);next.release({usedSeconds:2});
    assert.equal(next.state.days['2026-10-17'],2);
    assert.throws(()=>next.reserve(now+3000),/hourly_limit/);
  }finally{rmSync(dir,{recursive:true,force:true});}
});

test('remaining allowance supports a shorter final call and invalid settlement fails closed',()=>{
  const gate=new Admission(null,{sessionSeconds:300,dailySeconds:50});
  gate.reserve();assert.equal(gate.allowedSeconds,35);
  gate.release({usedSeconds:NaN});assert.equal(Object.values(gate.state.days)[0],50);
  assert.throws(()=>gate.reserve(),/daily_limit/);
});
