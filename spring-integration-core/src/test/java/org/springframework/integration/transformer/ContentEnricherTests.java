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

package org.springframework.integration.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.channel.RendezvousChannel;
import org.springframework.integration.config.ExpressionFactoryBean;
import org.springframework.integration.config.TestErrorHandler;
import org.springframework.integration.endpoint.PollingConsumer;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.handler.ReplyRequiredException;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.transformer.support.StaticHeaderValueMessageProcessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Artem Bilan
 * @author Gary Russell
 * @author Kris Jacyna
 *
 * @since 2.1
 */
public class ContentEnricherTests {

	/**
	 * In this test a {@link Target} message is passed into a {@link ContentEnricher}.
	 * The Enricher passes the message to a "request-channel" that is backed by a
	 * {@link QueueChannel}. The consumer of the "request-channel" takes a long
	 * time to execute, longer actually than the specified "replyTimeout" set on
	 * the {@link ContentEnricher}.
	 *
	 * Due to the occurring replyTimeout, a Null replyMessage is returned and because
	 * "requiresReply" is set to "true" on the {@link ContentEnricher}, a
	 * {@link ReplyRequiredException} is raised.
	 */
	@Test
	public void replyChannelReplyTimingOut() throws Exception {

		final long requestTimeout = 500L;
		final long replyTimeout = 100L;

		final DirectChannel replyChannel = new DirectChannel();
		final QueueChannel requestChannel = new QueueChannel(1);

		final ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);
		enricher.setReplyChannel(replyChannel);

		enricher.setOutputChannel(new NullChannel());
		enricher.setRequestTimeout(requestTimeout);
		enricher.setReplyTimeout(replyTimeout);

		final ExpressionFactoryBean expressionFactoryBean = new ExpressionFactoryBean("payload");
		expressionFactoryBean.setSingleton(false);
		expressionFactoryBean.afterPropertiesSet();

		final Map<String, Expression> expressions = new HashMap<String, Expression>();
		expressions.put("name", new LiteralExpression("cartman"));
		expressions.put("child.name", expressionFactoryBean.getObject());

		enricher.setPropertyExpressions(expressions);
		enricher.setRequiresReply(true);
		enricher.setBeanName("Enricher");
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();

