package com.artworkguard.notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class NotificationSettings {
 private final boolean enabled;private final String from;private final URI appUrl;
 public NotificationSettings(@Value("${artworkguard.notification.enabled:false}") boolean enabled,
  @Value("${artworkguard.notification.from:}") String from,@Value("${artworkguard.notification.app-url:http://127.0.0.1:3000}") String appUrl){
  this.enabled=enabled;this.from=from.strip();
  try{this.appUrl=URI.create(appUrl);}catch(Exception e){throw new IllegalArgumentException("APP_URL must be a valid URL");}
  boolean loopback="http".equals(this.appUrl.getScheme())&&("127.0.0.1".equals(this.appUrl.getHost())||"localhost".equalsIgnoreCase(this.appUrl.getHost()));
  if(this.appUrl.getHost()==null||this.appUrl.getUserInfo()!=null||this.appUrl.getFragment()!=null||!("https".equals(this.appUrl.getScheme())||loopback))throw new IllegalArgumentException("APP_URL must be HTTPS or loopback HTTP");
  if(enabled&&!this.from.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new IllegalArgumentException("NOTIFICATION_FROM must be a valid email address when notifications are enabled");
 }
 public boolean enabled(){return enabled;}public String from(){return from;}public URI appUrl(){return appUrl;}
 @Override public String toString(){return "NotificationSettings[enabled="+enabled+",from=[REDACTED],appUrl="+appUrl+"]";}
}
