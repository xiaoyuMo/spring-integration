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

package org.springframework.integration.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.gateway.RequestReplyExchanger;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.GenericMessage;

/**
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Gary Russell
 *
 */
public class UnicastingDispatcherTests {

	@SuppressWarnings("unchecked")
	@Test
	public void withInboundGatewayAsyncRequestChannelAndExplicitErrorChannel() throws Exception {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("unicasting-with-async.xml", this.getClass());
		SubscribableChannel errorChannel = context.getBean("errorChannel", SubscribableChannel.class);
		MessageHandler errorHandler = message -> {
			MessageChannel replyChannel = (MessageChannel) message.getHeaders().getReplyChannel();
			assertThat(message.getPayload() instanceof MessageDeliveryException).isTrue();
			replyChannel.send(new GenericMessage<String>("reply"));
		};
		errorChannel.subscribe(errorHandler);

		RequestReplyExchanger exchanger = context.getBean(RequestReplyExchanger.class);
		Message<String> reply = (Message<String>) exchanger.exchange(new GenericMessage<String>("Hello"));
		assertThat(reply.getPayload()).isEqualTo("reply");
		context.close();
	}

}
