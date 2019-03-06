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

package org.springframework.integration.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;

/**
 * @author Iwein Fuld
 */
@RunWith(MockitoJUnitRunner.class)
public class SimpleWebServiceInboundGatewayTests {

	private final SimpleWebServiceInboundGateway gateway = new SimpleWebServiceInboundGateway();

	@Mock
	private MessageContext context;

	@Mock
	private WebServiceMessage request;

	@Mock
	private WebServiceMessage response;

	@Mock
	private MessageChannel requestChannel;

	private final MessageChannel replyChannel = new DirectChannel();

	private final String input = "<hello/>";

	private final Source payloadSource = new StreamSource(new StringReader(input));

	private final StringWriter output = new StringWriter();

	private final Result payloadResult = new StreamResult(output);

	@Before
	public void setup() {
		gateway.setRequestChannel(requestChannel);
		gateway.setReplyChannel(replyChannel);
		gateway.setBeanFactory(mock(BeanFactory.class));
		gateway.start();

		when(context.getResponse()).thenReturn(response);
		when(response.getPayloadResult()).thenReturn(payloadResult);
		when(context.getRequest()).thenReturn(request);
	}

	@Test
	public void invokePoxSourceWithReply() throws Exception {
		when(requestChannel.send(isA(Message.class), eq(1000L))).thenAnswer(
				withReplyTo(replyChannel));
		when(request.getPayloadSource()).thenReturn(payloadSource);
		gateway.start();
		gateway.invoke(context);
		verify(requestChannel).send(argThat(m -> m.getPayload().equals(payloadSource)), eq(1000L));
		assertThat(output.toString().endsWith(input)).isTrue();
	}

	@Test(expected = MessageDeliveryException.class)
	public void invokePoxSourceTimeout() throws Exception {
		gateway.setRequestTimeout(10);
		gateway.setReplyTimeout(10);
		when(requestChannel.send(isA(Message.class), anyLong())).thenReturn(false);
		when(request.getPayloadSource()).thenReturn(payloadSource);
		gateway.invoke(context);
	}


	private Answer<Boolean> withReplyTo(final MessageChannel replyChannel) {
		return invocation -> {
			replyChannel.send((Message<?>) invocation.getArguments()[0]);
			return true;
		};
	}

}
