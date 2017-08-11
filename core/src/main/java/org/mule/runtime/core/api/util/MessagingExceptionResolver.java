/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.api.util;

import static org.mule.runtime.api.exception.ExceptionHelper.getExceptionsAsList;
import static org.mule.runtime.core.api.context.notification.EnrichedNotificationInfo.createInfo;
import static org.mule.runtime.core.api.util.ExceptionUtils.getErrorMappings;
import static org.mule.runtime.core.api.util.ExceptionUtils.getMessagingExceptionCause;

import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.notification.EnrichedNotificationInfo;
import org.mule.runtime.core.api.exception.ErrorTypeLocator;
import org.mule.runtime.core.api.exception.MessagingException;
import org.mule.runtime.core.api.message.ErrorBuilder;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.internal.exception.ErrorMapping;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class MessagingExceptionResolver {

  public static MessagingException resolveWithError(MessagingException me, Processor failing, MuleContext context) {
//    // If Event already has Error, for example because of an interceptor then conserve existing Error instance
//    if (!me.getEvent().getError().isPresent()) {
//      me.setProcessedEvent(createErrorEvent(me.getEvent(), failing, me, context.getErrorTypeLocator()));
//    }
    return enrich(me, failing, me.getEvent(), context);
  }

  /**
   * Resolves a new {@link MessagingException} to be thrown based on the content of the {@code me} parameter and the chain of
   * causes inside it.
   *
   * @param failing    the failing processor
   * @param me         the me to update based on it's content
   * @param locator    the error type locator
   * @return a {@link MessagingException} with the proper {@link Error} associated to it's {@link InternalEvent}
   */
  public static MessagingException resolve(Processor failing,
                                           MessagingException me,
                                           ErrorTypeLocator locator) {
    Map<Throwable, ErrorType> errors = collectErrors(me, locator);

    if (errors.isEmpty()) {
      return me;
    }

    Map.Entry<Throwable, ErrorType> first = errors.entrySet().iterator().next();
    Throwable root = first.getKey();
    ErrorType rootErrorType = first.getValue();
    Processor failingProcessor = getFailingProcessor(me, root).orElse(failing);
    ErrorType errorType = getErrorMappings(failing)
                            .stream()
                            .filter(m -> m.match(rootErrorType))
                            .findFirst()
                            .map(ErrorMapping::getTarget)
                            .orElse(rootErrorType);

    Error error = ErrorBuilder.builder(getMessagingExceptionCause(root).orElse(root)).errorType(errorType).build();
    InternalEvent event = InternalEvent.builder(me.getEvent()).error(error).build();

    if (root instanceof MessagingException) {
      ((MessagingException) root).setProcessedEvent(event);
      return ((MessagingException) root);
    } else {
      return new MessagingException(event, root, failingProcessor);
    }
  }

  private static Optional<Processor> getFailingProcessor(MessagingException me, Throwable root) {
    Processor failingProcessor = me.getFailingMessageProcessor();
    if (failingProcessor == null && root instanceof MessagingException) {
      failingProcessor = ((MessagingException) root).getFailingMessageProcessor();
    }
    return Optional.ofNullable(failingProcessor);
  }

  private static Map<Throwable, ErrorType> collectErrors(MessagingException me, ErrorTypeLocator locator) {
    LinkedHashMap<Throwable, ErrorType> errors = new LinkedHashMap<>();
    getExceptionsAsList(me).forEach(e -> {
      ErrorType type = errorTypeFromException(locator, e);
      if (!type.getIdentifier().equals("UNKNOWN")) {
        errors.put(e, type);
      }
    });
    return errors;
  }

  private static ErrorType errorTypeFromException(ErrorTypeLocator locator, Throwable e) {
    return isMessagingExceptionWithError(e) ?
                ((MessagingException) e).getEvent().getError().get().getErrorType() : locator.lookupErrorType(e);
  }

  private static boolean isMessagingExceptionWithError(Throwable cause) {
    return cause instanceof MessagingException
             && ((MessagingException) cause).getEvent().getError().isPresent()
             && ((MessagingException) cause).getFailingMessageProcessor() != null;
  }

  private static MessagingException enrich(MessagingException me, Processor failing, InternalEvent event, MuleContext context) {
    EnrichedNotificationInfo notificationInfo = createInfo(event, me, null);
    context.getExceptionContextProviders().forEach(cp -> {
      cp.getContextInfo(notificationInfo, failing).forEach((k, v) -> me.getInfo().putIfAbsent(k, v));
    });
    return me;
  }
}
