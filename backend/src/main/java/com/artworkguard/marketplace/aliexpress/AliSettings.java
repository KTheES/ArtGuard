package com.artworkguard.marketplace.aliexpress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
@Component
public class AliSettings {
 private final boolean enabled;private final String key,secret,tracking;
 public AliSettings(@Value("${artworkguard.aliexpress.enabled:false}") boolean enabled,
  @Value("${artworkguard.aliexpress.app-key:}") String key,@Value("${artworkguard.aliexpress.app-secret:}") String secret,
  @Value("${artworkguard.aliexpress.tracking-id:}") String tracking){this.enabled=enabled;this.key=key;this.secret=secret;this.tracking=tracking;}
 public boolean ready(){return enabled&&!key.isBlank()&&!secret.isBlank();}
 public void requireReady(){if(!ready())throw new AliException(HttpStatus.SERVICE_UNAVAILABLE,"ALIEXPRESS_NOT_CONFIGURED");}
 String key(){return key;}String secret(){return secret;}String tracking(){return tracking;}
 @Override public String toString(){return "AliSettings[REDACTED]";}
}
