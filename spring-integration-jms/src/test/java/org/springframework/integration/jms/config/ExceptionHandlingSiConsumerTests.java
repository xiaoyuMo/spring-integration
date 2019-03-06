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

package org.springframework.integration.jms.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.junit.Test;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

/**
 * @author Oleg Zhurakousky
 */
public class ExceptionHandlingSiConsumerTests {

	@SuppressWarnings("resource")
	@Test
	public void nonSiProducer_siConsumer_sync_withReturn() throws Exception {
		ActiveMqTestUtils.prepare();
		ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext("Exception-nonSiProducer-siConsumer.xml", ExceptionHandlingSiConsumerTests.class);
		JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean("jmsConnectionFactory", ConnectionFactory.class));
		Destination request = applicationContext.getBean("requestQueueA", Destination.class);
		final Destination reply = applicationContext.getBean("replyQueueA", Destination.class);
		jmsTemplate.send(request, (MessageCreator) session -> {
			TextMessage message = session.createTextMessage();
			message.setText("echoChannel");
			message.setJMSReplyTo(reply);
			return message;
		});
		Message message = jmsTemplate.receive(reply);
		assertThat(message).isNotNull();
		applicationContext.close();
	}

	@SuppressWarnings("resource")
	@Test
	public void nonSiProducer_siConsumer_sync_withReturnNoException() throws Exception {
		ActiveMqTestUtils.prepare();
		ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext("Exception-nonSiProducer-siConsumer.xml", ExceptionHandlingSiConsumerTests.class);
		JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean("jmsConnectionFactory", ConnectionFactory.class));
		Destination request = applicationContext.getBean("requestQueueB", Destination.class);
		final Destination reply = applicationContext.getBean("replyQueueB", Destination.class);
		jmsTemplate.send(request, (MessageCreator) session -> {
			TextMessage message = session.createTextMessage();
			message.setText("echoWithExceptionChannel");
			message.setJMSReplyTo(reply);
			return message;
		});
		Message message = jmsTemplate.receive(reply);
		assertThat(message).isNotNull();
		assertThat(((TextMessage) message).getText()).isEqualTo("echoWithException");
		applicationContext.close();
	}

	@Test
	public void nonSiProducer_siConsumer_sync_withOutboundGateway() throws Exception {
		ActiveMqTestUtils.prepare();
		final ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext("Exception-nonSiProducer-siConsumer.xml", ExceptionHandlingSiConsumerTests.class);
		SampleGateway gateway = applicationContext.getBean("sampleGateway", SampleGateway.class);
		String reply = gateway.echo("echoWithExceptionChannel");
		assertThat(reply).isEqualTo("echoWithException");
		applicationContext.close();
	}


	public static class SampleService {

		public String echoWithException(String value) {
			throw new SampleException("echoWithException");
		}

		public String echo(String value) {
			return value;
		}
	}


	@SuppressWarnings("serial")
	public static class SampleException extends RuntimeException {
		public SampleException(String message) {
			super(message);
		}
	}


	public interface SampleGateway {
		String echo(String value);
	}


	public static class SampleErrorTransformer {
		public org.springframework.messaging.Message<?> transform(Throwable t) throws Exception {
			return MessageBuilder.withPayload(t.getCause().getMessage()).build();
		}
	}

}
