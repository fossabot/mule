/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.el.mvel.datatype;

import static org.mule.runtime.core.el.mvel.MessageVariableResolverFactory.MESSAGE_PAYLOAD;
import static org.mule.runtime.core.el.mvel.MessageVariableResolverFactory.PAYLOAD;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.api.metadata.DataType;
import org.mule.mvel2.ast.ASTNode;

/**
 * Resolves data type for expressions representing message's payload
 */
public class PayloadExpressionDataTypeResolver extends AbstractExpressionDataTypeResolver {

  @Override
  protected DataType getDataType(InternalEvent event, ASTNode node) {
    if (node.isIdentifier() && (PAYLOAD.equals(node.getName()) || MESSAGE_PAYLOAD.equals(node.getName()))) {
      return event.getMessage().getPayload().getDataType();
    } else {
      return null;
    }
  }
}
