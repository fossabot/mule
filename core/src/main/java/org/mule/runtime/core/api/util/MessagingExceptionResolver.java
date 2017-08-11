/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.api.util;

import static java.util.stream.Collectors.toList;
import static org.mule.runtime.api.exception.ExceptionHelper.getExceptionsAsList;
import static org.mule.runtime.core.api.context.notification.EnrichedNotificationInfo.createInfo;
import static org.mule.runtime.core.api.util.ExceptionUtils.createErrorEvent;
import static org.mule.runtime.core.api.util.ExceptionUtils.getComponentIdentifier;
import static org.mule.runtime.core.api.util.ExceptionUtils.getErrorMappings;
import static org.mule.runtime.core.api.util.ExceptionUtils.getMessagingExceptionCause;
import static org.mule.runtime.core.api.util.ExceptionUtils.isUnknownMuleError;

import org.mule.runtime.api.component.ComponentIdentifier;
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
import java.util.LinkedList;
import java.util.List;
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
   * @param processor  the failing processor
   * @param me         the {@link MessagingException} to update based on it's content
   * @param locator    the error type locator
   * @return a {@link MessagingException} with the proper {@link Error} associated to it's {@link InternalEvent}
   */
  public static MessagingException resolve(Processor processor, MessagingException me, ErrorTypeLocator locator) {
    Optional<Pair<Throwable, ErrorType>> rootCause = findRoot(processor, me, locator);

    if (!rootCause.isPresent()) {
      me.setProcessedEvent(createErrorEvent(me.getEvent(), processor, me, locator));
      return me;
    }

    Throwable root = rootCause.get().getFirst();
    ErrorType rootErrorType = rootCause.get().getSecond();
    Processor failingProcessor = getFailingProcessor(me, root).orElse(processor);

    ErrorType errorType = getErrorMappings(processor)
        .stream()
        .filter(m -> m.match(rootErrorType))
        .findFirst()
        .map(ErrorMapping::getTarget)
        .orElse(rootErrorType);

    Error error = ErrorBuilder.builder(getMessagingExceptionCause(root)).errorType(errorType).build();
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


  private static Optional<Pair<Throwable, ErrorType>> findRoot(Object p, MessagingException me, ErrorTypeLocator locator) {
    List<Pair<Throwable, ErrorType>> errors = collectErrors(p, me, locator);
    if (errors.isEmpty()) {
      return Optional.empty();
    }
    ErrorType rootType = errors.get(0).getSecond();
    // Get the last exception with the same type as the root
    List<Pair<Throwable, ErrorType>> filtered = errors.stream().filter(e -> e.getSecond().equals(rootType)).collect(toList());
    return Optional.ofNullable(filtered.get(filtered.size() - 1));
  }

  private static List<Pair<Throwable, ErrorType>> collectErrors(Object p, MessagingException me, ErrorTypeLocator locator) {
    List<Pair<Throwable, ErrorType>> errors = new LinkedList<>();
    getExceptionsAsList(me).forEach(e -> {
      ErrorType type = errorTypeFromException(p, locator, e);
      if (!isUnknownMuleError(type)) {
        errors.add(new Pair<>(e, type));
      }
    });
    return errors;
  }

  private static ErrorType errorTypeFromException(Object processor, ErrorTypeLocator locator, Throwable e) {
    if (isMessagingExceptionWithError(e)) {
      return ((MessagingException) e).getEvent().getError().get().getErrorType();
    } else {
      Optional<ComponentIdentifier> componentIdentifier = getComponentIdentifier(processor);
      return componentIdentifier.map(ci -> locator.lookupComponentErrorType(ci, e)).orElse(locator.lookupErrorType(e));
    }
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
