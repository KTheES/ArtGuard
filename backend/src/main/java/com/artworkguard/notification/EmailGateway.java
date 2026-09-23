package com.artworkguard.notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

public interface EmailGateway {
 void send(String recipient,String subject,String body);
}

@Component
@ConditionalOnProperty(name="artworkguard.notification.enabled",havingValue="true")
class SmtpEmailGateway implements EmailGateway {
 private final JavaMailSender mail;private final NotificationSettings settings;
 SmtpEmailGateway(JavaMailSender mail,NotificationSettings settings){this.mail=mail;this.settings=settings;}
 public void send(String recipient,String subject,String body){
  var message=new SimpleMailMessage();message.setFrom(settings.from());message.setTo(recipient);message.setSubject(subject);message.setText(body);mail.send(message);
 }
}
