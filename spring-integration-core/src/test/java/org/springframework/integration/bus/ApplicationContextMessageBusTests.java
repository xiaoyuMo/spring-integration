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

package org.springframework.integration.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.endpoint.PollingConsumer;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.test.util.TestUtils.TestApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * @author Mark Fisher
 * @author Artem Bilan
 * @author Andreas Baer
 */
public class ApplicationContextMessageBusTests {

	@Test
	public void endpointRegistrationWithInputChannelReference() {
		TestApplicationContext context = TestUtils.createTestApplicationContext();
		QueueChannel sourceChannel = new QueueChannel();
		QueueChannel targetChannel = new QueueChannel();
		context.registerChannel("sourceChannel", sourceChannel);
		context.registerChannel("targetChannel", targetChannel);
		Message<String> message = MessageBuilder.withPayload("test")
				.setReplyChannelName("targetChannel").build();
		sourceChannel.send(message);
		AbstractReplyProducingMessageHandler handler = new AbstractReplyProducingMessageHandler() {

			@Override
			public Object handleRequestMessage(Message<?> message) {
				return message;
			}
		};
		handler.setBeanFactory(context);
		handler.afterPropertiesSet();
		PollingConsumer endpoint = new PollingConsumer(sourceChannel, handler);
		endpoint.setBeanFactory(mock(BeanFactory.class));
		context.registerEndpoint("testEndpoint", endpoint);
		context.refresh();
		Message<?> result = targetChannel.receive(10000);
		assertThat(result.getPayload()).isEqualTo("test");
		context.close();
	}

	@Test
	public void channelsWithoutHandlers() {
		TestApplicationContext context = TestUtils.createTestApplicationContext();
		QueueChannel sourceChannel = new QueueChannel();
		context.registerChannel("sourceChannel", sourceChannel);
		sourceChannel.send(new GenericMessage<>("test"));
		QueueChannel targetChannel = new QueueChannel();
		context.registerChannel("targetChannel", targetChannel);
		context.refresh();
		Message<?> result = targetChannel.receive(10);
		assertThat(result).isNull();
		context.close();
	}

	@Test
	public void autoDetectionWithApplicationContext() {
		ClassPathXmlApplicationContext context =
				new ClassPathXmlApplicationContext("messageBusTests.xml", this.getClass());
		context.start();
		PollableChannel sourceChannel = (PollableChannel) context.getBean("sourceChannel");
		sourceChannel.send(new GenericMessage<>("test"));
		PollableChannel targetChannel = (PollableChannel) context.getBean("targetChannel");
		Message<?> result = targetChannel.receive(10000);
		assertThat(result.getPayload()).isEqualTo("test");
		context.close();
	}

	@Test
	public void exactlyOneConsumerReceivesPointToPointMessage() {
		TestApplicationContext context = TestUtils.createTestApplicationContext();
		QueueChannel inputChannel = new QueueChannel();
		QueueChannel outputChannel1 = new QueueChannel();
		QueueChannel outputChannel2 = new QueueChannel();
		AbstractReplyProducingMessageHandler handler1 = new AbstractReplyProducingMessageHandler() {

			@Override
			public Object handleRequestMessage(Message<?> message) {
				return message;
			}
		};
		AbstractReplyProducingMessageHandler handler2 = new AbstractReplyProducingMessageHandler() {

			@Override
			public Object handleRequestMessage(Message<?> message) {
				return message;
			}
		};
		context.registerChannel("input", inputChannel);
		context.registerChannel("output1", outputChannel1);
		context.registerChannel("output2", outputChannel2);
		handler1.setOutputChannel(outputChannel1);
		handler2.setOutputChannel(outputChannel2);
		PollingConsumer endpoint1 = new PollingConsumer(inputChannel, handler1);
		endpoint1.setBeanFactory(mock(BeanFactory.class));
		PollingConsumer endpoint2 = new PollingConsumer(inputChannel, handler2);
		endpoint2.setBeanFactory(mock(BeanFactory.class));
		context.registerEndpoint("testEndpoint1", endpoint1);
		context.registerEndpoint("testEndpoint2", endpoint2);
		context.refresh();
		inputChannel.send(new GenericMessage<>("testing"));
		Message<?> message1 = outputChannel1.receive(10000);
		Message<?> message2 = outputChannel2.receive(0);
		context.close();
		assertThat(message1 == null ^ message2 == null).as("exactly one message should be null").isTrue();
	}

