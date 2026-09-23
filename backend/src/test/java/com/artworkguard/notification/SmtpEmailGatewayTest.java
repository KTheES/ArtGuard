package com.artworkguard.notification;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SmtpEmailGatewayTest {
 @Test void sendsPlainTextWithConfiguredSender(){
  JavaMailSender mail=mock(JavaMailSender.class);var gateway=new SmtpEmailGateway(mail,new NotificationSettings(true,"alerts@example.com","https://app.example"));
  gateway.send("owner@example.com","HIGH detection","body");
  var message=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(message.capture());
  assertEquals("alerts@example.com",message.getValue().getFrom());assertArrayEquals(new String[]{"owner@example.com"},message.getValue().getTo());assertEquals("HIGH detection",message.getValue().getSubject());assertEquals("body",message.getValue().getText());
 }
}
