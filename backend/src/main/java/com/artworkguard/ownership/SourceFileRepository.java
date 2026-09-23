package com.artworkguard.ownership;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.time.Instant;
@Repository
public class SourceFileRepository {
 private final JdbcTemplate jdbc;public SourceFileRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Source(UUID claimId,String format,String sha256,long sizeBytes,String validation,String storageKey,Instant uploadedAt){}
 public boolean lockPending(UUID owner,UUID claim){return !jdbc.query("""
  SELECT c.id FROM ownership_claim c JOIN artwork a ON a.id=c.artwork_id JOIN app_user u ON u.id=c.owner_id
  WHERE c.id=? AND c.owner_id=? AND c.status='PENDING' AND a.deleted=FALSE AND u.status='ACTIVE' FOR UPDATE OF c
  """,(r,n)->r.getObject(1,UUID.class),claim,owner).isEmpty();}
 public Optional<Source> find(UUID claim){return jdbc.query("SELECT * FROM ownership_source_file WHERE claim_id=?",(r,n)->new Source(r.getObject("claim_id",UUID.class),r.getString("format"),r.getString("sha256"),r.getLong("size_bytes"),r.getString("validation"),r.getString("storage_key"),r.getTimestamp("uploaded_at").toInstant()),claim).stream().findFirst();}
 public void insert(UUID claim,String format,String hash,int size,String validation,String key){jdbc.update("INSERT INTO ownership_source_file(claim_id,format,sha256,size_bytes,validation,storage_key) VALUES(?,?,?,?,?,?)",claim,format,hash,size,validation,key);}
}
