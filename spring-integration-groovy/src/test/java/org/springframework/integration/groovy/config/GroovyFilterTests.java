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

package org.springframework.integration.groovy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessageSelector;
import org.springframework.integration.filter.MessageFilter;
import org.springframework.integration.filter.MethodInvokingSelector;
import org.springframework.integration.groovy.GroovyScriptExecutingMessageProcessor;
import org.springframework.integration.handler.MessageProcessor;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Artem Bilan
 * @since 2.0
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class GroovyFilterTests {

	@Autowired
	private MessageChannel referencedScriptInput;

	@Autowired
	private MessageChannel inlineScriptInput;

	@Autowired
	private MessageChannel compileStaticFailScriptInput;

	@Autowired
	@Qualifier("groovyFilter.handler")
	private MessageHandler groovyFilterMessageHandler;

	@Test
	public void referencedScript() {
		QueueChannel replyChannel = new QueueChannel();
		replyChannel.setBeanName("returnAddress");
		Message<?> message1 = MessageBuilder.withPayload("test-1")
				.setReplyChannel(replyChannel)
				.setHeader("type", "bad")
				.build();
		Message<?> message2 = MessageBuilder.withPayload("test-2")
				.setReplyChannel(replyChannel)
				.setHeader("type", Math.PI)
				.build();
		this.referencedScriptInput.send(message1);
		this.referencedScriptInput.send(message2);
		assertThat(replyChannel.receive(0).getPayload()).isEqualTo("test-2");
		assertThat(replyChannel.receive(0)).isNull();
	}

	@Test
	public void inlineScript() {
		QueueChannel replyChannel = new QueueChannel();
		replyChannel.setBeanName("returnAddress");
		Message<?> message1 = MessageBuilder.withPayload("bad").setReplyChannel(replyChannel).build();
		Message<?> message2 = MessageBuilder.withPayload("good").setReplyChannel(replyChannel).build();
		this.inlineScriptInput.send(message1);
		this.inlineScriptInput.send(message2);
		Message<?> received = replyChannel.receive(0);
		assertThat(received).isNotNull();
		assertThat(received.getPayload()).isEqualTo("good");
		assertThat(received).isEqualTo(message2);
		assertThat(replyChannel.receive(0)).isNull();
	}

	@Test
	public void testInt2433VerifyRiddingOfMessageProcessorsWrapping() {
		assertThat(this.groovyFilterMessageHandler instanceof MessageFilter).isTrue();
		MessageSelector selector = TestUtils.getPropertyValue(this.groovyFilterMessageHandler, "selector",
				MethodInvokingSelector.class);
		@SuppressWarnings("rawtypes")
		MessageProcessor messageProcessor = TestUtils.getPropertyValue(selector, "messageProcessor", MessageProcessor.class);
		//before it was MethodInvokingMessageProcessor
		assertThat(messageProcessor instanceof GroovyScriptExecutingMessageProcessor).isTrue();
	}

	@Test
	public void testCompileStaticIsApplied() {
		try {
			this.compileStaticFailScriptInput.send(new GenericMessage<Object>("foo"));
			fail("MultipleCompilationErrorsException expected");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(MessageHandlingException.class);
			assertThat(e.getCause()).isInstanceOf(MultipleCompilationErrorsException.class);
			assertThat(e.getMessage()).contains("[Static type checking] - The variable [payload] is undeclared.");
		}
	}

	@Component
	public static class TestConfig {

		@Bean
		public CompilerConfiguration compilerConfiguration() {
			CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
			ImportCustomizer importCustomizer = new ImportCustomizer();
			importCustomizer.addStaticImport("pi", "java.lang.Math", "PI"); // import static java.lang.Math.PI as pi
			compilerConfiguration.addCompilationCustomizers(importCustomizer);
			return compilerConfiguration;
		}

	}

}
