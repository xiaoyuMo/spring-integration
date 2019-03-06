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

package org.springframework.integration.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;


/**
 * @author Mark Fisher
 * @author Artem Bilan
 */
class MailToStringTransformerParserTests {

	@Test
	void topLevelTransformer() throws Exception {
		try (ClassPathXmlApplicationContext context =
				new ClassPathXmlApplicationContext("mailToStringTransformerParserTests.xml", this.getClass());) {

			MessageChannel input = new BeanFactoryChannelResolver(context).resolveDestination("input");
			PollableChannel output =
					(PollableChannel) new BeanFactoryChannelResolver(context).resolveDestination("output");
			MimeMessage mimeMessage = Mockito.mock(MimeMessage.class);
			Mockito.when(mimeMessage.getContent()).thenReturn("hello");
			input.send(new GenericMessage<javax.mail.Message>(mimeMessage));
			Message<?> result = output.receive(10_000);
			assertThat(result).isNotNull();
			assertThat(result.getPayload()).isEqualTo("hello");
			Mockito.verify(mimeMessage).getContent();
		}
	}

	@Test
	void transformerWithinChain() throws Exception {
		try (ClassPathXmlApplicationContext context =
				new ClassPathXmlApplicationContext("mailToStringTransformerWithinChain.xml", this.getClass());) {

			MessageChannel input = new BeanFactoryChannelResolver(context).resolveDestination("input");
			PollableChannel output =
					(PollableChannel) new BeanFactoryChannelResolver(context).resolveDestination("output");
			MimeMessage mimeMessage = Mockito.mock(MimeMessage.class);
			Mockito.when(mimeMessage.getContent()).thenReturn("foo");
			input.send(new GenericMessage<javax.mail.Message>(mimeMessage));
			Message<?> result = output.receive(0);
			assertThat(result).isNotNull();
			assertThat(result.getPayload()).isEqualTo("FOO!!!");
			Mockito.verify(mimeMessage).getContent();
		}
	}

	@Test
	void topLevelTransformerMissingInput() {
		try {
			new ClassPathXmlApplicationContext("mailToStringTransformerWithoutInputChannel.xml", this.getClass())
					.close();
		}
		catch (BeanDefinitionStoreException e) {
			assertThat(e.getMessage().contains("input-channel")).isTrue();
		}
	}

}
