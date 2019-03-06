/*
 * Copyright 2007-2019 the original author or authors.
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

package org.springframework.integration.mongodb.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.mongodb.rules.MongoDbAvailable;
import org.springframework.integration.mongodb.rules.MongoDbAvailableTests;

import com.mongodb.BasicDBObject;

/**
 * @author Amol Nayak
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Yaron Yamin
 * @author Artem Bilan
 *
 * @since 2.2
 *
 */
public class MongoDbMessageSourceTests extends MongoDbAvailableTests {

	/**
	 * Tests by providing a null MongoDB Factory
	 *
	 */
	@Test(expected = IllegalArgumentException.class)
	public void withNullMongoDBFactory() {
		Expression expression = mock(Expression.class);
		new MongoDbMessageSource((MongoDbFactory) null, expression);
	}

	@Test(expected = IllegalArgumentException.class)
	public void withNullMongoTemplate() {
		Expression expression = mock(Expression.class);
		new MongoDbMessageSource((MongoOperations) null, expression);
	}

	@Test(expected = IllegalArgumentException.class)
	public void withNullQueryExpression() {
		MongoDbFactory mongoDbFactory = mock(MongoDbFactory.class);
		new MongoDbMessageSource(mongoDbFactory, null);
	}

	@Test
	@MongoDbAvailable
	public void validateSuccessfulQueryWithSingleElementIfOneInListAsDbObject() throws Exception {
		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MongoTemplate template = new MongoTemplate(mongoDbFactory);
		template.save(this.createPerson(), "data");

		Expression queryExpression = new LiteralExpression("{'name' : 'Oleg'}");
		MongoDbMessageSource messageSource = new MongoDbMessageSource(mongoDbFactory, queryExpression);
		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();
		@SuppressWarnings("unchecked")
		List<Document> results = ((List<Document>) messageSource.receive().getPayload());
		assertThat(results.size()).isEqualTo(1);
		Document resultObject = results.get(0);

		assertThat(resultObject.get("name")).isEqualTo("Oleg");
	}

	@Test
	@MongoDbAvailable
	public void validateSuccessfulQueryWithSingleElementIfOneInList() throws Exception {

		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MongoTemplate template = new MongoTemplate(mongoDbFactory);
		template.save(this.createPerson(), "data");

		Expression queryExpression = new LiteralExpression("{'name' : 'Oleg'}");
		MongoDbMessageSource messageSource = new MongoDbMessageSource(mongoDbFactory, queryExpression);
		messageSource.setEntityClass(Object.class);
		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();
		@SuppressWarnings("unchecked")
		List<Person> results = ((List<Person>) messageSource.receive().getPayload());
		assertThat(results.size()).isEqualTo(1);
		Person person = results.get(0);
		assertThat(person.getName()).isEqualTo("Oleg");
		assertThat(person.getAddress().getState()).isEqualTo("PA");
	}

	@Test
	@MongoDbAvailable
	public void validateSuccessfulQueryWithSingleElementIfOneInListAndSingleResult() throws Exception {

		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MongoTemplate template = new MongoTemplate(mongoDbFactory);
		template.save(this.createPerson(), "data");

		Expression queryExpression = new LiteralExpression("{'name' : 'Oleg'}");
		MongoDbMessageSource messageSource = new MongoDbMessageSource(mongoDbFactory, queryExpression);
		messageSource.setEntityClass(Object.class);
		messageSource.setExpectSingleResult(true);
		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();
		Person person = (Person) messageSource.receive().getPayload();

		assertThat(person.getName()).isEqualTo("Oleg");
		assertThat(person.getAddress().getState()).isEqualTo("PA");
	}


	@Test
	@MongoDbAvailable
	public void validateSuccessfulSubObjectQueryWithSingleElementIfOneInList() throws Exception {

		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MongoTemplate template = new MongoTemplate(mongoDbFactory);
		template.save(this.createPerson(), "data");

		Expression queryExpression = new LiteralExpression("{'address.state' : 'PA'}");
		MongoDbMessageSource messageSource = new MongoDbMessageSource(mongoDbFactory, queryExpression);
		messageSource.setEntityClass(Object.class);
		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();
		@SuppressWarnings("unchecked")
		List<Person> results = ((List<Person>) messageSource.receive().getPayload());
		Person person = results.get(0);
		assertThat(person.getName()).isEqualTo("Oleg");
		assertThat(person.getAddress().getState()).isEqualTo("PA");
	}

	@Test
	@MongoDbAvailable
	public void validateSuccessfulQueryWithMultipleElements() throws Exception {
		List<Person> persons = queryMultipleElements(new LiteralExpression("{'address.state' : 'PA'}"));
		assertThat(persons.size()).isEqualTo(3);
	}

