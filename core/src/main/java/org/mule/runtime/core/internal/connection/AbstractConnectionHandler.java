/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.connection;

import static org.mule.runtime.api.util.Preconditions.checkArgument;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.exception.MuleException;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractConnectionHandler<C> implements ConnectionHandlerAdapter<C> {

  private final AtomicBoolean released = new AtomicBoolean(false);

  private void assertNotReleased() {
    checkArgument(!released.get(), "Connection Handler has already been released");
  }

  @Override
  public final C getConnection() throws ConnectionException {
    assertNotReleased();
    return doGetConnection();
  }

  @Override
  public final void release() {
    if (notReleased()) {
      doRelease();
    }
  }

  @Override
  public final void invalidate() {
    if (notReleased()) {
      doInvalidate();
    }
  }

  @Override
  public final void close() throws MuleException {
    if (notReleased()) {
      doClose();
    }
  }

  private boolean notReleased() {
    return released.compareAndSet(false, true);
  }

  protected abstract C doGetConnection() throws ConnectionException;

  protected abstract void doRelease();

  protected abstract void doInvalidate();

  protected abstract void doClose() throws MuleException;
}
