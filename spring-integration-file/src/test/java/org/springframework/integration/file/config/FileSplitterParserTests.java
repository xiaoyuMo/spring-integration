/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.integration.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.file.splitter.FileSplitter;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 4.2
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class FileSplitterParserTests {

	@Autowired
	private EventDrivenConsumer fullBoat;

	@Autowired
	private FileSplitter splitter;

	@Autowired
	private MessageChannel in;

	@Autowired
	private MessageChannel out;

	@Test
	public void testComplete() {
		assertThat(TestUtils.getPropertyValue(this.splitter, "returnIterator", Boolean.class)).isFalse();
		assertThat(TestUtils.getPropertyValue(this.splitter, "markers", Boolean.class)).isTrue();
		assertThat(TestUtils.getPropertyValue(this.splitter, "markersJson", Boolean.class)).isTrue();
		assertThat(TestUtils.getPropertyValue(this.splitter, "requiresReply", Boolean.class)).isTrue();
		assertThat(TestUtils.getPropertyValue(this.splitter, "applySequence", Boolean.class)).isTrue();
		assertThat(TestUtils.getPropertyValue(this.splitter, "charset")).isEqualTo(Charset.forName("UTF-8"));
		assertThat(TestUtils.getPropertyValue(this.splitter, "messagingTemplate.sendTimeout")).isEqualTo(5L);
		assertThat(TestUtils.getPropertyValue(this.splitter, "firstLineHeaderName")).isEqualTo("foo");
		assertThat(this.splitter.getOutputChannel()).isSameAs(this.out);
		assertThat(this.splitter.getOrder()).isEqualTo(2);
		assertThat(this.fullBoat.getInputChannel()).isSameAs(this.in);
		assertThat(this.fullBoat.isAutoStartup()).isFalse();
		assertThat(this.fullBoat.getPhase()).isEqualTo(1);
	}

}
