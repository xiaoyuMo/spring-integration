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

package org.springframework.integration.file.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import org.springframework.messaging.Message;

/**
 * @author Alex Peters
 * @author Artem Bilan
 */
public class FileToStringTransformerTests extends
		AbstractFilePayloadTransformerTests<FileToStringTransformer> {

	@Before
	public void setUp() {
		transformer = new FileToStringTransformer();
		transformer.setCharset(DEFAULT_ENCODING);
	}

	@Test
	public void transform_withFilePayload_convertedToString() {
		Message<?> result = transformer.transform(message);
		assertThat(result).isNotNull();
		assertThat(result.getPayload())
				.isInstanceOf(String.class)
				.isEqualTo(SAMPLE_CONTENT);
	}

	@Test
	public void transform_withWrongEncoding_notMatching() {
		transformer.setCharset("ISO-8859-1");
		Message<?> result = transformer.transform(message);
		assertThat(result).isNotNull();
		assertThat(result.getPayload())
				.isInstanceOf(String.class)
				.isEqualTo(SAMPLE_CONTENT);
	}

}
