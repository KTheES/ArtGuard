package com.artworkguard.notification;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
class NotificationWorkerTest {
 final NotificationRepository repository=mock(NotificationRepository.class);final EmailGateway email=mock(EmailGateway.class);
 final NotificationWorker worker=new NotificationWorker(repository,email);final UUID id=UUID.randomUUID();
 @Test void noPendingDeliveryDoesNothing(){when(repository.claim()).thenReturn(Optional.empty());worker.deliver();verifyNoInteractions(email);}
 @Test void successfulDeliveryIsMarkedSent(){var item=new NotificationRepository.Delivery(id,"owner@example.com","subject","body",1);when(repository.claim()).thenReturn(Optional.of(item));worker.deliver();verify(email).send("owner@example.com","subject","body");verify(repository).sent(id);verify(repository,never()).failed(any(),anyInt());}
 @Test void smtpFailureSchedulesRetryWithoutLeakingException(){var item=new NotificationRepository.Delivery(id,"owner@example.com","subject","body",3);when(repository.claim()).thenReturn(Optional.of(item));doThrow(new IllegalStateException("smtp secret")).when(email).send(any(),any(),any());worker.deliver();verify(repository).failed(id,3);verify(repository,never()).sent(any());}
}
