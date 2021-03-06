/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.operation;

import static org.mule.runtime.api.meta.TargetType.MESSAGE;
import static org.mule.runtime.core.internal.util.message.MessageUtils.unwrapResultIfNecessary;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.meta.TargetType;
import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.streaming.CursorProviderFactory;
import org.mule.runtime.module.extension.internal.runtime.ExecutionContextAdapter;

/**
 * An implementation of {@link AbstractReturnDelegate} which sets the output message on a variable which key is taken from the
 * {@link #target} field.
 * <p>
 * The target variable will always contain a {@link Message}, even if the operation returned a simple value
 * <p>
 * The original message is left untouched.
 *
 * @since 4.0
 */
final class TargetReturnDelegate extends AbstractReturnDelegate {

  private final String target;
  private final TargetType targetType;

  /**
   * {@inheritDoc}
   *
   * @param target the name of the variable in which the output message will be set
   */
  TargetReturnDelegate(String target,
                       TargetType targetType,
                       ComponentModel componentModel,
                       CursorProviderFactory cursorProviderFactory,
                       MuleContext muleContext) {
    super(componentModel, cursorProviderFactory, muleContext);
    this.target = target;
    this.targetType = targetType;
  }

  @Override
  public InternalEvent asReturnValue(Object value, ExecutionContextAdapter operationContext) {
    InternalEvent.Builder builder = InternalEvent.builder(operationContext.getEvent());
    if (targetType == MESSAGE) {
      builder.addVariable(target, toMessage(value, operationContext));
    } else {
      Object targetValue = unwrapResultIfNecessary(value);
      builder.addVariable(target, targetValue,
                          DataType.builder().fromObject(targetValue).mediaType(resolveMediaType(value, operationContext))
                              .build());
    }
    return builder.build();
  }
}
