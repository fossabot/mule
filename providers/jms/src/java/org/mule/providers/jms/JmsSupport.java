/*
 * $Header$
 * $Revision$
 * $Date$
 * ------------------------------------------------------------------------------------------------------
 *
 * Copyright (c) Cubis Limited. All rights reserved.
 * http://www.cubis.co.uk
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */
package org.mule.providers.jms;

import javax.jms.*;

/**
 * <code>JmsSupport</code> is an interface that provides a polymorphic facade to the Jms 1.0.2b and 1.1
 * api specifications.
 * this interface is not intended for general purpose use and should only be used with the Mule Jms connector.
 *
 * @author <a href="mailto:ross.mason@cubis.co.uk">Ross Mason</a>
 * @version $Revision$
 */

public interface JmsSupport
{
    Connection createConnection(ConnectionFactory connectionFactory) throws JMSException;

    Connection createConnection(ConnectionFactory connectionFactory, String username, String password) throws JMSException;

    Session createSession(Connection connection, boolean transacted, int ackMode, boolean noLocal) throws JMSException;

    MessageProducer createProducer(Session session, Destination destination) throws JMSException;

    MessageConsumer createConsumer(Session session, Destination destination, String messageSelector, boolean noLocal, String durableName) throws JMSException;

    MessageConsumer createConsumer(Session session, Destination destination) throws JMSException;

    Destination createDestination(Session session, String name, boolean topic) throws JMSException;

    Destination createTemporaryDestination(Session session, boolean topic) throws JMSException;

    void send(MessageProducer producer, Message message) throws JMSException;

    void send(MessageProducer producer, Message message, boolean persistent, int priority, long ttl) throws JMSException;

    void send(MessageProducer producer, Message message, Destination dest) throws JMSException;

    void send(MessageProducer producer, Message message, Destination dest, boolean persistent, int priority, long ttl) throws JMSException;
}
