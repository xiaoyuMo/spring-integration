/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.integration.channel.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.ConsumerEndpointFactoryBean;
import org.springframework.integration.endpoint.ReactiveStreamsConsumer;
import org.springframework.integration.handler.MethodInvokingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;

/**
 * @author Artem Bilan
 *
 * @since 5.0
 */
public class ReactiveStreamsConsumerTests {

	@Test
	public void testReactiveStreamsConsumerFluxMessageChannel() throws InterruptedException {
		FluxMessageChannel testChannel = new FluxMessageChannel();

		List<Message<?>> result = new LinkedList<>();
		CountDownLatch stopLatch = new CountDownLatch(2);

		MessageHandler messageHandler = m -> {
			result.add(m);
			stopLatch.countDown();
		};

		MessageHandler testSubscriber = new MethodInvokingMessageHandler(messageHandler, (String) null);
		((MethodInvokingMessageHandler) testSubscriber).setBeanFactory(mock(BeanFactory.class));
		ReactiveStreamsConsumer reactiveConsumer = new ReactiveStreamsConsumer(testChannel, testSubscriber);
		reactiveConsumer.setBeanFactory(mock(BeanFactory.class));
		reactiveConsumer.afterPropertiesSet();
		reactiveConsumer.start();

		Message<?> testMessage = new GenericMessage<>("test");
		testChannel.send(testMessage);

		reactiveConsumer.stop();

		try {
			testChannel.send(testMessage);
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(MessageDeliveryException.class);
			assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
			assertThat(e.getMessage()).contains("doesn't have subscribers to accept messages");
		}

		reactiveConsumer.start();

		Message<?> testMessage2 = new GenericMessage<>("test2");
		testChannel.send(testMessage2);

		assertThat(stopLatch.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(result).containsExactly(testMessage, testMessage2);
	}


	@Test
	@SuppressWarnings("unchecked")
	public void testReactiveStreamsConsumerDirectChannel() throws InterruptedException {
		DirectChannel testChannel = new DirectChannel();

		Subscriber<Message<?>> testSubscriber = (Subscriber<Message<?>>) Mockito.mock(Subscriber.class);

		BlockingQueue<Message<?>> messages = new LinkedBlockingQueue<>();

		willAnswer(i -> {
			messages.put(i.getArgument(0));
			return null;
		})
				.given(testSubscriber)
				.onNext(any(Message.class));

		ReactiveStreamsConsumer reactiveConsumer = new ReactiveStreamsConsumer(testChannel, testSubscriber);
		reactiveConsumer.setBeanFactory(mock(BeanFactory.class));
		reactiveConsumer.afterPropertiesSet();
		reactiveConsumer.start();

		Message<?> testMessage = new GenericMessage<>("test");
		testChannel.send(testMessage);

		ArgumentCaptor<Subscription> subscriptionArgumentCaptor = ArgumentCaptor.forClass(Subscription.class);
		verify(testSubscriber).onSubscribe(subscriptionArgumentCaptor.capture());
		Subscription subscription = subscriptionArgumentCaptor.getValue();

		subscription.request(1);

		Message<?> message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage);

		reactiveConsumer.stop();

		try {
			testChannel.send(testMessage);
			fail("MessageDeliveryException");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(MessageDeliveryException.class);
		}

		reactiveConsumer.start();

		subscription.request(1);

		testMessage = new GenericMessage<>("test2");

		testChannel.send(testMessage);

		message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage);

		verify(testSubscriber, never()).onError(any(Throwable.class));
		verify(testSubscriber, never()).onComplete();

		assertThat(messages.isEmpty()).isTrue();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReactiveStreamsConsumerPollableChannel() throws InterruptedException {
		QueueChannel testChannel = new QueueChannel();

		Subscriber<Message<?>> testSubscriber = (Subscriber<Message<?>>) Mockito.mock(Subscriber.class);

		BlockingQueue<Message<?>> messages = new LinkedBlockingQueue<>();

		willAnswer(i -> {
			messages.put(i.getArgument(0));
			return null;
		})
				.given(testSubscriber)
				.onNext(any(Message.class));

		ReactiveStreamsConsumer reactiveConsumer = new ReactiveStreamsConsumer(testChannel, testSubscriber);
		reactiveConsumer.setBeanFactory(mock(BeanFactory.class));
		reactiveConsumer.afterPropertiesSet();
		reactiveConsumer.start();

		Message<?> testMessage = new GenericMessage<>("test");
		testChannel.send(testMessage);

		ArgumentCaptor<Subscription> subscriptionArgumentCaptor = ArgumentCaptor.forClass(Subscription.class);
		verify(testSubscriber).onSubscribe(subscriptionArgumentCaptor.capture());
		Subscription subscription = subscriptionArgumentCaptor.getValue();

		subscription.request(1);

		Message<?> message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage);

		reactiveConsumer.stop();


		testChannel.send(testMessage);

		reactiveConsumer.start();

		verify(testSubscriber, times(2)).onSubscribe(subscriptionArgumentCaptor.capture());
		subscription = subscriptionArgumentCaptor.getValue();

		subscription.request(2);

		Message<?> testMessage2 = new GenericMessage<>("test2");

		testChannel.send(testMessage2);

		message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage);

		message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage2);

		verify(testSubscriber, never()).onError(any(Throwable.class));
		verify(testSubscriber, never()).onComplete();

		assertThat(messages.isEmpty()).isTrue();
	}

	@Test
	public void testReactiveStreamsConsumerViaConsumerEndpointFactoryBean() throws Exception {
		FluxMessageChannel testChannel = new FluxMessageChannel();

		List<Message<?>> result = new LinkedList<>();
		CountDownLatch stopLatch = new CountDownLatch(3);

		MessageHandler messageHandler = m -> {
			result.add(m);
			stopLatch.countDown();
		};

		ConsumerEndpointFactoryBean endpointFactoryBean = new ConsumerEndpointFactoryBean();
		endpointFactoryBean.setBeanFactory(mock(ConfigurableBeanFactory.class));
		endpointFactoryBean.setInputChannel(testChannel);
		endpointFactoryBean.setHandler(messageHandler);
		endpointFactoryBean.setBeanName("reactiveConsumer");
		endpointFactoryBean.afterPropertiesSet();
		endpointFactoryBean.start();

		Message<?> testMessage = new GenericMessage<>("test");
		testChannel.send(testMessage);

		endpointFactoryBean.stop();

		try {
			testChannel.send(testMessage);
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(MessageDeliveryException.class);
			assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
			assertThat(e.getMessage()).contains("doesn't have subscribers to accept messages");
		}

		endpointFactoryBean.start();

		Message<?> testMessage2 = new GenericMessage<>("test2");

		testChannel.send(testMessage2);
		testChannel.send(testMessage2);

		assertThat(stopLatch.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(result.size()).isEqualTo(3);
		assertThat(result).containsExactly(testMessage, testMessage2, testMessage2);
	}

}
