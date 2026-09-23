import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';

// Local development only: fixed upstream; never proxy arbitrary URLs.
export function createServer(backend='http://127.0.0.1:8080') {
 const upstream=new URL(backend);
 if(upstream.protocol!=='http:' || upstream.hostname!=='127.0.0.1' || upstream.pathname!=='/' || upstream.search || upstream.username || upstream.password) throw Error('Use a loopback HTTP backend origin');
 const files={'/':'index.html','/app.js':'app.js','/api.js':'api.js','/style.css':'style.css'};
 return http.createServer(async(req,res)=>{
  res.setHeader('Cache-Control','no-store'); res.setHeader('X-Content-Type-Options','nosniff'); res.setHeader('Referrer-Policy','no-referrer');
  const origin=`http://${req.headers.host}`;
  if(!/^127\.0\.0\.1:\d+$/.test(req.headers.host||'') || (req.headers.origin && req.headers.origin!==origin)){res.writeHead(403).end();return;}
  const path=req.url||'/';
  if(path.startsWith('/api/v1/') && !path.includes('\\') && !path.includes('#')){
   const headers={}; for(const key of ['authorization','content-type','accept']) if(req.headers[key])headers[key]=req.headers[key];
   const proxy=http.request(upstream.origin+path,{method:req.method,headers,timeout:30000},r=>{
    res.writeHead(r.statusCode,{'Content-Type':r.headers['content-type']||'application/json'});r.pipe(res);
   });
   proxy.on('timeout',()=>proxy.destroy());proxy.on('error',()=>{if(!res.headersSent)res.writeHead(502,{'Content-Type':'application/json'});res.end('{"success":false,"error":{"code":"BACKEND_UNAVAILABLE"}}');});
   req.on('aborted',()=>proxy.destroy());req.pipe(proxy);return;
  }
  if(req.method!=='GET'||!files[path]){res.writeHead(404).end();return;}
  res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob: http: https:; connect-src 'self' http: https:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
  try{const body=await readFile(new URL(files[path],import.meta.url));res.setHeader('Content-Type',path.endsWith('.js')?'text/javascript':path.endsWith('.css')?'text/css':'text/html; charset=utf-8');res.end(body);}catch{res.writeHead(500).end();}
 });
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url))createServer(process.env.BACKEND_ORIGIN).listen(Number(process.env.PORT||5173),'127.0.0.1',()=>console.log('ArtworkGuard: http://127.0.0.1:'+ (process.env.PORT||5173)));
