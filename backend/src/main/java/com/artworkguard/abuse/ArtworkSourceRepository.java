package com.artworkguard.abuse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.security.*;
import java.util.*;
@Repository
public class ArtworkSourceRepository {
 private final JdbcTemplate jdbc;
 public ArtworkSourceRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void record(UUID artwork,byte[] uploaded,String contentType,int width,int height){
  jdbc.update("INSERT INTO artwork_source_metadata(artwork_id,sha256,size_bytes,content_type,width,height) VALUES(?,?,?,?,?,?)",
   artwork,sha256(uploaded),uploaded.length,contentType,width,height);
 }
 static String sha256(byte[] bytes){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
