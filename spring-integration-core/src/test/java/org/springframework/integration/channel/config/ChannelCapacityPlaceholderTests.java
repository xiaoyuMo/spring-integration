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

package org.springframework.integration.channel.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.PriorityChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class ChannelCapacityPlaceholderTests {

	@Autowired
	private ApplicationContext context;

	@Test
	public void verifyCapacityValueChanges() {
		QueueChannel channel = context.getBean("channel", QueueChannel.class);
		assertThat(channel).isNotNull();
		assertThat(channel.getRemainingCapacity()).isEqualTo(99);
		channel.send(MessageBuilder.withPayload("test1").build());
		channel.send(MessageBuilder.withPayload("test2").build());
		assertThat(channel.getRemainingCapacity()).isEqualTo(97);
		assertThat(channel.receive(0)).isNotNull();
		assertThat(channel.getRemainingCapacity()).isEqualTo(98);
	}

	@Test
	public void testCapacityOnPriorityChannel() {
		PriorityChannel channel = context.getBean("priorityChannel", PriorityChannel.class);
		assertThat(channel).isNotNull();
		assertThat(channel.getRemainingCapacity()).isEqualTo(99);
		channel.send(MessageBuilder.withPayload("test1").build());
		channel.send(MessageBuilder.withPayload("test2").build());
		assertThat(channel.getRemainingCapacity()).isEqualTo(97);
		assertThat(channel.receive(0)).isNotNull();
		assertThat(channel.getRemainingCapacity()).isEqualTo(98);
	}

}
