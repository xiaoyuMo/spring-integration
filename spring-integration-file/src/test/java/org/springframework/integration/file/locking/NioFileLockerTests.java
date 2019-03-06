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

package org.springframework.integration.file.locking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.springframework.integration.file.filters.FileListFilter;

/**
 * @author Iwein Fuld
 */
public class NioFileLockerTests {

	private File workdir;

	@Rule
	public TemporaryFolder temp = new TemporaryFolder() {

		@Override
		public void create() throws IOException {
			super.create();
			workdir = temp.newFolder(NioFileLockerTests.class.getSimpleName());
		}
	};

	@Test
	public void fileListedByFirstFilter() throws IOException {
		NioFileLocker filter = new NioFileLocker();
		File testFile = new File(workdir, "test0");
		testFile.createNewFile();
		assertThat(filter.filterFiles(workdir.listFiles()).get(0)).isEqualTo(testFile);
		filter.lock(testFile);
		assertThat(filter.filterFiles(workdir.listFiles()).get(0)).isEqualTo(testFile);
	}

	@Test
	public void fileNotListedWhenLockedByOtherFilter() throws IOException {
		NioFileLocker filter1 = new NioFileLocker();
		FileListFilter<File> filter2 = new NioFileLocker();
		File testFile = new File(workdir, "test1");
		testFile.createNewFile();
		assertThat(filter1.filterFiles(workdir.listFiles()).get(0)).isEqualTo(testFile);
		filter1.lock(testFile);
		assertThat(filter2.filterFiles(workdir.listFiles())).isEqualTo((List<File>) new ArrayList<File>());
	}

}
