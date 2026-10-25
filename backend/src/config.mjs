import { readFileSync } from 'node:fs';
import { parseEnv } from 'node:util';
import { fileURLToPath } from 'node:url';
export const root = fileURLToPath(new URL('../',import.meta.url));
export function loadConfig() {
  // Explicitly use the approved Kinform file, not an inherited unrelated OPENAI_API_KEY.
  const env = parseEnv(readFileSync(root+'.env.local','utf8'));
  if (!env.OPENAI_API_KEY?.startsWith('sk-')) throw Error('Kinform backend key is missing');
  const config = JSON.parse(readFileSync(root+'private/server.json','utf8'));
  return {...config, apiKey:env.OPENAI_API_KEY, key:readFileSync(root+'private/tls-key.pem'), cert:readFileSync(root+'private/tls-cert.pem')};
}