	@Test
	@MongoDbAvailable
	public void validateSuccessfulStringQueryExpressionWithMultipleElements() throws Exception {
		List<Person> persons = queryMultipleElements(new SpelExpressionParser()
				.parseExpression("\"{'address.state' : 'PA'}\""));
		assertThat(persons.size()).isEqualTo(3);
	}

	@Test
	@MongoDbAvailable
	public void validateSuccessfulBasicQueryExpressionWithMultipleElements() throws Exception {
		List<Person> persons = queryMultipleElements(new SpelExpressionParser()
				.parseExpression("new BasicQuery(\"{'address.state' : 'PA'}\").limit(2)"));
		assertThat(persons.size()).isEqualTo(2);
	}

	@SuppressWarnings("unchecked")
	private List<Person> queryMultipleElements(Expression queryExpression) throws Exception {
		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MongoTemplate template = new MongoTemplate(mongoDbFactory);
		template.save(this.createPerson("Manny"), "data");
		template.save(this.createPerson("Moe"), "data");
		template.save(this.createPerson("Jack"), "data");

		MongoDbMessageSource messageSource = new MongoDbMessageSource(mongoDbFactory, queryExpression);
		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();

		return (List<Person>) messageSource.receive().getPayload();
	}

	@Test
	@MongoDbAvailable
	public void validateSuccessfulQueryWithNullReturn() throws Exception {

		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MongoTemplate template = new MongoTemplate(mongoDbFactory);
		template.save(this.createPerson("Manny"), "data");
		template.save(this.createPerson("Moe"), "data");
		template.save(this.createPerson("Jack"), "data");

		Expression queryExpression = new LiteralExpression("{'address.state' : 'NJ'}");
		MongoDbMessageSource messageSource = new MongoDbMessageSource(mongoDbFactory, queryExpression);
		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();
		assertThat(messageSource.receive()).isNull();
	}

	@SuppressWarnings("unchecked")
	@Test
	@MongoDbAvailable
	public void validateSuccessfulQueryWithCustomConverter() throws Exception {

		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MongoTemplate template = new MongoTemplate(mongoDbFactory);
		template.save(this.createPerson("Manny"), "data");
		template.save(this.createPerson("Moe"), "data");
		template.save(this.createPerson("Jack"), "data");

		Expression queryExpression = new LiteralExpression("{'address.state' : 'PA'}");
		MongoDbMessageSource messageSource = new MongoDbMessageSource(mongoDbFactory, queryExpression);
		MappingMongoConverter converter = new TestMongoConverter(mongoDbFactory, new MongoMappingContext());
		messageSource.setBeanFactory(mock(BeanFactory.class));
		converter.afterPropertiesSet();
		converter = spy(converter);
		messageSource.setMongoConverter(converter);
		messageSource.afterPropertiesSet();

		List<Person> persons = (List<Person>) messageSource.receive().getPayload();
		assertThat(persons.size()).isEqualTo(3);
		verify(converter, times(3)).read((Class<Person>) Mockito.any(), Mockito.any(Bson.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	@MongoDbAvailable
	public void validateSuccessfulQueryWithMongoTemplate() throws Exception {

		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MappingMongoConverter converter = new TestMongoConverter(mongoDbFactory, new MongoMappingContext());
		converter.afterPropertiesSet();
		converter = spy(converter);

		MongoTemplate template = new MongoTemplate(mongoDbFactory, converter);
		Expression queryExpression = new LiteralExpression("{'address.state' : 'PA'}");
		MongoDbMessageSource messageSource = new MongoDbMessageSource(template, queryExpression);
		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();

		MongoTemplate writingTemplate = new MongoTemplate(mongoDbFactory, converter);
		writingTemplate.save(this.createPerson("Manny"), "data");
		writingTemplate.save(this.createPerson("Moe"), "data");
		writingTemplate.save(this.createPerson("Jack"), "data");

		List<Person> persons = (List<Person>) messageSource.receive().getPayload();
		assertThat(persons.size()).isEqualTo(3);
		verify(converter, times(3)).read((Class<Person>) Mockito.any(), Mockito.any(Bson.class));
	}

	@Test
	@MongoDbAvailable
	public void validatePipelineInModifyOut() throws Exception {

		MongoDbFactory mongoDbFactory = this.prepareMongoFactory();

		MongoTemplate template = new MongoTemplate(mongoDbFactory);

		template.save(BasicDBObject.parse("{'name' : 'Manny', 'id' : 1}"), "data");

		Expression queryExpression = new LiteralExpression("{'name' : 'Manny'}");
		MongoDbMessageSource messageSource = new MongoDbMessageSource(mongoDbFactory, queryExpression);
		messageSource.setExpectSingleResult(true);
		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();
		Document result = (Document) messageSource.receive().getPayload();
		Object id = result.get("_id");
		result.put("company", "PepBoys");
		template.save(result, "data");
		result = (Document) messageSource.receive().getPayload();
		assertThat(result.get("_id")).isEqualTo(id);
	}

}
