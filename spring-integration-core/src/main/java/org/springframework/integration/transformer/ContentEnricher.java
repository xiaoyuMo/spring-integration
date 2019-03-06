/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.integration.transformer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.context.Lifecycle;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.expression.ExpressionUtils;
import org.springframework.integration.gateway.MessagingGatewaySupport;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.support.DefaultMessageBuilderFactory;
import org.springframework.integration.transformer.support.HeaderValueMessageProcessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Content Enricher is a Message Transformer that can augment a message's payload with
 * either static values or by optionally invoking a downstream message flow via its
 * request channel and then applying values from the reply Message to the original
 * payload.
 *
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Gary Russell
 * @author Artem Bilan
 * @author Liujiong
 * @author Kris Jacyna
 *
 * @since 2.1
 */
public class ContentEnricher extends AbstractReplyProducingMessageHandler implements Lifecycle {

	/**
	 * Customized SpelExpressionParser to allow to specify nested properties when paren is null
	 */
	private final SpelExpressionParser parser = new SpelExpressionParser(new SpelParserConfiguration(true, true));

	private volatile Map<Expression, Expression> nullResultPropertyExpressions = new HashMap<>();

	private volatile Map<String, HeaderValueMessageProcessor<?>> nullResultHeaderExpressions =
			new HashMap<>();

	private volatile Map<Expression, Expression> propertyExpressions = new HashMap<>();

	private volatile Map<String, HeaderValueMessageProcessor<?>> headerExpressions =
			new HashMap<>();

	private EvaluationContext sourceEvaluationContext;

	private EvaluationContext targetEvaluationContext;

	private volatile boolean shouldClonePayload = false;

	private Expression requestPayloadExpression;

	private volatile MessageChannel requestChannel;

	private volatile String requestChannelName;

	private volatile MessageChannel replyChannel;

	private volatile String replyChannelName;

	private volatile MessageChannel errorChannel;

	private volatile String errorChannelName;

	private volatile Gateway gateway = null;

	private volatile Long requestTimeout;

	private volatile Long replyTimeout;

	public void setNullResultPropertyExpressions(Map<String, Expression> nullResultPropertyExpressions) {
		Map<Expression, Expression> localMap = new HashMap<>(nullResultPropertyExpressions.size());
		for (Map.Entry<String, Expression> entry : nullResultPropertyExpressions.entrySet()) {
			String key = entry.getKey();
			Expression value = entry.getValue();
			localMap.put(this.parser.parseExpression(key), value);
		}
		this.nullResultPropertyExpressions = localMap;
	}

	public void setNullResultHeaderExpressions(Map<String, HeaderValueMessageProcessor<?>> nullResultHeaderExpressions) {
		this.nullResultHeaderExpressions = new HashMap<>(nullResultHeaderExpressions);
	}

	/**
	 * Provide the map of expressions to evaluate when enriching the target payload. The
	 * keys should simply be property names, and the values should be Expressions that
	 * will evaluate against the reply Message as the root object.
	 * @param propertyExpressions The property expressions.
	 */
	public void setPropertyExpressions(Map<String, Expression> propertyExpressions) {
		Assert.notEmpty(propertyExpressions, "propertyExpressions must not be empty");
		Assert.noNullElements(propertyExpressions.keySet().toArray(), "propertyExpressions keys must not be empty");
		Assert.noNullElements(propertyExpressions.values().toArray(), "propertyExpressions values must not be empty");
		Map<Expression, Expression> localMap = new HashMap<>(propertyExpressions.size());
		for (Map.Entry<String, Expression> entry : propertyExpressions.entrySet()) {
			String key = entry.getKey();
			Expression value = entry.getValue();
			localMap.put(this.parser.parseExpression(key), value);
		}
		this.propertyExpressions = localMap;
	}

	/**
	 * Provide the map of {@link HeaderValueMessageProcessor} to evaluate when enriching
	 * the target MessageHeaders. The keys should simply be header names, and the values
	 * should be Expressions that will evaluate against the reply Message as the root
	 * object.
	 * @param headerExpressions The header expressions.
	 */
	public void setHeaderExpressions(Map<String, HeaderValueMessageProcessor<?>> headerExpressions) {
		Assert.notEmpty(headerExpressions, "headerExpressions must not be empty");
		Assert.noNullElements(headerExpressions.keySet().toArray(), "headerExpressions keys must not be empty");
		Assert.noNullElements(headerExpressions.values().toArray(), "headerExpressions values must not be empty");
		this.headerExpressions = new HashMap<>(headerExpressions);
	}

