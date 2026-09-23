export function safeUrl(value){try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password?u.href:null;}catch{return null;}}
export function createApi(fetcher=fetch,onExpired=()=>{}){
 let tokens=null,refreshing=null,epoch=0;
 function clear(){tokens=null;epoch++;}
 async function refresh(){
  if(!refreshing){const current=epoch,token=tokens?.refreshToken;refreshing=(async()=>{
   if(!token)throw Error('로그인이 필요합니다.');
   const r=await fetcher('/api/v1/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:token})});
   const data=await r.json();if(!r.ok||!data.success||current!==epoch)throw Error('다시 로그인해 주세요.');tokens=data.data;
  })().finally(()=>refreshing=null);}return refreshing;
 }
 async function request(path,{method='GET',body,blob=false,retry=true}={}){
  const current=epoch;
  const r=await fetcher('/api/v1'+path,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(tokens?{Authorization:'Bearer '+tokens.accessToken}:{})},...(body?{body:JSON.stringify(body)}:{})});
  if(r.status===401 && tokens && retry){try{await refresh();}catch{clear();onExpired();throw Error('세션이 만료되었습니다. 다시 로그인해 주세요.');}return request(path,{method,body,blob,retry:false});}
  if(current!==epoch)throw Error('세션이 변경되었습니다.');
  if(r.ok&&blob)return r.blob();
  const data=await r.json().catch(()=>null);
  if(!r.ok||!data?.success){if(r.status===401&&tokens){clear();onExpired();}const error=Error(({401:'이메일과 비밀번호를 확인해 주세요.',403:'이 작업의 권한이 없습니다.',409:'상태가 변경되었습니다. 새로고침 후 다시 시도해 주세요.',413:'파일 크기 제한을 초과했습니다.',429:'요청이 많습니다. 잠시 후 다시 시도해 주세요.',502:'백엔드에 연결할 수 없습니다.'})[r.status]||'요청을 완료하지 못했습니다.');error.code=data?.error?.code;throw error;}
  return data.data;
 }
 return {request,async login(body){clear();tokens=await request('/auth/login',{method:'POST',body});},async logout(){const token=tokens?.refreshToken;clear();if(token)await request('/auth/logout',{method:'POST',body:{refreshToken:token}});},clear};
}
