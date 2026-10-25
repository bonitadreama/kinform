import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {sessionConfig, VOICES} from '../src/policy.mjs';

const character = {id:'greg',name:'Greg',body:'Rock',personality:'Dry wit',boundaries:'',memoryMode:'OFF',memories:[]};
test('voice selection reaches Live while old clients retain Marin', () => {
  assert.equal(sessionConfig({type:'start',character}).audio.output.voice,'marin');
  for (const voice of VOICES) {
    const config=sessionConfig({type:'start',character:{...character,voice}});
    assert.equal(config.audio.output.voice,voice);
    assert.equal(config.model,'gpt-live-1');
  }
  for (const voice of ['ember','onyx','',null,{},'MARIN'])
    assert.throws(()=>sessionConfig({type:'start',character:{...character,voice}}),/invalid_voice/);
});
test('Android picker and backend allowlist stay identical', () => {
  const source=readFileSync(new URL('../../app/src/main/java/org/kinform/app/CharacterBook.java',import.meta.url),'utf8');
  const list=source.match(/VOICES = \{([^}]+)\}/)[1];
  assert.deepEqual([...list.matchAll(/"([a-z]+)"/g)].map(match=>match[1]),VOICES);
});