	/**
	 * Sets the content enricher's request channel. If specified, then an internal Gateway
	 * will be initialized. Setting a request channel is optional. Not setting a request
	 * channel is useful in situations where message payloads shall be enriched with
	 * static values only.
	 * @param requestChannel The request channel.
	 */
	public void setRequestChannel(MessageChannel requestChannel) {
		this.requestChannel = requestChannel;
	}

	public void setRequestChannelName(String requestChannelName) {
		Assert.hasText(requestChannelName, "'requestChannelName' must not be empty");
		this.requestChannelName = requestChannelName;
	}

	/**
	 * Sets the content enricher's reply channel. If not specified, yet the request
	 * channel is set, an anonymous reply channel will automatically created for each
	 * request.
	 * @param replyChannel The reply channel.
	 */
	public void setReplyChannel(MessageChannel replyChannel) {
		this.replyChannel = replyChannel;
	}

	public void setReplyChannelName(String replyChannelName) {
		Assert.hasText(replyChannelName, "'replyChannelName' must not be empty");
		this.replyChannelName = replyChannelName;
	}

	/**
	 * Set the content enricher's error channel to allow the error handling flow to return
	 * of an alternative object to use for enrichment if exceptions occur in the
	 * downstream flow.
	 * @param errorChannel The error channel.
	 * @since 4.1
	 */
	public void setErrorChannel(MessageChannel errorChannel) {
		this.errorChannel = errorChannel;
	}

	public void setErrorChannelName(String errorChannelName) {
		Assert.hasText(errorChannelName, "'errorChannelName' must not be empty");
		this.errorChannelName = errorChannelName;
	}

	/**
	 * Set the timeout value for sending request messages. If not explicitly configured,
	 * the default is one second.
	 * @param requestTimeout the timeout value in milliseconds. Must not be null.
	 */
	public void setRequestTimeout(Long requestTimeout) {
		Assert.notNull(requestTimeout, "requestTimeout must not be null");
		this.requestTimeout = requestTimeout;
	}

	/**
	 * Set the timeout value for receiving reply messages. If not explicitly configured,
	 * the default is one second.
	 * @param replyTimeout the timeout value in milliseconds. Must not be null.
	 */
	public void setReplyTimeout(Long replyTimeout) {
		Assert.notNull(replyTimeout, "replyTimeout must not be null");
		this.replyTimeout = replyTimeout;
	}

	/**
	 * By default the original message's payload will be used as the actual payload that
	 * will be send to the request-channel.
	 * <p>
	 * By providing a SpEL expression as value for this setter, a subset of the original
	 * payload, a header value or any other resolvable SpEL expression can be used as the
	 * basis for the payload, that will be send to the request-channel.
	 * <p>
	 * For the Expression evaluation the full message is available as the <b>root
	 * object</b>.
	 * <p>
	 * For instance the following SpEL expressions (among others) are possible:
	 * <ul>
	 *   <li>payload.foo</li>
	 *   <li>headers.foobar</li>
	 *   <li>new java.util.Date()</li>
	 *   <li>'foo' + 'bar'</li>
	 * </ul>
	 * <p>
	 * If more sophisticated logic is required (e.g. changing the message headers etc.)
	 * please use additional downstream transformers.
	 * @param requestPayloadExpression The request payload expression.
	 *
	 */
	public void setRequestPayloadExpression(Expression requestPayloadExpression) {
		this.requestPayloadExpression = requestPayloadExpression;
	}

	/**
	 * Specify whether to clone payload objects to create the target object. This is only
	 * applicable for payload types that implement Cloneable.
	 * @param shouldClonePayload true if the payload should be cloned.
	 */
	public void setShouldClonePayload(boolean shouldClonePayload) {
		this.shouldClonePayload = shouldClonePayload;
	}

	public void setIntegrationEvaluationContext(EvaluationContext evaluationContext) {
		this.sourceEvaluationContext = evaluationContext;
	}

	@Override
	public String getComponentType() {
		return "enricher";
	}

