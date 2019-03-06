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

package org.springframework.integration.mapping;

import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * Strategy interface for mapping from an Object to a{@link Message}.
 *
 * @author Mark Fisher
 * @author Artem Bilan
 * @author Gary Russell
 */
@FunctionalInterface
public interface InboundMessageMapper<T> {

	/**
	 * Convert a provided object to the {@link Message}.
	 * @param object the object for message payload or some other conversion logic
	 * @return the message as a result of mapping
	 * @throws Exception the exception thrown by the underlying mapper implementation
	 */
	@Nullable
	default Message<?> toMessage(T object) throws Exception { // NOSONAR - TODO remove Exception in 5.2
		return toMessage(object, null);
	}

	/**
	 * Convert a provided object to the {@link Message}
	 * and supply with headers if necessary and provided.
	 * @param object the object for message payload or some other conversion logic
	 * @param headers additional headers for building message. Can be null
	 * @return the message as a result of mapping
	 * @throws Exception the exception thrown by the underlying mapper implementation
	 * @since 5.0
	 */
	@Nullable
	Message<?> toMessage(T object, @Nullable Map<String, Object> headers) throws Exception; // NOSONAR

}
