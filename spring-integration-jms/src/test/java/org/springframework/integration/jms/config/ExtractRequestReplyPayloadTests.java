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
import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.JMSException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.jms.ChannelPublishingJmsMessageListener;
import org.springframework.integration.jms.JmsOutboundGateway;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author ozhurakousky
 * @author Gunnar Hillert
 * @author Gary Russell
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class ExtractRequestReplyPayloadTests {

	@Rule
	public TestName testName = new TestName();

	@Autowired
	ApplicationContext applicationContext;

	@Autowired
	MessageChannel outboundChannel;

	@Autowired
	SubscribableChannel jmsInputChannel;

	@Autowired
	PollableChannel replyChannel;

	@Autowired
	JmsOutboundGateway outboundGateway;

	@Autowired
	ChannelPublishingJmsMessageListener inboundGateway;

	@Test
	public void testOutboundInboundDefault() {
		this.outboundGateway.setExtractRequestPayload(true);
		this.outboundGateway.setExtractReplyPayload(true);

		this.inboundGateway.setExtractReplyPayload(true);
		this.inboundGateway.setExtractRequestPayload(true);

		MessageHandler handler = echoInboundStringHandler();
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));

		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload() instanceof String).isTrue();
		this.jmsInputChannel.unsubscribe(handler);
	}

	@Test
	public void testOutboundInboundDefaultIsTx() {
		this.outboundGateway.setExtractRequestPayload(true);
		this.outboundGateway.setExtractReplyPayload(true);

		this.inboundGateway.setExtractReplyPayload(true);
		this.inboundGateway.setExtractRequestPayload(true);

		final AtomicBoolean failOnce = new AtomicBoolean();
		MessageHandler handler = message -> {
			assertThat(message.getPayload() instanceof String).isTrue();
			if (failOnce.compareAndSet(false, true)) {
				throw new RuntimeException("test tx");
			}
			MessagingTemplate template = new MessagingTemplate();
			template.setDefaultDestination((MessageChannel) message.getHeaders().getReplyChannel());
			template.send(message);
		};
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));

		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload() instanceof String).isTrue();
		this.jmsInputChannel.unsubscribe(handler);
	}

	@Test
	public void testOutboundBothFalseInboundDefault() {
		this.outboundGateway.setExtractRequestPayload(false);
		this.outboundGateway.setExtractReplyPayload(false);

		this.inboundGateway.setExtractReplyPayload(true);
		this.inboundGateway.setExtractRequestPayload(true);

		MessageHandler handler = echoInboundStringHandler();
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));

		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload()).isInstanceOf(javax.jms.TextMessage.class);
		this.jmsInputChannel.unsubscribe(handler);
	}

	@Test
	public void testOutboundDefaultInboundBothFalse() throws Exception {
		this.outboundGateway.setExtractRequestPayload(true);
		this.outboundGateway.setExtractReplyPayload(true);

		this.inboundGateway.setExtractReplyPayload(false);
		this.inboundGateway.setExtractRequestPayload(false);

		MessageHandler handler = unwrapTextMessageAndEchoHandler();
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));
		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload()).isInstanceOf(String.class);
		this.jmsInputChannel.unsubscribe(handler);
	}

	@Test
	public void testOutboundDefaultInboundReplyTrueRequestFalse() {
		this.outboundGateway.setExtractRequestPayload(true);
		this.outboundGateway.setExtractReplyPayload(true);

		this.inboundGateway.setExtractReplyPayload(true);
		this.inboundGateway.setExtractRequestPayload(false);

		MessageHandler handler = unwrapTextMessageAndEchoHandler();
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));
		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload()).isInstanceOf(String.class);
		this.jmsInputChannel.unsubscribe(handler);
	}

	@Test
	public void testOutboundDefaultInboundReplyFalseRequestTrue() {
		this.outboundGateway.setExtractRequestPayload(true);
		this.outboundGateway.setExtractReplyPayload(true);

		this.inboundGateway.setExtractReplyPayload(false);
		this.inboundGateway.setExtractRequestPayload(true);

		MessageHandler handler = echoInboundStringHandler();
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));
		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload() instanceof String).isTrue();
		this.jmsInputChannel.unsubscribe(handler);
	}

	@Test
	public void testOutboundRequestTrueReplyFalseInboundDefault() {
		this.outboundGateway.setExtractRequestPayload(true);
		this.outboundGateway.setExtractReplyPayload(false);

		this.inboundGateway.setExtractReplyPayload(true);
		this.inboundGateway.setExtractRequestPayload(true);

		MessageHandler handler = echoInboundStringHandler();
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));
		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload() instanceof javax.jms.Message).isTrue();
		this.jmsInputChannel.unsubscribe(handler);
	}

	@Test
	public void testOutboundRequestFalseReplyTrueInboundDefault() {
		this.outboundGateway.setExtractRequestPayload(false);
		this.outboundGateway.setExtractReplyPayload(true);

		this.inboundGateway.setExtractReplyPayload(true);
		this.inboundGateway.setExtractRequestPayload(true);

		MessageHandler handler = echoInboundStringHandler();
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));
		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload()).isInstanceOf(String.class);
		this.jmsInputChannel.unsubscribe(handler);
	}

	@Test
	public void testAllFalse() throws Exception {
		this.outboundGateway.setExtractRequestPayload(false);
		this.outboundGateway.setExtractReplyPayload(false);

		this.inboundGateway.setExtractReplyPayload(false);
		this.inboundGateway.setExtractRequestPayload(false);

		MessageHandler handler = unwrapObjectMessageAndEchoHandler();
		this.jmsInputChannel.subscribe(handler);
		this.outboundChannel.send(new GenericMessage<String>("Hello " + this.testName.getMethodName()));
		Message<?> replyMessage = this.replyChannel.receive(10000);
		assertThat(replyMessage.getPayload()).isInstanceOf(javax.jms.Message.class);
		this.jmsInputChannel.unsubscribe(handler);
	}

	private MessageHandler echoInboundStringHandler() {
		return message -> {
			assertThat(message.getPayload() instanceof String).isTrue();
			MessagingTemplate template = new MessagingTemplate();
			template.setDefaultDestination((MessageChannel) message.getHeaders().getReplyChannel());
			template.send(message);
		};
	}

	private MessageHandler unwrapObjectMessageAndEchoHandler() {
		return message -> {
			assertThat(message.getPayload()).isInstanceOf(javax.jms.ObjectMessage.class);
			MessagingTemplate template = new MessagingTemplate();
			template.setDefaultDestination((MessageChannel) message.getHeaders().getReplyChannel());
			Message<?> origMessage = null;
			try {
				origMessage = (Message<?>) ((javax.jms.ObjectMessage) message.getPayload()).getObject();
			}
			catch (JMSException e) {
				fail("failed to deserialize message");
			}
			template.send(origMessage);
		};
	}

	private MessageHandler unwrapTextMessageAndEchoHandler() {
		return message -> {
			assertThat(message.getPayload()).isInstanceOf(javax.jms.TextMessage.class);
			MessagingTemplate template = new MessagingTemplate();
			template.setDefaultDestination((MessageChannel) message.getHeaders().getReplyChannel());
			String payload = null;
			try {
				payload = ((javax.jms.TextMessage) message.getPayload()).getText();
			}
			catch (JMSException e) {
				fail("failed to deserialize message");
			}
			template.send(new GenericMessage<String>(payload));
		};
	}

}
