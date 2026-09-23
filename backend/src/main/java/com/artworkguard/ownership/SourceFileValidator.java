package com.artworkguard.ownership;
import com.artworkguard.artwork.service.ArtworkException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.nio.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
@Component
public class SourceFileValidator {
 public enum Format { PSD,PROCREATE }
 public static final int MAX_BYTES=20*1024*1024;
 public String validate(Format format,byte[] data){
  if(format==null || data.length==0 || data.length>MAX_BYTES)throw invalid();
  if(format==Format.PSD){psd(data);return "PSD_HEADER_AND_SECTION_BOUNDS_ONLY";}
  archive(data);return "ZIP_CONTAINER_ONLY_NOT_PROCREATE_AUTHENTICATED";
 }
 private void psd(byte[] data){
  if(data.length<40)throw invalid();var b=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
  if(b.getInt()!=0x38425053 || b.getShort()!=1)throw invalid();
  for(int i=0;i<6;i++)if(b.get()!=0)throw invalid();
  int channels=Short.toUnsignedInt(b.getShort()),height=b.getInt(),width=b.getInt(),depth=Short.toUnsignedInt(b.getShort()),mode=Short.toUnsignedInt(b.getShort());
  if(channels<1 || channels>56 || width<1 || height<1 || width>30000 || height>30000 || !Set.of(1,8,16,32).contains(depth) || !Set.of(0,1,2,3,4,7,8,9).contains(mode))throw invalid();
  for(int i=0;i<3;i++){if(b.remaining()<4)throw invalid();long length=Integer.toUnsignedLong(b.getInt());if(length>b.remaining())throw invalid();b.position(b.position()+(int)length);}
  if(b.remaining()<3 || Short.toUnsignedInt(b.getShort())>3)throw invalid();
 }
 private void archive(byte[] data){
  Path temp=null;
  try{
   temp=Files.createTempFile("artworkguard-source-",".zip");Files.write(temp,data);
   try(var zip=new ZipFile(temp.toFile())){
    if(zip.size()==0 || zip.size()>2000)throw invalid();long expanded=0;var names=new HashSet<String>();var entries=zip.entries();byte[] buffer=new byte[8192];int files=0;
    while(entries.hasMoreElements()){
     var entry=entries.nextElement();String name=entry.getName();
     if(name.startsWith("/") || name.contains("\\") || name.contains(":") || name.indexOf('\0')>=0 || Arrays.asList(name.split("/")).contains("..") || !names.add(name))throw invalid();
     if(entry.isDirectory())continue;files++;
     if(entry.getSize()<0 || entry.getSize()>64L*1024*1024 || entry.getCrc()<0)throw invalid();
     long read=0;var crc=new CRC32();
     try(var stream=zip.getInputStream(entry)){int n;while((n=stream.read(buffer))!=-1){read+=n;expanded+=n;if(expanded>64L*1024*1024)throw invalid();crc.update(buffer,0,n);}}
     if(read!=entry.getSize() || crc.getValue()!=entry.getCrc())throw invalid();
    }
    if(files==0)throw invalid();
   }
  }catch(IOException e){throw invalid();}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
 }
 private static ArtworkException invalid(){return new ArtworkException(HttpStatus.BAD_REQUEST,"SOURCE_FILE_INVALID","파일 크기와 선언한 형식의 구조를 확인해 주세요.");}
}
