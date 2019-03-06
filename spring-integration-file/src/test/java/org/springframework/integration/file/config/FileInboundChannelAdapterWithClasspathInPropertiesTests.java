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

package org.springframework.integration.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Iterator;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.ExpressionFileListFilter;
import org.springframework.integration.file.filters.FileListFilter;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Iwein Fuld
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringRunner.class)
public class FileInboundChannelAdapterWithClasspathInPropertiesTests {

	@Autowired
	private FileReadingMessageSource source;

	@Autowired
	private BeanFactory beanFactory;

	private DirectFieldAccessor accessor;

	@Before
	public void init() {
		accessor = new DirectFieldAccessor(source);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void inputDirectory() throws Exception {
		File expected = new ClassPathResource("").getFile();
		File actual = (File) accessor.getPropertyValue("directory");
		assertThat(actual).as("'directory' should be set").isEqualTo(expected);

		FileListFilter<File> fileListFilter =
				TestUtils.getPropertyValue(this.source, "scanner.filter", FileListFilter.class);
		assertThat(fileListFilter).isInstanceOf(CompositeFileListFilter.class);
		Set<FileListFilter<File>> fileFilters =
				TestUtils.getPropertyValue(fileListFilter, "fileFilters", Set.class);
		assertThat(fileFilters.size()).isEqualTo(2);
		Iterator<FileListFilter<File>> iterator = fileFilters.iterator();
		iterator.next();
		FileListFilter<File> expressionFilter = iterator.next();
		assertThat(expressionFilter).isInstanceOf(ExpressionFileListFilter.class);
		assertThat(TestUtils.getPropertyValue(expressionFilter, "expression.expression", String.class))
				.isEqualTo("true");
		assertThat(TestUtils.getPropertyValue(expressionFilter, "beanFactory")).isSameAs(this.beanFactory);
	}

}
