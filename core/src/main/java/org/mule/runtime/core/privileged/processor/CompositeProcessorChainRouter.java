/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.privileged.processor;

import static java.util.Collections.emptyList;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.disposeIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.startIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.stopIfNeeded;
import static org.mule.runtime.core.api.util.UUID.getUUID;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.InputEvent;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Lifecycle;
import org.mule.runtime.api.meta.AbstractAnnotatedObject;
import org.mule.runtime.core.DefaultEventContext;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.processor.MessageProcessorChain;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component to be used that supports a collection of {@link MessageProcessorChain} and executes them in order.
 * <p/>
 * Meant to be used by runtime privileged extensions that need to construct top level chains of execution.
 * 
 * @since 4.0
 */
public class CompositeProcessorChainRouter extends AbstractAnnotatedObject implements Lifecycle {

  private static Logger LOGGER = LoggerFactory.getLogger(CompositeProcessorChainRouter.class);

  @Inject
  private MuleContext muleContext;

  private String name;
  private List<MessageProcessorChain> processorChains = emptyList();

  public Event process(InputEvent inputEvent) throws MuleException {
    InternalEvent.Builder builder =
        InternalEvent.builder(DefaultEventContext.create(getUUID(), muleContext.getId(), getLocation()));
    InternalEvent defaultEvent = builder.variables(inputEvent.getVariables())
        .properties(inputEvent.getProperties())
        .parameters(inputEvent.getParameters())
        .message(inputEvent.getMessage())
        .error(inputEvent.getError().orElse(null)).build();
    for (MessageProcessorChain processorChain : processorChains) {
      defaultEvent = processorChain.process(defaultEvent);
    }
    return defaultEvent;
  }

  public void setProcessorChains(List processorChains) {
    this.processorChains = processorChains;
  }

  @Override
  public void stop() throws MuleException {
    stopIfNeeded(processorChains);
  }

  @Override
  public void dispose() {
    disposeIfNeeded(processorChains, LOGGER);
  }

  @Override
  public void start() throws MuleException {
    startIfNeeded(processorChains);
  }

  @Override
  public void initialise() throws InitialisationException {
    initialiseIfNeeded(processorChains, muleContext);
  }
}
