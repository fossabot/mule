/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.foo.hello;

import org.mule.runtime.extension.api.annotation.param.Config;

public class HelloOperation {

  public HelloOperation() {}

  public String printMessage(@Config HelloExtension config) {
    System.out.println("Test plugin extension says: " + config.getMessage());
    return config.getMessage();
  }
}
