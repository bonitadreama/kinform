import {existsSync, readFileSync, writeFileSync, renameSync} from 'node:fs';
import {authenticated} from './policy.mjs';

export class CustomVoices {
  constructor(config, {file = new URL('../private/custom-voices.json', import.meta.url), request = fetch} = {}) {
    this.config=config; this.file=file; this.request=request; this.busy=false; this.attempts=[];
    this.voices=existsSync(file)?JSON.parse(readFileSync(file,'utf8')):[];
    if(!Array.isArray(this.voices))throw Error('invalid_voice_registry');
  }
  has(id) {return typeof id==='string' && this.voices.some(v=>v.id===id);}
  async handle(req,res) {
    const reply=(status,value)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(value));};
    if(req.headers.origin || !authenticated(req.headers.authorization,this.config.tokenHash))return reply(401,{error:'unauthorized'});
    if(req.method==='GET')return reply(200,{voices:this.voices});
    if(req.method!=='POST')return reply(405,{error:'method_not_allowed'});
    if(this.busy)return reply(429,{error:'voice_creation_busy'});
    this.busy=true;
    let stage='read_request';const started=Date.now();
    try {
      const chunks=[];let size=0;
      for await(const chunk of req){size+=chunk.length;if(size>8192)return reply(413,{error:'description_too_large'});chunks.push(Buffer.from(chunk));}
      const body=Buffer.concat(chunks).toString('utf8');
      let input;try{input=JSON.parse(body);}catch{return reply(400,{error:'invalid_description'});}
      if(!input || Object.keys(input).some(k=>!['name','prompt'].includes(k)) || typeof input.name!=='string' || !input.name.trim() || input.name.length>80 || typeof input.prompt!=='string' || input.prompt.trim().length<20 || input.prompt.length>2000)return reply(400,{error:'invalid_description'});
      const name=input.name.trim(),prompt=input.prompt.trim();
      const existing=this.voices.find(v=>v.name===name && v.prompt===prompt);
      if(existing)return reply(200,existing);
      this.attempts=this.attempts.filter(t=>Date.now()-t<3600000);
      if(this.attempts.length>=10 || this.voices.length>=100)return reply(429,{error:'voice_creation_limit'});
      this.attempts.push(Date.now());
      stage='provider_request';
      const result=await this.request('https://api.openai.com/v1/audio/voices',{
        method:'POST',redirect:'error',signal:AbortSignal.timeout(75000),
        headers:{Authorization:`Bearer ${this.config.apiKey}`,'Content-Type':'application/json'},
        body:JSON.stringify({type:'prompt',model:'auto',name,prompt})
      });
      if(!result.ok){console.error(JSON.stringify({event:'voice_creation_failed',stage,status:result.status,elapsedMs:Date.now()-started}));await result.body?.cancel();return reply(502,{error:result.status===403 || result.status===401?'voice_access_denied':result.status===429?'provider_voice_limit':'voice_creation_rejected'});}
      stage='provider_response';
      const data=await result.json();
      if(typeof data.id!=='string' || !/^voice_[A-Za-z0-9_-]{1,122}$/.test(data.id))throw Error('invalid_provider_voice');
      const voice={id:data.id,name,prompt};
      const next=[...this.voices,voice];
      // File URLs need conversion for atomic same-directory replacement.
      const {fileURLToPath}=await import('node:url');
      const target=this.file instanceof URL?fileURLToPath(this.file):this.file;
      stage='local_save';
      writeFileSync(target+'.tmp',JSON.stringify(next),{mode:0o600});renameSync(target+'.tmp',target);
      this.voices=next;reply(201,voice);
    }catch(e) {
      // Never log exception messages, request bodies, descriptions, or credentials.
      const known=['ETIMEDOUT','ECONNRESET','ECONNREFUSED','ENOTFOUND','EACCES','EPERM','ENOSPC'];
      const code=known.includes(e?.cause?.code)?e.cause.code:known.includes(e?.code)?e.code:e?.name==='TimeoutError'?'TIMEOUT':'UNCLASSIFIED';
      console.error(JSON.stringify({event:'voice_creation_failed',stage,code,elapsedMs:Date.now()-started}));
      reply(502,{error:'voice_creation_uncertain'});
    }
    finally{this.busy=false;}
  }
}
