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

package org.springframework.integration.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.endpoint.PollingConsumer;
import org.springframework.integration.handler.MethodInvokingMessageHandler;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.test.util.TestUtils.TestApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 */
public class MethodInvokingMessageHandlerTests {

	@Test
	public void validMethod() {
		MethodInvokingMessageHandler handler = new MethodInvokingMessageHandler(new TestSink(), "validMethod");
		handler.setBeanFactory(mock(BeanFactory.class));
		handler.handleMessage(new GenericMessage<>("test"));
	}

	@Test
	public void validMethodWithNoArgs() {
		new MethodInvokingMessageHandler(new TestSink(), "validMethodWithNoArgs");
	}

	@Test(expected = MessagingException.class)
	public void methodWithReturnValue() {
		Message<?> message = new GenericMessage<>("test");
		try {
			MethodInvokingMessageHandler handler = new MethodInvokingMessageHandler(new TestSink(),
					"methodWithReturnValue");
			handler.handleMessage(message);
		}
		catch (MessagingException e) {
			assertThat(message).isEqualTo(e.getFailedMessage());
			throw e;
		}
	}

	@Test(expected = IllegalStateException.class)
	public void noMatchingMethodName() {
		new MethodInvokingMessageHandler(new TestSink(), "noSuchMethod");
	}

	@Test
	public void subscription() throws Exception {
		TestApplicationContext context = TestUtils.createTestApplicationContext();
		SynchronousQueue<String> queue = new SynchronousQueue<>();
		TestBean testBean = new TestBean(queue);
		QueueChannel channel = new QueueChannel();
		context.registerChannel("channel", channel);
		Message<String> message = new GenericMessage<>("testing");
		channel.send(message);
		assertThat(queue.poll()).isNull();
		MethodInvokingMessageHandler handler = new MethodInvokingMessageHandler(testBean, "foo");
		handler.setBeanFactory(context);
		PollingConsumer endpoint = new PollingConsumer(channel, handler);
		endpoint.setTrigger(new PeriodicTrigger(10));
		context.registerEndpoint("testEndpoint", endpoint);
		context.refresh();
		String result = queue.poll(2000, TimeUnit.MILLISECONDS);
		assertThat(result).isNotNull();
		assertThat(result).isEqualTo("testing");
		context.close();
	}


	private static class TestBean {

		private final BlockingQueue<String> queue;

		TestBean(BlockingQueue<String> queue) {
			this.queue = queue;
		}

		@SuppressWarnings("unused")
		public void foo(String s) {
			try {
				this.queue.put(s);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

	}


	@SuppressWarnings("unused")
	private static class TestSink {

		private String result;


		TestSink() {
			super();
		}

		public void validMethod(String s) {
		}

		public void validMethodWithNoArgs() {
		}

		public String methodWithReturnValue(String s) {
			return "value";
		}

		public void store(String s) {
			this.result = s;
		}

		public String get() {
			return this.result;
		}

	}

}
