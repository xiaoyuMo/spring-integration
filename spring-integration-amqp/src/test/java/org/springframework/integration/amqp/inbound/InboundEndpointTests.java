/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.integration.amqp.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.integration.amqp.support.AmqpMessageHeaderErrorMessageStrategy;
import org.springframework.integration.amqp.support.DefaultAmqpHeaderMapper;
import org.springframework.integration.amqp.support.ManualAckListenerExecutionFailedException;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.handler.advice.ErrorMessageSendingRecoverer;
import org.springframework.integration.json.JsonToObjectTransformer;
import org.springframework.integration.json.ObjectToJsonTransformer;
import org.springframework.integration.mapping.support.JsonHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.transformer.MessageTransformingHandler;
import org.springframework.integration.transformer.Transformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.retry.support.RetryTemplate;

import com.rabbitmq.client.Channel;

/**
 * @author Artem Bilan
 * @author Gary Russell
 *
 * @since 3.0
 */
public class InboundEndpointTests {

	@Test
	public void testInt2809JavaTypePropertiesToAmqp() throws Exception {
		Connection connection = mock(Connection.class);
		doAnswer(invocation -> mock(Channel.class)).when(connection).createChannel(anyBoolean());
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		when(connectionFactory.createConnection()).thenReturn(connection);
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.setAcknowledgeMode(AcknowledgeMode.MANUAL);

		AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(container);
		adapter.setMessageConverter(new Jackson2JsonMessageConverter());

		PollableChannel channel = new QueueChannel();

		adapter.setOutputChannel(channel);
		adapter.setBeanFactory(mock(BeanFactory.class));
		adapter.afterPropertiesSet();

		Object payload = new Foo("bar1");

		Transformer objectToJsonTransformer = new ObjectToJsonTransformer();
		Message<?> jsonMessage = objectToJsonTransformer.transform(new GenericMessage<Object>(payload));

		MessageProperties amqpMessageProperties = new MessageProperties();
		amqpMessageProperties.setDeliveryTag(123L);
		org.springframework.amqp.core.Message amqpMessage =
				new SimpleMessageConverter().toMessage(jsonMessage.getPayload(), amqpMessageProperties);
		DefaultAmqpHeaderMapper.inboundMapper().fromHeadersToRequest(jsonMessage.getHeaders(), amqpMessageProperties);

		ChannelAwareMessageListener listener = (ChannelAwareMessageListener) container.getMessageListener();
		Channel rabbitChannel = mock(Channel.class);
		listener.onMessage(amqpMessage, rabbitChannel);

		Message<?> result = channel.receive(1000);
		assertThat(result.getPayload()).isEqualTo(payload);

		assertThat(result.getHeaders().get(AmqpHeaders.CHANNEL)).isSameAs(rabbitChannel);
		assertThat(result.getHeaders().get(AmqpHeaders.DELIVERY_TAG)).isEqualTo(123L);
	}

	@Test
	public void testInt2809JavaTypePropertiesFromAmqp() throws Exception {
		Connection connection = mock(Connection.class);
		doAnswer(invocation -> mock(Channel.class)).when(connection).createChannel(anyBoolean());
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		when(connectionFactory.createConnection()).thenReturn(connection);
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);

		AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(container);

		PollableChannel channel = new QueueChannel();

		adapter.setOutputChannel(channel);
		adapter.setBeanFactory(mock(BeanFactory.class));
		adapter.afterPropertiesSet();

		Object payload = new Foo("bar1");

		MessageProperties amqpMessageProperties = new MessageProperties();
		org.springframework.amqp.core.Message amqpMessage =
				new Jackson2JsonMessageConverter().toMessage(payload, amqpMessageProperties);

		ChannelAwareMessageListener listener = (ChannelAwareMessageListener) container.getMessageListener();
		listener.onMessage(amqpMessage, null);

		Message<?> receive = channel.receive(1000);

		Message<?> result = new JsonToObjectTransformer().transform(receive);

