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

package org.springframework.integration.test.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.exceptions.verification.junit.ArgumentsAreDifferent;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

/**
 * @author Alex Peters
 * @author Iwein Fuld
 * @author Gunnar Hillert
 *
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class MockitoMessageMatchersTests {

	static final Date SOME_PAYLOAD = new Date();

	static final String SOME_HEADER_VALUE = "bar";

	static final String SOME_HEADER_KEY = "test.foo";

	@Mock
	MessageHandler handler;

	@Mock
	MessageChannel channel;

	Message<Date> message;

	@Before
	public void setUp() {
		message = MessageBuilder.withPayload(SOME_PAYLOAD).setHeader(SOME_HEADER_KEY,
				SOME_HEADER_VALUE).build();
	}

	@Test
	public void anyMatcher_withVerifyArgumentMatcherAndEqualPayload_matching() {
		handler.handleMessage(message);
		verify(handler).handleMessage(MockitoMessageMatchers.messageWithPayload(SOME_PAYLOAD));
		verify(handler)
				.handleMessage(MockitoMessageMatchers.messageWithPayload(Matchers.is(Matchers.instanceOf(Date.class))));
	}

	@Test(expected = ArgumentsAreDifferent.class)
	public void anyMatcher_withVerifyAndDifferentPayload_notMatching() {
		handler.handleMessage(message);
		verify(handler).handleMessage(MockitoMessageMatchers.messageWithPayload(Matchers.nullValue()));
	}

	@Test
	public void anyMatcher_withWhenArgumentMatcherAndEqualPayload_matching() {
		when(channel.send(MockitoMessageMatchers.messageWithPayload(SOME_PAYLOAD))).thenReturn(true);
		assertThat(channel.send(message)).isTrue();
	}

	@Test
	public void anyMatcher_withWhenAndDifferentPayload_notMatching() {
		when(channel.send(
				MockitoMessageMatchers.messageWithHeaderEntry(SOME_HEADER_KEY,
						Matchers.is(Matchers.instanceOf(Short.class)))))
				.thenReturn(true);
		assertThat(channel.send(message)).isFalse();
	}

}
