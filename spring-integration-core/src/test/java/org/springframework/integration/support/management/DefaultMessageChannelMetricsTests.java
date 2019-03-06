/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.integration.support.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * @author Ivan Krizsan
 * @author Artem Bilan
 */
public class DefaultMessageChannelMetricsTests {

	protected static final int MESSAGE_COUNT = 10;

	protected static final long SEND_TIMEOUT = 1;

	@Test
	public void errorCountWithCountsEnabledOnlySuccessTest() {
		final QueueChannel theMessageChannel = new QueueChannel();
		theMessageChannel.setCountsEnabled(true);


		for (int i = 0; i < MESSAGE_COUNT; i++) {
			Message<String> theInputMessage =
					MessageBuilder.withPayload(Integer.toString(i)).build();
			theMessageChannel.send(theInputMessage, SEND_TIMEOUT);
		}

		assertThat(theMessageChannel.getSendCount()).as("Message count should match number of sent messages")
				.isEqualTo(MESSAGE_COUNT);
		assertThat(theMessageChannel.getSendErrorCount()).as("Error count should indicate no errors").isEqualTo(0);
	}

	@Test
	public void errorCountWithCountsEnabledHalfErrorsTest() {
		Message<String> theInputMessage;
		final QueueChannel theMessageChannel = new QueueChannel(MESSAGE_COUNT / 2);
		theMessageChannel.setCountsEnabled(true);

		for (int i = 0; i < MESSAGE_COUNT; i++) {
			theInputMessage = MessageBuilder.withPayload(Integer.toString(i)).build();
			theMessageChannel.send(theInputMessage, SEND_TIMEOUT);
		}

		assertThat(theMessageChannel.getSendCount()).as("Message count should match number of sent messages")
				.isEqualTo(MESSAGE_COUNT);
		assertThat(theMessageChannel.getSendErrorCount()).as("Error count should indicate errors half the messages")
				.isEqualTo(MESSAGE_COUNT / 2);
	}

}
