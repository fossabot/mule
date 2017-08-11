/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.module.extension.error;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.functional.junit4.matchers.MessageMatchers.hasPayload;
import static org.mule.functional.junit4.matchers.ThatMatcher.that;

import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.core.api.exception.MessagingException;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.test.module.extension.AbstractExtensionFunctionalTestCase;
import org.junit.Rule;
import org.junit.Test;

public class ErrorTypesTestCase extends AbstractExtensionFunctionalTestCase {

  @Rule
  public DynamicPort port = new DynamicPort("port");

  @Override
  protected String getConfigFile() {
    return "error-types.xml";
  }

  @Test
  public void previousError() throws Exception {
    MessagingException error = flowRunner("previousConnectorError").runExpectingException();
    ErrorType errorType = error.getEvent().getError().get().getErrorType();
    assertThat(errorType.getNamespace(), is("MARVEL"));
  }

  @Test
  public void previousError222() throws Exception {
    MessagingException error = flowRunner("previousConnectorError2").runExpectingException();
    ErrorType errorType = error.getEvent().getError().get().getErrorType();
    assertThat(errorType.getNamespace(), is("MARVEL"));  }

  @Test
  public void previousError333() throws Exception {
    MessagingException error = flowRunner("previousConnectorError3").runExpectingException();
    ErrorType errorType = error.getEvent().getError().get().getErrorType();
    assertThat(errorType.getNamespace(), is("MARVEL"));
  }

  @Test
  public void previousMuleError() throws Exception {
    flowRunner("previousMuleError").run().getMessage();
  }

  @Test
  public void mapping() throws Exception {
    flowRunner("mapping").run().getMessage();
  }

  private void verify(String flowName, String expectedPayload, Object headers) throws Exception {
    assertThat(flowRunner(flowName).withVariable("headers", headers).run().getMessage(), hasPayload(that(is(expectedPayload))));
  }
}
