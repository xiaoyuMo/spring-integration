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

import java.util.Arrays;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.integration.support.AbstractIntegrationMessageBuilder;
import org.springframework.integration.support.DefaultMessageBuilderFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;

/**
 * Transformer that removes Message headers.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 2.0
 */
public class HeaderFilter extends IntegrationObjectSupport implements Transformer {

	private final String[] headersToRemove;

	private volatile boolean patternMatch = true;


	public HeaderFilter(String... headersToRemove) {
		Assert.notEmpty(headersToRemove, "At least one header name to remove is required.");
		this.headersToRemove = headersToRemove;
	}

	public void setPatternMatch(boolean patternMatch) {
		this.patternMatch = patternMatch;
	}

	@Override
	public String getComponentType() {
		return "header-filter";
	}

	@Override
	protected void onInit() {
		super.onInit();
		if (getMessageBuilderFactory() instanceof DefaultMessageBuilderFactory) {
			for (String header : this.headersToRemove) {
				if (!header.contains("*")
						&& (MessageHeaders.ID.equals(header) || MessageHeaders.TIMESTAMP.equals(header))) {
					throw new BeanInitializationException(
							"HeaderFilter cannot remove 'id' and 'timestamp' read-only headers.\n" +
									"Wrong 'headersToRemove' [" + Arrays.toString(this.headersToRemove)
									+ "] configuration for " + getComponentName());
				}
			}
		}
	}

	@Override
	public Message<?> transform(Message<?> message) {
		AbstractIntegrationMessageBuilder<?> builder = this.getMessageBuilderFactory().fromMessage(message);
		if (this.patternMatch) {
			builder.removeHeaders(this.headersToRemove);
		}
		else {
			for (String headerToRemove : this.headersToRemove) {
				builder.removeHeader(headerToRemove);
			}
		}
		return builder.build();
	}

}
