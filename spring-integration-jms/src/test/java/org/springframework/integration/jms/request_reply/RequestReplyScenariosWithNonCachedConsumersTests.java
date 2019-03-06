/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.jms.request_reply;

import static org.assertj.core.api.Assertions.assertThat;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.junit.Rule;
import org.junit.Test;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.MessageTimeoutException;
import org.springframework.integration.gateway.RequestReplyExchanger;
import org.springframework.integration.jms.ActiveMQMultiContextTests;
import org.springframework.integration.jms.config.ActiveMqTestUtils;
import org.springframework.integration.test.support.LongRunningIntegrationTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.messaging.support.GenericMessage;
/**
 * @author Oleg Zhurakousky
 * @author Gary Russell
 */
public class RequestReplyScenariosWithNonCachedConsumersTests extends ActiveMQMultiContextTests {

	@Rule
	public LongRunningIntegrationTest longTests = new LongRunningIntegrationTest();

	@Test(expected = MessageTimeoutException.class)
	public void messageCorrelationBasedOnRequestMessageIdOptimized() throws Exception {
		ActiveMqTestUtils.prepare();
		AbstractApplicationContext context = new ClassPathXmlApplicationContext("producer-no-cached-consumers.xml", this.getClass());
		try {
			RequestReplyExchanger gateway = context.getBean("optimizedMessageId", RequestReplyExchanger.class);
			ConnectionFactory connectionFactory = context.getBean(ConnectionFactory.class);
			final JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);

			final Destination requestDestination = context.getBean("siOutQueueC", Destination.class);
			final Destination replyDestination = context.getBean("siInQueueC", Destination.class);
			new Thread(() -> {
				final Message requestMessage = jmsTemplate.receive(requestDestination);
				jmsTemplate.send(replyDestination, (MessageCreator) session -> {
					TextMessage message = session.createTextMessage();
					message.setText("bar");
					message.setJMSCorrelationID(requestMessage.getJMSMessageID());
					return message;
				});
			}).start();
			org.springframework.messaging.Message<?> siReplyMessage = gateway.exchange(new GenericMessage<String>("foo"));
			assertThat(siReplyMessage.getPayload()).isEqualTo("bar");
		}
		finally {
			context.close();
		}
	}

	@Test
	public void messageCorrelationBasedOnRequestMessageIdNonOptimized() throws Exception {
		ActiveMqTestUtils.prepare();
		AbstractApplicationContext context = new ClassPathXmlApplicationContext("producer-no-cached-consumers.xml", this.getClass());
		try {
			RequestReplyExchanger gateway = context.getBean("nonoptimizedMessageId", RequestReplyExchanger.class);
			ConnectionFactory connectionFactory = context.getBean(ConnectionFactory.class);
			final JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);

			final Destination requestDestination = context.getBean("siOutQueueD", Destination.class);
			final Destination replyDestination = context.getBean("siInQueueD", Destination.class);
			new Thread(() -> {
				final Message requestMessage = jmsTemplate.receive(requestDestination);
				jmsTemplate.send(replyDestination, (MessageCreator) session -> {
					TextMessage message = session.createTextMessage();
					message.setText("bar");
					message.setJMSCorrelationID(requestMessage.getJMSMessageID());
					return message;
				});
			}).start();
			org.springframework.messaging.Message<?> siReplyMessage = gateway.exchange(new GenericMessage<String>("foo"));
			assertThat(siReplyMessage.getPayload()).isEqualTo("bar");
		}
		finally {
			context.close();
		}
	}

	@Test
	public void messageCorrelationBasedOnRequestCorrelationIdOptimized() throws Exception {
		ActiveMqTestUtils.prepare();
		AbstractApplicationContext context = new ClassPathXmlApplicationContext("producer-no-cached-consumers.xml", this.getClass());
		try {
			RequestReplyExchanger gateway = context.getBean("optimized", RequestReplyExchanger.class);
			ConnectionFactory connectionFactory = context.getBean(ConnectionFactory.class);
			final JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);

			final Destination requestDestination = context.getBean("siOutQueueA", Destination.class);
			final Destination replyDestination = context.getBean("siInQueueA", Destination.class);
			new Thread(() -> {
				final Message requestMessage = jmsTemplate.receive(requestDestination);
				jmsTemplate.send(replyDestination, (MessageCreator) session -> {
					TextMessage message = session.createTextMessage();
					message.setText("bar");
					message.setJMSCorrelationID(requestMessage.getJMSCorrelationID());
					return message;
				});
			}).start();
			org.springframework.messaging.Message<?> siReplyMessage = gateway.exchange(new GenericMessage<String>("foo"));
			assertThat(siReplyMessage.getPayload()).isEqualTo("bar");
		}
		finally {
			context.close();
		}
	}

	@Test(expected = MessageTimeoutException.class)
	public void messageCorrelationBasedOnRequestCorrelationIdNonOptimized() throws Exception {
		ActiveMqTestUtils.prepare();
		AbstractApplicationContext context = new ClassPathXmlApplicationContext("producer-no-cached-consumers.xml", this.getClass());
		try {
			RequestReplyExchanger gateway = context.getBean("nonoptimized", RequestReplyExchanger.class);
			ConnectionFactory connectionFactory = context.getBean(ConnectionFactory.class);
			final JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);

			final Destination requestDestination = context.getBean("siOutQueueB", Destination.class);
			final Destination replyDestination = context.getBean("siInQueueB", Destination.class);
			new Thread(() -> {
				final Message requestMessage = jmsTemplate.receive(requestDestination);
				jmsTemplate.send(replyDestination, (MessageCreator) session -> {
					TextMessage message = session.createTextMessage();
					message.setText("bar");
					message.setJMSCorrelationID(requestMessage.getJMSCorrelationID());
					return message;
				});
			}).start();
			gateway.exchange(new GenericMessage<String>("foo"));
		}
		finally {
			context.close();
		}
	}
}
