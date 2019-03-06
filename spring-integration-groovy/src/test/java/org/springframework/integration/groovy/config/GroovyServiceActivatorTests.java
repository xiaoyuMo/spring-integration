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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.parsing.BeanDefinitionParsingException;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.handler.ReplyRequiredException;
import org.springframework.integration.scripting.ScriptVariableGenerator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scripting.groovy.GroovyObjectCustomizer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import groovy.lang.GroovyObject;
import groovy.lang.MissingPropertyException;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 * @author Gunnar Hillert
 * @since 2.0
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class GroovyServiceActivatorTests {

	@Autowired
	private MessageChannel referencedScriptInput;

	@Autowired
	private MessageChannel inlineScriptInput;

	@Autowired
	private MessageChannel withScriptVariableGenerator;

	@Autowired
	private MessageChannel invalidInlineScript;

	@Autowired
	private MessageChannel scriptWithoutVariablesInput;

	@Autowired
	private MyGroovyCustomizer groovyCustomizer;

	@Autowired
	private AtomicBoolean invoked;

	@Autowired
	private MessageChannel outboundChannelAdapterWithGroovy;


	@Test
	public void referencedScriptAndCustomiser() throws Exception {
		groovyCustomizer.executed = false;
		QueueChannel replyChannel = new QueueChannel();
		replyChannel.setBeanName("returnAddress");
		for (int i = 1; i <= 3; i++) {
			Message<?> message = MessageBuilder.withPayload("test-" + i).setReplyChannel(replyChannel).build();
			this.referencedScriptInput.send(message);
			Thread.sleep(1000);
		}
		String value1 = (String) replyChannel.receive(0).getPayload();
		String value2 = (String) replyChannel.receive(0).getPayload();
		String value3 = (String) replyChannel.receive(0).getPayload();
		assertThat(value1.startsWith("groovy-test-1-foo - bar")).isTrue();
		assertThat(value2.startsWith("groovy-test-2-foo - bar")).isTrue();
		assertThat(value3.startsWith("groovy-test-3-foo - bar")).isTrue();
		// because we are using 'prototype bean the suffix date will be different

		assertThat(value1.substring(26).equals(value2.substring(26))).isFalse();
		assertThat(value2.substring(26).equals(value3.substring(26))).isFalse();
		assertThat(groovyCustomizer.executed).isTrue();
		assertThat(replyChannel.receive(0)).isNull();
	}

	@Test
	public void withScriptVariableGenerator() throws Exception {
		groovyCustomizer.executed = false;
		QueueChannel replyChannel = new QueueChannel();
		replyChannel.setBeanName("returnAddress");
		for (int i = 1; i <= 3; i++) {
			Message<?> message = MessageBuilder.withPayload("test-" + i).setReplyChannel(replyChannel).build();
			this.withScriptVariableGenerator.send(message);
			Thread.sleep(1000);
		}
		String value1 = (String) replyChannel.receive(0).getPayload();
		String value2 = (String) replyChannel.receive(0).getPayload();
		String value3 = (String) replyChannel.receive(0).getPayload();
		assertThat(value1.startsWith("groovy-test-1-foo - bar")).isTrue();
		assertThat(value2.startsWith("groovy-test-2-foo - bar")).isTrue();
		assertThat(value3.startsWith("groovy-test-3-foo - bar")).isTrue();
		// because we are using 'prototype bean the suffix date will be different

		assertThat(value1.substring(26).equals(value2.substring(26))).isFalse();
		assertThat(value2.substring(26).equals(value3.substring(26))).isFalse();
		assertThat(groovyCustomizer.executed).isTrue();
		assertThat(replyChannel.receive(0)).isNull();
	}

	@Test
	public void inlineScript() throws Exception {
		groovyCustomizer.executed = false;
		QueueChannel replyChannel = new QueueChannel();
		replyChannel.setBeanName("returnAddress");
		for (int i = 1; i <= 3; i++) {
			Message<?> message = MessageBuilder.withPayload("test-" + i).setReplyChannel(replyChannel).build();
			this.inlineScriptInput.send(message);
		}

		DateFormat format = new SimpleDateFormat("dd.MM.yyyy");

		String now = format.format(new Date());

		assertThat(replyChannel.receive(0).getPayload()).isEqualTo("inline-test-1 : " + now);
		assertThat(replyChannel.receive(0).getPayload()).isEqualTo("inline-test-2 : " + now);
		assertThat(replyChannel.receive(0).getPayload()).isEqualTo("inline-test-3 : " + now);

		assertThat(replyChannel.receive(0)).isNull();
		assertThat(groovyCustomizer.executed).isTrue();
	}

	@Test
	public void testScriptWithoutVariables() throws Exception {
		PollableChannel replyChannel = new QueueChannel();
		for (int i = 1; i <= 3; i++) {
			Message<?> message = MessageBuilder.withPayload("test-" + i).setReplyChannel(replyChannel).build();
			this.scriptWithoutVariablesInput.send(message);
		}

		DateFormat format = new SimpleDateFormat("dd.MM.yyyy");

		String now = format.format(new Date());

		assertThat(replyChannel.receive(0).getPayload()).isEqualTo("withoutVariables-test-1 : " + now);
		assertThat(replyChannel.receive(0).getPayload()).isEqualTo("withoutVariables-test-2 : " + now);
		assertThat(replyChannel.receive(0).getPayload()).isEqualTo("withoutVariables-test-3 : " + now);

		assertThat(replyChannel.receive(0)).isNull();
	}

	//INT-2399
	@Test(expected = MessageHandlingException.class)
	public void invalidInlineScript() throws Exception {
		Message<?> message =
				new ErrorMessage(new ReplyRequiredException(new GenericMessage<String>("test"), "reply required!"));
		try {
			this.invalidInlineScript.send(message);
			fail("MessageHandlingException expected!");
		}
		catch (Exception e) {
			Throwable cause = e.getCause();
			assertThat(cause.getClass()).isEqualTo(MissingPropertyException.class);
			assertThat(cause.getMessage()).contains("No such property: ReplyRequiredException for class: script");
			throw e;
		}

	}

	@Test(expected = BeanDefinitionParsingException.class)
	public void variablesAndScriptVariableGenerator() throws Exception {
		new ClassPathXmlApplicationContext("GroovyServiceActivatorTests-fail-withgenerator-context.xml",
				this.getClass()).close();
	}

	@Test
	public void testGroovyScriptForOutboundChannelAdapter() {
		this.outboundChannelAdapterWithGroovy.send(new GenericMessage<String>("foo"));
		assertThat(this.invoked.get()).isTrue();
	}


	public static class SampleScriptVariSource implements ScriptVariableGenerator {

		public Map<String, Object> generateScriptVariables(Message<?> message) {
			Map<String, Object> variables = new HashMap<String, Object>();
			variables.put("foo", "foo");
			variables.put("bar", "bar");
			variables.put("date", new Date());
			variables.put("payload", message.getPayload());
			variables.put("headers", message.getHeaders());
			return variables;
		}

	}


	public static class MyGroovyCustomizer implements GroovyObjectCustomizer {

		private volatile boolean executed;

		public void customize(GroovyObject goo) {
			this.executed = true;
		}

	}

}
