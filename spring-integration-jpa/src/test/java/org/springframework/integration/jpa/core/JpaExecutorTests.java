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

package org.springframework.integration.jpa.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.jpa.support.JpaParameter;
import org.springframework.integration.jpa.support.parametersource.ExpressionEvaluatingParameterSourceFactory;
import org.springframework.integration.jpa.support.parametersource.ParameterSourceFactory;
import org.springframework.integration.jpa.test.entity.StudentDomain;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Gunnar Hillert
 * @author Amol Nayak
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 2.2
 */
@RunWith(SpringRunner.class)
public class JpaExecutorTests {

	@Autowired
	protected EntityManager entityManager;

	@Autowired
	private BeanFactory beanFactory;

	/**
	 * In this test, the {@link JpaExecutor}'s poll method will be called without
	 * specifying a 'query', 'namedQuery' or 'entityClass' property. This should
	 * result in an {@link IllegalArgumentException}.
	 */
	@Test
	public void testExecutePollWithNoEntityClassSpecified() {
		JpaExecutor jpaExecutor = new JpaExecutor(mock(EntityManager.class));
		jpaExecutor.setBeanFactory(mock(BeanFactory.class));
		jpaExecutor.afterPropertiesSet();
		try {
			jpaExecutor.poll();
		}
		catch (IllegalStateException e) {
			assertThat(e.getMessage()).as("Exception Message does not match.")
					.isEqualTo("For the polling operation, one of "
							+ "the following properties must be specified: "
							+ "query, namedQuery or entityClass.");
			return;
		}

		fail("Was expecting an IllegalStateException to be thrown.");
	}

	@Test
	public void testInstantiateJpaExecutorWithNullJpaOperations() {
		JpaOperations jpaOperations = null;

		try {
			new JpaExecutor(jpaOperations);
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("jpaOperations must not be null.");
		}
	}

	@Test
	public void testSetMultipleQueryTypes() {
		JpaExecutor executor = new JpaExecutor(mock(EntityManager.class));
		executor.setJpaQuery("select s from Student s");
		assertThat(TestUtils.getPropertyValue(executor, "jpaQuery", String.class)).isNotNull();

		try {
			executor.setNamedQuery("NamedQuery");
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("You can define only one of the "
					+ "properties 'jpaQuery', 'nativeQuery', 'namedQuery'");
		}

		assertThat(TestUtils.getPropertyValue(executor, "namedQuery")).isNull();

		try {
			executor.setNativeQuery("select * from Student");
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("You can define only one of the "
					+ "properties 'jpaQuery', 'nativeQuery', 'namedQuery'");
		}
		assertThat(TestUtils.getPropertyValue(executor, "nativeQuery")).isNull();

		executor = new JpaExecutor(mock(EntityManager.class));
		executor.setNamedQuery("NamedQuery");
		assertThat(TestUtils.getPropertyValue(executor, "namedQuery", String.class)).isNotNull();

		try {
			executor.setJpaQuery("select s from Student s");
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("You can define only one of the "
					+ "properties 'jpaQuery', 'nativeQuery', 'namedQuery'");
		}
		assertThat(TestUtils.getPropertyValue(executor, "jpaQuery")).isNull();

	}

	@Test
	@Transactional
	public void selectWithMessageAsParameterSource() {
		String query = "select s from Student s where s.firstName = :firstName";
		Message<Map<String, String>> message =
				MessageBuilder.withPayload(Collections.singletonMap("firstName", "First One")).build();
		JpaExecutor executor = getJpaExecutorForMessageAsParamSource(query);
		StudentDomain student = (StudentDomain) executor.poll(message);
		assertThat(student).isNotNull();
	}

	@Test
	@Transactional
	public void selectWithPayloadAsParameterSource() {
		String query = "select s from Student s where s.firstName = :firstName";
		Message<String> message =
				MessageBuilder.withPayload("First One").build();
		JpaExecutor executor = getJpaExecutorForPayloadAsParamSource(query);
		StudentDomain student = (StudentDomain) executor.poll(message);
		assertThat(student).isNotNull();
	}

	@Test
	@Transactional
	public void updateWithMessageAsParameterSource() {
		String query = "update Student s set s.firstName = :firstName where s.lastName = 'Last One'";
		Message<Map<String, String>> message =
				MessageBuilder.withPayload(Collections.singletonMap("firstName", "First One")).build();
		JpaExecutor executor = getJpaExecutorForMessageAsParamSource(query);
		Integer rowsAffected = (Integer) executor.executeOutboundJpaOperation(message);
		assertThat((int) rowsAffected).isEqualTo(1);
	}

	@Test
	@Transactional
	public void updateWithPayloadAsParameterSource() {
		String query = "update Student s set s.firstName = :firstName where s.lastName = 'Last One'";
		Message<String> message =
				MessageBuilder.withPayload("First One").build();
		JpaExecutor executor = getJpaExecutorForPayloadAsParamSource(query);
		Integer rowsAffected = (Integer) executor.executeOutboundJpaOperation(message);
		assertThat((int) rowsAffected).isEqualTo(1);
	}