	/**
	 * Initializes the Content Enricher. Will instantiate an internal Gateway if the
	 * requestChannel is set.
	 */
	@Override
	protected void doInit() {
		Assert.state(!(this.requestChannelName != null && this.requestChannel != null),
				"'requestChannelName' and 'requestChannel' are mutually exclusive.");

		Assert.state(!(this.replyChannelName != null && this.replyChannel != null),
				"'replyChannelName' and 'replyChannel' are mutually exclusive.");

		Assert.state(!(this.errorChannelName != null && this.errorChannel != null),
				"'errorChannelName' and 'errorChannel' are mutually exclusive.");

		if (this.replyChannel != null || this.replyChannelName != null) {
			Assert.state(this.requestChannel != null || this.requestChannelName != null,
					"If the replyChannel is set, then the requestChannel must not be null");
		}
		if (this.errorChannel != null || this.errorChannelName != null) {
			Assert.state(this.requestChannel != null || this.requestChannelName != null,
					"If the errorChannel is set, then the requestChannel must not be null");
		}
		if (this.requestChannel != null || this.requestChannelName != null) {
			this.gateway = new Gateway();
			this.gateway.setRequestChannel(this.requestChannel);
			if (this.requestChannelName != null) {
				this.gateway.setRequestChannelName(this.requestChannelName);
			}

			if (this.requestTimeout != null) {
				this.gateway.setRequestTimeout(this.requestTimeout);
			}
			if (this.replyTimeout != null) {
				this.gateway.setReplyTimeout(this.replyTimeout);
			}

			this.gateway.setReplyChannel(this.replyChannel);
			if (this.replyChannelName != null) {
				this.gateway.setReplyChannelName(this.replyChannelName);
			}

			this.gateway.setErrorChannel(this.errorChannel);
			if (this.errorChannelName != null) {
				this.gateway.setErrorChannelName(this.errorChannelName);
			}

			if (this.getBeanFactory() != null) {
				this.gateway.setBeanFactory(this.getBeanFactory());
			}

			this.gateway.afterPropertiesSet();
		}

		if (this.sourceEvaluationContext == null) {
			this.sourceEvaluationContext = ExpressionUtils.createStandardEvaluationContext(getBeanFactory());
		}

		StandardEvaluationContext targetContext = ExpressionUtils.createStandardEvaluationContext(getBeanFactory());
		// bean resolution is NOT allowed for the target of the enrichment
		targetContext.setBeanResolver(null); // NOSONAR (null)
		this.targetEvaluationContext = targetContext;

		if (getBeanFactory() != null) {
			boolean checkReadOnlyHeaders = getMessageBuilderFactory() instanceof DefaultMessageBuilderFactory;

			for (Map.Entry<String, HeaderValueMessageProcessor<?>> entry : this.headerExpressions.entrySet()) {
				if (checkReadOnlyHeaders &&
						(MessageHeaders.ID.equals(entry.getKey()) || MessageHeaders.TIMESTAMP.equals(entry.getKey()))) {
					throw new BeanInitializationException(
							"ContentEnricher cannot override 'id' and 'timestamp' read-only headers.\n" +
									"Wrong 'headerExpressions' [" + this.headerExpressions
									+ "] configuration for " + getComponentName());
				}
				if (entry.getValue() instanceof BeanFactoryAware) {
					((BeanFactoryAware) entry.getValue()).setBeanFactory(getBeanFactory());
				}
			}

			for (Map.Entry<String, HeaderValueMessageProcessor<?>> entry : this.nullResultHeaderExpressions.entrySet()) {
				if (checkReadOnlyHeaders &&
						(MessageHeaders.ID.equals(entry.getKey()) || MessageHeaders.TIMESTAMP.equals(entry.getKey()))) {
					throw new BeanInitializationException(
							"ContentEnricher cannot override 'id' and 'timestamp' read-only headers.\n" +
									"Wrong 'nullResultHeaderExpressions' [" + this.nullResultHeaderExpressions
									+ "] configuration for " + getComponentName());
				}
				if (entry.getValue() instanceof BeanFactoryAware) {
					((BeanFactoryAware) entry.getValue()).setBeanFactory(getBeanFactory());
				}
			}
		}
	}

