/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.util;

import static java.lang.System.lineSeparator;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static org.mule.runtime.core.api.exception.Errors.Identifiers.UNKNOWN_ERROR_IDENTIFIER;
import static org.mule.runtime.core.internal.component.ComponentAnnotations.ANNOTATION_NAME;
import static org.mule.runtime.core.internal.exception.ErrorMapping.ANNOTATION_ERROR_MAPPINGS;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.exception.ErrorMessageAwareException;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.api.meta.AnnotatedObject;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.core.api.exception.ErrorTypeLocator;
import org.mule.runtime.core.api.exception.MessagingException;
import org.mule.runtime.core.api.exception.WrapperErrorMessageAwareException;
import org.mule.runtime.core.api.message.ErrorBuilder;
import org.mule.runtime.core.internal.exception.ErrorMapping;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Mule exception utilities.
 */
public class ExceptionUtils {

  /**
   * This method returns true if the throwable contains a {@link Throwable} that matches the specified class or subclass in the
   * exception chain. Subclasses of the specified class do match.
   *
   * @param throwable the throwable to inspect, may be null
   * @param type      the type to search for, subclasses match, null returns false
   * @return the index into the throwable chain, false if no match or null input
   */
  public static boolean containsType(Throwable throwable, Class<?> type) {
    return org.apache.commons.lang3.exception.ExceptionUtils.indexOfType(throwable, type) > -1;
  }

  /**
   * Similar to {@link org.apache.commons.lang3.exception.ExceptionUtils#getStackTrace(Throwable)} but removing the exception and
   * causes messages. This is useful to determine if two exceptions have matching stack traces regardless of the messages which
   * may contain invokation specific data
   *
   * @param throwable the throwable to inspect, may be <code>null</code>
   * @return the stack trace as a string, with the messages stripped out. Empty string if throwable was <code>null</code>
   */
  public static String getFullStackTraceWithoutMessages(Throwable throwable) {
    StringBuilder builder = new StringBuilder();

    for (String frame : org.apache.commons.lang3.exception.ExceptionUtils.getStackFrames(throwable)) {
      builder.append(frame.replaceAll(":\\s+([\\w\\s]*.*)", "").trim()).append(lineSeparator());
    }

    return builder.toString();
  }

  /**
   * Introspects the {@link Throwable} parameter to obtain the first {@link Throwable} of type {@link ConnectionException} in the
   * exception chain.
   *
   * @param throwable the last throwable in the exception chain.
   * @return an {@link Optional} value with the first {@link ConnectionException} in the exception chain if any.
   */
  public static Optional<ConnectionException> extractConnectionException(Throwable throwable) {
    return extractOfType(throwable, ConnectionException.class);
  }

  /**
   * Introspects the {@link Throwable} parameter to obtain the first {@link Throwable} of type {@code throwableType} in the
   * exception chain and return the cause of it.
   *
   * @param throwable     the last throwable on the exception chain.
   * @param throwableType the type of the throwable that the cause is wanted.
   * @return the cause of the first {@link Throwable} of type {@code throwableType}.
   */
  public static Optional<Throwable> extractCauseOfType(Throwable throwable, Class<? extends Throwable> throwableType) {
    return extractOfType(throwable, throwableType).map(Throwable::getCause);
  }

