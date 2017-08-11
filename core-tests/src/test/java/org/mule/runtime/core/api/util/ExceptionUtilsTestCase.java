/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.util;

import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.core.api.util.ExceptionUtils.containsType;
import static org.mule.runtime.core.api.util.ExceptionUtils.extractCauseOfType;
import static org.mule.runtime.core.api.util.ExceptionUtils.extractConnectionException;
import static org.mule.runtime.core.api.util.ExceptionUtils.extractOfType;
import static org.mule.runtime.core.api.util.ExceptionUtils.getFullStackTraceWithoutMessages;
import static org.mule.runtime.core.api.util.MessagingExceptionResolver.resolve;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.core.api.InternalEventContext;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.notification.FlowCallStack;
import org.mule.runtime.core.api.exception.ErrorTypeLocator;
import org.mule.runtime.core.api.exception.MessagingException;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;
import org.junit.Test;
import java.io.IOException;
import java.util.Optional;

@SmallTest
public class ExceptionUtilsTestCase extends AbstractMuleTestCase {

  private static final String ERROR_MESSAGE = "Excepted Error Message";

  @Test
  public void testContainsType() {
    assertTrue(containsType(new IllegalArgumentException(), IllegalArgumentException.class));
    assertTrue(containsType(new Exception(new IllegalArgumentException()), IllegalArgumentException.class));
    assertTrue(containsType(new Exception(new IllegalArgumentException(new NullPointerException())), NullPointerException.class));
    assertTrue(containsType(new Exception(new IllegalArgumentException(new NullPointerException())), RuntimeException.class));
    assertTrue(containsType(new Exception(new IllegalArgumentException(new NullPointerException())), Exception.class));
    assertFalse(containsType(new Exception(new IllegalArgumentException(new NullPointerException())), IOException.class));
  }

  @Test
  public void testFullStackTraceWithoutMessage() throws Exception {
    final String mainMessage = "main message 112312 [][] ''' ... sdfsd blah";
    final String causeMessage = "cause message 2342998n  fwefoskjdcas  sdcasdhfsadjgsadkgasd \t\nsdfsllki";

    Exception e = new RuntimeException(mainMessage, new RuntimeException(causeMessage));
    String withoutMessage = getFullStackTraceWithoutMessages(e);
    String fullStackTrace = getStackTrace(e);

    String[] linesWithoutMessage = withoutMessage.split(lineSeparator());
    String[] lines = fullStackTrace.split(lineSeparator());

    assertEquals(lines.length, linesWithoutMessage.length);

    for (int i = 0; i < lines.length; i++) {
      assertTrue(lines[i].contains(linesWithoutMessage[i]));
      assertFalse(linesWithoutMessage[i].contains(mainMessage));
      assertFalse(linesWithoutMessage[i].contains(causeMessage));
    }
  }

  @Test
  public void extractExceptionOfType() {
    Exception exception = new Exception(new Throwable(new ConnectionException(new IOException(new NullPointerException()))));
    Optional<IOException> ioException = extractOfType(exception, IOException.class);
    assertThat(ioException.isPresent(), is(true));
    assertThat(ioException.get().getCause(), instanceOf(NullPointerException.class));
  }

  @Test
  public void extractExceptionOfSubtype() {
    Exception exception = new Exception(new IllegalStateException(new java.lang.Error(new RuntimeException(new IOException()))));
    Optional<RuntimeException> runtimeException = extractOfType(exception, RuntimeException.class);
    assertThat(runtimeException.isPresent(), is(true));
    assertThat(runtimeException.get(), instanceOf(IllegalStateException.class));
  }

  @Test
  public void extractExceptionCauseOf() {
    Exception exception = new Exception(new IOException(new ConnectionException(ERROR_MESSAGE, new NullPointerException())));
    Optional<? extends Throwable> throwable = extractCauseOfType(exception, IOException.class);
    assertThat(throwable.isPresent(), is(true));
    assertThat(throwable.get(), instanceOf(ConnectionException.class));
    assertThat(throwable.get().getMessage(), is(ERROR_MESSAGE));
  }

