import test from 'node:test';
import assert from 'node:assert/strict';
import {sessionConfig} from '../src/policy.mjs';
const character={id:'greg',name:'Greg',body:'Rock',personality:'Dry humor',boundaries:'No movement',memoryMode:'ASK',memories:['A private fact.']};
test('legacy combined mode migrates to Live only and memory protocol is opt-in',()=>{
  for(const mode of [undefined,'LIVE','LIVE_56']) {
    const c={...character};if(mode)c.conversationMode=mode;
    const session=sessionConfig({type:'start',character:c});
    assert.equal(session.model,'gpt-live-1');assert.deepEqual(session.delegation,{type:'client'});
    assert(!JSON.stringify(session).includes('gpt-5.6'));
    assert(session.instructions.includes('Memory writing is unavailable'));
  }
  assert(sessionConfig({type:'start',memoryProtocol:1,character}).instructions.includes('Memory protocol:'));
  const off=sessionConfig({type:'start',memoryProtocol:1,character:{...character,memoryMode:'OFF'}});
  assert(!JSON.stringify(off).includes('A private fact.'));assert(off.instructions.includes('Memory writing is unavailable'));
  assert.throws(()=>sessionConfig({type:'start',character,memoryProtocol:2}));
});
