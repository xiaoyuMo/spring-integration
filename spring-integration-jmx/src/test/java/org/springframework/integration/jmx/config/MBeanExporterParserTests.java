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

package org.springframework.integration.jmx.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import javax.management.MBeanServer;

import org.assertj.core.data.Offset;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.config.IntegrationManagementConfigurer;
import org.springframework.integration.monitor.IntegrationMBeanExporter;
import org.springframework.integration.support.management.AbstractMessageChannelMetrics;
import org.springframework.integration.support.management.AbstractMessageHandlerMetrics;
import org.springframework.integration.support.management.DefaultMessageChannelMetrics;
import org.springframework.integration.support.management.DefaultMessageHandlerMetrics;
import org.springframework.integration.support.management.ExponentialMovingAverage;
import org.springframework.integration.support.management.ExponentialMovingAverageRate;
import org.springframework.integration.support.management.ExponentialMovingAverageRatio;
import org.springframework.integration.support.management.MessageChannelMetrics;
import org.springframework.integration.support.management.MessageHandlerMetrics;
import org.springframework.integration.support.management.MetricsFactory;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Gary Russell
 *
 * @since 2.0
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class MBeanExporterParserTests {

	@Autowired
	private ApplicationContext context;

	@Test
	public void testMBeanExporterExists() {
		IntegrationMBeanExporter exporter = this.context.getBean(IntegrationMBeanExporter.class);
		MBeanServer server = this.context.getBean("mbs", MBeanServer.class);
		Properties properties = TestUtils.getPropertyValue(exporter, "objectNameStaticProperties", Properties.class);
		assertThat(properties).isNotNull();
		assertThat(properties.size()).isEqualTo(2);
		assertThat(properties.containsKey("foo")).isTrue();
		assertThat(properties.containsKey("bar")).isTrue();
		assertThat(exporter.getServer()).isEqualTo(server);
		assertThat(TestUtils.getPropertyValue(exporter, "namingStrategy")).isSameAs(context.getBean("keyNamer"));
		MessageChannelMetrics metrics = context.getBean("foo", MessageChannelMetrics.class);
		assertThat(metrics.isCountsEnabled()).isTrue();
		assertThat(metrics.isStatsEnabled()).isFalse();
		checkCustomized(metrics);
		MessageHandlerMetrics handlerMetrics = context.getBean("transformer.handler", MessageHandlerMetrics.class);
		checkCustomized(handlerMetrics);
		metrics = context.getBean("bar", MessageChannelMetrics.class);
		assertThat(metrics.isCountsEnabled()).isTrue();
		assertThat(metrics.isStatsEnabled()).isFalse();
		metrics = context.getBean("baz", MessageChannelMetrics.class);
		assertThat(metrics.isCountsEnabled()).isFalse();
		assertThat(metrics.isStatsEnabled()).isFalse();
		metrics = context.getBean("qux", MessageChannelMetrics.class);
		assertThat(metrics.isCountsEnabled()).isFalse();
		assertThat(metrics.isStatsEnabled()).isFalse();
		metrics = context.getBean("fiz", MessageChannelMetrics.class);
		assertThat(metrics.isCountsEnabled()).isTrue();
		assertThat(metrics.isStatsEnabled()).isTrue();
		metrics = context.getBean("buz", MessageChannelMetrics.class);
		assertThat(metrics.isCountsEnabled()).isTrue();
		assertThat(metrics.isStatsEnabled()).isTrue();
		metrics = context.getBean("!excluded", MessageChannelMetrics.class);
		assertThat(metrics.isCountsEnabled()).isFalse();
		assertThat(metrics.isStatsEnabled()).isFalse();
		checkCustomized(metrics);
		MetricsFactory factory = context.getBean(MetricsFactory.class);
		IntegrationManagementConfigurer configurer = context.getBean(IntegrationManagementConfigurer.class);
		assertThat(TestUtils.getPropertyValue(configurer, "metricsFactory")).isSameAs(factory);
		exporter.destroy();
	}

	private void checkCustomized(MessageChannelMetrics metrics) {
		assertThat(metrics.isLoggingEnabled()).isFalse();
		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendDuration.window")).isEqualTo(20);
		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendDuration.factor", Double.class))
				.isCloseTo(1000000., Offset.offset(.01));
		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendErrorRate.window")).isEqualTo(30);
		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendErrorRate.period", Double.class))
				.isCloseTo(2000000., Offset.offset(.01));
		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendErrorRate.lapse", Double.class))
				.isCloseTo(.001 / 120000, Offset.offset(.01));

		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendSuccessRatio.window")).isEqualTo(40);
		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendRate.lapse", Double.class))
				.isCloseTo(.001 / 130000, Offset.offset(.01));

		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendRate.window")).isEqualTo(50);
		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendRate.period", Double.class))
				.isCloseTo(3000000., Offset.offset(.01));
		assertThat(TestUtils.getPropertyValue(metrics, "channelMetrics.sendRate.lapse", Double.class))
				.isCloseTo(.001 / 140000, Offset.offset(.01));
	}

	private void checkCustomized(MessageHandlerMetrics metrics) {
		assertThat(TestUtils.getPropertyValue(metrics, "handlerMetrics.duration.window")).isEqualTo(20);
		assertThat(TestUtils.getPropertyValue(metrics, "handlerMetrics.duration.factor", Double.class))
				.isCloseTo(1000000., Offset.offset(.01));
	}

	public static class CustomMetrics implements MetricsFactory {

		@Override
		public AbstractMessageChannelMetrics createChannelMetrics(String name) {
			return new DefaultMessageChannelMetrics(name,
					new ExponentialMovingAverage(20, 1000000.),
					new ExponentialMovingAverageRate(2000, 120000, 30, true),
					new ExponentialMovingAverageRatio(130000, 40, true),
					new ExponentialMovingAverageRate(3000, 140000, 50, true));
		}

		@Override
		public AbstractMessageHandlerMetrics createHandlerMetrics(String name) {
			return new DefaultMessageHandlerMetrics(name, new ExponentialMovingAverage(20, 1000000.));
		}

	}

}
