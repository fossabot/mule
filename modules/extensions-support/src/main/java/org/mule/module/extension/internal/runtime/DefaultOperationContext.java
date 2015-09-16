/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.extension.internal.runtime;

import org.mule.api.MuleEvent;
import org.mule.extension.introspection.ParameterModel;
import org.mule.extension.runtime.ConfigurationInstance;
import org.mule.module.extension.internal.runtime.resolver.ResolverSetResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link OperationContextAdapter} which
 * adds additional information which is relevant to this implementation
 * of the extensions-api, even though it's not part of the API itself
 *
 * @since 3.7.0
 */
public class DefaultOperationContext implements OperationContextAdapter
{

    private final ConfigurationInstance<?> configuration;
    private final Map<String, Object> parameters;
    private final MuleEvent event;

    /**
     * Creates a new instance with the given state
     *
     * @param configuration the {@link ConfigurationInstance} that the operation will use
     * @param parameters    the parameters that the operation will use
     * @param event         the current {@link MuleEvent}
     */
    public DefaultOperationContext(ConfigurationInstance<Object> configuration, ResolverSetResult parameters, MuleEvent event)
    {
        this.configuration = configuration;
        this.event = event;

        Map<ParameterModel, Object> parameterMap = parameters.asMap();
        this.parameters = new HashMap<>(parameterMap.size());
        parameters.asMap().entrySet().forEach(parameter -> this.parameters.put(parameter.getKey().getName(), parameter.getValue()));
    }

    /**
     * {@inheritDoc}
     */
    public <C> ConfigurationInstance<C> getConfiguration()
    {
        return (ConfigurationInstance<C>) configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getParameter(String parameterName)
    {
        return parameters.get(parameterName);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MuleEvent getEvent()
    {
        return event;
    }
}