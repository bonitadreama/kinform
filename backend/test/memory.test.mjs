import test from 'node:test';
import assert from 'node:assert/strict';
import {MemoryCommands} from '../src/memory.mjs';
const feed=(m,text,id=Math.random().toString())=>m.transcript({event_id:id,delta:text});
test('fragmented explicit request requires readback and separate confirmation',()=>{
  const m=new MemoryCommands(true,'greg');
  feed(m,'Remember ','1');feed(m,'this: I like ','2');feed(m,'this: I like ','2');feed(m,'purple rocks.','3');
  const proposal=m.delegate();assert(proposal.say.includes('I like purple rocks.'));assert(!proposal.save);
  feed(m,'Save that memory.');const result=m.delegate();assert.equal(result.save.characterId,'greg');assert.equal(result.save.text,'I like purple rocks.');
  assert.equal(m.acknowledge({id:'wrong',status:'saved'}),null);
  assert(m.acknowledge({id:result.save.id,status:'saved'}).includes('saved successfully'));
  assert.equal(m.acknowledge({id:result.save.id,status:'saved'}),null);
});
test('off, negation, cancellation, expiry, failure, and session isolation do not save',()=>{
  for(const phrase of ['Do not remember this: purple.','He said remember this: purple.','Save that memory.','I like purple.']){
    const m=new MemoryCommands(true,'greg');feed(m,phrase);assert(!m.delegate().save);
  }
  const off=new MemoryCommands(false,'greg');feed(off,'Remember this: purple.');assert(!off.delegate().save);assert.equal(off.buffer,'');
  const m=new MemoryCommands(true,'greg');feed(m,'Remember this: purple.');m.delegate(100);
  feed(m,'Save that memory.');assert(!m.delegate(120101).save);
  feed(m,'Remember this: purple.');m.delegate();feed(m,'Cancel that memory.');m.delegate();feed(m,'Save that memory.');assert(!m.delegate().save);
  feed(m,'Remember this: purple.');m.delegate();feed(m,'Save that memory.');const req=m.delegate().save;
  assert(m.acknowledge({id:req.id,status:'failed'}).includes('did not save'));
  const other=new MemoryCommands(true,'patchy');feed(other,'Save that memory.');assert(!other.delegate().save);
});
test('oversized facts and ambiguous confirmation fail closed',()=>{
  const m=new MemoryCommands(true,'greg');feed(m,'Remember this: '+'x'.repeat(1001));assert(!m.delegate().save);
  feed(m,'Remember this: purple.');m.delegate();feed(m,'Save that memory, actually no.');assert(!m.delegate().save);
});
