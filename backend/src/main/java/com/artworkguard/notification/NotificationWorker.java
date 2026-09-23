package com.artworkguard.notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="artworkguard.notification.enabled",havingValue="true")
public class NotificationWorker {
 private final NotificationRepository repository;private final EmailGateway email;
 public NotificationWorker(NotificationRepository repository,EmailGateway email){this.repository=repository;this.email=email;}
 @Scheduled(fixedDelayString="${artworkguard.notification.delay-ms:5000}")
 public void deliver(){
  var claimed=repository.claim();if(claimed.isEmpty())return;var item=claimed.get();
  try{email.send(item.recipient(),item.subject(),item.body());repository.sent(item.id());}
  catch(RuntimeException e){repository.failed(item.id(),item.attempts());}
 }
}