		final AbstractReplyProducingMessageHandler handler = new AbstractReplyProducingMessageHandler() {

			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				try {
					Thread.sleep(5000);
				}
				catch (InterruptedException e) {
					fail(e.getMessage());
				}
				return new Target("child");
			}

		};

		handler.setBeanFactory(mock(BeanFactory.class));
		handler.afterPropertiesSet();

		final PollingConsumer consumer = new PollingConsumer(requestChannel, handler);
		final TestErrorHandler errorHandler = new TestErrorHandler();

		consumer.setTrigger(new PeriodicTrigger(0));
		consumer.setErrorHandler(errorHandler);

		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setPoolSize(2);
		taskScheduler.afterPropertiesSet();

		consumer.setTaskScheduler(taskScheduler);
		consumer.setBeanFactory(mock(BeanFactory.class));
		consumer.afterPropertiesSet();
		consumer.start();

		final Target target = new Target("replace me");
		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();

		try {
			enricher.handleMessage(requestMessage);
		}
		catch (ReplyRequiredException e) {
			assertThat(e.getMessage())
					.isEqualTo("No reply produced by handler 'Enricher', and its 'requiresReply' property is set to true.");
			return;
		}
		finally {
			consumer.stop();
			taskScheduler.destroy();
		}

		fail("ReplyRequiredException expected.");

	}

	@Test
	public void requestChannelSendTimingOut() {

		final String requestChannelName = "Request_Channel";
		final long requestTimeout = 200L;

		QueueChannel replyChannel = new QueueChannel();
		QueueChannel requestChannel = new RendezvousChannel();
		requestChannel.setBeanName(requestChannelName);

		ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);
		enricher.setRequestTimeout(requestTimeout);
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();

		Target target = new Target("replace me");
		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();

		try {
			enricher.handleMessage(requestMessage);
		}
		catch (MessageDeliveryException e) {
			assertThat(e.getMessage()).isEqualToIgnoringCase("failed to send message to channel '" + requestChannelName
					+ "' within timeout: " + requestTimeout);
		}

	}

	@Test
	public void simpleProperty() {
		QueueChannel replyChannel = new QueueChannel();
		DirectChannel requestChannel = new DirectChannel();
		requestChannel.subscribe(new AbstractReplyProducingMessageHandler() {
			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				return new Source("John", "Doe");
			}
		});

		ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);

		SpelExpressionParser parser = new SpelExpressionParser();
		Map<String, Expression> propertyExpressions = new HashMap<String, Expression>();
		propertyExpressions.put("name", parser.parseExpression("payload.lastName + ', ' + payload.firstName"));
		enricher.setPropertyExpressions(propertyExpressions);
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();

		Target target = new Target("replace me");
		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();
		enricher.handleMessage(requestMessage);
		Message<?> reply = replyChannel.receive(0);
		assertThat(((Target) reply.getPayload()).getName()).isEqualTo("Doe, John");
	}

	@Test
	public void setReplyChannelWithoutRequestChannel() {

		QueueChannel replyChannel = new QueueChannel();

		ContentEnricher enricher = new ContentEnricher();
		enricher.setReplyChannel(replyChannel);
		enricher.setBeanFactory(mock(BeanFactory.class));

		try {
			enricher.afterPropertiesSet();
		}
		catch (IllegalStateException e) {
			assertThat(e.getMessage())
					.isEqualTo("If the replyChannel is set, then the requestChannel must not be null");
			return;
		}

		fail("Expected an exception.");
	}

	@Test
	public void setNullReplyTimeout() {

		ContentEnricher enricher = new ContentEnricher();
		enricher.setBeanFactory(mock(BeanFactory.class));

		try {
			enricher.setReplyTimeout(null);
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("replyTimeout must not be null");
			return;
		}

		fail("Expected an exception.");
	}

	@Test
	public void setNullRequestTimeout() {

		ContentEnricher enricher = new ContentEnricher();
		enricher.setBeanFactory(mock(BeanFactory.class));

		try {
			enricher.setRequestTimeout(null);
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("requestTimeout must not be null");
			return;
		}

		fail("Expected an exception.");
	}

	@Test
	public void testSimplePropertyWithoutUsingRequestChannel() {
		QueueChannel replyChannel = new QueueChannel();
		ContentEnricher enricher = new ContentEnricher();
		SpelExpressionParser parser = new SpelExpressionParser();
		Map<String, Expression> propertyExpressions = new HashMap<String, Expression>();
		propertyExpressions.put("name", parser.parseExpression("'just a static string'"));
		enricher.setPropertyExpressions(propertyExpressions);
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();
		Target target = new Target("replace me");
		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();
		enricher.handleMessage(requestMessage);
		Message<?> reply = replyChannel.receive(0);
		assertThat(((Target) reply.getPayload()).getName()).isEqualTo("just a static string");
	}

	@Test
	public void testContentEnricherWithNullRequestChannel() {

		ContentEnricher enricher = new ContentEnricher();
		enricher.setReplyChannel(new QueueChannel());
		enricher.setBeanFactory(mock(BeanFactory.class));

		try {
			enricher.afterPropertiesSet();
		}
		catch (IllegalStateException e) {
			assertThat(e.getMessage())
					.isEqualTo("If the replyChannel is set, then the requestChannel must not be null");
			return;
		}

		fail("Expected an IllegalArgumentException to be thrown.");
	}

	@Test
	public void nestedProperty() {
		QueueChannel replyChannel = new QueueChannel();
		DirectChannel requestChannel = new DirectChannel();
		requestChannel.subscribe(new AbstractReplyProducingMessageHandler() {
			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				return new Source("John", "Doe");
			}
		});
		ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);

		SpelExpressionParser parser = new SpelExpressionParser();
		Map<String, Expression> propertyExpressions = new HashMap<String, Expression>();
		propertyExpressions.put("child.name", parser.parseExpression("payload.lastName + ', ' + payload.firstName"));
		enricher.setPropertyExpressions(propertyExpressions);
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();

		Target target = new Target("test");
		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();
		enricher.handleMessage(requestMessage);
		Message<?> reply = replyChannel.receive(0);
		Target result = (Target) reply.getPayload();
		assertThat(result.getName()).isEqualTo("test");
		assertThat(result.getChild().getName()).isEqualTo("Doe, John");
	}

	@Test
	public void clonePayload() {
		QueueChannel replyChannel = new QueueChannel();
		DirectChannel requestChannel = new DirectChannel();
		requestChannel.subscribe(new AbstractReplyProducingMessageHandler() {
			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				return new Source("John", "Doe");
			}
		});
		ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);

		enricher.setShouldClonePayload(true);
		SpelExpressionParser parser = new SpelExpressionParser();
		Map<String, Expression> propertyExpressions = new HashMap<String, Expression>();
		propertyExpressions.put("name", parser.parseExpression("payload.lastName + ', ' + payload.firstName"));
		enricher.setPropertyExpressions(propertyExpressions);
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();

		Target target = new Target("replace me");
		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();
		enricher.handleMessage(requestMessage);
		Message<?> reply = replyChannel.receive(0);
		Target result = (Target) reply.getPayload();
		assertThat(result.getName()).isEqualTo("Doe, John");
		assertThat(result).isNotSameAs(target);
	}

	@Test
	public void clonePayloadIgnored() {
		QueueChannel replyChannel = new QueueChannel();
		DirectChannel requestChannel = new DirectChannel();
		requestChannel.subscribe(new AbstractReplyProducingMessageHandler() {
			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				return new Source("John", "Doe");
			}
		});
		ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);

		enricher.setShouldClonePayload(true);
		SpelExpressionParser parser = new SpelExpressionParser();
		Map<String, Expression> propertyExpressions = new HashMap<String, Expression>();
		propertyExpressions.put("name", parser.parseExpression("payload.lastName + ', ' + payload.firstName"));
		enricher.setPropertyExpressions(propertyExpressions);
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();

		TargetUser target = new TargetUser();
		target.setName("replace me");

		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();
		enricher.handleMessage(requestMessage);
		Message<?> reply = replyChannel.receive(0);
		TargetUser result = (TargetUser) reply.getPayload();
		assertThat(result.getName()).isEqualTo("Doe, John");

		assertThat(result).isSameAs(target);
	}

	@Test
	public void clonePayloadWithFailure() {
		QueueChannel replyChannel = new QueueChannel();
		DirectChannel requestChannel = new DirectChannel();
		requestChannel.subscribe(new AbstractReplyProducingMessageHandler() {
			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				return new Source("John", "Doe");
			}
		});
		ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);

		enricher.setShouldClonePayload(true);
		SpelExpressionParser parser = new SpelExpressionParser();
		Map<String, Expression> propertyExpressions = new HashMap<String, Expression>();
		propertyExpressions.put("name", parser.parseExpression("payload.lastName + ', ' + payload.firstName"));
		enricher.setPropertyExpressions(propertyExpressions);
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();

		UncloneableTargetUser target = new UncloneableTargetUser();
		target.setName("replace me");

		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();

		try {
			enricher.handleMessage(requestMessage);
		}
		catch (MessageHandlingException e) {
			assertThat(e.getMessage()).contains("Failed to clone payload object");
			return;
		}

		fail("Expected a MessageHandlingException to be thrown.");

	}

	@Test
	public void testLifeCycleMethodsWithoutRequestChannel() {
		ContentEnricher enricher = new ContentEnricher();
		enricher.setBeanFactory(mock(BeanFactory.class));

		enricher.afterPropertiesSet();

		assertThat(enricher.isRunning()).isTrue();
		enricher.stop();
		assertThat(enricher.isRunning()).isTrue();
	}

	@Test
	public void testLifeCycleMethodsWithRequestChannel() {

		DirectChannel requestChannel = new DirectChannel();
		requestChannel.subscribe(new AbstractReplyProducingMessageHandler() {
			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				return new Source("John", "Doe");
			}
		});

		ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);
		enricher.setBeanFactory(mock(BeanFactory.class));

		enricher.afterPropertiesSet();

		enricher.start();
		assertThat(enricher.isRunning()).isTrue();
		enricher.stop();
		assertThat(enricher.isRunning()).isFalse();
		enricher.start();
		assertThat(enricher.isRunning()).isTrue();

		enricher.stop();
	}

	/**
	 * In this test a {@link Target} message is passed into a {@link ContentEnricher}.
	 * The Enricher passes the message to a "request-channel" to a handler which throws
	 * an exception. The {@link ContentEnricher} then uses the error flow and consults
	 * the "error-channel" which returns a alternative {@link Target}.
	 */
	@Test
	public void testErrorChannel() throws Exception {

		final DirectChannel requestChannel = new DirectChannel();
		requestChannel.subscribe(new AbstractReplyProducingMessageHandler() {
			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				throw new RuntimeException();
			}

		});

		final DirectChannel errorChannel = new DirectChannel();
		errorChannel.subscribe(new AbstractReplyProducingMessageHandler() {
			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				return new Target("failed");
			}

		});

		final QueueChannel replyChannel = new QueueChannel();

		final ContentEnricher enricher = new ContentEnricher();
		enricher.setRequestChannel(requestChannel);
		enricher.setErrorChannel(errorChannel);

		SpelExpressionParser parser = new SpelExpressionParser();
		Map<String, Expression> propertyExpressions = new HashMap<String, Expression>();
		propertyExpressions.put("name", parser.parseExpression("payload.name + ' target'"));

		enricher.setPropertyExpressions(propertyExpressions);
		enricher.setBeanFactory(mock(BeanFactory.class));
		enricher.afterPropertiesSet();

		final Target target = new Target("replace me");
		Message<?> requestMessage = MessageBuilder.withPayload(target).setReplyChannel(replyChannel).build();

		enricher.handleMessage(requestMessage);
		Message<?> reply = replyChannel.receive(10000);
		Target result = (Target) reply.getPayload();
		assertThat(result.getName()).isEqualTo("failed target");
	}

	@Test
	public void testOverwriteTimestamp() {
		ContentEnricher contentEnricher = new ContentEnricher();
		contentEnricher.setHeaderExpressions(
				Collections.singletonMap(MessageHeaders.TIMESTAMP, new StaticHeaderValueMessageProcessor<>("foo")));

		contentEnricher.setBeanFactory(mock(BeanFactory.class));
		try {
			contentEnricher.afterPropertiesSet();
			fail("BeanInitializationException expected");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanInitializationException.class);
			assertThat(e.getMessage())
					.contains("ContentEnricher cannot override 'id' and 'timestamp' read-only headers.");
		}
	}

	@Test
	public void testOverwriteIdNullResult() {
		ContentEnricher contentEnricher = new ContentEnricher();
		contentEnricher.setNullResultHeaderExpressions(
				Collections.singletonMap(MessageHeaders.ID, new StaticHeaderValueMessageProcessor<>("foo")));

		contentEnricher.setBeanFactory(mock(BeanFactory.class));
		try {
			contentEnricher.afterPropertiesSet();
			fail("BeanInitializationException expected");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanInitializationException.class);
			assertThat(e.getMessage())
					.contains("ContentEnricher cannot override 'id' and 'timestamp' read-only headers.");
		}
	}

	@SuppressWarnings("unused")
	private static final class Source {

		private final String firstName;

		private final String lastName;

		Source(String firstName, String lastName) {
			this.firstName = firstName;
			this.lastName = lastName;
		}

		public String getFirstName() {
			return firstName;
		}

		public String getLastName() {
			return lastName;
		}

	}


	public static final class Target implements Cloneable {

		private volatile String name;

		private volatile Target child;

		public Target() {
			this.name = "default";
		}

		private Target(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Target getChild() {
			return this.child;
		}

		public void setChild(Target child) {
			this.child = child;
		}

		@Override
		public Object clone() {
			Target clone = new Target(this.name);
			clone.setChild(this.child);
			return clone;
		}

	}

	public static final class TargetUser {

		private volatile String name;

		public TargetUser() {
			this.name = "default";
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

	public static final class UncloneableTargetUser implements Cloneable {

		private volatile String name;

		public UncloneableTargetUser() {
			this.name = "default";
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public Object clone() {
			throw new IllegalStateException("Cloning not possible");
		}

	}

}
