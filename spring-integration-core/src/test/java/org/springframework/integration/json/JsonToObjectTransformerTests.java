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

package org.springframework.integration.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import org.springframework.integration.support.json.Jackson2JsonObjectMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mark Fisher
 * @author Artem Bilan
 * @author Gary Russell
 * @since 2.0
 */
public class JsonToObjectTransformerTests {

	@Test
	public void objectPayload() {
		JsonToObjectTransformer transformer = new JsonToObjectTransformer(TestPerson.class);
		// Since DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES is disabled by default
		// (see Jackson2JsonObjectMapper)
		// the extra "foo" property is ignored.
		String jsonString = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":42," +
				"\"address\":{\"number\":123,\"street\":\"Main Street\"}, \"foo\":\"bar\"}";
		Message<?> message = transformer.transform(new GenericMessage<>(jsonString));
		TestPerson person = (TestPerson) message.getPayload();
		assertThat(person.getFirstName()).isEqualTo("John");
		assertThat(person.getLastName()).isEqualTo("Doe");
		assertThat(person.getAge()).isEqualTo(42);
		assertThat(person.getAddress().toString()).isEqualTo("123 Main Street");
	}

	@Test
	public void objectPayloadWithCustomMapper() {
		ObjectMapper customMapper = new ObjectMapper();
		customMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, Boolean.TRUE);
		customMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, Boolean.TRUE);
		JsonToObjectTransformer transformer =
				new JsonToObjectTransformer(TestPerson.class, new Jackson2JsonObjectMapper(customMapper));
		String jsonString = "{firstName:'John', lastName:'Doe', age:42, address:{number:123, street:'Main Street'}}";
		Message<?> message = transformer.transform(new GenericMessage<>(jsonString));
		TestPerson person = (TestPerson) message.getPayload();
		assertThat(person.getFirstName()).isEqualTo("John");
		assertThat(person.getLastName()).isEqualTo("Doe");
		assertThat(person.getAge()).isEqualTo(42);
		assertThat(person.getAddress().toString()).isEqualTo("123 Main Street");
	}

}