  /**
   * Introspects the {@link Throwable} parameter to obtain the first {@link Throwable} of type {@code throwableType} in the
   * exception chain.
   * <p>
   * This method handles recursive cause structures that might otherwise cause infinite loops. If the throwable parameter is a
   * {@link ConnectionException} the same value will be returned. If the throwable parameter has a cause of itself, then an empty
   * value will be returned.
   *
   * @param throwable     the last throwable on the exception chain.
   * @param throwableType the type of the throwable is wanted to find.
   * @return the cause of the first {@link Throwable} of type {@code throwableType}.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Throwable> Optional<T> extractOfType(Throwable throwable, Class<T> throwableType) {
    if (throwable == null || !containsType(throwable, throwableType)) {
      return empty();
    }

    return (Optional<T>) stream(org.apache.commons.lang3.exception.ExceptionUtils.getThrowables(throwable))
                           .filter(throwableType::isInstance).findFirst();
  }

  /**
   * Executes the given {@code callable} knowing that it might throw an {@link Exception} of type {@code expectedExceptionType}.
   * If that happens, then it will re throw such exception.
   * <p>
   * If the {@code callable} throws a {@link RuntimeException} of a different type, then it is also re-thrown. Finally, if an
   * exception of any different type is thrown, then it is handled by delegating into the {@code exceptionHandler}, which might in
   * turn also throw an exception or handle it returning a value.
   *
   * @param expectedExceptionType the type of exception which is expected to be thrown
   * @param callable              the delegate to be executed
   * @param exceptionHandler      a {@link ExceptionHandler} in case an unexpected exception is found instead
   * @param <T>                   the generic type of the return value
   * @param <E>                   the generic type of the expected exception
   * @return a value returned by either the {@code callable} or the {@code exceptionHandler}
   * @throws E if the expected exception is actually thrown
   */
  public static <T, E extends Exception> T tryExpecting(Class<E> expectedExceptionType, Callable<T> callable,
                                                        ExceptionHandler<T, E> exceptionHandler)
    throws E {
    try {
      return callable.call();
    } catch (Exception e) {
      if (expectedExceptionType.isInstance(e)) {
        throw (E) e;
      }

      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }

      return exceptionHandler.handle(e);
    }
  }

  /**
   * Determine the {@link ErrorType} of a given exception thrown by a given message processor.
   *
   * @param processor the component that threw the exception.
   * @param exception the exception thrown.
   * @param locator   the {@link ErrorTypeLocator}.
   * @return the resolved {@link ErrorType}
   */
  private static ErrorType getErrorTypeFromFailingProcessor(Object processor, Throwable exception,
                                                            ErrorType currentError, ErrorTypeLocator locator) {
    Throwable cause = getWrapperErrorCause(exception);
    ErrorType errorType = getComponentIdentifier(processor)
                            .map(ci -> locator.lookupComponentErrorType(ci, cause))
                            .orElse(locator.lookupErrorType(cause));
    return getErrorMappings(processor)
             .stream()
             .filter(m -> m.match(currentError == null || isUnknownMuleError(currentError) ? errorType : currentError))
             .findFirst()
             .map(ErrorMapping::getTarget)
             .orElse(errorType);
  }

  public static Error getErrorFromFailingProcessor(InternalEvent currentEvent, Object processor,
                                                   Throwable cause, ErrorTypeLocator locator) {
    ErrorType currentError = currentEvent != null ? currentEvent.getError().map(Error::getErrorType).orElse(null) : null;
    ErrorType foundErrorType = locator.lookupErrorType(cause);
    // CHECK CONNECTIVITY ACA
    currentError = foundErrorType.getIdentifier().equals(UNKNOWN_ERROR_IDENTIFIER) ? currentError : foundErrorType;
    return ErrorBuilder.builder(cause).errorType(getErrorTypeFromFailingProcessor(processor, cause, currentError, locator))
             .build();
  }

  private static Throwable getWrapperErrorCause(Throwable exception) {
    return exception instanceof WrapperErrorMessageAwareException ?
             ((WrapperErrorMessageAwareException) exception).getRootCause() :
             exception;
  }

  public static List<ErrorMapping> getErrorMappings(Object processor) {
    if (AnnotatedObject.class.isAssignableFrom(processor.getClass())) {
      List<ErrorMapping> list = (List<ErrorMapping>) ((AnnotatedObject) processor).getAnnotation(ANNOTATION_ERROR_MAPPINGS);
      return list != null ? list : emptyList();
    }
    return emptyList();
  }

  private static Optional<ComponentIdentifier> getComponentIdentifier(Object processor) {
    if (AnnotatedObject.class.isAssignableFrom(processor.getClass())) {
      return Optional.of((ComponentIdentifier) ((AnnotatedObject) processor).getAnnotation(ANNOTATION_NAME));
    }
    return empty();
  }

  private static boolean isMessagingExceptionCause(MessagingException me, Throwable cause) {
    return !me.getEvent().getError()
              .filter(error -> cause.equals(error.getCause()))
              .filter(error -> me.causedExactlyBy(error.getCause().getClass()))
              .isPresent();
  }

  private static boolean isUnknownMuleError(ErrorTypeLocator locator, Throwable cause) {
    return isUnknownMuleError(locator.lookupErrorType(cause));
  }

  private static boolean isUnknownMuleError(ErrorType type) {
    return type.getIdentifier().equals(UNKNOWN_ERROR_IDENTIFIER);
  }

  /**
   * Create new {@link InternalEvent} with {@link org.mule.runtime.api.message.Error} instance set.
   *
   * @param currentEvent event when error occured.
   * @param processor    message processor/source.
   * @param me           messaging exception.
   * @param locator      the mule context.
   * @return new {@link InternalEvent} with relevant {@link org.mule.runtime.api.message.Error} set.
   */
  public static InternalEvent createErrorEvent(InternalEvent currentEvent,
                                               Object processor,
                                               MessagingException me,
                                               ErrorTypeLocator locator) {
    Throwable cause = me.getCause() != null ? me.getCause() : me;
    if (hasErrorMappings(processor) || isMessagingExceptionCause(me, cause)) {
      Error newError = getErrorFromFailingProcessor(currentEvent, processor, cause, locator);
      InternalEvent newEvent = InternalEvent.builder(me.getEvent()).error(newError).build();
      me.setProcessedEvent(newEvent);
      return newEvent;
    } else {
      return currentEvent;
    }
  }

  // TODO: MULE-10970/MULE-10971 - Change signature to AnnotatedObject once every processor and source is one
  static boolean hasErrorMappings(Object obj) {
    boolean hasErrorMappings = false;
    if (AnnotatedObject.class.isAssignableFrom(obj.getClass())) {
      final Object errorMappingAnnotation = ((AnnotatedObject) obj).getAnnotation(ANNOTATION_ERROR_MAPPINGS);
      hasErrorMappings = errorMappingAnnotation != null && !((List<ErrorMapping>) errorMappingAnnotation).isEmpty();
    }
    return hasErrorMappings;
  }

  /**
   * Resolve the root cause of an exception. If the exception is an instance of {@link ErrorMessageAwareException} then it's root
   * cause is used, else the candidate exception instance if returned.
   *
   * @param exception candidate exception.
   * @return root cause exception.
   */
  public static Throwable getErrorMessageAwareExceptionCause(Throwable exception) {
    return exception instanceof ErrorMessageAwareException ? ((ErrorMessageAwareException) exception).getRootCause() : exception;
  }

  /**
   * Given a {@link MessagingException} return the first cause that isn't a messaging exception. If the candidate exception is not
   * a {@link MessagingException} then it is returned as is.
   *
   * @param exception candidate exception
   * @return cause exception.
   */
  public static Optional<Throwable> getMessagingExceptionCause(Throwable exception) {
    Throwable cause = exception;
    while (cause instanceof MessagingException) {
      cause = cause.getCause();
    }
    return Optional.ofNullable(cause);
  }
}
