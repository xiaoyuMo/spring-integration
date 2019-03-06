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

package org.springframework.integration.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.jdbc.storedproc.CreateUser;
import org.springframework.integration.jdbc.storedproc.User;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gunnar Hillert
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext // close at the end after class
public class StoredProcOutboundGatewayWithNamespaceIntegrationTests {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	private Consumer consumer;

	@Autowired
	CreateUser createUser;

	@Autowired
	MessageChannel storedProcOutboundGatewayInsideChain;

	@Autowired
	PollableChannel replyChannel;

	@Before
	public void setUp() {
		this.jdbcTemplate.execute("delete from USERS");
	}

	@Test
	public void test() throws Exception {

		createUser.createUser(new User("myUsername", "myPassword", "myEmail"));

		List<Message<Collection<User>>> received = new ArrayList<Message<Collection<User>>>();

		received.add(consumer.poll(2000));

		Message<Collection<User>> message = received.get(0);

		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isNotNull();

		Collection<User> allUsers = message.getPayload();

		assertThat(allUsers.size() == 1).isTrue();

		User userFromDb = allUsers.iterator().next();

		assertThat(userFromDb.getUsername()).as("Wrong username").isEqualTo("myUsername");
		assertThat(userFromDb.getPassword()).as("Wrong password").isEqualTo("myPassword");
		assertThat(userFromDb.getEmail()).as("Wrong email").isEqualTo("'myEmail'");

	}

	@Test //INT-1029
	public void testStoredProcOutboundGatewayInsideChain() throws Exception {

		Message<User> requestMessage = MessageBuilder.withPayload(new User("myUsername", "myPassword", "myEmail")).build();

		storedProcOutboundGatewayInsideChain.send(requestMessage);

		@SuppressWarnings("unchecked")
		Message<Collection<User>> message = (Message<Collection<User>>) replyChannel.receive();

		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isNotNull();

		Collection<User> allUsers = message.getPayload();

		assertThat(allUsers.size() == 1).isTrue();

		User userFromDb = allUsers.iterator().next();

		assertThat(userFromDb.getUsername()).as("Wrong username").isEqualTo("myUsername");
		assertThat(userFromDb.getPassword()).as("Wrong password").isEqualTo("myPassword");
		assertThat(userFromDb.getEmail()).as("Wrong email").isEqualTo("myEmail");

	}

	static class Counter {

		private final AtomicInteger count = new AtomicInteger();

		public Integer next() throws InterruptedException {
			if (count.get() > 2) {
				//prevent message overload
				return null;
			}
			return count.incrementAndGet();
		}
	}


	static class Consumer {

		private final BlockingQueue<Message<Collection<User>>> messages = new LinkedBlockingQueue<Message<Collection<User>>>();

		@ServiceActivator
		public void receive(Message<Collection<User>> message) {
			messages.add(message);
		}

		Message<Collection<User>> poll(long timeoutInMillis) throws InterruptedException {
			return messages.poll(timeoutInMillis, TimeUnit.MILLISECONDS);
		}
	}

	static class TestService {

		public String quote(String s) {
			return "'" + s + "'";
		}
	}

}
