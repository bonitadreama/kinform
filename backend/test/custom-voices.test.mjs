import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {Readable} from 'node:stream';
import {CustomVoices} from '../src/custom-voices.mjs';
import {hash,sessionConfig} from '../src/policy.mjs';
const token='x'.repeat(43),config={tokenHash:hash(token),apiKey:'test-secret'};
const input={name:'Greg',prompt:'An adult low-pitched warm gravelly voice.'};
async function call(service,method='POST',body=input,headers={authorization:'Bearer '+token}) {
  const req=Readable.from([Buffer.from(typeof body==='string'?body:JSON.stringify(body))]);req.method=method;req.headers=headers;
  let status,data;
  await service.handle(req,{writeHead(s){status=s;},end(raw){data=JSON.parse(raw);}});
  return {status,data};
}
test('custom voice creation is authenticated, bounded, persisted, deduplicated and allowlisted',async()=>{
  const dir=mkdtempSync(join(tmpdir(),'kinform-voices-')),file=join(dir,'voices.json');let calls=0;
  try {
    const service=new CustomVoices(config,{file,request:async(url,options)=>{
      calls++;assert.equal(url,'https://api.openai.com/v1/audio/voices');assert.equal(options.redirect,'error');
      assert.deepEqual(JSON.parse(options.body),{type:'prompt',model:'auto',...input});
      return {ok:true,json:async()=>({id:'voice_test123'})};
    }});
    assert.equal((await call(service,'GET',{},{})).status,401);
    assert.equal((await call(service,'POST',input,{authorization:'Bearer '+token,origin:'https://other.test'})).status,401);
    assert.equal((await call(service,'POST',{...input,model:'other'})).status,400);
    assert.equal((await call(service,'POST','x'.repeat(8193))).status,413);
    assert.equal(calls,0);
    const made=await call(service);assert.equal(made.status,201);assert.equal(made.data.id,'voice_test123');
    assert.equal((await call(service)).status,200);assert.equal(calls,1);
    const restarted=new CustomVoices(config,{file});assert.equal(restarted.has('voice_test123'),true);
    assert.equal((await call(restarted,'GET')).data.voices[0].prompt,input.prompt);
    const character={id:'greg',name:'Greg',body:'rock',personality:'Dry humor',boundaries:'',memoryMode:'OFF',memories:[],voice:'voice_test123'};
    assert.throws(()=>sessionConfig({type:'start',character}),/invalid_voice/);
    assert.deepEqual(sessionConfig({type:'start',character},id=>restarted.has(id)).audio.output.voice,{id:'voice_test123'});
    assert.throws(()=>sessionConfig({type:'start',character:{...character,voice:'voice_unknown'}},id=>restarted.has(id)),/invalid_voice/);
  }finally{rmSync(dir,{recursive:true,force:true});}
});
test('provider failures do not expose keys, grant voice access or silently retry',async()=>{
  const dir=mkdtempSync(join(tmpdir(),'kinform-voices-'));let calls=0;
  try {
    const service=new CustomVoices(config,{file:join(dir,'voices.json'),request:async()=>{calls++;throw Error('test-secret');}});
    assert.deepEqual(await call(service),{status:502,data:{error:'voice_creation_uncertain'}});
    assert.equal(calls,1);assert.equal(service.voices.length,0);assert.equal(service.busy,false);
    service.attempts=Array(10).fill(Date.now());assert.equal((await call(service)).status,429);assert.equal(calls,1);
  }finally{rmSync(dir,{recursive:true,force:true});}
});
