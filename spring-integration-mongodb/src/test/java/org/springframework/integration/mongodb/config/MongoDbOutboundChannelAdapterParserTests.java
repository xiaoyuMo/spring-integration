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

package org.springframework.integration.mongodb.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.parsing.BeanDefinitionParsingException;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.integration.endpoint.AbstractEndpoint;
import org.springframework.integration.endpoint.PollingConsumer;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.mongodb.outbound.MongoDbStoringMessageHandler;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.MessageHandler;
/**
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 * @author Gary Russell
 */
public class MongoDbOutboundChannelAdapterParserTests {

	@Test
	public void minimalConfig() {
		ClassPathXmlApplicationContext context =
				new ClassPathXmlApplicationContext("outbound-adapter-parser-config.xml", this.getClass());
		MongoDbStoringMessageHandler handler =
				TestUtils.getPropertyValue(context.getBean("minimalConfig.adapter"), "handler", MongoDbStoringMessageHandler.class);
		assertThat(TestUtils.getPropertyValue(handler, "componentName")).isEqualTo("minimalConfig.adapter");
		assertThat(TestUtils.getPropertyValue(handler, "shouldTrack")).isEqualTo(false);
		assertThat(TestUtils.getPropertyValue(handler, "mongoTemplate")).isNotNull();
		assertThat(TestUtils.getPropertyValue(handler, "mongoDbFactory")).isEqualTo(context.getBean("mongoDbFactory"));
		assertThat(TestUtils.getPropertyValue(handler, "evaluationContext")).isNotNull();
		assertThat(TestUtils.getPropertyValue(handler, "collectionNameExpression") instanceof LiteralExpression)
				.isTrue();
		assertThat(TestUtils.getPropertyValue(handler, "collectionNameExpression.literalValue")).isEqualTo("data");
		context.close();
	}

	@Test
	public void fullConfigWithCollectionExpression() {
		ClassPathXmlApplicationContext context =
				new ClassPathXmlApplicationContext("outbound-adapter-parser-config.xml", this.getClass());
		MongoDbStoringMessageHandler handler =
				TestUtils.getPropertyValue(context.getBean("fullConfigWithCollectionExpression.adapter"), "handler", MongoDbStoringMessageHandler.class);
		assertThat(TestUtils.getPropertyValue(handler, "componentName"))
				.isEqualTo("fullConfigWithCollectionExpression.adapter");
		assertThat(TestUtils.getPropertyValue(handler, "shouldTrack")).isEqualTo(false);
		assertThat(TestUtils.getPropertyValue(handler, "mongoTemplate")).isNotNull();
		assertThat(TestUtils.getPropertyValue(handler, "mongoDbFactory")).isEqualTo(context.getBean("mongoDbFactory"));
		assertThat(TestUtils.getPropertyValue(handler, "evaluationContext")).isNotNull();
		assertThat(TestUtils.getPropertyValue(handler, "collectionNameExpression") instanceof SpelExpression).isTrue();
		assertThat(TestUtils.getPropertyValue(handler, "collectionNameExpression.expression"))
				.isEqualTo("headers.collectionName");
		context.close();
	}

	@Test
	public void fullConfigWithCollection() {
		ClassPathXmlApplicationContext context =
				new ClassPathXmlApplicationContext("outbound-adapter-parser-config.xml", this.getClass());
		MongoDbStoringMessageHandler handler =
				TestUtils.getPropertyValue(context.getBean("fullConfigWithCollection.adapter"), "handler", MongoDbStoringMessageHandler.class);
		assertThat(TestUtils.getPropertyValue(handler, "componentName")).isEqualTo("fullConfigWithCollection.adapter");
		assertThat(TestUtils.getPropertyValue(handler, "shouldTrack")).isEqualTo(false);
		assertThat(TestUtils.getPropertyValue(handler, "mongoTemplate")).isNotNull();
		assertThat(TestUtils.getPropertyValue(handler, "mongoDbFactory")).isEqualTo(context.getBean("mongoDbFactory"));
		assertThat(TestUtils.getPropertyValue(handler, "evaluationContext")).isNotNull();
		assertThat(TestUtils.getPropertyValue(handler, "collectionNameExpression") instanceof LiteralExpression)
				.isTrue();
		assertThat(TestUtils.getPropertyValue(handler, "collectionNameExpression.literalValue")).isEqualTo("foo");
		context.close();
	}

	@Test
	public void fullConfigWithMongoTemplate() {
		ClassPathXmlApplicationContext context =
				new ClassPathXmlApplicationContext("outbound-adapter-parser-config.xml", this.getClass());
		MongoDbStoringMessageHandler handler =
				TestUtils.getPropertyValue(context.getBean("fullConfigWithMongoTemplate.adapter"), "handler", MongoDbStoringMessageHandler.class);
		assertThat(TestUtils.getPropertyValue(handler, "componentName"))
				.isEqualTo("fullConfigWithMongoTemplate.adapter");
		assertThat(TestUtils.getPropertyValue(handler, "shouldTrack")).isEqualTo(false);
		assertThat(TestUtils.getPropertyValue(handler, "mongoTemplate")).isNotNull();
		assertThat(TestUtils.getPropertyValue(handler, "evaluationContext")).isNotNull();
		assertThat(TestUtils.getPropertyValue(handler, "collectionNameExpression") instanceof LiteralExpression)
				.isTrue();
		assertThat(TestUtils.getPropertyValue(handler, "collectionNameExpression.literalValue")).isEqualTo("foo");
		context.close();
	}

	@Test(expected = BeanDefinitionParsingException.class)
	public void templateAndFactoryFail() {
		new ClassPathXmlApplicationContext("outbound-adapter-parser-fail-template-factory-config.xml", this.getClass())
				.close();
	}

	@Test(expected = BeanDefinitionParsingException.class)
	public void templateAndConverterFail() {
		new ClassPathXmlApplicationContext("outbound-adapter-parser-fail-template-converter-config.xml",
				this.getClass()).close();
	}

	@Test
	public void testInt3024PollerAndRequestHandlerAdviceChain() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"outbound-adapter-parser-config.xml", this.getClass());
		AbstractEndpoint endpoint = context.getBean("pollableAdapter", AbstractEndpoint.class);
		assertThat(endpoint).isInstanceOf(PollingConsumer.class);
		MessageHandler handler = TestUtils.getPropertyValue(endpoint, "handler", MessageHandler.class);
		assertThat(AopUtils.isAopProxy(handler)).isTrue();
		List<?> advisors = TestUtils.getPropertyValue(handler, "h.advised.advisors", List.class);
		assertThat(TestUtils.getPropertyValue(advisors.get(0), "advice")).isInstanceOf(RequestHandlerRetryAdvice.class);
		context.close();
	}

}