	private JpaExecutor getJpaExecutorForMessageAsParamSource(String query) {
		JpaExecutor executor = new JpaExecutor(entityManager);
		ExpressionEvaluatingParameterSourceFactory factory =
				new ExpressionEvaluatingParameterSourceFactory(mock(BeanFactory.class));
		factory.setParameters(
				Collections.singletonList(new JpaParameter("firstName", null, "payload['firstName']")));
		executor.setParameterSourceFactory(factory);
		executor.setJpaQuery(query);
		executor.setExpectSingleResult(true);
		executor.setUsePayloadAsParameterSource(false);
		executor.setBeanFactory(mock(BeanFactory.class));
		executor.afterPropertiesSet();
		return executor;
	}

	private JpaExecutor getJpaExecutorForPayloadAsParamSource(String query) {
		JpaExecutor executor = new JpaExecutor(entityManager);
		ExpressionEvaluatingParameterSourceFactory factory =
				new ExpressionEvaluatingParameterSourceFactory(mock(BeanFactory.class));
		factory.setParameters(
				Collections.singletonList(new JpaParameter("firstName", null, "#this")));
		executor.setParameterSourceFactory(factory);
		executor.setJpaQuery(query);
		executor.setExpectSingleResult(true);
		executor.setUsePayloadAsParameterSource(true);
		executor.setBeanFactory(mock(BeanFactory.class));
		executor.afterPropertiesSet();
		return executor;
	}

	@Test
	public void testResultStartingFromThirdRecordForJPAQuery() {
		final JpaExecutor jpaExecutor = new JpaExecutor(entityManager);
		jpaExecutor.setJpaQuery("select s from Student s");
		jpaExecutor.setFirstResultExpression(new LiteralExpression("2"));
		jpaExecutor.setBeanFactory(this.beanFactory);
		jpaExecutor.afterPropertiesSet();

		List<?> results = (List<?>) jpaExecutor.poll(MessageBuilder.withPayload("").build());
		assertThat(results).isNotNull();
		assertThat(results.size()).isEqualTo(1);
	}

	@Test
	public void testResultStartingFromThirdRecordForNativeQuery() {
		final JpaExecutor jpaExecutor = new JpaExecutor(entityManager);
		jpaExecutor.setNativeQuery("select * from Student s");
		jpaExecutor.setFirstResultExpression(new LiteralExpression("2"));
		jpaExecutor.setBeanFactory(this.beanFactory);
		jpaExecutor.afterPropertiesSet();

		List<?> results = (List<?>) jpaExecutor.poll(MessageBuilder.withPayload("").build());
		assertThat(results).isNotNull();
		assertThat(results.size()).isEqualTo(1);
	}

	@Test
	public void testResultStartingFromThirdRecordForNamedQuery() {
		final JpaExecutor jpaExecutor = new JpaExecutor(entityManager);
		jpaExecutor.setNamedQuery("selectAllStudents");
		jpaExecutor.setFirstResultExpression(new LiteralExpression("2"));
		jpaExecutor.setBeanFactory(this.beanFactory);
		jpaExecutor.afterPropertiesSet();

		List<?> results = (List<?>) jpaExecutor.poll(MessageBuilder.withPayload("").build());
		assertThat(results).isNotNull();
		assertThat(results.size()).isEqualTo(1);
	}

	@Test
	public void testResultStartingFromThirdRecordUsingEntity() {
		final JpaExecutor jpaExecutor = new JpaExecutor(entityManager);
		jpaExecutor.setEntityClass(StudentDomain.class);
		jpaExecutor.setFirstResultExpression(new LiteralExpression("2"));
		jpaExecutor.setBeanFactory(this.beanFactory);
		jpaExecutor.afterPropertiesSet();

		List<?> results = (List<?>) jpaExecutor.poll(MessageBuilder.withPayload("").build());
		assertThat(results).isNotNull();
		assertThat(results.size()).isEqualTo(1);
	}

	@Test
	public void withNullMaxResultsExpression() {
		final JpaExecutor jpaExecutor = new JpaExecutor(mock(EntityManager.class));
		try {
			jpaExecutor.setMaxResultsExpression(null);
		}
		catch (Exception e) {
			assertThat(e.getMessage()).isEqualTo("maxResultsExpression cannot be null");
			return;
		}
		fail("Expected the test case to throw an exception");
	}

	@Test
	public void testParameterSourceFactoryAndJpaParameters() {
		JpaExecutor executor = new JpaExecutor(this.entityManager);
		ParameterSourceFactory parameterSourceFactory = new ExpressionEvaluatingParameterSourceFactory();
		executor.setParameterSourceFactory(parameterSourceFactory);
		executor.setJpaParameters(Collections.singletonList(new JpaParameter("firstName", null, "#this")));

		assertThatIllegalStateException()
				.isThrownBy(executor::afterPropertiesSet)
				.withMessageStartingWith("The 'jpaParameters' and 'parameterSourceFactory' are mutually exclusive.");
	}

}
