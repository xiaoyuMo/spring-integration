/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.integration.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.jmx.config.EnableIntegrationMBeanExport;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.jmx.support.MBeanServerFactoryBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Artem Bilan
 *
 * @since 5.1.2
 */
@RunWith(SpringRunner.class)
@DirtiesContext
public class MqttDslTests {

	@ClassRule
	public static final BrokerRunning brokerRunning = BrokerRunning.isRunning(1883);

	@Autowired
	@Qualifier("mqttOutFlow.input")
	private MessageChannel mqttOutFlowInput;

	@Autowired
	private PollableChannel fromMqttChannel;

	@Autowired
	private MBeanServer server;

	@Test
	public void testMqttChannelAdaptersAndJmx() throws MalformedObjectNameException {
		Set<ObjectName> mbeanNames = this.server.queryNames(
				new ObjectName("org.springframework.integration:type=ManagedEndpoint,*"), null);

		assertThat(mbeanNames.size()).isEqualTo(1);
		ObjectName objectName = mbeanNames.iterator().next();
		assertThat(objectName.toString()).contains("name=\"mqttInFlow.mqtt:inbound-channel-adapter#0\"");

		String testPayload = "foo";

		this.mqttOutFlowInput.send(
				MessageBuilder.withPayload(testPayload)
						.setHeader(MqttHeaders.TOPIC, "jmxTests")
						.build());

		Message<?> receive = this.fromMqttChannel.receive(10_000);

		assertThat(receive).isNotNull();
		assertThat(receive.getPayload()).isEqualTo(testPayload);
	}

	@Configuration
	@EnableIntegration
	@EnableIntegrationMBeanExport(server = "mbeanServer")
	public static class Config {

		@Bean
		public static MBeanServerFactoryBean mbeanServer() {
			return new MBeanServerFactoryBean();
		}

		@Bean
		public DefaultMqttPahoClientFactory pahoClientFactory() {
			DefaultMqttPahoClientFactory pahoClientFactory = new DefaultMqttPahoClientFactory();
			MqttConnectOptions connectionOptions = new MqttConnectOptions();
			connectionOptions.setServerURIs(new String[] { "tcp://localhost:1883" });
			pahoClientFactory.setConnectionOptions(connectionOptions);
			return pahoClientFactory;
		}

		@Bean
		public IntegrationFlow mqttOutFlow() {
			return f -> f.handle(new MqttPahoMessageHandler("jmxTestOut", pahoClientFactory()));
		}

		@Bean
		public IntegrationFlow mqttInFlow() {
			return IntegrationFlows.from(
					new MqttPahoMessageDrivenChannelAdapter("jmxTestIn",
							pahoClientFactory(), "jmxTests"))
					.channel(c -> c.queue("fromMqttChannel"))
					.get();
		}

	}

}
