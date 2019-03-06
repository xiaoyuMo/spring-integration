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

package org.springframework.integration.xml.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.xml.transform.dom.DOMResult;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.SmartLifecycleRoleController;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.xml.config.StubResultFactory.StubStringResult;
import org.springframework.integration.xml.transformer.CustomTestResultFactory;
import org.springframework.integration.xml.util.XmlTestUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.MultiValueMap;
import org.springframework.xml.transform.StringResult;

/**
 * @author Jonas Partner
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Gary Russell
 */
public class XsltPayloadTransformerParserTests {

	private final String doc = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><order><orderItem>test</orderItem></order>";

	private ApplicationContext applicationContext;

	private PollableChannel output;

	@Before
	public void setUp() {
		applicationContext = new ClassPathXmlApplicationContext(getClass().getSimpleName() + "-context.xml", getClass());
		output = (PollableChannel) applicationContext.getBean("output");
	}

	@Test
	public void testParse() throws Exception {
		EventDrivenConsumer consumer = (EventDrivenConsumer) applicationContext.getBean("parseOnly");
		assertThat(TestUtils.getPropertyValue(consumer, "handler.order")).isEqualTo(2);
		assertThat(TestUtils.getPropertyValue(consumer, "handler.messagingTemplate.sendTimeout")).isEqualTo(123L);
		assertThat(TestUtils.getPropertyValue(consumer, "phase")).isEqualTo(-1);
		assertThat(TestUtils.getPropertyValue(consumer, "autoStartup", Boolean.class)).isFalse();
		SmartLifecycleRoleController roleController = applicationContext.getBean(SmartLifecycleRoleController.class);
		@SuppressWarnings("unchecked")
		List<SmartLifecycle> list = (List<SmartLifecycle>) TestUtils.getPropertyValue(roleController, "lifecycles",
				MultiValueMap.class).get("foo");
		assertThat(list).containsExactly((SmartLifecycle) consumer);
	}

	@Test
	public void testWithResourceProvided() throws Exception {
		MessageChannel input = (MessageChannel) applicationContext.getBean("withResourceIn");
		GenericMessage<Object> message = new GenericMessage<Object>(XmlTestUtil.getDomSourceForString(doc));
		input.send(message);
		Message<?> result = output.receive(0);
		assertThat(result.getPayload() instanceof DOMResult).as("Payload was not a DOMResult").isTrue();
		Document doc = (Document) ((DOMResult) result.getPayload()).getNode();
		assertThat(doc.getDocumentElement().getTextContent()).as("Wrong payload").isEqualTo("test");
		assertThat(TestUtils.getPropertyValue(applicationContext.getBean("xsltTransformerWithResource.handler"),
				"transformer.evaluationContext.beanResolver")).isNotNull();
	}

	@Test
	public void testWithTemplatesProvided() throws Exception {
		MessageChannel input = (MessageChannel) applicationContext.getBean("withTemplatesIn");
		GenericMessage<Object> message = new GenericMessage<Object>(XmlTestUtil.getDomSourceForString(doc));
		input.send(message);
		Message<?> result = output.receive(0);
		assertThat(result.getPayload() instanceof DOMResult).as("Payload was not a DOMResult").isTrue();
		Document doc = (Document) ((DOMResult) result.getPayload()).getNode();
		assertThat(doc.getDocumentElement().getTextContent()).as("Wrong payload").isEqualTo("test");
	}

	@Test
	public void testWithTemplatesAndResultTransformer() throws Exception {
		MessageChannel input = (MessageChannel) applicationContext.getBean("withTemplatesAndResultTransformerIn");
		GenericMessage<Object> message = new GenericMessage<Object>(XmlTestUtil.getDomSourceForString(doc));
		input.send(message);
		Message<?> result = output.receive(0);
		assertThat(result.getPayload().getClass()).as("Wrong payload type").isEqualTo(String.class);
		String strResult = (String) result.getPayload();
		assertThat(strResult).as("Wrong payload").isEqualTo("testReturn");
	}

	@Test
	public void testWithResourceProvidedAndStubResultFactory() throws Exception {
		MessageChannel input = (MessageChannel) applicationContext.getBean("withTemplatesAndResultFactoryIn");
		GenericMessage<Object> message = new GenericMessage<Object>(XmlTestUtil.getDomSourceForString(doc));
		input.send(message);
		Message<?> result = output.receive(0);
		assertThat(result.getPayload() instanceof StubStringResult).as("Payload was not a StubStringResult").isTrue();
	}

	@Test
	public void testWithResourceAndStringResultType() throws Exception {
		MessageChannel input = (MessageChannel) applicationContext.getBean("withTemplatesAndStringResultTypeIn");
		GenericMessage<Object> message = new GenericMessage<Object>(XmlTestUtil.getDomSourceForString(doc));
		input.send(message);
		Message<?> result = output.receive(0);
		assertThat(result.getPayload() instanceof StringResult).as("Payload was not a StringResult").isTrue();
	}

	@Test
	public void docInStringResultOut() throws Exception {
		MessageChannel input = applicationContext.getBean("docinStringResultOutTransformerChannel",
				MessageChannel.class);
		Message<?> message = MessageBuilder.withPayload(XmlTestUtil.getDocumentForString(this.doc)).build();
		input.send(message);
		Message<?> resultMessage = output.receive();
		assertThat(resultMessage.getPayload().getClass()).as("Wrong payload type").isEqualTo(StringResult.class);
		String payload = resultMessage.getPayload().toString();
		assertThat(payload.contains("<bob>test</bob>")).isTrue();
	}

	@Test
	public void stringInDocResultOut() throws Exception {
		MessageChannel input = applicationContext.getBean("stringResultOutTransformerChannel", MessageChannel.class);
		Message<?> message = MessageBuilder.withPayload(this.doc).build();
		input.send(message);
		Message<?> resultMessage = output.receive();
		assertThat(resultMessage.getPayload().getClass()).as("Wrong payload type").isEqualTo(DOMResult.class);
		Document payload = (Document) ((DOMResult) resultMessage.getPayload()).getNode();
		assertThat(XmlTestUtil.docToString(payload).contains("<bob>test</bob>")).isTrue();
	}

	@Test
	public void stringInAndCustomResultFactory() throws Exception {
		MessageChannel input = applicationContext.getBean("stringInCustomResultFactoryChannel", MessageChannel.class);
		Message<?> message = MessageBuilder.withPayload(XmlTestUtil.getDocumentForString(this.doc)).build();
		input.send(message);
		Message<?> resultMessage = output.receive();
		assertThat(resultMessage
				.getPayload().getClass()).as("Wrong payload type")
				.isEqualTo(CustomTestResultFactory.FixedStringResult.class);
		String payload = resultMessage.getPayload().toString();
		assertThat(payload.contains("fixedStringForTesting")).isTrue();
	}

}
