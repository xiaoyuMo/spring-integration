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

package org.springframework.integration.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.messaging.Message;

/**
 * @author Iwein Fuld
 * @author Mark Fisher
 * @author Artem Bilan
 * @author Gary Russell
 */
@RunWith(MockitoJUnitRunner.class)
public class FileReadingMessageSourceTests {

	private FileReadingMessageSource source;

	@Mock
	private File inputDirectoryMock;

	@Mock
	private File fileMock;

	@Mock
	private FileLocker locker;

	@Mock
	private Comparator<File> comparator;

	public void prepResource() throws Exception {
		when(inputDirectoryMock.isDirectory()).thenReturn(true);
		when(inputDirectoryMock.exists()).thenReturn(true);
		when(inputDirectoryMock.canRead()).thenReturn(true);
		when(inputDirectoryMock.getAbsolutePath()).thenReturn("foo/bar");
		when(fileMock.getAbsolutePath()).thenReturn("foo/bar/fileMock");
		when(locker.lock(isA(File.class))).thenReturn(true);
	}

	@Before
	public void initialize() throws Exception {
		prepResource();
		this.source = new FileReadingMessageSource(comparator);
		this.source.setDirectory(inputDirectoryMock);
		this.source.setLocker(locker);
		this.source.afterPropertiesSet();
	}

	@Test
	public void straightProcess() throws Exception {
		when(inputDirectoryMock.listFiles()).thenReturn(new File[] { fileMock });
		assertThat(source.receive().getPayload()).isEqualTo(fileMock);
	}

	@Test
	public void requeueOnFailure() throws Exception {
		when(inputDirectoryMock.listFiles()).thenReturn(new File[] { fileMock });
		Message<File> received = source.receive();
		assertThat(received).isNotNull();
		source.onFailure(received);
		assertThat(source.receive().getPayload()).isEqualTo(received.getPayload());
		verify(inputDirectoryMock, times(1)).listFiles();
	}

	@Test
	public void scanEachPoll() throws Exception {
		File anotherFileMock = mock(File.class);
		when(anotherFileMock.getAbsolutePath()).thenReturn("foo/bar/anotherFileMock");
		when(inputDirectoryMock.listFiles()).thenReturn(new File[] { fileMock, anotherFileMock });
		source.setScanEachPoll(true);
		assertThat(source.receive()).isNotNull();
		assertThat(source.receive()).isNotNull();
		assertThat(source.receive()).isNull();
		verify(inputDirectoryMock, times(3)).listFiles();
	}

	@Test
	public void noDuplication() throws Exception {
		when(inputDirectoryMock.listFiles()).thenReturn(new File[] { fileMock });
		Message<File> received = source.receive();
		assertThat(received).isNotNull();
		assertThat(received.getPayload()).isEqualTo(fileMock);
		assertThat(source.receive()).isNull();
		verify(inputDirectoryMock, times(2)).listFiles();
	}

	@Test(expected = IllegalArgumentException.class)
	public void nullFilter() throws Exception {
		source.setFilter(null);
	}

	@Test
	public void lockIsAcquired() throws IOException {
		when(inputDirectoryMock.listFiles()).thenReturn(new File[] { fileMock });
		Message<File> received = source.receive();
		assertThat(received).isNotNull();
		assertThat(received.getPayload()).isEqualTo(fileMock);
		verify(locker).lock(fileMock);
	}

	@Test
	public void lockedFilesAreIgnored() throws IOException {
		when(inputDirectoryMock.listFiles()).thenReturn(new File[] { fileMock });
		when(locker.lock(fileMock)).thenReturn(false);
		Message<File> received = source.receive();
		assertThat(received).isNull();
		verify(locker).lock(fileMock);
	}

	@Test
	public void orderedReception() throws Exception {
		File file1 = mock(File.class);
		when(file1.getAbsolutePath()).thenReturn("foo/bar/file1");
		File file2 = mock(File.class);
		when(file2.getAbsolutePath()).thenReturn("foo/bar/file2");
		File file3 = mock(File.class);
		when(file3.getAbsolutePath()).thenReturn("foo/bar/file3");

		// record the comparator to reverse order the files
		when(comparator.compare(file1, file2)).thenReturn(1);
		when(comparator.compare(file1, file3)).thenReturn(1);
		when(comparator.compare(file3, file2)).thenReturn(-1);

		when(inputDirectoryMock.listFiles()).thenReturn(new File[]{file2, file3, file1});
		assertThat(source.receive().getPayload()).isSameAs(file3);
		assertThat(source.receive().getPayload()).isSameAs(file2);
		assertThat(source.receive().getPayload()).isSameAs(file1);
		assertThat(source.receive()).isNull();
		verify(inputDirectoryMock, times(2)).listFiles();
	}

}
