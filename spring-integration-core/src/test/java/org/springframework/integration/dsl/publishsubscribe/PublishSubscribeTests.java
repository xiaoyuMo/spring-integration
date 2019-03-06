/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.integration.dsl.publishsubscribe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Artem Bilan
 * @author Gary Russell
 *
 * @since 5.0
 */
@RunWith(SpringRunner.class)
public class PublishSubscribeTests {

	@Autowired
	@Qualifier("pubSubFlow.input")
	private MessageChannel inputChannel;

	@Autowired
	private List<Integer> subscribersOrderedCall;

	@Test
	public void executeFirstFlow() {
		this.inputChannel.send(new GenericMessage<>("Test"));
		assertThat(this.subscribersOrderedCall).containsExactly(0, 1, 2, 3, 4, 5);
	}

	@Configuration
	@EnableIntegration
	static class PubSubBugTestContext {

		@Bean
		public List<Integer> subscribersOrderedCall() {
			return new LinkedList<>();
		}

		@Bean
		public Consumer<String> subscriberConsumerBean() {
			return s -> subscribersOrderedCall().add(2);
		}

		@Bean
		public MessageHandler subscriberMessageHandlerBean() {
			return s -> subscribersOrderedCall().add(3);
		}

		@Bean
		public IntegrationFlow pubSubFlow() {
			return f -> f
					.publishSubscribeChannel(c -> c
							.subscribe(sf -> sf
									.handle(m -> subscribersOrderedCall().add(0)))
							.subscribe(sf -> sf
									.<String>handle((p, h) -> {
										subscribersOrderedCall().add(1);
										return null;
									}))
							.subscribe(sf -> sf
									.handle(subscriberConsumerBean()))
							.subscribe(sf -> sf
									.handle(subscriberMessageHandlerBean()))
							.subscribe(sf -> sf
									.channel("secondInlineSubscriberChannel")
									.handle(m -> subscribersOrderedCall().add(4)))
					)
					.<String>handle((p, h) -> {
						subscribersOrderedCall().add(5);
						return null;
					});
		}

	}

}