	@Override
	protected Object handleRequestMessage(Message<?> requestMessage) {
		final Object requestPayload = requestMessage.getPayload();
		final Object targetPayload;
		if (requestPayload instanceof Cloneable && this.shouldClonePayload) {
			try {
				Method cloneMethod = requestPayload.getClass().getMethod("clone");
				targetPayload = ReflectionUtils.invokeMethod(cloneMethod, requestPayload);
			}
			catch (Exception e) {
				throw new MessageHandlingException(requestMessage, "Failed to clone payload object", e);
			}
		}
		else {
			targetPayload = requestPayload;
		}
		final Message<?> actualRequestMessage;
		if (this.requestPayloadExpression == null) {
			actualRequestMessage = requestMessage;
		}
		else {
			final Object requestMessagePayload =
					this.requestPayloadExpression.getValue(this.sourceEvaluationContext, requestMessage);
			Assert.state(requestMessagePayload != null,
					() -> "Request payload expression produced null for " + requestMessage);
			actualRequestMessage = getMessageBuilderFactory().withPayload(requestMessagePayload)
					.copyHeaders(requestMessage.getHeaders()).build();
		}
		final Message<?> replyMessage;
		if (this.gateway == null) {
			replyMessage = actualRequestMessage;
		}
		else {
			replyMessage = this.gateway.sendAndReceiveMessage(actualRequestMessage);
			if (replyMessage == null) {
				if (this.nullResultPropertyExpressions.isEmpty() && this.nullResultHeaderExpressions.isEmpty()) {
					return null;
				}
				for (Map.Entry<Expression, Expression> entry : this.nullResultPropertyExpressions.entrySet()) {
					Expression propertyExpression = entry.getKey();
					Expression valueExpression = entry.getValue();
					Object value = valueExpression.getValue(this.sourceEvaluationContext, requestMessage);
					propertyExpression.setValue(this.targetEvaluationContext, targetPayload, value);
				}
				if (this.nullResultHeaderExpressions.isEmpty()) {
					return targetPayload;
				}
				else {
					Map<String, Object> targetHeaders = new HashMap<String, Object>(
							this.nullResultHeaderExpressions.size());
					for (Map.Entry<String, HeaderValueMessageProcessor<?>> entry : this.nullResultHeaderExpressions
							.entrySet()) {
						String header = entry.getKey();
						HeaderValueMessageProcessor<?> valueProcessor = entry.getValue();
						Boolean overwrite = valueProcessor.isOverwrite();
						overwrite = overwrite != null ? overwrite : true;
						if (overwrite || !requestMessage.getHeaders().containsKey(header)) {
							Object value = valueProcessor.processMessage(requestMessage);
							targetHeaders.put(header, value);
						}
					}
					return this.getMessageBuilderFactory().withPayload(targetPayload).copyHeaders(targetHeaders)
							.build();
				}
			}
		}
		for (Map.Entry<Expression, Expression> entry : this.propertyExpressions.entrySet()) {
			Expression propertyExpression = entry.getKey();
			Expression valueExpression = entry.getValue();
			Object value = valueExpression.getValue(this.sourceEvaluationContext, replyMessage);
			propertyExpression.setValue(this.targetEvaluationContext, targetPayload, value);
		}

		if (this.headerExpressions.isEmpty()) {
			return targetPayload;
		}
		else {
			Map<String, Object> targetHeaders = new HashMap<String, Object>(this.headerExpressions.size());
			for (Map.Entry<String, HeaderValueMessageProcessor<?>> entry : this.headerExpressions.entrySet()) {
				String header = entry.getKey();
				HeaderValueMessageProcessor<?> valueProcessor = entry.getValue();
				Boolean overwrite = valueProcessor.isOverwrite();
				overwrite = overwrite != null ? overwrite : true;
				if (overwrite || !requestMessage.getHeaders().containsKey(header)) {
					Object value = valueProcessor.processMessage(replyMessage);
					targetHeaders.put(header, value);
				}
			}
			return getMessageBuilderFactory()
					.withPayload(targetPayload)
					.copyHeaders(targetHeaders);
		}
	}

	/**
	 * Lifecycle implementation. If no requestChannel is defined, this method has no
	 * effect as in that case no Gateway is initialized.
	 */
	@Override
	public void start() {
		if (this.gateway != null) {
			this.gateway.start();
		}
	}

	/**
	 * Lifecycle implementation. If no requestChannel is defined, this method has no
	 * effect as in that case no Gateway is initialized.
	 */
	@Override
	public void stop() {
		if (this.gateway != null) {
			this.gateway.stop();
		}
	}

	/**
	 * Lifecycle implementation. If no requestChannel is defined, this method will return
	 * always return true as no Gateway is initialized.
	 */
	@Override
	public boolean isRunning() {
		return this.gateway == null || this.gateway.isRunning();
	}

	/**
	 * Internal gateway implementation for request/reply handling. Simply exposes the
	 * sendAndReceiveMessage method.
	 */
	private static final class Gateway extends MessagingGatewaySupport {

		Gateway() {
			super();
		}

		@Override
		protected Message<?> sendAndReceiveMessage(Object object) {
			return super.sendAndReceiveMessage(object);
		}

		@Override
		public String getComponentType() {
			return "enricher$gateway";
		}

	}

}
