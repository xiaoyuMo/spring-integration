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

package org.springframework.integration.mongodb.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.handler.DelayHandler;
import org.springframework.integration.mongodb.rules.MongoDbAvailable;
import org.springframework.integration.mongodb.rules.MongoDbAvailableTests;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.support.LongRunningIntegrationTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author Artem Bilan
 *
 * @since 3.0
 */
public class DelayerHandlerRescheduleIntegrationTests extends MongoDbAvailableTests {

	public static final String DELAYER_ID = "delayerWithMongoMS";

	@Rule
	public LongRunningIntegrationTest longTests = new LongRunningIntegrationTest();

	@Test
	@MongoDbAvailable
	public void testWithMongoDbMessageStore() throws Exception {
		testDelayerHandlerRescheduleWithMongoDbMessageStore("DelayerHandlerRescheduleIntegrationTests-context.xml");
	}

	@Test
	@MongoDbAvailable
	public void testWithConfigurableMongoDbMessageStore() throws Exception {
		testDelayerHandlerRescheduleWithMongoDbMessageStore("DelayerHandlerRescheduleIntegrationConfigurableTests-context.xml");
	}

	@SuppressWarnings("unchecked")
	private void testDelayerHandlerRescheduleWithMongoDbMessageStore(String config) throws Exception {
		AbstractApplicationContext context = new ClassPathXmlApplicationContext(config, this.getClass());
		MessageChannel input = context.getBean("input", MessageChannel.class);
		MessageGroupStore messageStore = context.getBean("messageStore", MessageGroupStore.class);

		String delayerMessageGroupId = DELAYER_ID + ".messageGroupId";

		messageStore.removeMessageGroup(delayerMessageGroupId);

		Message<String> message1 = MessageBuilder.withPayload("test1").build();
		input.send(message1);
		input.send(MessageBuilder.withPayload("test2").build());

		// Emulate restart and check DB state before next start
		// Interrupt taskScheduler as quickly as possible
		ThreadPoolTaskScheduler taskScheduler =
				(ThreadPoolTaskScheduler) IntegrationContextUtils.getTaskScheduler(context);
		taskScheduler.shutdown();
		taskScheduler.getScheduledExecutor().awaitTermination(10, TimeUnit.SECONDS);

		assertThat(messageStore.messageGroupSize(delayerMessageGroupId)).isEqualTo(2);

		MessageGroup messageGroup = messageStore.getMessageGroup(delayerMessageGroupId);
		Iterator<Message<?>> iterator = messageGroup.getMessages().iterator();
		Message<?> messageInStore = iterator.next();
		Object payload = messageInStore.getPayload();

		// INT-3049
		assertThat(payload instanceof DelayHandler.DelayedMessageWrapper).isTrue();

		Message<String> original1 = (Message<String>) ((DelayHandler.DelayedMessageWrapper) payload).getOriginal();
		messageInStore = iterator.next();
		Message<String> original2 = (Message<String>) ((DelayHandler.DelayedMessageWrapper) messageInStore.getPayload())
				.getOriginal();
		assertThat(message1).isIn(original1, original2);

		context.close();

		context.refresh();

		PollableChannel output = context.getBean("output", PollableChannel.class);

		Message<?> message = output.receive(20000);
		assertThat(message).isNotNull();

		Object payload1 = message.getPayload();

		message = output.receive(20000);
		assertThat(message).isNotNull();
		Object payload2 = message.getPayload();
		assertThat(payload2).isNotSameAs(payload1);

		messageStore = context.getBean("messageStore", MessageGroupStore.class);

		assertThat(messageStore.messageGroupSize(delayerMessageGroupId)).isEqualTo(0);
		context.close();
	}

}
