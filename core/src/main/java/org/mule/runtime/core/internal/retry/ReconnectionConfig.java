/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.retry;

import org.mule.runtime.core.api.retry.policy.NoRetryPolicyTemplate;
import org.mule.runtime.core.api.retry.policy.RetryPolicyTemplate;

public class ReconnectionConfig {

  private final boolean failsDeployment;
  private final RetryPolicyTemplate retryPolicyTemplate;

  public static ReconnectionConfig getDefault() {
    return new ReconnectionConfig(false, new NoRetryPolicyTemplate());
  }

  public ReconnectionConfig(boolean failsDeployment, RetryPolicyTemplate retryPolicyTemplate) {
    this.failsDeployment = failsDeployment;
    this.retryPolicyTemplate = retryPolicyTemplate;
  }

  public boolean isFailsDeployment() {
    return failsDeployment;
  }

  public RetryPolicyTemplate getRetryPolicyTemplate() {
    return retryPolicyTemplate;
  }
}