  @Test
  public void extractRootConnectionException() {
    Exception withConnectionExceptionCause =
        new Exception(new ConnectionException(ERROR_MESSAGE, new ConnectionException(new NullPointerException())));
    Optional<ConnectionException> connectionException = extractConnectionException(withConnectionExceptionCause);
    assertThat(connectionException.isPresent(), is(true));
    assertThat(connectionException.get().getMessage(), is(ERROR_MESSAGE));
  }

  @Test
  public void extractMissingConnectionException() {
    Exception withoutConnectionException = new Exception(new NullPointerException());
    Optional<ConnectionException> exception = extractConnectionException(withoutConnectionException);
    assertThat(exception.isPresent(), is(false));
  }

  @Test
  public void updateMessaginExceptionWithErrorWithOutError() {
    MessagingException messagingExceptionMock = mock(MessagingException.class);
    Processor processorMock = mock(Processor.class);
    MuleContext muleContextMock = mock(MuleContext.class);
    ErrorTypeLocator errorTypeLocatorMock = mock(ErrorTypeLocator.class);

    InternalEvent eventMock = mock(InternalEvent.class);
    FlowCallStack flowCallStackMock = mock(FlowCallStack.class);
    Message messageMock = mock(Message.class);
    InternalEventContext eventContextMock = mock(InternalEventContext.class);
    when(eventContextMock.getOriginatingLocation()).thenReturn(TEST_CONNECTOR_LOCATION);

    Optional<Error> errorOptional = Optional.ofNullable(null);

    when(messagingExceptionMock.getEvent()).thenReturn(eventMock);

    when(eventMock.getError()).thenReturn(errorOptional);
    when(eventMock.getFlowCallStack()).thenReturn(flowCallStackMock);
    when(eventMock.getMessage()).thenReturn(messageMock);
    when(eventMock.getContext()).thenReturn(eventContextMock);

    when(eventContextMock.getId()).thenReturn("someid");

    when(muleContextMock.getErrorTypeLocator()).thenReturn(errorTypeLocatorMock);

    ErrorType errorType = mock(ErrorType.class);
    when(errorType.getIdentifier()).thenReturn("ID");
    when(errorTypeLocatorMock.lookupErrorType(messagingExceptionMock)).thenReturn(errorType);
    when(errorTypeLocatorMock.lookupErrorType(messagingExceptionMock)).thenReturn(errorType);

    resolve(processorMock, messagingExceptionMock, muleContextMock.getErrorTypeLocator());

    //    verify(messagingExceptionMock, atLeast(1)).setProcessedEvent(any(InternalEvent.class));
  }

  @Test
  public void updateMessaginExceptionWithError() {
    MessagingException messagingExceptionMock = mock(MessagingException.class);
    Processor processorMock = mock(Processor.class);
    MuleContext muleContextMock = mock(MuleContext.class);
    ErrorTypeLocator errorTypeLocatorMock = mock(ErrorTypeLocator.class);

    InternalEvent eventMock = mock(InternalEvent.class);
    FlowCallStack flowCallStackMock = mock(FlowCallStack.class);
    Message messageMock = mock(Message.class);
    InternalEventContext eventContextMock = mock(InternalEventContext.class);
    when(eventContextMock.getOriginatingLocation()).thenReturn(TEST_CONNECTOR_LOCATION);

    Error errorMock = mock(Error.class);
    Optional<Error> errorOptional = Optional.ofNullable(errorMock);

    when(messagingExceptionMock.getEvent()).thenReturn(eventMock);

    when(eventMock.getError()).thenReturn(errorOptional);
    when(eventMock.getFlowCallStack()).thenReturn(flowCallStackMock);
    when(eventMock.getMessage()).thenReturn(messageMock);
    when(eventMock.getContext()).thenReturn(eventContextMock);

    when(eventContextMock.getId()).thenReturn("someid");

    when(muleContextMock.getErrorTypeLocator()).thenReturn(errorTypeLocatorMock);

    ErrorType errorType = mock(ErrorType.class);
    when(errorType.getIdentifier()).thenReturn("ID");
    when(errorTypeLocatorMock.lookupErrorType(messagingExceptionMock)).thenReturn(errorType);

    resolve(processorMock, messagingExceptionMock, muleContextMock.getErrorTypeLocator());

    //      verify(messagingExceptionMock, times(0)).setProcessedEvent(any(InternalEvent.class));
  }
}