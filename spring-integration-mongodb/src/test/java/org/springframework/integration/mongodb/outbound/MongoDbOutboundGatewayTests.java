/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.integration.mongodb.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.expression.FunctionExpression;
import org.springframework.integration.mongodb.rules.MongoDbAvailable;
import org.springframework.integration.mongodb.rules.MongoDbAvailableTests;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Xavier Padr?
 * @author Gary Rssell
 * @author Artem Bilan
 *
 * @since 5.0
 */
@RunWith(SpringRunner.class)
@DirtiesContext
public class MongoDbOutboundGatewayTests extends MongoDbAvailableTests {

	private static final String COLLECTION_NAME = "data";

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	@Autowired
	private BeanFactory beanFactory;

	@Autowired
	private MongoOperations mongoTemplate;

	@Autowired
	private MongoConverter mongoConverter;

	@Autowired
	private MongoDbFactory mongoDbFactory;

	@Before
	public void setUp() {
		BulkOperations bulkOperations = this.mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, COLLECTION_NAME);
		bulkOperations.insert(Arrays.asList(
				this.createPerson("Artem"),
				this.createPerson("Gary"),
				this.createPerson("Oleg"),
				this.createPerson("Xavi")));
		bulkOperations.execute();
	}

	@After
	public void cleanUp() {
		mongoTemplate.dropCollection(COLLECTION_NAME);
	}

	@Test
	@MongoDbAvailable
	public void testNoFactorySpecified() {

		try {
			new MongoDbOutboundGateway((MongoDbFactory) null);
			fail("Expected the test case to throw an IllegalArgumentException");
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("MongoDbFactory translator must not be null!");
		}
	}

	@Test
	@MongoDbAvailable
	public void testNoTemplateSpecified() {
		try {
			new MongoDbOutboundGateway((MongoTemplate) null);
			fail("Expected the test case to throw an IllegalArgumentException");
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("mongoTemplate must not be null.");
		}
	}

	@Test
	@MongoDbAvailable
	public void testNoQuerySpecified() {
		Message<String> message = MessageBuilder.withPayload("test").build();
		MongoDbOutboundGateway gateway = createGateway();

		try {
			gateway.afterPropertiesSet();
			gateway.handleRequestMessage(message);
			fail("Expected the test case to throw an IllegalArgumentException");
		}
		catch (IllegalStateException e) {
			assertThat(e.getMessage()).isEqualTo("no query or collectionCallback is specified");
		}
	}

	@Test
	@MongoDbAvailable
	public void testListOfResultsWithQueryExpressionAndLimit() {
		Message<String> message = MessageBuilder.withPayload("").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setQueryExpression(
				PARSER.parseExpression("new BasicQuery('{''address.state'' : ''PA''}').limit(2)"));
		gateway.afterPropertiesSet();

		Object result = gateway.handleRequestMessage(message);

		List<Person> persons = getPersonsFromResult(result);
		assertThat(persons.size()).isEqualTo(2);
	}

	@Test
	@MongoDbAvailable
	public void testListOfResultsWithQueryFunction() {
		Message<String> message = MessageBuilder.withPayload("Xavi").build();
		MongoDbOutboundGateway gateway = createGateway();
		Function<Message<String>, Query> queryFunction =
				msg -> new BasicQuery("{'name' : '" + msg.getPayload() + "'}");
		FunctionExpression<Message<String>> functionExpression = new FunctionExpression<>(queryFunction);
		gateway.setQueryExpression(functionExpression);
		gateway.setExpectSingleResult(true);
		gateway.setEntityClass(Person.class);
		gateway.afterPropertiesSet();

		Object result = gateway.handleRequestMessage(message);

		Person person = (Person) result;

		assertThat(person.getName()).isEqualTo("Xavi");
	}

	@Test
	@MongoDbAvailable
	public void testListOfResultsWithQueryExpressionNotInitialized() {
		MongoDbOutboundGateway gateway = new MongoDbOutboundGateway(mongoDbFactory);
		gateway.setBeanFactory(beanFactory);
		gateway.setMongoConverter(mongoConverter);
		try {
			gateway.afterPropertiesSet();
			fail("Expected the test case to throw an IllegalStateException");
		}
		catch (IllegalStateException e) {
			assertThat(e.getMessage()).isEqualTo("no query or collectionCallback is specified");
		}
	}

	@Test
	@MongoDbAvailable
	public void testListOfResultsWithQueryExpression() {
		Message<String> message = MessageBuilder.withPayload("{}").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setEntityClass(Person.class);
		gateway.setQueryExpression(PARSER.parseExpression("payload"));
		gateway.afterPropertiesSet();

		Object result = gateway.handleRequestMessage(message);

		List<Person> persons = getPersonsFromResult(result);
		assertThat(persons.size()).isEqualTo(4);
	}

	@Test
	@MongoDbAvailable
	public void testListOfResultsWithQueryExpressionReturningOneResult() {
		Message<String> message = MessageBuilder.withPayload("{name : 'Xavi'}").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setEntityClass(Person.class);
		gateway.setQueryExpression(PARSER.parseExpression("payload"));
		gateway.afterPropertiesSet();

		Object result = gateway.handleRequestMessage(message);

		List<Person> persons = getPersonsFromResult(result);
		assertThat(persons.size()).isEqualTo(1);
		assertThat(persons.get(0).getName()).isEqualTo("Xavi");
	}

	@Test
	@MongoDbAvailable
	public void testSingleResultWithQueryExpressionAsString() {
		Message<String> message = MessageBuilder.withPayload("{name : 'Artem'}").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setQueryExpression(PARSER.parseExpression("payload"));
		gateway.setExpectSingleResult(true);
		gateway.setEntityClass(Person.class);
		gateway.afterPropertiesSet();

		Object result = gateway.handleRequestMessage(message);

		Person person = (Person) result;
		assertThat(person.getName()).isEqualTo("Artem");
	}

	@Test
	@MongoDbAvailable
	public void testSingleResultWithQueryExpressionAsQuery() {
		Message<String> message = MessageBuilder.withPayload("").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setQueryExpression(PARSER.parseExpression("new BasicQuery('{''name'' : ''Gary''}')"));
		gateway.setExpectSingleResult(true);
		gateway.setEntityClass(Person.class);
		gateway.afterPropertiesSet();

		Object result = gateway.handleRequestMessage(message);

		Person person = (Person) result;
		assertThat(person.getName()).isEqualTo("Gary");
	}

	@Test
	@MongoDbAvailable
	public void testSingleResultWithQueryExpressionAndNoEntityClass() {
		Message<String> message = MessageBuilder.withPayload("").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setQueryExpression(new LiteralExpression("{name : 'Xavi'}"));
		gateway.setExpectSingleResult(true);
		gateway.afterPropertiesSet();

		Object result = gateway.handleRequestMessage(message);

		Document person = (Document) result;
		assertThat(person.get("name")).isEqualTo("Xavi");
	}

	@Test
	@MongoDbAvailable
	public void testWithNullCollectionNameExpression() {
		MongoDbOutboundGateway gateway = new MongoDbOutboundGateway(mongoDbFactory);
		gateway.setBeanFactory(beanFactory);
		gateway.setQueryExpression(new LiteralExpression("{name : 'Xavi'}"));
		gateway.setExpectSingleResult(true);

		try {
			gateway.afterPropertiesSet();
			fail("Expected the test case to throw an IllegalArgumentException");
		}
		catch (IllegalStateException e) {
			assertThat(e.getMessage()).isEqualTo("no collection name specified");
		}
	}

	@Test
	@MongoDbAvailable
	public void testWithCollectionNameExpressionSpecified() {
		Message<String> message = MessageBuilder.withPayload("").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setQueryExpression(new LiteralExpression("{name : 'Xavi'}"));
		gateway.setExpectSingleResult(true);
		gateway.setCollectionNameExpression(new LiteralExpression("anotherCollection"));
		gateway.afterPropertiesSet();

		Object result = gateway.handleRequestMessage(message);

		assertThat(result).isNull();
		LiteralExpression collectionNameExpression =
				(LiteralExpression) TestUtils.getPropertyValue(gateway, "collectionNameExpression");
		assertThat(collectionNameExpression).isNotNull();
		assertThat(collectionNameExpression.getValue()).isEqualTo("anotherCollection");
	}

	@Test
	@MongoDbAvailable
	public void testWithCollectionCallbackCount() {
		Message<String> message = MessageBuilder.withPayload("").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setEntityClass(Person.class);
		gateway.setCollectionNameExpression(new LiteralExpression("data"));

		gateway.setMessageCollectionCallback((collection, requestMessage) -> collection.countDocuments());
		gateway.afterPropertiesSet();

		long result = (long) gateway.handleRequestMessage(message);

		assertThat(result).isEqualTo(4);
	}

	@Test
	@MongoDbAvailable
	public void testWithCollectionCallbackFindOne() {
		Message<String> message = MessageBuilder.withPayload("Mike").build();
		MongoDbOutboundGateway gateway = createGateway();
		gateway.setEntityClass(Person.class);
		gateway.setCollectionNameExpression(new LiteralExpression("data"));
		gateway.setRequiresReply(false);

		gateway.setMessageCollectionCallback((collection, requestMessage) -> {
			collection.insertOne(new Document("name", requestMessage.getPayload()));
			return null;
		});
		gateway.afterPropertiesSet();

		gateway.handleRequestMessage(message);

		List<Person> persons = this.mongoTemplate.find(new Query(), Person.class, COLLECTION_NAME);
		assertThat(persons.size()).isEqualTo(5);
		assertThat(persons.stream().anyMatch(p -> p.getName().equals("Mike"))).isTrue();
	}

	@SuppressWarnings("unchecked")
	private List<Person> getPersonsFromResult(Object result) {
		return (List<Person>) result;
	}

	private MongoDbOutboundGateway createGateway() {
		MongoDbOutboundGateway gateway = new MongoDbOutboundGateway(mongoDbFactory);
		gateway.setBeanFactory(beanFactory);
		gateway.setCollectionNameExpression(new LiteralExpression("data"));

		return gateway;
	}

}
