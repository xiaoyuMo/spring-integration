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

package org.springframework.integration.xml.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Result;
import javax.xml.transform.dom.DOMResult;

import org.junit.Test;

import org.springframework.integration.xml.result.StringResultFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.XmlMappingException;
import org.springframework.xml.transform.StringResult;

/**
 * @author Mark Fisher
 */
public class MarshallingTransformerTests {

	@Test
	public void testStringToStringResult() throws Exception {
		TestMarshaller marshaller = new TestMarshaller();
		MarshallingTransformer transformer = new MarshallingTransformer(marshaller);
		transformer.setResultFactory(new StringResultFactory());
		Message<?> resultMessage = transformer.transform(new GenericMessage<String>("world"));
		Object resultPayload = resultMessage.getPayload();
		assertThat(resultPayload.getClass()).isEqualTo(StringResult.class);
		assertThat(resultPayload.toString()).isEqualTo("hello world");
		assertThat(marshaller.payloads.get(0)).isEqualTo("world");
	}

	@Test
	public void testDefaultResultFactory() throws Exception {
		TestMarshaller marshaller = new TestMarshaller();
		MarshallingTransformer transformer = new MarshallingTransformer(marshaller);
		Message<?> resultMessage = transformer.transform(new GenericMessage<String>("world"));
		Object resultPayload = resultMessage.getPayload();
		assertThat(resultPayload.getClass()).isEqualTo(DOMResult.class);
		assertThat(marshaller.payloads.get(0)).isEqualTo("world");
	}

	@Test
	public void testMarshallingEntireMessage() throws Exception {
		TestMarshaller marshaller = new TestMarshaller();
		MarshallingTransformer transformer = new MarshallingTransformer(marshaller);
		transformer.setExtractPayload(false);
		Message<?> message = new GenericMessage<String>("test");
		transformer.transform(message);
		assertThat(marshaller.payloads.size()).isEqualTo(0);
		assertThat(marshaller.messages.size()).isEqualTo(1);
		assertThat(marshaller.messages.get(0)).isSameAs(message);
	}


	private static class TestMarshaller implements Marshaller {

		private final List<Message<?>> messages = new ArrayList<Message<?>>();

		private final List<Object> payloads = new ArrayList<Object>();

		TestMarshaller() {
			super();
		}

		@Override
		public boolean supports(Class<?> clazz) {
			return true;
		}

		@Override
		public void marshal(Object source, Result result) throws XmlMappingException, IOException {
			if (source instanceof Message) {
				this.messages.add((Message<?>) source);
			}
			else {
				this.payloads.add(source);
			}
			if (result instanceof StringResult) {
				((StringResult) result).getWriter().write("hello " + source);
			}
		}

	}

}