		assertThat(result.getPayload()).isEqualTo(payload);
	}

	@Test
	public void testMessageConverterJsonHeadersHavePrecedenceOverMessageHeaders() throws Exception {
		Connection connection = mock(Connection.class);
		doAnswer(invocation -> mock(Channel.class)).when(connection).createChannel(anyBoolean());
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		when(connectionFactory.createConnection()).thenReturn(connection);
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.setAcknowledgeMode(AcknowledgeMode.MANUAL);

		DirectChannel channel = new DirectChannel();

		final Channel rabbitChannel = mock(Channel.class);

		channel.subscribe(new MessageTransformingHandler(message -> {
			assertThat(message.getHeaders().get(AmqpHeaders.CHANNEL)).isSameAs(rabbitChannel);
			assertThat(message.getHeaders().get(AmqpHeaders.DELIVERY_TAG)).isEqualTo(123L);
			return MessageBuilder.fromMessage(message)
					.setHeader(JsonHeaders.TYPE_ID, "foo")
					.setHeader(JsonHeaders.CONTENT_TYPE_ID, "bar")
					.setHeader(JsonHeaders.KEY_TYPE_ID, "baz")
					.build();
		}));

		RabbitTemplate rabbitTemplate = spy(new RabbitTemplate());
		rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());

		CountDownLatch sendLatch = new CountDownLatch(1);

		Mockito.doAnswer(invocation -> {
			org.springframework.amqp.core.Message message =
					invocation.getArgument(2);
			Map<String, Object> headers = message.getMessageProperties().getHeaders();
			assertThat(headers.containsKey(JsonHeaders.TYPE_ID.replaceFirst(JsonHeaders.PREFIX, ""))).isTrue();
			assertThat(headers.get(JsonHeaders.TYPE_ID.replaceFirst(JsonHeaders.PREFIX, ""))).isNotEqualTo("foo");
			assertThat(headers.containsKey(JsonHeaders.CONTENT_TYPE_ID.replaceFirst(JsonHeaders.PREFIX, ""))).isFalse();
			assertThat(headers.containsKey(JsonHeaders.KEY_TYPE_ID.replaceFirst(JsonHeaders.PREFIX, ""))).isFalse();
			assertThat(headers.containsKey(JsonHeaders.TYPE_ID)).isFalse();
			assertThat(headers.containsKey(JsonHeaders.KEY_TYPE_ID)).isFalse();
			assertThat(headers.containsKey(JsonHeaders.CONTENT_TYPE_ID)).isFalse();
			sendLatch.countDown();
			return null;
		}).when(rabbitTemplate)
				.send(anyString(), anyString(), any(org.springframework.amqp.core.Message.class), isNull());

		AmqpInboundGateway gateway = new AmqpInboundGateway(container, rabbitTemplate);
		gateway.setMessageConverter(new Jackson2JsonMessageConverter());
		gateway.setRequestChannel(channel);
		gateway.setBeanFactory(mock(BeanFactory.class));
		gateway.setDefaultReplyTo("foo");
		gateway.afterPropertiesSet();


		Object payload = new Foo("bar1");

		MessageProperties amqpMessageProperties = new MessageProperties();
		amqpMessageProperties.setDeliveryTag(123L);
		org.springframework.amqp.core.Message amqpMessage =
				new Jackson2JsonMessageConverter().toMessage(payload, amqpMessageProperties);

		ChannelAwareMessageListener listener = (ChannelAwareMessageListener) container.getMessageListener();
		listener.onMessage(amqpMessage, rabbitChannel);

		assertThat(sendLatch.await(10, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	public void testAdapterConversionError() throws Exception {
		Connection connection = mock(Connection.class);
		doAnswer(invocation -> mock(Channel.class)).when(connection).createChannel(anyBoolean());
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		when(connectionFactory.createConnection()).thenReturn(connection);
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(container);
		QueueChannel outputChannel = new QueueChannel();
		adapter.setOutputChannel(outputChannel);
		QueueChannel errorChannel = new QueueChannel();
		adapter.setErrorChannel(errorChannel);
		adapter.setMessageConverter(new SimpleMessageConverter() {

			@Override
			public Object fromMessage(org.springframework.amqp.core.Message message) throws MessageConversionException {
				throw new MessageConversionException("intended");
			}

		});
		adapter.afterPropertiesSet();
		org.springframework.amqp.core.Message message = mock(org.springframework.amqp.core.Message.class);
		MessageProperties props = new MessageProperties();
		props.setDeliveryTag(42L);
		given(message.getMessageProperties()).willReturn(props);
		((ChannelAwareMessageListener) container.getMessageListener())
				.onMessage(message, null);
		assertThat(outputChannel.receive(0)).isNull();
		Message<?> received = errorChannel.receive(0);
		assertThat(received).isNotNull();
		assertThat(received.getHeaders().get(AmqpMessageHeaderErrorMessageStrategy.AMQP_RAW_MESSAGE)).isNotNull();
		assertThat(received.getPayload().getClass()).isEqualTo(ListenerExecutionFailedException.class);

		container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		Channel channel = mock(Channel.class);
		((ChannelAwareMessageListener) container.getMessageListener())
				.onMessage(message, channel);
		assertThat(outputChannel.receive(0)).isNull();
		received = errorChannel.receive(0);
		assertThat(received).isNotNull();
		assertThat(received.getHeaders().get(AmqpMessageHeaderErrorMessageStrategy.AMQP_RAW_MESSAGE)).isNotNull();
		assertThat(received.getPayload()).isInstanceOf(ManualAckListenerExecutionFailedException.class);
		ManualAckListenerExecutionFailedException ex = (ManualAckListenerExecutionFailedException) received
				.getPayload();
		assertThat(ex.getChannel()).isEqualTo(channel);
		assertThat(ex.getDeliveryTag()).isEqualTo(props.getDeliveryTag());
	}

	@Test
	public void testGatewayConversionError() throws Exception {
		Connection connection = mock(Connection.class);
		doAnswer(invocation -> mock(Channel.class)).when(connection).createChannel(anyBoolean());
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		when(connectionFactory.createConnection()).thenReturn(connection);
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		AmqpInboundGateway adapter = new AmqpInboundGateway(container);
		QueueChannel outputChannel = new QueueChannel();
		adapter.setRequestChannel(outputChannel);
		QueueChannel errorChannel = new QueueChannel();
		adapter.setErrorChannel(errorChannel);
		adapter.setMessageConverter(new MessageConverter() {

			@Override
			public org.springframework.amqp.core.Message toMessage(Object object, MessageProperties messageProperties)
					throws MessageConversionException {
				throw new MessageConversionException("intended");
			}

			@Override
			public Object fromMessage(org.springframework.amqp.core.Message message) throws MessageConversionException {
				throw new MessageConversionException("intended");
			}

		});
		adapter.afterPropertiesSet();
		org.springframework.amqp.core.Message message = mock(org.springframework.amqp.core.Message.class);
		MessageProperties props = new MessageProperties();
		props.setDeliveryTag(42L);
		given(message.getMessageProperties()).willReturn(props);
		((ChannelAwareMessageListener) container.getMessageListener())
				.onMessage(message, null);
		assertThat(outputChannel.receive(0)).isNull();
		Message<?> received = errorChannel.receive(0);
		assertThat(received).isNotNull();
		assertThat(received.getHeaders().get(AmqpMessageHeaderErrorMessageStrategy.AMQP_RAW_MESSAGE)).isNotNull();

		container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		Channel channel = mock(Channel.class);
		((ChannelAwareMessageListener) container.getMessageListener())
				.onMessage(message, channel);
		assertThat(outputChannel.receive(0)).isNull();
		received = errorChannel.receive(0);
		assertThat(received).isNotNull();
		assertThat(received.getHeaders().get(AmqpMessageHeaderErrorMessageStrategy.AMQP_RAW_MESSAGE)).isNotNull();
		assertThat(received.getPayload()).isInstanceOf(ManualAckListenerExecutionFailedException.class);
		ManualAckListenerExecutionFailedException ex = (ManualAckListenerExecutionFailedException) received
				.getPayload();
		assertThat(ex.getChannel()).isEqualTo(channel);
		assertThat(ex.getDeliveryTag()).isEqualTo(props.getDeliveryTag());
	}

	@Test
	public void testRetryWithinOnMessageAdapter() throws Exception {
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		AbstractMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
		AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(container);
		adapter.setOutputChannel(new DirectChannel());
		adapter.setRetryTemplate(new RetryTemplate());
		QueueChannel errors = new QueueChannel();
		ErrorMessageSendingRecoverer recoveryCallback = new ErrorMessageSendingRecoverer(errors);
		recoveryCallback.setErrorMessageStrategy(new AmqpMessageHeaderErrorMessageStrategy());
		adapter.setRecoveryCallback(recoveryCallback);
		adapter.afterPropertiesSet();
		ChannelAwareMessageListener listener = (ChannelAwareMessageListener) container.getMessageListener();
		listener.onMessage(org.springframework.amqp.core.MessageBuilder.withBody("foo".getBytes())
				.andProperties(new MessageProperties()).build(), null);
		Message<?> errorMessage = errors.receive(0);
		assertThat(errorMessage).isNotNull();
		assertThat(errorMessage.getPayload()).isInstanceOf(MessagingException.class);
		MessagingException payload = (MessagingException) errorMessage.getPayload();
		assertThat(payload.getMessage()).contains("Dispatcher has no");
		assertThat(StaticMessageHeaderAccessor.getDeliveryAttempt(payload.getFailedMessage()).get()).isEqualTo(3);
		org.springframework.amqp.core.Message amqpMessage = errorMessage.getHeaders()
			.get(AmqpMessageHeaderErrorMessageStrategy.AMQP_RAW_MESSAGE, org.springframework.amqp.core.Message.class);
		assertThat(amqpMessage).isNotNull();
		assertThat(errors.receive(0)).isNull();
	}

	@Test
	public void testRetryWithinOnMessageGateway() throws Exception {
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		AbstractMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
		AmqpInboundGateway adapter = new AmqpInboundGateway(container);
		adapter.setRequestChannel(new DirectChannel());
		adapter.setRetryTemplate(new RetryTemplate());
		QueueChannel errors = new QueueChannel();
		ErrorMessageSendingRecoverer recoveryCallback = new ErrorMessageSendingRecoverer(errors);
		recoveryCallback.setErrorMessageStrategy(new AmqpMessageHeaderErrorMessageStrategy());
		adapter.setRecoveryCallback(recoveryCallback);
		adapter.afterPropertiesSet();
		ChannelAwareMessageListener listener = (ChannelAwareMessageListener) container.getMessageListener();
		listener.onMessage(org.springframework.amqp.core.MessageBuilder.withBody("foo".getBytes())
				.andProperties(new MessageProperties()).build(), null);
		Message<?> errorMessage = errors.receive(0);
		assertThat(errorMessage).isNotNull();
		assertThat(errorMessage.getPayload()).isInstanceOf(MessagingException.class);
		MessagingException payload = (MessagingException) errorMessage.getPayload();
		assertThat(payload.getMessage()).contains("Dispatcher has no");
		assertThat(StaticMessageHeaderAccessor.getDeliveryAttempt(payload.getFailedMessage()).get()).isEqualTo(3);
		org.springframework.amqp.core.Message amqpMessage = errorMessage.getHeaders()
			.get(AmqpMessageHeaderErrorMessageStrategy.AMQP_RAW_MESSAGE, org.springframework.amqp.core.Message.class);
		assertThat(amqpMessage).isNotNull();
		assertThat(errors.receive(0)).isNull();
	}

	public static class Foo {

		private String bar;

		public Foo() {
		}

		public Foo(String bar) {
			this.bar = bar;
		}

		public String getBar() {
			return bar;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}

			Foo foo = (Foo) o;

			return !(bar != null ? !bar.equals(foo.bar) : foo.bar != null);

		}

		@Override
		public int hashCode() {
			return bar != null ? bar.hashCode() : 0;
		}

	}

}
