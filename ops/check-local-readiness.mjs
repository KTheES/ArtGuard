import {execFile} from 'node:child_process';
import net from 'node:net';
import {promisify} from 'node:util';
const exec=promisify(execFile);
// Read-only readiness probes. Open ports alone never prove integration success.
async function docker(){try{const {stdout}=await exec('docker',['version','--format','{{.Server.Version}}'],{timeout:10000,windowsHide:true});return {name:'docker',ready:!!stdout.trim()};}catch{return {name:'docker',ready:false,reason:'ENGINE_UNAVAILABLE'};}}
async function tcp(name,port){return new Promise(resolve=>{const socket=net.createConnection({host:'127.0.0.1',port});const finish=ready=>{socket.destroy();resolve({name,port,ready,check:'TCP_ONLY'});};socket.setTimeout(1500);socket.once('connect',()=>finish(true));socket.once('error',()=>finish(false));socket.once('timeout',()=>finish(false));});}
async function health(name,port,path){try{const response=await fetch(`http://127.0.0.1:${port}${path}`,{signal:AbortSignal.timeout(3000),redirect:'error'});const body=await response.json();return {name,port,ready:response.ok&&body.status==='UP',check:'HTTP_HEALTH'};}catch{return {name,port,ready:false,check:'HTTP_HEALTH'};}}
const checks=await Promise.all([docker(),tcp('postgres',5432),tcp('redis',6379),tcp('kafka',9092),tcp('storage',9000),health('backend',8080,'/actuator/health'),health('ai',8001,'/health/ready')]);
const ready=checks.every(c=>c.ready);
console.log(JSON.stringify({checkedAt:new Date().toISOString(),status:ready?'PREREQUISITES_REACHABLE':'BLOCKED',integrationPassed:false,checks},null,2));
process.exitCode=ready?0:2;
