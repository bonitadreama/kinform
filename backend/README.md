# Optional legacy LAN backend

Phone-only mode does not need this server. This relay is retained for advanced local experiments; newer phone-only suggestion/history flows are not implemented here. Do not expose it to the public internet.

Requires Node.js 22+, a private trusted LAN, and your own eligible OpenAI API key. From this folder run `npm ci` and `npm test` first. Tests mock the provider.

Run `npm run setup -- YOUR_LAN_IPV4` with your actual LAN IPv4 address. Setup creates a TLS key/certificate, hashed-token server configuration, and a sensitive `private/kinform-connection.json` pairing file. Transfer that file privately to your phone and import it using the private-backend connection option. Do not upload it or the private folder.

Create `.env.local` yourself with `OPENAI_API_KEY=` followed by your key, then run `npm start`. The server reads that file explicitly. Keep it excluded from Git. Restrict firewall access to your private LAN. Reconfiguration with `--rotate` revokes the old pairing token; restart the backend afterward. Local limits are not provider billing caps.

`npm run smoke:live` makes a real provider request with configured credentials and is not part of routine offline checks. No credential or working pairing file is included in this repository.
