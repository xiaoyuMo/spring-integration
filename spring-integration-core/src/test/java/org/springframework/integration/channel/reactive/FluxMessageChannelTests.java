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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.MessageChannelReactiveUtils;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import reactor.core.publisher.Flux;

/**
 * @author Artem Bilan
 *
 * @since 5.0
 */
@RunWith(SpringRunner.class)
@DirtiesContext
public class FluxMessageChannelTests {

	@Autowired
	private MessageChannel fluxMessageChannel;

	@Autowired
	private MessageChannel queueChannel;

	@Autowired
	private PollableChannel errorChannel;

	@Autowired
	private IntegrationFlowContext integrationFlowContext;

	@Test
	public void testFluxMessageChannel() {
		QueueChannel replyChannel = new QueueChannel();

		for (int i = 0; i < 10; i++) {
			this.fluxMessageChannel.send(MessageBuilder.withPayload(i).setReplyChannel(replyChannel).build());
		}

		for (int i = 0; i < 9; i++) {
			Message<?> receive = replyChannel.receive(10000);
			assertThat(receive).isNotNull();
			assertThat(receive.getPayload()).isIn("0", "1", "2", "3", "4", "6", "7", "8", "9");
		}
		assertThat(replyChannel.receive(0)).isNull();

		Message<?> error = this.errorChannel.receive(0);
		assertThat(error).isNotNull();
		assertThat(((MessagingException) error.getPayload()).getFailedMessage().getPayload()).isEqualTo(5);
	}

	@Test
	public void testMessageChannelReactiveAdaptation() throws InterruptedException {
		CountDownLatch done = new CountDownLatch(2);
		List<String> results = new ArrayList<>();

		Flux.from(MessageChannelReactiveUtils.<String>toPublisher(this.queueChannel))
				.map(Message::getPayload)
				.map(String::toUpperCase)
				.doOnNext(results::add)
				.subscribe(v -> done.countDown());

		this.queueChannel.send(new GenericMessage<>("foo"));
		this.queueChannel.send(new GenericMessage<>("bar"));

		assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(results).containsExactly("FOO", "BAR");
	}

	@Test
	public void testFluxMessageChannelCleanUp() throws InterruptedException {
		FluxMessageChannel flux = MessageChannels.flux().get();

		CountDownLatch finishLatch = new CountDownLatch(1);

		IntegrationFlow testFlow = f -> f
				.<String>split(__ -> Flux.fromStream(IntStream.range(0, 100).boxed()), null)
				.channel(flux)
				.aggregate(a -> a.releaseStrategy(m -> m.size() == 100))
				.handle(__ -> finishLatch.countDown());

		IntegrationFlowContext.IntegrationFlowRegistration flowRegistration =
				this.integrationFlowContext.registration(testFlow)
						.register();

		flowRegistration.getInputChannel().send(new GenericMessage<>("foo"));

		assertThat(finishLatch.await(10, TimeUnit.SECONDS)).isTrue();

		assertThat(TestUtils.getPropertyValue(flux, "publishers", Map.class).isEmpty()).isTrue();

		flowRegistration.destroy();
	}

	@Configuration
	@EnableIntegration
	public static class TestConfiguration {

		@Bean
		public QueueChannel errorChannel() {
			return new QueueChannel();
		}

		@Bean
		public MessageChannel fluxMessageChannel() {
			return new FluxMessageChannel();
		}

		@ServiceActivator(inputChannel = "fluxMessageChannel")
		public String handle(int payload) {
			if (payload == 5) {
				throw new IllegalStateException("intentional");
			}
			return "" + payload;
		}

		@Bean
		public MessageChannel queueChannel() {
			return new QueueChannel();
		}

	}

}
