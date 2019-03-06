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

package org.springframework.integration.gemfire.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.geode.cache.query.CqEvent;
import org.apache.geode.internal.cache.LocalRegion;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.integration.gemfire.fork.ForkUtil;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author David Turanski
 * @author Gary Russell
 * @author Artem Bilan
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@DirtiesContext
public class CqInboundChannelAdapterTests {

	@Autowired
	LocalRegion region;

	@Autowired
	ConfigurableApplicationContext applicationContext;

	@Autowired
	PollableChannel outputChannel1;

	@Autowired
	PollableChannel outputChannel2;

	@Autowired
	ContinuousQueryMessageProducer withDurable;

	static OutputStream os;

	@BeforeClass
	public static void startUp() throws Exception {
		os = ForkUtil.cacheServer();
	}

	@Test
	public void testCqEvent() throws InterruptedException {
		assertThat(TestUtils.getPropertyValue(withDurable, "durable", Boolean.class)).isTrue();
		region.put("one", 1);
		Message<?> msg = outputChannel1.receive(10000);
		assertThat(msg).isNotNull();
		assertThat(msg.getPayload() instanceof CqEvent).isTrue();
	}

	@Test
	public void testPayloadExpression() throws InterruptedException {
		region.put("one", 1);
		Message<?> msg = outputChannel2.receive(10000);
		assertThat(msg).isNotNull();
		assertThat(msg.getPayload()).isEqualTo(1);
	}

	@AfterClass
	public static void cleanUp() {
		sendSignal();
	}

	public static void sendSignal() {
		try {
			os.write("\n".getBytes());
			os.flush();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot communicate with forked VM", ex);
		}
	}

}
