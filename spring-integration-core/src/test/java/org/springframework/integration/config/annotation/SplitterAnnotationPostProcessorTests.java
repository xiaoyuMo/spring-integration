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

package org.springframework.integration.config.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.context.Lifecycle;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Splitter;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.endpoint.AbstractEndpoint;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.test.util.TestUtils.TestApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

/**
 * @author Mark Fisher
 * @author Artem Bilan
 */
public class SplitterAnnotationPostProcessorTests {

	private TestApplicationContext context = TestUtils.createTestApplicationContext();

	private DirectChannel inputChannel = new DirectChannel();

	private QueueChannel outputChannel = new QueueChannel();


	@Before
	public void init() {
		context.registerChannel("input", inputChannel);
		context.registerChannel("output", outputChannel);
	}

	@After
	public void tearDown() {
		this.context.close();
	}

	@Test
	public void testSplitterAnnotation() {
		MessagingAnnotationPostProcessor postProcessor = new MessagingAnnotationPostProcessor();
		postProcessor.setBeanFactory(context.getBeanFactory());
		postProcessor.afterPropertiesSet();
		postProcessor.afterSingletonsInstantiated();
		TestSplitter splitter = new TestSplitter();
		postProcessor.postProcessAfterInitialization(splitter, "testSplitter");
		context.refresh();
		inputChannel.send(new GenericMessage<>("this.is.a.test"));
		Message<?> message1 = outputChannel.receive(500);
		assertThat(message1).isNotNull();
		assertThat(message1.getPayload()).isEqualTo("this");
		Message<?> message2 = outputChannel.receive(500);
		assertThat(message2).isNotNull();
		assertThat(message2.getPayload()).isEqualTo("is");
		Message<?> message3 = outputChannel.receive(500);
		assertThat(message3).isNotNull();
		assertThat(message3.getPayload()).isEqualTo("a");
		Message<?> message4 = outputChannel.receive(500);
		assertThat(message4).isNotNull();
		assertThat(message4.getPayload()).isEqualTo("test");
		assertThat(outputChannel.receive(0)).isNull();

		AbstractEndpoint endpoint = context.getBean(AbstractEndpoint.class);

		assertThat(splitter.isRunning()).isTrue();
		endpoint.stop();
		assertThat(splitter.isRunning()).isFalse();
		endpoint.start();
		assertThat(splitter.isRunning()).isTrue();

		context.stop();
	}


	@MessageEndpoint
	public static class TestSplitter implements Lifecycle {

		private boolean running;

		@Splitter(inputChannel = "input", outputChannel = "output")
		public String[] split(String s) {
			return s.split("\\.");
		}

		@Override
		public void start() {
			this.running = true;
		}

		@Override
		public void stop() {
			this.running = false;
		}

		@Override
		public boolean isRunning() {
			return this.running;
		}

	}

}
