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

package org.springframework.integration.config.xml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Marius Bogoevici
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class IntervalTriggerParserTests {

	@Autowired
	ApplicationContext context;

	@Test
	public void testFixedRateTrigger() {
		Object poller = context.getBean("pollerWithFixedRateAttribute");
		assertThat(poller.getClass()).isEqualTo(PollerMetadata.class);
		PollerMetadata metadata = (PollerMetadata) poller;
		Trigger trigger = metadata.getTrigger();
		assertThat(trigger.getClass()).isEqualTo(PeriodicTrigger.class);
		DirectFieldAccessor accessor = new DirectFieldAccessor(trigger);
		Boolean fixedRate = (Boolean) accessor.getPropertyValue("fixedRate");
		Long period = (Long) accessor.getPropertyValue("period");
		assertThat(true).isEqualTo(fixedRate);
		assertThat(period.longValue()).isEqualTo(36L);
	}

	@Test
	public void testFixedDelayTrigger() {
		Object poller = context.getBean("pollerWithFixedDelayAttribute");
		assertThat(poller.getClass()).isEqualTo(PollerMetadata.class);
		PollerMetadata metadata = (PollerMetadata) poller;
		Trigger trigger = metadata.getTrigger();
		assertThat(trigger.getClass()).isEqualTo(PeriodicTrigger.class);
		DirectFieldAccessor accessor = new DirectFieldAccessor(trigger);
		Boolean fixedRate = (Boolean) accessor.getPropertyValue("fixedRate");
		Long period = (Long) accessor.getPropertyValue("period");
		assertThat(false).isEqualTo(fixedRate);
		assertThat(period.longValue()).isEqualTo(37L);
	}
}
