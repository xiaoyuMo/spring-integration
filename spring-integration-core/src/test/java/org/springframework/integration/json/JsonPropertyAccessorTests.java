/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Tests for {@link JsonPropertyAccessor}.
 *
 * @author Eric Bottard
 * @author Artem Bilan
 * @author Paul Martin
 * @since 3.0
 */
public class JsonPropertyAccessorTests {

	private final SpelExpressionParser parser = new SpelExpressionParser();

	private final StandardEvaluationContext context = new StandardEvaluationContext();

	private final ObjectMapper mapper = new ObjectMapper();

	@Before
	public void setup() {
		context.addPropertyAccessor(new JsonPropertyAccessor());
	}

	@Test
	public void testSimpleLookup() throws Exception {
		Object json = mapper.readTree("{\"foo\": \"bar\"}");
		Object value = evaluate(json, "foo", Object.class);
		assertThat(value).isInstanceOf(JsonPropertyAccessor.ToStringFriendlyJsonNode.class);
		assertThat(value.toString()).isEqualTo("bar");
		Object json2 = mapper.readTree("{\"foo\": \"bar\"}");
		Object value2 = evaluate(json2, "foo", Object.class);
		assertThat(value2).isInstanceOf(JsonPropertyAccessor.ToStringFriendlyJsonNode.class);
		assertThat(value.equals(value2)).isTrue();
		assertThat(value2.hashCode()).isEqualTo(value.hashCode());
	}

	@Test(expected = SpelEvaluationException.class)
	public void testUnsupportedJsonConstruct() throws Exception {
		Object json = mapper.readTree("\"foo\"");
		evaluate(json, "fizz", Object.class);
	}

	@Test(expected = SpelEvaluationException.class)
	public void testMissingProperty() throws Exception {
		Object json = mapper.readTree("{\"foo\": \"bar\"}");
		evaluate(json, "fizz", Object.class);
	}

	@Test
	public void testArrayLookup() throws Exception {
		ArrayNode json = (ArrayNode) mapper.readTree("[3, 4, 5]");
		// JsonNode actual = evaluate("1", json, JsonNode.class); // Does not work
		// Have to wrap the root array because ArrayNode itself is not a container
		Object actual = evaluate(JsonPropertyAccessor.wrap(json), "[1]", Object.class);
		assertThat(actual.toString()).isEqualTo("4");
	}

	@Test
	public void testArrayNegativeIndex() throws Exception {
		JsonNode json = mapper.readTree("{\"foo\":[3, 4, 5]}");
		assertThat(evaluate(json, "foo[-1]", Object.class)).isNull();
	}

	@Test(expected = SpelEvaluationException.class)
	public void testArrayIndexOutOfBounds() throws Exception {
		JsonNode json = mapper.readTree("{\"foo\":[3, 4, 5]}");
		evaluate(json, "foo[3]", Object.class);
	}

	@Test
	public void testArrayLookupWithStringIndex() throws Exception {
		Object json = mapper.readTree("[3, 4, 5]");
		// JsonNode actual = evaluate("'1'", json, JsonNode.class); // Does not work
		Object actual = evaluate(json, "['1']", Object.class);
		assertThat(actual.toString()).isEqualTo("4");
	}

	@Test
	public void testNestedArrayConstruct() throws Exception {
		ArrayNode json = (ArrayNode) mapper.readTree("[[3], [4, 5], []]");
		// JsonNode actual = evaluate("1.1", json, JsonNode.class); // Does not work
		Object actual = evaluate(JsonPropertyAccessor.wrap(json), "[1][1]", Object.class);
		assertThat(actual.toString()).isEqualTo("5");
	}

	@Test
	public void testNestedArrayConstructWithStringIndex() throws Exception {
		Object json = mapper.readTree("[[3], [4, 5], []]");
		// JsonNode actual = evaluate("1.1", json, JsonNode.class); // Does not work
		Object actual = evaluate(json, "['1']['1']", Object.class);
		assertThat(actual.toString()).isEqualTo("5");
	}

	@Test
	public void testArrayResult() throws Exception {
		Object json = mapper.readTree(
				"{\"foo\": {\"bar\": [ { \"fizz\": 5, \"buzz\": 6 }, {\"fizz\": 7}, {\"fizz\": 8} ] } }");
		// Filter the bar array to return only the fizz value of each element (to prove that SPeL considers bar
		// an array/list)
		List<?> actualArray = evaluate(json, "foo.bar.![fizz]", List.class);
		assertThat(actualArray.size()).as("Array size").isEqualTo(3);
		assertThat(evaluate(actualArray, "[0]", Object.class).toString()).as("[0]").isEqualTo("5");
		assertThat(evaluate(actualArray, "[1]", Object.class).toString()).as("[1]").isEqualTo("7");
		assertThat(evaluate(actualArray, "[2]", Object.class).toString()).as("[2]").isEqualTo("8");
	}

	@Test
	public void testEmptyArrayResult() throws Exception {
		Object json = mapper.readTree(
				"{\"foo\": {\"bar\": [ { \"fizz\": 5, \"buzz\": 6 }, {\"fizz\": 7}, {\"fizz\": 8} ] } }");
		// Filter bar objects so that none match
		List<?> actualArray = evaluate(json, "foo.bar.?[fizz=='0']", List.class);
		assertThat(actualArray.size()).isEqualTo(0);
	}

	@Test
	public void testNestedHashConstruct() throws Exception {
		Object json = mapper.readTree("{\"foo\": {\"bar\": 4, \"fizz\": 5} }");
		Object actual = evaluate(json, "foo.fizz", Object.class);
		assertThat(actual.toString()).isEqualTo("5");
	}

	@Test
	public void testImplicitStringConversion() throws Exception {
		String json = "{\"foo\": {\"bar\": 4, \"fizz\": 5} }";
		Object actual = evaluate(json, "foo.fizz", Object.class);
		assertThat(actual.toString()).isEqualTo("5");
	}

	@Test(expected = SpelEvaluationException.class)
	public void testUnsupportedString() throws Exception {
		String xml = "<what>?</what>";
		evaluate(xml, "what", Object.class);
	}

	@Test(expected = SpelEvaluationException.class)
	public void testUnsupportedJson() throws Exception {
		String json = "\"literal\"";
		evaluate(json, "foo", Object.class);
	}

	@Test
	public void testNoNullPointerWithCachedReadAccessor() throws Exception {
		Expression expression = parser.parseExpression("foo");
		Object json = mapper.readTree("{\"foo\": \"bar\"}");
		Object value = expression.getValue(this.context, json);
		assertThat(value).isInstanceOf(JsonPropertyAccessor.ToStringFriendlyJsonNode.class);
		assertThat(value.toString()).isEqualTo("bar");
		Object json2 = mapper.readTree("{}");
		Object value2 = expression.getValue(this.context, json2);
		assertThat(value2).isNull();
	}

	private <T> T evaluate(Object target, String expression, Class<T> expectedType) {
		return parser.parseExpression(expression).getValue(context, target, expectedType);
	}

}
