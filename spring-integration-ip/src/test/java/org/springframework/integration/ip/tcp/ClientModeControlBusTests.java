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

package org.springframework.integration.ip.tcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.util.TestingUtilities;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @since 2.1
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class ClientModeControlBusTests {

	@Autowired
	ControlBus controlBus;

	@Autowired
	TcpReceivingChannelAdapter tcpIn;

	@Autowired
	AbstractServerConnectionFactory server;

	@Autowired
	AbstractClientConnectionFactory client;

	@Autowired
	TaskScheduler taskScheduler; // default

	@Before
	public void before() {
		TestingUtilities.waitListening(this.server, null);
		this.client.setPort(this.server.getPort());
		this.tcpIn.start();
	}

	@Test
	public void test() throws Exception {
		assertThat(controlBus.boolResult("@tcpIn.isClientMode()")).isTrue();
		int n = 0;
		while (!controlBus.boolResult("@tcpIn.isClientModeConnected()")) {
			Thread.sleep(100);
			n += 100;
			if (n > 10000) {
				fail("Connection never established");
			}
		}
		assertThat(controlBus.boolResult("@tcpIn.isRunning()")).isTrue();
		assertThat(TestUtils.getPropertyValue(tcpIn, "taskScheduler")).isSameAs(taskScheduler);
		controlBus.voidResult("@tcpIn.retryConnection()");
	}

	public interface ControlBus {

		boolean boolResult(String command);

		void voidResult(String command);

	}

}