	@Test
	public void bothConsumersReceivePublishSubscribeMessage() throws InterruptedException {
		TestApplicationContext context = TestUtils.createTestApplicationContext();
		PublishSubscribeChannel inputChannel = new PublishSubscribeChannel();
		QueueChannel outputChannel1 = new QueueChannel();
		QueueChannel outputChannel2 = new QueueChannel();
		final CountDownLatch latch = new CountDownLatch(2);
		AbstractReplyProducingMessageHandler handler1 = new AbstractReplyProducingMessageHandler() {

			@Override
			public Object handleRequestMessage(Message<?> message) {
				latch.countDown();
				return message;
			}
		};
		AbstractReplyProducingMessageHandler handler2 = new AbstractReplyProducingMessageHandler() {

			@Override
			public Object handleRequestMessage(Message<?> message) {
				latch.countDown();
				return message;
			}
		};
		context.registerChannel("input", inputChannel);
		context.registerChannel("output1", outputChannel1);
		context.registerChannel("output2", outputChannel2);
		handler1.setOutputChannel(outputChannel1);
		handler2.setOutputChannel(outputChannel2);
		EventDrivenConsumer endpoint1 = new EventDrivenConsumer(inputChannel, handler1);
		EventDrivenConsumer endpoint2 = new EventDrivenConsumer(inputChannel, handler2);
		context.registerEndpoint("testEndpoint1", endpoint1);
		context.registerEndpoint("testEndpoint2", endpoint2);
		context.refresh();
		inputChannel.send(new GenericMessage<String>("testing"));
		latch.await(500, TimeUnit.MILLISECONDS);
		assertThat(latch.getCount()).as("both handlers should have been invoked").isEqualTo(0);
		Message<?> message1 = outputChannel1.receive(500);
		Message<?> message2 = outputChannel2.receive(500);
		context.close();
		assertThat(message1).as("both handlers should have replied to the message").isNotNull();
		assertThat(message2).as("both handlers should have replied to the message").isNotNull();
	}

	@Test
	public void errorChannelWithFailedDispatch() throws InterruptedException {
		TestApplicationContext context = TestUtils.createTestApplicationContext();
		QueueChannel errorChannel = new QueueChannel();
		QueueChannel outputChannel = new QueueChannel();
		context.registerChannel("errorChannel", errorChannel);
		CountDownLatch latch = new CountDownLatch(1);
		SourcePollingChannelAdapter channelAdapter = new SourcePollingChannelAdapter();
		channelAdapter.setSource(new FailingSource(latch));
		PollerMetadata pollerMetadata = new PollerMetadata();
		pollerMetadata.setTrigger(new PeriodicTrigger(1000));
		channelAdapter.setOutputChannel(outputChannel);
		context.registerEndpoint("testChannel", channelAdapter);
		context.refresh();
		latch.await(2000, TimeUnit.MILLISECONDS);
		Message<?> message = errorChannel.receive(5000);
		context.close();
		assertThat(outputChannel.receive(100)).isNull();
		assertThat(message).as("message should not be null").isNotNull();
		assertThat(message instanceof ErrorMessage).isTrue();
		Throwable exception = ((ErrorMessage) message).getPayload();
		assertThat(exception.getCause().getMessage()).isEqualTo("intentional test failure");
	}

	@Test
	public void consumerSubscribedToErrorChannel() throws InterruptedException {
		TestApplicationContext context = TestUtils.createTestApplicationContext();
		QueueChannel errorChannel = new QueueChannel();
		context.registerChannel(IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME, errorChannel);
		final CountDownLatch latch = new CountDownLatch(1);
		AbstractReplyProducingMessageHandler handler = new AbstractReplyProducingMessageHandler() {

			@Override
			public Object handleRequestMessage(Message<?> message) {
				latch.countDown();
				return null;
			}
		};
		PollingConsumer endpoint = new PollingConsumer(errorChannel, handler);
		endpoint.setBeanFactory(mock(BeanFactory.class));
		context.registerEndpoint("testEndpoint", endpoint);
		context.refresh();
		errorChannel.send(new ErrorMessage(new RuntimeException("test-exception")));
		latch.await(1000, TimeUnit.MILLISECONDS);
		assertThat(latch.getCount()).as("handler should have received error message").isEqualTo(0);
		context.close();
	}


	private static class FailingSource implements MessageSource<Object> {

		private final CountDownLatch latch;

		FailingSource(CountDownLatch latch) {
			this.latch = latch;
		}

		@Override
		public Message<Object> receive() {
			latch.countDown();
			throw new RuntimeException("intentional test failure");
		}

	}

}
