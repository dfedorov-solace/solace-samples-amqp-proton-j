/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 *  Solace AMQP JMS 2.0 Examples: SimpleReplier
 */

package com.solace.samples;

import org.apache.logging.log4j.Logger;
import org.apache.qpid.jms.JmsTemporaryQueue;
import org.apache.logging.log4j.LogManager;

import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Receives a request message using JMS 2.0 API over AMQP 1.0 and replies to it. Solace Message Router is used as the
 * message broker.
 * 
 * The queues used for requests must exist on the message broker.
 * 
 * This is the Replier in the Request/Reply messaging pattern.
 */
public class SimpleReplier {

    private static final Logger LOG = LogManager.getLogger(SimpleReplier.class.getName());

    // connectionfactory.solaceConnectionLookup in file "jndi.properties"
    final String SOLACE_CONNECTION_LOOKUP = "solaceConnectionLookup";
    // queue.queueLookup in file "jndi.properties"
    final String QUEUE_LOOKUP = "queueLookup";

    private void run() {
        try {
            // pick up properties from the "jndi.properties" file
            Context initialContext = new InitialContext();
            ConnectionFactory factory;
            factory = (ConnectionFactory) initialContext.lookup(SOLACE_CONNECTION_LOOKUP);

            // establish connection that uses the Solace Message Router as a message broker
            try (JMSContext context = factory.createContext()) {
                // the source for requests: a queue that already exists on the broker
                Queue source = (Queue) initialContext.lookup(QUEUE_LOOKUP);
                // create consumer and wait for a request to arrive.
                LOG.info("Waiting for a request...");
                // the current thread blocks at the next statement until a request arrives
                Message request = context.createConsumer(source).receive();
                // process received request
                if (request instanceof TextMessage) {
                    TextMessage requestTextMessage = (TextMessage) request;
                    LOG.info("Received request with string data: \"{}\"", requestTextMessage.getText());
                    // prepare reply with received string data
                    Message replyMessage = context.createTextMessage(
                            String.format("Reply to \"%s\"", requestTextMessage.getText()));
                    replyMessage.setJMSCorrelationID(request.getJMSCorrelationID());
                    // workaround as the Apache Qpid JMS API sets JMSReplyTo to a non-temporary queue
                    // should be: JmsTemporaryQueue replyTo = (JmsTemporaryQueue) request.getJMSReplyTo();
                    Destination replyDestination = new JmsTemporaryQueue(
                            ((Queue) request.getJMSReplyTo()).getQueueName());
                    // create producer and send the reply
                    context.createProducer().setDeliveryMode(DeliveryMode.NON_PERSISTENT)
                            .send(replyDestination, replyMessage);
                    LOG.info("Request Message replied successfully.");
                    TimeUnit.SECONDS.sleep(3);
                } else {
                    LOG.warn("Unexpected data type in request: \"{}\"", request.toString());
                }
            } catch (JMSException ex) {
                LOG.error(ex);
            } catch (JMSRuntimeException ex) {
                LOG.error(ex);
            } catch (InterruptedException ex) {
                LOG.error(ex);
            }

            initialContext.close();
        } catch (NamingException ex) {
            LOG.error(ex);
        }
    }

    public static void main(String[] args) {
        new SimpleReplier().run();
    }

}
