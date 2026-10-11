package com.mirrorsoul.mirrorsoul_api.support;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.Delivery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import java.util.Properties;
class SupportMailWorkerTest {
 @Test void sendsOnlyPersistedPublicReplyAndMarksSent() throws Exception {
  var repo=mock(SupportEntryRepository.class);var delivery=mock(SupportDeliveryService.class);var mail=mock(JavaMailSender.class);
  var metrics=new SimpleMeterRegistry();var attempt=new SupportDeliveryService.Attempt(3L,1L,"attempt","member@example.com","public answer");
  when(delivery.claim(3L,true)).thenReturn(attempt);var message=new MimeMessage(Session.getInstance(new Properties()));when(mail.createMimeMessage()).thenReturn(message);
  new SupportMailWorker(repo,delivery,mail,metrics,true,"support@example.com").dispatch(3L);
  verify(mail).send(message);verify(delivery).finish(attempt,Delivery.SENT,null);
  assertThat(message.getContent()).isEqualTo("public answer");assertThat(message.getHeader("Message-ID")[0]).isEqualTo("<support-3@mirrorsoul.support>");
  assertThat(metrics.counter("support.mail.sent").count()).isEqualTo(1);
 }
 @Test void uncertainSmtpFailureRequiresOperatorRetryInsteadOfAutomaticResend() {
  var repo=mock(SupportEntryRepository.class);var delivery=mock(SupportDeliveryService.class);var mail=mock(JavaMailSender.class);
  var metrics=new SimpleMeterRegistry();var attempt=new SupportDeliveryService.Attempt(3L,1L,"attempt","member@example.com","public answer");
  when(delivery.claim(3L,true)).thenReturn(attempt);var message=new MimeMessage(Session.getInstance(new Properties()));when(mail.createMimeMessage()).thenReturn(message);
  doThrow(new MailSendException("provider response containing private data")).when(mail).send(message);
  new SupportMailWorker(repo,delivery,mail,metrics,true,"support@example.com").dispatch(3L);
  verify(delivery).finish(attempt,Delivery.UNKNOWN,"DELIVERY_UNCERTAIN");assertThat(metrics.counter("support.mail.uncertain").count()).isEqualTo(1);
 }
}
