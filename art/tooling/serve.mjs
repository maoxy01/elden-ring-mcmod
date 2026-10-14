import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../margit');
const port = Number(process.env.MODEL_PREVIEW_PORT || 4176);
const server = http.createServer(async (request, response) => {
  try {
    const filename = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const target = path.resolve(root, '.' + (filename === '/' ? '/preview.html' : filename));
    if (target !== root && !target.startsWith(root + path.sep)) { response.writeHead(403); response.end(); return; }
    const data = await fs.readFile(target);
    const type = { '.html': 'text/html; charset=utf-8', '.png': 'image/png', '.json': 'application/json' }[path.extname(target)] || 'application/octet-stream';
    response.writeHead(200, { 'Content-Type': type }); response.end(data);
  } catch { response.writeHead(404); response.end(); }
});
server.listen(port, '127.0.0.1', () => console.log(`Model preview: http://127.0.0.1:${port}`));
