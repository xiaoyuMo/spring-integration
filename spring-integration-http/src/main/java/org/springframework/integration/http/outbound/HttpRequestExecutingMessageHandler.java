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

package org.springframework.integration.http.outbound;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.integration.expression.ValueExpression;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * A {@link org.springframework.messaging.MessageHandler}
 * implementation that executes HTTP requests by delegating
 * to a {@link RestTemplate} instance. If the 'expectReply' flag is set to true (the default)
 * then a reply Message will be generated from the HTTP response. If that response contains
 * a body, it will be used as the reply Message's payload. Otherwise the reply Message's
 * payload will contain the response status as an instance of the
 * {@link org.springframework.http.HttpStatus} enum.
 * When there is a response body, the {@link org.springframework.http.HttpStatus} enum
 * instance will instead be
 * copied to the MessageHeaders of the reply. In both cases, the response headers will
 * be mapped to the reply Message's headers by this handler's
 * {@link org.springframework.integration.mapping.HeaderMapper} instance.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Gunnar Hillert
 * @author Artem Bilan
 * @author Wallace Wadge
 * @author Shiliang Li
 *
 * @since 2.0
 */
public class HttpRequestExecutingMessageHandler extends AbstractHttpRequestExecutingMessageHandler {
	private final RestTemplate restTemplate;

	/**
	 * Create a handler that will send requests to the provided URI.
	 *
	 * @param uri The URI.
	 */
	public HttpRequestExecutingMessageHandler(URI uri) {
		this(new ValueExpression<URI>(uri));
	}

	/**
	 * Create a handler that will send requests to the provided URI.
	 *
	 * @param uri The URI.
	 */
	public HttpRequestExecutingMessageHandler(String uri) {
		this(uri, null);
	}

	/**
	 * Create a handler that will send requests to the provided URI Expression.
	 *
	 * @param uriExpression The URI expression.
	 */
	public HttpRequestExecutingMessageHandler(Expression uriExpression) {
		this(uriExpression, null);
	}

	/**
	 * Create a handler that will send requests to the provided URI using a provided RestTemplate
	 * @param uri The URI.
	 * @param restTemplate The rest template.
	 */
	public HttpRequestExecutingMessageHandler(String uri, RestTemplate restTemplate) {
		this(new LiteralExpression(uri), restTemplate);
		/*
		 *  We'd prefer to do this assertion first, but the compiler doesn't allow it. However,
		 *  it's safe because the literal expression simply wraps the String variable, even
		 *  when null.
		 */
		Assert.hasText(uri, "URI is required");
	}

	/**
	 * Create a handler that will send requests to the provided URI using a provided RestTemplate
	 * @param uriExpression A SpEL Expression that can be resolved against the message object and
	 * {@link org.springframework.beans.factory.BeanFactory}.
	 * @param restTemplate The rest template.
	 */
	public HttpRequestExecutingMessageHandler(Expression uriExpression, RestTemplate restTemplate) {
		super(uriExpression);
		this.restTemplate = (restTemplate == null ? new RestTemplate() : restTemplate);
	}

	@Override
	public String getComponentType() {
		return (this.isExpectReply() ? "http:outbound-gateway" : "http:outbound-channel-adapter");
	}

	/**
	 * Set the {@link ResponseErrorHandler} for the underlying {@link RestTemplate}.
	 * @param errorHandler The error handler.
	 * @see RestTemplate#setErrorHandler(ResponseErrorHandler)
	 */
	public void setErrorHandler(ResponseErrorHandler errorHandler) {
		this.restTemplate.setErrorHandler(errorHandler);
	}

	/**
	 * Set a list of {@link HttpMessageConverter}s to be used by the underlying {@link RestTemplate}.
	 * Converters configured via this method will override the default converters.
	 * @param messageConverters The message converters.
	 * @see RestTemplate#setMessageConverters(java.util.List)
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.restTemplate.setMessageConverters(messageConverters);
	}

	/**
	 * Set the {@link ClientHttpRequestFactory} for the underlying {@link RestTemplate}.
	 *
	 * @param requestFactory The request factory.
	 *
	 * @see RestTemplate#setRequestFactory(ClientHttpRequestFactory)
	 */
	public void setRequestFactory(ClientHttpRequestFactory requestFactory) {
		this.restTemplate.setRequestFactory(requestFactory);
	}

	@Override
	protected Object exchange(Supplier<URI> uriSupplier, HttpMethod httpMethod, HttpEntity<?> httpRequest,
			Object expectedResponseType, Message<?> requestMessage) {
		URI uri = uriSupplier.get();
		ResponseEntity<?> httpResponse;
		try {
			if (expectedResponseType instanceof ParameterizedTypeReference<?>) {
				httpResponse = this.restTemplate.exchange(uri, httpMethod, httpRequest,
						(ParameterizedTypeReference<?>) expectedResponseType);
			}
			else {
				httpResponse = this.restTemplate.exchange(uri, httpMethod, httpRequest,
						(Class<?>) expectedResponseType);
			}
			return getReply(httpResponse);
		}
		catch (RestClientException e) {
			throw new MessageHandlingException(requestMessage,
					"HTTP request execution failed for URI [" + uri + "]", e);
		}
	}
}
