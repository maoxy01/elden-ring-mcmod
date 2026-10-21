import fs from 'node:fs/promises';
const endpoint='http://127.0.0.1:3000/bb-mcp';
let session,id=0;
async function rpc(method,params={},notification=false){
 const body={jsonrpc:'2.0',method,params};if(!notification)body.id=++id;
 const r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json, text/event-stream',...(session?{'Mcp-Session-Id':session,'MCP-Protocol-Version':'2025-03-26'}:{})},body:JSON.stringify(body),signal:AbortSignal.timeout(90000)});
 session=r.headers.get('mcp-session-id')||session;const raw=await r.text();if(!r.ok)throw Error('HTTP '+r.status+' '+raw.slice(0,500));if(notification||!raw.trim())return;
 const messages=r.headers.get('content-type')?.includes('text/event-stream')?raw.split(/\r?\n\r?\n/).flatMap(e=>{const data=e.split(/\r?\n/).filter(l=>l.startsWith('data:')).map(l=>l.slice(5).trimStart()).join('\n');return data?[JSON.parse(data)]:[];}):[JSON.parse(raw)];
 const m=messages.find(m=>m.id===body.id);if(!m)throw Error('Missing RPC result');if(m.error)throw Error(JSON.stringify(m.error));return m.result;
}
try{
 await rpc('initialize',{protocolVersion:'2025-03-26',capabilities:{},clientInfo:{name:'codex-native-art-client',version:'1.0.0'}});await rpc('notifications/initialized',{},true);
 let result;
 if(process.argv[2]==='list'){result=await rpc('tools/list');if(process.argv[3])await fs.writeFile(process.argv[3],JSON.stringify(result,null,2));console.log(JSON.stringify(result.tools.map(t=>({name:t.name,description:t.description?.slice(0,100),schema:t.inputSchema})),null,2));}
 else{const evaluating=process.argv[2]==='eval';const request=evaluating?{name:'risky_eval',arguments:{code:await fs.readFile(process.argv[3],'utf8')}}:JSON.parse(await fs.readFile(process.argv[2]));const output=process.argv[evaluating?4:3],imageOutput=process.argv[evaluating?5:4];result=await rpc('tools/call',request);if(output)await fs.writeFile(output,JSON.stringify(result,null,2));for(const c of result.content||[]){if(c.type==='text')console.log(c.text);else if(c.type==='image'&&imageOutput){await fs.writeFile(imageOutput,Buffer.from(c.data,'base64'));console.log('Native image saved: '+imageOutput);}}if(result.isError)process.exitCode=1;}
}finally{if(session)await fetch(endpoint,{method:'DELETE',headers:{'Mcp-Session-Id':session},signal:AbortSignal.timeout(5000)}).catch(()=>{});}
