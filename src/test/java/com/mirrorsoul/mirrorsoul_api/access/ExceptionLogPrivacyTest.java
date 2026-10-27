package com.mirrorsoul.mirrorsoul_api.access;
import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.ExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
class ExceptionLogPrivacyTest {
 @Test void parseErrorsAndUnhandledExceptionsDoNotLogOrReturnPrivatePayload() {
  String secret="private-provider-key-and-raw-dialogue";var logger=(Logger)LoggerFactory.getLogger(ExceptionAdvice.class);var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);
  try {
   var advice=new ExceptionAdvice();var response=advice.handleException(new IllegalStateException(secret));
   advice.handleHttpMessageNotReadable(new HttpMessageNotReadableException(secret,new MockHttpInputMessage(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
   assertThat(response.getBody().getError().toString()).doesNotContain(secret);
   assertThat(logs.list).allSatisfy(e->{assertThat(e.getFormattedMessage()).doesNotContain(secret);assertThat(e.getThrowableProxy()).isNull();});
  } finally {logger.detachAppender(logs);logs.stop();}
 }
}
