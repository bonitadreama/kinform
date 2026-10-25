import { loadConfig, root } from './config.mjs';
import { createBackend } from './relay.mjs';
try {
  const config=loadConfig();
  const backend=createBackend({...config,ledger:root+'private/usage.json'});
  backend.server.listen(config.port,config.bind,()=>console.log(`Kinform TLS backend listening on port ${config.port}. Private pairing file required.`));
  backend.server.on('error',()=>{console.error('Backend could not listen. Check port availability and configuration.');process.exitCode=1;});
  for(const signal of ['SIGINT','SIGTERM']) process.on(signal,()=>backend.close());
} catch { console.error('Backend setup is incomplete. Run npm run setup and confirm the private env file exists. No secrets were logged.'); process.exitCode=1; }
