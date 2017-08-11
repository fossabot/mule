/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.api.streaming.exception.StreamingBufferSizeExceededException;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.exception.ErrorTypeLocator;
import org.mule.runtime.core.api.exception.ErrorTypeRepository;
import org.mule.runtime.core.api.exception.MessagingException;
import org.mule.runtime.core.api.exception.MuleFatalException;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.routing.ValidationException;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.internal.exception.ErrorTypeLocatorFactory;
import org.mule.runtime.core.internal.exception.ErrorTypeRepositoryFactory;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;
import org.junit.Test;
import java.util.Optional;

@SmallTest
public class MessagingExceptionResolverTestCase extends AbstractMuleTestCase {

  private static final String MESSAGING_ERROR_MESSAGE = "Messaging Error Message";
  private static final String DEFAULT_ERROR_MESSAGE = "Error Message";

  private Processor processor = mock(Processor.class);
  private InternalEvent event = mock(InternalEvent.class);
  private MuleContext context = mock(MuleContext.class);

  private final StreamingBufferSizeExceededException STREAMING_EXCEPTION = new StreamingBufferSizeExceededException(1);
  private final TransformerException TRANSFORMER_EXCEPTION = new TransformerException(createStaticMessage("TRANSFORMER"));
  private final ValidationException VALIDATION_EXCEPTION = new ValidationException();
  private final ConnectionException CONNECTION_EXCEPTION = new ConnectionException("CONNECTION");
  private final MuleFatalException FATAL_EXCEPTION = new MuleFatalException(createStaticMessage("FATAL!"));
  private final Throwable THROWABLE = new Throwable("THROWABLE");
  private final java.lang.Error ERROR = new java.lang.Error("ERROR");

  private final ErrorTypeRepository repository = ErrorTypeRepositoryFactory.createDefaultErrorTypeRepository();
  private final ErrorTypeLocator locator = ErrorTypeLocatorFactory.createDefaultErrorTypeLocator(repository);

  private final ErrorType UNKNOWN = locator.lookupErrorType(Exception.class);
  private final ErrorType FATAL = locator.lookupErrorType(ERROR.getClass());
  private final ErrorType CONNECTION = locator.lookupErrorType(CONNECTION_EXCEPTION.getClass());

  @Test
  public void test() {
    Optional<Error> error = mockError(CONNECTION, null);
    when(event.getError()).thenReturn(error);
    MessagingException me = newMessagingException(ERROR, event, processor);
  }

  private MessagingException newMessagingException(Throwable e, InternalEvent event, Processor processor) {
    return new MessagingException(createStaticMessage(MESSAGING_ERROR_MESSAGE), event, e, processor);
  }

  private Optional<Error> mockError(ErrorType errorType, Throwable cause) {
    Error error = mock(Error.class);
    when(error.getErrorType()).thenReturn(errorType);
    when(error.getCause()).thenReturn(cause);
    return Optional.of(error);
  }

  /*
   .defaultExceptionMapper(ExceptionMapper.builder()
   .addExceptionMapping(MessageTransformerException.class, errorTypeRepository.lookupErrorType(TRANSFORMATION).get())
   .addExceptionMapping(TransformerException.class, errorTypeRepository.lookupErrorType(TRANSFORMATION).get())
   .addExceptionMapping(ExpressionRuntimeException.class, errorTypeRepository.lookupErrorType(EXPRESSION).get())
   .addExceptionMapping(RoutingException.class, errorTypeRepository.lookupErrorType(ROUTING).get())
   .addExceptionMapping(ConnectionException.class, errorTypeRepository.lookupErrorType(CONNECTIVITY).get())
   .addExceptionMapping(ValidationException.class, errorTypeRepository.lookupErrorType(VALIDATION).get())
   .addExceptionMapping(DuplicateMessageException.class, errorTypeRepository.lookupErrorType(DUPLICATE_MESSAGE).get())
   .addExceptionMapping(RetryPolicyExhaustedException.class, errorTypeRepository.lookupErrorType(RETRY_EXHAUSTED).get())
   .addExceptionMapping(SecurityException.class, errorTypeRepository.lookupErrorType(SECURITY).get())
   .addExceptionMapping(ClientSecurityException.class, errorTypeRepository.lookupErrorType(CLIENT_SECURITY).get())
   .addExceptionMapping(ServerSecurityException.class, errorTypeRepository.lookupErrorType(SERVER_SECURITY).get())
   .addExceptionMapping(NotPermittedException.class, errorTypeRepository.lookupErrorType(NOT_PERMITTED).get())
   .addExceptionMapping(RejectedExecutionException.class, errorTypeRepository.getErrorType(OVERLOAD).get())
   .addExceptionMapping(MessageRedeliveredException.class,
   errorTypeRepository.lookupErrorType(REDELIVERY_EXHAUSTED).get())
   .addExceptionMapping(Exception.class, unknown)
   .addExceptionMapping(Error.class, errorTypeRepository.getCriticalErrorType())
   .addExceptionMapping(StreamingBufferSizeExceededException.class,
   errorTypeRepository.lookupErrorType(STREAM_MAXIMUM_SIZE_EXCEEDED).get())
   .addExceptionMapping(MuleFatalException.class, errorTypeRepository.getErrorType(FATAL).get())
   .build())
    */
}
