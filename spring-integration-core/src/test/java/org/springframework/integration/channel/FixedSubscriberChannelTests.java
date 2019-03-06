/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.integration.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.parsing.BeanDefinitionParsingException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @since 4.0
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class FixedSubscriberChannelTests {

	@Autowired
	private MessageChannel in;

	@Autowired
	private PollableChannel out;

	@Test
	public void testHappyDay() {
		this.in.send(new GenericMessage<String>("foo"));
		Message<?> out = this.out.receive(0);
		assertThat(out.getPayload()).isEqualTo("FOO");
		assertThat(this.in).isInstanceOf(FixedSubscriberChannel.class);
	}

	@Test
	public void testNoSubs() {
		ConfigurableApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(this.getClass().getSimpleName() + "NoSubs-fail-context.xml",
					this.getClass());
			fail("Expected exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanCreationException.class);
			assertThat(e.getCause()).isInstanceOf(BeanInstantiationException.class);
			assertThat(e.getCause().getCause()).isInstanceOf(IllegalArgumentException.class);
			assertThat(e.getCause().getCause().getMessage()).contains("Cannot instantiate a");
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testTwoSubs() {
		ConfigurableApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(this.getClass().getSimpleName() + "TwoSubs-fail-context.xml",
					this.getClass());
			fail("Expected exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanDefinitionParsingException.class);
			assertThat(e.getMessage()).contains("Only one subscriber is allowed for a FixedSubscriberChannel.");
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testTwoSubsAfter() {
		ConfigurableApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(this.getClass().getSimpleName() + "TwoSubsAfter-fail-context.xml",
					this.getClass());
			fail("Expected exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(IllegalArgumentException.class);
			assertThat(e.getMessage()).contains("Only one subscriber is allowed for a FixedSubscriberChannel.");
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testInterceptors() {
		ConfigurableApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(this.getClass().getSimpleName() + "Interceptors-fail-context.xml",
					this.getClass());
			fail("Expected exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanDefinitionParsingException.class);
			assertThat(e.getMessage()).contains("Cannot have interceptors when 'fixed-subscriber=\"true\"'");
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testDatatype() {
		ConfigurableApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(this.getClass().getSimpleName() + "Datatype-fail-context.xml",
					this.getClass());
			fail("Expected exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanDefinitionParsingException.class);
			assertThat(e.getMessage()).contains("Cannot have 'datatype' when 'fixed-subscriber=\"true\"'");
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testConverter() {
		ConfigurableApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(this.getClass().getSimpleName() + "Converter-fail-context.xml",
					this.getClass());
			fail("Expected exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanDefinitionParsingException.class);
			assertThat(e.getMessage()).contains("Cannot have 'message-converter' when 'fixed-subscriber=\"true\"'");
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testQueue() {
		ConfigurableApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(this.getClass().getSimpleName() + "Queue-fail-context.xml",
					this.getClass());
			fail("Expected exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanDefinitionParsingException.class);
			assertThat(e.getMessage())
					.contains("The 'fixed-subscriber' attribute is not allowed when a <queue/> child element is present.");
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testDispatcher() {
		ConfigurableApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(this.getClass().getSimpleName() + "Dispatcher-fail-context.xml",
					this.getClass());
			fail("Expected exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanDefinitionParsingException.class);
			assertThat(e.getMessage())
					.contains("The 'fixed-subscriber' attribute is not allowed when a <dispatcher/> child element is present.");
		}
		if (context != null) {
			context.close();
		}
	}

}
