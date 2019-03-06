/*
 * Copyright 2007-2019 the original author or authors.
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

package org.springframework.integration.gemfire.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.Scope;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.history.MessageHistory;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.SimpleMessageGroup;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import junit.framework.AssertionFailedError;

/**
 * @author Oleg Zhurakousky
 * @author David Turanski
 * @author Gary Russell
 * @author Artem Bilan
 *
 */
public class GemfireGroupStoreTests {

	private static CacheFactoryBean cacheFactoryBean;

	public static Region<Object, Object> region;

	@Test
	public void testNonExistingEmptyMessageGroup() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);
		MessageGroup messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup).isNotNull();
		assertThat(messageGroup instanceof SimpleMessageGroup).isTrue();
		assertThat(messageGroup.size()).isEqualTo(0);
	}

	@Test
	public void testMessageGroupWithAddedMessage() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);
		MessageGroup messageGroup = store.getMessageGroup(1);
		Message<?> message = new GenericMessage<String>("Hello");
		messageGroup = store.addMessageToGroup(1, message);
		assertThat(messageGroup.size()).isEqualTo(1);

		// make sure the store is properly rebuild from Gemfire
		store = new GemfireMessageStore(region);

		messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup.size()).isEqualTo(1);
	}

	@Test
	public void testRemoveMessageFromTheGroup() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);
		MessageGroup messageGroup = store.getMessageGroup(1);
		Message<?> message = new GenericMessage<String>("2");

		messageGroup = store.addMessageToGroup(messageGroup.getGroupId(), new GenericMessage<String>("1"));
		messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup.size()).isEqualTo(1);
		Thread.sleep(1); //since it adds to a local region some times CREATED_DATE ends up to be the same
		// Unrealistic in a real scenario

		messageGroup = store.addMessageToGroup(messageGroup.getGroupId(), message);
		messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup.size()).isEqualTo(2);
		Thread.sleep(1);

		messageGroup = store.addMessageToGroup(messageGroup.getGroupId(), new GenericMessage<String>("3"));
		messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup.size()).isEqualTo(3);

		store.removeMessagesFromGroup(messageGroup.getGroupId(), message);
		messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup.size()).isEqualTo(2);

		// make sure the store is properly rebuild from Gemfire
		store = new GemfireMessageStore(region);

		messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup.size()).isEqualTo(2);

	}

	@Test
	public void testRemoveMessageGroup() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);
		MessageGroup messageGroup = store.getMessageGroup(1);
		Message<?> message = new GenericMessage<String>("Hello");
		messageGroup = store.addMessageToGroup(messageGroup.getGroupId(), message);
		assertThat(messageGroup.size()).isEqualTo(1);

		store.removeMessageGroup(1);
		MessageGroup messageGroupA = store.getMessageGroup(1);
		assertThat(messageGroupA).isNotSameAs(messageGroup);
		assertThat(messageGroupA.getMessages().size()).isEqualTo(0);
		assertThat(messageGroupA.size()).isEqualTo(0);

		// make sure the store is properly rebuild from Gemfire
		store = new GemfireMessageStore(region);

		messageGroup = store.getMessageGroup(1);

		assertThat(messageGroup.getMessages().size()).isEqualTo(0);
		assertThat(messageGroup.size()).isEqualTo(0);
	}

	@Test
	public void testRemoveNonExistingMessageFromTheGroup() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);
		MessageGroup messageGroup = store.getMessageGroup(1);
		store.addMessagesToGroup(messageGroup.getGroupId(), new GenericMessage<String>("1"));
		store.removeMessagesFromGroup(1, new GenericMessage<String>("2"));
	}

	@Test
	public void testRemoveNonExistingMessageFromNonExistingTheGroup() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);
		store.removeMessagesFromGroup(1, new GenericMessage<String>("2"));
	}

	@Test
	public void testCompleteMessageGroup() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);
		MessageGroup messageGroup = store.getMessageGroup(1);
		Message<?> messageToMark = new GenericMessage<String>("1");
		store.addMessagesToGroup(messageGroup.getGroupId(), messageToMark);
		store.completeGroup(messageGroup.getGroupId());
		messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup.isComplete()).isTrue();
	}

	@Test
	public void testLastReleasedSequenceNumber() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);
		MessageGroup messageGroup = store.getMessageGroup(1);
		Message<?> messageToMark = new GenericMessage<String>("1");
		store.addMessagesToGroup(messageGroup.getGroupId(), messageToMark);
		store.setLastReleasedSequenceNumberForGroup(messageGroup.getGroupId(), 5);
		messageGroup = store.getMessageGroup(1);
		assertThat(messageGroup.getLastReleasedMessageSequenceNumber()).isEqualTo(5);
	}

	@Test
	public void testMultipleInstancesOfGroupStore() throws Exception {
		GemfireMessageStore store1 = new GemfireMessageStore(region);

		GemfireMessageStore store2 = new GemfireMessageStore(region);

		Message<?> message = new GenericMessage<String>("1");
		store1.addMessagesToGroup(1, message);
		MessageGroup messageGroup = store2.addMessageToGroup(1, new GenericMessage<String>("2"));

		assertThat(messageGroup.getMessages().size()).isEqualTo(2);

		GemfireMessageStore store3 = new GemfireMessageStore(region);

		store3.removeMessagesFromGroup(1, message);
		messageGroup = store3.getMessageGroup(1);

		assertThat(messageGroup.getMessages().size()).isEqualTo(1);
	}

	@Test
	public void testWithMessageHistory() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(region);

		store.getMessageGroup(1);

		Message<?> message = new GenericMessage<String>("Hello");
		DirectChannel fooChannel = new DirectChannel();
		fooChannel.setBeanName("fooChannel");
		DirectChannel barChannel = new DirectChannel();
		barChannel.setBeanName("barChannel");

		message = MessageHistory.write(message, fooChannel);
		message = MessageHistory.write(message, barChannel);
		store.addMessagesToGroup(1, message);

		message = store.getMessageGroup(1).getMessages().iterator().next();

		MessageHistory messageHistory = MessageHistory.read(message);
		assertThat(messageHistory).isNotNull();
		assertThat(messageHistory.size()).isEqualTo(2);
		Properties fooChannelHistory = messageHistory.get(0);
		assertThat(fooChannelHistory.get("name")).isEqualTo("fooChannel");
		assertThat(fooChannelHistory.get("type")).isEqualTo("channel");
	}

	@Test
	public void testIteratorOfMessageGroups() throws Exception {
		GemfireMessageStore store1 = new GemfireMessageStore(region);
		GemfireMessageStore store2 = new GemfireMessageStore(region);

		store1.addMessagesToGroup(1, new GenericMessage<String>("1"));
		store2.addMessagesToGroup(2, new GenericMessage<String>("2"));
		store1.addMessagesToGroup(3, new GenericMessage<String>("3"), new GenericMessage<String>("3A"));

		Iterator<MessageGroup> messageGroups = store1.iterator();
		int counter = 0;
		while (messageGroups.hasNext()) {
			messageGroups.next();
			counter++;
		}
		assertThat(counter).isEqualTo(3);

		store2.removeMessageGroup(3);

		messageGroups = store1.iterator();
		counter = 0;
		while (messageGroups.hasNext()) {
			messageGroups.next();
			counter++;
		}
		assertThat(counter).isEqualTo(2);
	}

	@Test
	@Ignore
	public void testConcurrentModifications() throws Exception {

		final GemfireMessageStore store1 = new GemfireMessageStore(region);
		final GemfireMessageStore store2 = new GemfireMessageStore(region);

		final Message<?> message = new GenericMessage<String>("1");

		ExecutorService executor = null;

		final List<Object> failures = new ArrayList<Object>();

		for (int i = 0; i < 100; i++) {
			executor = Executors.newCachedThreadPool();

			executor.execute(() -> {
				MessageGroup group = store1.addMessageToGroup(1, message);
				if (group.getMessages().size() != 1) {
					failures.add("ADD");
					throw new AssertionFailedError("Failed on ADD");
				}
			});
			executor.execute(() -> {
				store2.removeMessagesFromGroup(1, message);
				MessageGroup group = store2.getMessageGroup(1);
				if (group.getMessages().size() != 0) {
					failures.add("REMOVE");
					throw new AssertionFailedError("Failed on Remove");
				}
			});

			executor.shutdown();
			executor.awaitTermination(10, TimeUnit.SECONDS);
			store2.removeMessagesFromGroup(1, message); // ensures that if ADD thread executed after REMOVE, the store is empty for the next cycle
		}
		assertThat(failures.size() == 0).isTrue();
	}

	@Test
	public void testWithAggregatorWithShutdown() {

		ClassPathXmlApplicationContext context1 = new ClassPathXmlApplicationContext("gemfire-aggregator-config.xml",
				this.getClass());
		MessageChannel input = context1.getBean("inputChannel", MessageChannel.class);
		QueueChannel output = context1.getBean("outputChannel", QueueChannel.class);

		Message<?> m1 = MessageBuilder.withPayload("1").setSequenceNumber(1).setSequenceSize(3).setCorrelationId(1)
				.build();
		Message<?> m2 = MessageBuilder.withPayload("2").setSequenceNumber(2).setSequenceSize(3).setCorrelationId(1)
				.build();
		input.send(m1);
		assertThat(output.receive(1000)).isNull();
		input.send(m2);
		assertThat(output.receive(1000)).isNull();

		ClassPathXmlApplicationContext context2 = new ClassPathXmlApplicationContext("gemfire-aggregator-config-a.xml",
				this.getClass());
		MessageChannel inputA = context2.getBean("inputChannel", MessageChannel.class);
		QueueChannel outputA = context2.getBean("outputChannel", QueueChannel.class);

		Message<?> m3 = MessageBuilder.withPayload("3").setSequenceNumber(3).setSequenceSize(3).setCorrelationId(1)
				.build();
		inputA.send(m3);
		assertThat(outputA.receive(1000)).isNotNull();
		context1.close();
		context2.close();
	}

	@Test
	public void testQueue() throws Exception {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("gemfire-queue-config.xml",
				this.getClass());

		QueueChannel gemfireQueue = context.getBean("gemfireQueue", QueueChannel.class);
		QueueChannel outputQueue = context.getBean("outputQueue", QueueChannel.class);

		for (int i = 0; i < 20; i++) {
			gemfireQueue.send(new GenericMessage<String>("Hello"));
			Thread.sleep(1);
		}
		for (int i = 0; i < 20; i++) {
			assertThat(outputQueue.receive(5000)).isNotNull();
		}
		assertThat(outputQueue.receive(1)).isNull();
		context.close();
	}

	@Before
	public void prepare() {
		if (region != null) {
			region.clear();
		}
	}

	@BeforeClass
	public static void init() throws Exception {
		cacheFactoryBean = new CacheFactoryBean();
		cacheFactoryBean.afterPropertiesSet();
		Cache cache = (Cache) cacheFactoryBean.getObject();
		region = cache.createRegionFactory().setScope(Scope.LOCAL).create("sig-tests");
	}

	@AfterClass
	public static void cleanup() throws Exception {
		if (region != null) {
			region.close();
		}
		if (cacheFactoryBean != null) {
			cacheFactoryBean.destroy();
		}
	}

}
