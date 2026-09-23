package com.artworkguard.marketplace.aliexpress;
import org.springframework.stereotype.Component;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.util.*;
@Component
public class AliTransport {
 public static final String API="https://eco.taobao.com/router/rest";
 private static final Set<String> IMAGE_HOSTS=Set.of("ae01.alicdn.com","ae02.alicdn.com","ae03.alicdn.com","ae04.alicdn.com","ae05.alicdn.com","ae-pic-a1.aliexpress-media.com","ae-pic-a2.aliexpress-media.com");
 public record ImageBytes(byte[] bytes,String contentType){}
 public byte[] post(byte[] body){
  HttpsURLConnection connection=null;
  try{
   connection=open(URI.create(API));connection.setRequestMethod("POST");connection.setDoOutput(true);
   connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
   connection.setFixedLengthStreamingMode(body.length);
   try(var out=connection.getOutputStream()){out.write(body);}
   int status=connection.getResponseCode();
   if(status!=200)throw new AliException(org.springframework.http.HttpStatus.BAD_GATEWAY,"ALIEXPRESS_HTTP_ERROR");
   try(var stream=connection.getInputStream()){return bounded(stream,2097152);}
  }catch(IOException e){throw new AliException(org.springframework.http.HttpStatus.BAD_GATEWAY,"ALIEXPRESS_UNAVAILABLE");}
  finally{if(connection!=null)connection.disconnect();}
 }
 public ImageBytes image(String url){
  HttpsURLConnection connection=null;
  try{
   connection=open(imageUri(url));int status=connection.getResponseCode();if(status!=200)throw AliException.invalid();
   String type=Objects.toString(connection.getContentType(),"").split(";")[0].strip().toLowerCase(Locale.ROOT);
   if(!Set.of("image/png","image/jpeg").contains(type))throw AliException.invalid();
   try(var stream=connection.getInputStream()){return new ImageBytes(bounded(stream,20971520),type);}
  }catch(IOException e){throw new AliException(org.springframework.http.HttpStatus.BAD_GATEWAY,"ALIEXPRESS_IMAGE_UNAVAILABLE");}
  finally{if(connection!=null)connection.disconnect();}
 }
 public static URI imageUri(String url){
  try{
   var uri=URI.create(url);
   if(url.length()>2048||!"https".equals(uri.getScheme())||uri.getHost()==null||!IMAGE_HOSTS.contains(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getFragment()!=null)throw AliException.invalid();
   return uri;
  }catch(IllegalArgumentException e){throw AliException.invalid();}
 }
 private HttpsURLConnection open(URI uri)throws IOException{
  for(var address:InetAddress.getAllByName(uri.getHost())){
   byte[] bytes=address.getAddress();
   if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||(bytes.length==16&&(bytes[0]&0xfe)==0xfc))throw AliException.invalid();
  }
  var connection=(HttpsURLConnection)uri.toURL().openConnection(Proxy.NO_PROXY);
  connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(10000);
  connection.setRequestProperty("Accept-Encoding","identity");
  return connection;
 }
 static byte[] bounded(InputStream input,int max)throws IOException{
  var out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];long end=System.nanoTime()+java.time.Duration.ofSeconds(20).toNanos();
  int n;while((n=input.read(buffer))!=-1){
   if(out.size()+n>max||System.nanoTime()>end)throw AliException.invalid();out.write(buffer,0,n);
  }
  return out.toByteArray();
 }
}
