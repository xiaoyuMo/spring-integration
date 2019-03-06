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

package org.springframework.integration.aggregator.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.support.LongRunningIntegrationTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

/**
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Artem Bilan
 *
 */
public class AggregatorWithCustomReleaseStrategyTests {

	@ClassRule
	public static LongRunningIntegrationTest longTests = new LongRunningIntegrationTest();

	private static ExecutorService executor = Executors.newCachedThreadPool();

	@AfterClass
	public static void tearDown() {
		executor.shutdownNow();
	}

	@Test
	public void testAggregatorsUnderStressWithConcurrency() throws Exception {
		// this is to be sure  after INT-2502
		for (int i = 0; i < 10; i++) {
			this.validateSequenceSizeHasNoAffectCustomCorrelator();
		}
		for (int i = 0; i < 10; i++) {
			this.validateSequenceSizeHasNoAffectWithSplitter();
		}
	}

	public void validateSequenceSizeHasNoAffectCustomCorrelator() throws Exception {
		AbstractApplicationContext context =
				new ClassPathXmlApplicationContext("aggregator-with-custom-release-strategy.xml", this.getClass());
		final MessageChannel inputChannel = context.getBean("aggregationChannelCustomCorrelation", MessageChannel.class);
		QueueChannel resultChannel = context.getBean("resultChannel", QueueChannel.class);

		final CountDownLatch latch = new CountDownLatch(1800);

		for (int i = 0; i < 600; i++) {
			final int counter = i;
			executor.execute(() -> {
				inputChannel.send(MessageBuilder.withPayload("foo").
						setHeader("correlation", "foo" + counter).build());
				latch.countDown();
			});
			executor.execute(() -> {
				inputChannel.send(MessageBuilder.withPayload("bar").
						setHeader("correlation", "foo" + counter).build());
				latch.countDown();
			});
			executor.execute(() -> {
				inputChannel.send(MessageBuilder.withPayload("baz").
						setHeader("correlation", "foo" + counter).build());
				latch.countDown();
			});
		}

		assertThat(latch.await(120, TimeUnit.SECONDS)).as("Sends failed to complete: " + latch.getCount() + " remain")
				.isTrue();

		Message<?> message = resultChannel.receive(1000);
		int counter = 0;
		while (message != null) {
			counter++;
			message = resultChannel.receive(1000);
		}
		assertThat(counter).isEqualTo(600);
		context.close();
	}

	public void validateSequenceSizeHasNoAffectWithSplitter() throws Exception {
		AbstractApplicationContext context =
				new ClassPathXmlApplicationContext("aggregator-with-custom-release-strategy.xml", this.getClass());
		final MessageChannel inputChannel = context.getBean("in", MessageChannel.class);
		QueueChannel resultChannel = context.getBean("resultChannel", QueueChannel.class);

		final CountDownLatch latch = new CountDownLatch(1800);

		for (int i = 0; i < 600; i++) {
			executor.execute(() -> {
				inputChannel.send(MessageBuilder.withPayload(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8}).build());
				latch.countDown();
			});
			executor.execute(() -> {
				inputChannel.send(MessageBuilder.withPayload(new Integer[]{9, 10, 11, 12, 13, 14, 15, 16}).build());
				latch.countDown();
			});
			executor.execute(() -> {
				inputChannel.send(MessageBuilder.withPayload(new Integer[]{17, 18, 19, 20, 21, 22, 23, 24}).build());
				latch.countDown();
			});
		}

		assertThat(latch.await(60, TimeUnit.SECONDS)).as("Sends failed to complete: " + latch.getCount() + " remain")
				.isTrue();

		Message<?> message = resultChannel.receive(1000);
		int counter = 0;
		while (message != null && ++counter < 7200) {
			message = resultChannel.receive(1000);
		}
		assertThat(counter).isEqualTo(7200);
		context.close();
	}

}
