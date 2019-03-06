/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.integration.ftp.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.filters.FileListFilter;
import org.springframework.integration.file.remote.InputStreamCallback;
import org.springframework.integration.file.remote.MessageSessionCallback;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway.Option;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.ftp.FtpTestSupport;
import org.springframework.integration.ftp.gateway.FtpOutboundGateway;
import org.springframework.integration.ftp.session.FtpRemoteFileTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.PartialSuccessException;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.FileCopyUtils;

/**
 * @author Artem Bilan
 * @author Gary Russell
 *
 * @since 3.0
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class FtpServerOutboundTests extends FtpTestSupport {

	@Autowired
	private SessionFactory<FTPFile> ftpSessionFactory;

	@Autowired
	private PollableChannel output;

	@Autowired
	private DirectChannel inboundGet;

	@Autowired
	@Qualifier("getGW.handler")
	private FtpOutboundGateway getGw;

	@Autowired
	private DirectChannel invalidDirExpression;

	@Autowired
	private DirectChannel inboundMGet;

	@Autowired
	private DirectChannel inboundMGetRecursive;

	@Autowired
	private DirectChannel inboundMGetRecursiveFiltered;

	@Autowired
	private DirectChannel inboundMPut;

	@Autowired
	private DirectChannel inboundMPutRecursive;

	@Autowired
	private DirectChannel inboundMPutRecursiveFiltered;

	@Autowired
	private DirectChannel appending;

	@Autowired
	private DirectChannel ignoring;

	@Autowired
	private DirectChannel failing;

	@Autowired
	private DirectChannel inboundGetStream;

	@Autowired
	private DirectChannel inboundCallback;

	@Autowired
	private DirectChannel inboundLs;

	@Autowired
	private DirectChannel inboundNlst;

	@Autowired
	private SourcePollingChannelAdapter ftpInbound;

	@Autowired
	private Config config;

	@Before
	public void setup() {
		this.config.targetLocalDirectoryName = getTargetLocalDirectoryName();
	}

	@Test
	public void testInt2866LocalDirectoryExpressionGET() {
		String dir = "ftpSource/";
		long modified = setModifiedOnSource1();
		this.inboundGet.send(new GenericMessage<Object>(dir + " ftpSource1.txt"));
		Message<?> result = this.output.receive(1000);
		assertThat(result).isNotNull();
		File localFile = (File) result.getPayload();
		assertThat(localFile.getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/"))
				.contains(dir.toUpperCase());
		assertPreserved(modified, localFile);

		dir = "ftpSource/subFtpSource/";
		this.inboundGet.send(new GenericMessage<Object>(dir + "subFtpSource1.txt"));
		result = this.output.receive(1000);
		assertThat(result).isNotNull();
		localFile = (File) result.getPayload();
		assertThat(localFile.getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/"))
				.contains(dir.toUpperCase());
	}

	@Test
	public void testGetWithRemove() {
		String dir = "ftpSource/";
		this.getGw.setOption(Option.DELETE);
		this.inboundGet.send(new GenericMessage<Object>(dir + "ftpSource2.txt"));
		Message<?> result = this.output.receive(1000);
		assertThat(result).isNotNull();
		File localFile = (File) result.getPayload();
		assertThat(localFile.getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/"))
				.contains(dir.toUpperCase());
		assertThat(new File(getSourceRemoteDirectory(), "ftpSource2.txt").exists()).isFalse();
	}

	@Test
	public void testInt2866InvalidLocalDirectoryExpression() {
		try {
			this.invalidDirExpression.send(new GenericMessage<Object>("/ftpSource/ ftpSource1.txt"));
			fail("Exception expected.");
		}
		catch (Exception e) {
			Throwable cause = e.getCause();
			assertThat(cause).isNotNull();
			cause = cause.getCause();
			assertThat(cause).isInstanceOf(IllegalArgumentException.class);
			assertThat(cause.getMessage()).startsWith("Failed to make local directory");
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testInt2866LocalDirectoryExpressionMGET() {
		String dir = "ftpSource/";
		long modified = setModifiedOnSource1();
		this.inboundMGet.send(new GenericMessage<Object>(dir + "*.txt"));
		Message<?> result = this.output.receive(1000);
		assertThat(result).isNotNull();
		List<File> localFiles = (List<File>) result.getPayload();

		assertThat(localFiles.size()).isGreaterThan(0);

		boolean assertedModified = false;
		for (File file : localFiles) {
			assertThat(file.getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/")).contains(dir);
			if (file.getPath().contains("localTarget1")) {
				assertedModified = assertPreserved(modified, file);
			}
		}
		assertThat(assertedModified).isTrue();

		dir = "ftpSource/subFtpSource/";
		this.inboundMGet.send(new GenericMessage<Object>(dir + "*.txt"));
		result = this.output.receive(1000);
		assertThat(result).isNotNull();
		localFiles = (List<File>) result.getPayload();

		assertThat(localFiles.size()).isGreaterThan(0);

		for (File file : localFiles) {
			assertThat(file.getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/")).contains(dir);
		}
		this.inboundMGet.send(new GenericMessage<Object>(dir + "*.txt"));
		result = this.output.receive(1000);
		assertThat(result).isNotNull();
		localFiles = (List<File>) result.getPayload();
		assertThat(localFiles.size()).isEqualTo(0);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMGETOnNullDir() throws IOException {
		Session<FTPFile> session = ftpSessionFactory.getSession();
		((FTPClient) session.getClientInstance()).changeWorkingDirectory("ftpSource");
		session.close();

		this.inboundMGet.send(new GenericMessage<Object>(""));
		Message<?> result = this.output.receive(1000);
		assertThat(result).isNotNull();
		List<File> localFiles = (List<File>) result.getPayload();

		assertThat(localFiles.size()).isGreaterThan(0);

		for (File file : localFiles) {
			assertThat(file.getName()).isIn(" localTarget1.txt", "localTarget2.txt");
			assertThat(file.getName()).doesNotContain("null");
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testInt3172LocalDirectoryExpressionMGETRecursive() throws IOException {
		String dir = "ftpSource/";
		long modified = setModifiedOnSource1();
		File secondRemote = new File(getSourceRemoteDirectory(), "ftpSource2.txt");
		secondRemote.setLastModified(System.currentTimeMillis() - 1_000_000);
		this.inboundMGetRecursive.send(new GenericMessage<Object>("*"));
		Message<?> result = this.output.receive(1000);
		assertThat(result).isNotNull();
		List<File> localFiles = (List<File>) result.getPayload();
		assertThat(localFiles.size()).isEqualTo(3);

		boolean assertedModified = false;
		for (File file : localFiles) {
			assertThat(file.getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/")).contains(dir);
			if (file.getPath().contains("localTarget1")) {
				assertedModified = assertPreserved(modified, file);
			}
		}
		assertThat(assertedModified).isTrue();
		assertThat(localFiles.get(2).getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/"))
				.contains(dir + "subFtpSource");

		File secondTarget = new File(getTargetLocalDirectory() + File.separator + "ftpSource", "localTarget2.txt");
		ByteArrayOutputStream remoteContents = new ByteArrayOutputStream();
		ByteArrayOutputStream localContents = new ByteArrayOutputStream();
		FileUtils.copyFile(secondRemote, remoteContents);
		FileUtils.copyFile(secondTarget, localContents);
		String localAsString = new String(localContents.toByteArray());
		assertThat(localAsString).isEqualTo(new String(remoteContents.toByteArray()));
		long oldLastModified = secondRemote.lastModified();
		FileUtils.copyInputStreamToFile(new ByteArrayInputStream("junk".getBytes()), secondRemote);
		long newLastModified = secondRemote.lastModified();
		secondRemote.setLastModified(oldLastModified);
		this.inboundMGetRecursive.send(new GenericMessage<Object>("*"));
		this.output.receive(0);
		localContents = new ByteArrayOutputStream();
		FileUtils.copyFile(secondTarget, localContents);
		assertThat(new String(localContents.toByteArray())).isEqualTo(localAsString);
		secondRemote.setLastModified(newLastModified);
		this.inboundMGetRecursive.send(new GenericMessage<Object>("*"));
		this.output.receive(0);
		localContents = new ByteArrayOutputStream();
		FileUtils.copyFile(secondTarget, localContents);
		assertThat(new String(localContents.toByteArray())).isEqualTo("junk");
		// restore the remote file contents
		FileUtils.copyInputStreamToFile(new ByteArrayInputStream(localAsString.getBytes()), secondRemote);
	}

	private long setModifiedOnSource1() {
		File firstRemote = new File(getSourceRemoteDirectory(), " ftpSource1.txt");
		firstRemote.setLastModified(System.currentTimeMillis() - 1_000_000);
		long modified = firstRemote.lastModified();
		assertThat(modified > 0).isTrue();
		return modified;
	}

	private boolean assertPreserved(long modified, File file) {
		// ftp only has 1 minute resolution
		assertThat(Math.abs(file.lastModified() - modified) < 61_000)
				.as("lastModified wrong by " + (modified - file.lastModified())).isTrue();
		return true;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testInt3172LocalDirectoryExpressionMGETRecursiveFiltered() {
		String dir = "ftpSource/";
		this.inboundMGetRecursiveFiltered.send(new GenericMessage<Object>(dir + "*"));
		Message<?> result = this.output.receive(1000);
		assertThat(result).isNotNull();
		List<File> localFiles = (List<File>) result.getPayload();
		// should have filtered ftpSource2.txt
		assertThat(localFiles.size()).isEqualTo(2);

		for (File file : localFiles) {
			assertThat(file.getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/")).contains(dir);
		}
		assertThat(localFiles.get(1).getPath().replaceAll(Matcher.quoteReplacement(File.separator), "/"))
				.contains(dir + "subFtpSource");

	}

	@Test
	public void testInt3100RawGET() throws Exception {
		Session<?> session = this.ftpSessionFactory.getSession();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		FileCopyUtils.copy(session.readRaw("ftpSource/ ftpSource1.txt"), baos);
		assertThat(session.finalizeRaw()).isTrue();
		assertThat(new String(baos.toByteArray())).isEqualTo("source1");

		baos = new ByteArrayOutputStream();
		FileCopyUtils.copy(session.readRaw("ftpSource/ftpSource2.txt"), baos);
		assertThat(session.finalizeRaw()).isTrue();
		assertThat(new String(baos.toByteArray())).isEqualTo("source2");

		session.close();
	}

	@Test
	public void testRawGETWithTemplate() throws Exception {
		RemoteFileTemplate<FTPFile> template = new RemoteFileTemplate<FTPFile>(this.ftpSessionFactory);
		template.setFileNameExpression(new SpelExpressionParser().parseExpression("payload"));
		template.setBeanFactory(mock(BeanFactory.class));
		template.afterPropertiesSet();
		final ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
		assertThat(template.get(new GenericMessage<String>("ftpSource/ ftpSource1.txt"),
				(InputStreamCallback) stream -> FileCopyUtils.copy(stream, baos1))).isTrue();
		assertThat(new String(baos1.toByteArray())).isEqualTo("source1");

		final ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
		assertThat(template.get(new GenericMessage<String>("ftpSource/ftpSource2.txt"),
				(InputStreamCallback) stream -> FileCopyUtils.copy(stream, baos2))).isTrue();
		assertThat(new String(baos2.toByteArray())).isEqualTo("source2");
	}

	@Test
	public void testInt3088MPutNotRecursive() {
		this.inboundMPut.send(new GenericMessage<File>(getSourceLocalDirectory()));
		@SuppressWarnings("unchecked")
		Message<List<String>> out = (Message<List<String>>) this.output.receive(1000);
		assertThat(out).isNotNull();
		assertThat(out.getPayload().size()).isEqualTo(2);
		assertThat(out.getPayload().get(0)).isNotEqualTo(out.getPayload().get(1));
		assertThat(out.getPayload().get(0))
				.isIn("ftpTarget/localSource1.txt", "ftpTarget/localSource2.txt");
		assertThat(out.getPayload().get(1))
				.isIn("ftpTarget/localSource1.txt", "ftpTarget/localSource2.txt");
	}

	@Test
	public void testInt3088MPutRecursive() {
		this.inboundMPutRecursive.send(new GenericMessage<File>(getSourceLocalDirectory()));
		@SuppressWarnings("unchecked")
		Message<List<String>> out = (Message<List<String>>) this.output.receive(1000);
		assertThat(out).isNotNull();
		assertThat(out.getPayload()).hasSize(3);
		assertThat(out.getPayload().get(0)).isNotEqualTo(out.getPayload().get(1));
		assertThat(out.getPayload().get(0))
				.isIn("ftpTarget/localSource1.txt", "ftpTarget/localSource2.txt",
						"ftpTarget/subLocalSource/subLocalSource1.txt");
		assertThat(out.getPayload().get(1))
				.isIn("ftpTarget/localSource1.txt", "ftpTarget/localSource2.txt",
						"ftpTarget/subLocalSource/subLocalSource1.txt");
		assertThat(out.getPayload().get(2))
				.isIn("ftpTarget/localSource1.txt", "ftpTarget/localSource2.txt",
						"ftpTarget/subLocalSource/subLocalSource1.txt");
	}

	@Test
	public void testInt3088MPutRecursiveFiltered() {
		this.inboundMPutRecursiveFiltered.send(new GenericMessage<File>(getSourceLocalDirectory()));
		@SuppressWarnings("unchecked")
		Message<List<String>> out = (Message<List<String>>) this.output.receive(1000);
		assertThat(out).isNotNull();
		assertThat(out.getPayload()).hasSize(2);
		assertThat(out.getPayload().get(0)).isNotEqualTo(out.getPayload().get(1));
		assertThat(out.getPayload().get(0))
				.isIn("ftpTarget/localSource1.txt", "ftpTarget/localSource2.txt",
						"ftpTarget/subLocalSource/subLocalSource1.txt");
		assertThat(out.getPayload().get(1))
				.isIn("ftpTarget/localSource1.txt", "ftpTarget/localSource2.txt",
						"ftpTarget/subLocalSource/subLocalSource1.txt");
	}

	@Test
	public void testInt3412FileMode() {
		FtpRemoteFileTemplate template = new FtpRemoteFileTemplate(ftpSessionFactory);
		assertThat(template.exists("ftpTarget/appending.txt")).isFalse();
		Message<String> m = MessageBuilder.withPayload("foo")
				.setHeader(FileHeaders.FILENAME, "appending.txt")
				.build();
		appending.send(m);
		appending.send(m);

		assertLength6(template);

		ignoring.send(m);
		assertLength6(template);
		try {
			failing.send(m);
			fail("Expected exception");
		}
		catch (MessagingException e) {
			assertThat(e.getCause().getCause().getMessage()).contains("The destination file already exists");
		}

	}

	@Test
	public void testStream() {
		String dir = "ftpSource/";
		this.inboundGetStream.send(new GenericMessage<Object>(dir + " ftpSource1.txt"));
		Message<?> result = this.output.receive(1000);
		assertThat(result).isNotNull();
		assertThat(result.getPayload()).isEqualTo("source1");
		assertThat(result.getHeaders().get(FileHeaders.REMOTE_DIRECTORY)).isEqualTo("ftpSource/");
		assertThat(result.getHeaders().get(FileHeaders.REMOTE_FILE)).isEqualTo(" ftpSource1.txt");

		Session<?> session = (Session<?>) result.getHeaders().get(IntegrationMessageHeaderAccessor.CLOSEABLE_RESOURCE);
		// Returned to cache
		assertThat(session.isOpen()).isTrue();
		// Raw reading is finished
		assertThat(TestUtils.getPropertyValue(session, "targetSession.readingRaw", AtomicBoolean.class).get())
				.isFalse();

		// Check that we can use the same session from cache to read another remote InputStream
		this.inboundGetStream.send(new GenericMessage<Object>(dir + "ftpSource2.txt"));
		result = this.output.receive(1000);
		assertThat(result).isNotNull();
		assertThat(result.getPayload()).isEqualTo("source2");
		assertThat(result.getHeaders())
				.containsEntry(FileHeaders.REMOTE_DIRECTORY, "ftpSource/")
				.containsEntry(FileHeaders.REMOTE_FILE, "ftpSource2.txt");
		assertThat(TestUtils
				.getPropertyValue(result.getHeaders().get(IntegrationMessageHeaderAccessor.CLOSEABLE_RESOURCE),
						"targetSession")).isSameAs(TestUtils.getPropertyValue(session, "targetSession"));
	}

	@Test
	public void testMgetPartial() throws Exception {
		Session<FTPFile> session = spyOnSession();
		doAnswer(invocation -> {
			FTPFile[] files = (FTPFile[]) invocation.callRealMethod();
			// add an extra file where the get will fail
			files = Arrays.copyOf(files, files.length + 1);
			FTPFile bogusFile = new FTPFile();
			bogusFile.setName("bogus.txt");
			files[files.length - 1] = bogusFile;
			return files;
		}).when(session).list("ftpSource/subFtpSource/*");
		String dir = "ftpSource/subFtpSource/";
		try {
			this.inboundMGet.send(new GenericMessage<Object>(dir + "*"));
			fail("expected exception");
		}
		catch (PartialSuccessException e) {
			assertThat(e.getDerivedInput()).hasSize(2);
			assertThat(e.getPartialResults()).hasSize(1);
			assertThat(e.getCause().getMessage())
					.contains("/ftpSource/subFtpSource/bogus.txt: No such file or directory.");
		}

	}

	@Test
	public void testMgetRecursivePartial() throws Exception {
		Session<FTPFile> session = spyOnSession();
		doAnswer(invocation -> {
			FTPFile[] files = (FTPFile[]) invocation.callRealMethod();
			// add an extra file where the get will fail
			files = Arrays.copyOf(files, files.length + 1);
			FTPFile bogusFile = new FTPFile();
			bogusFile.setName("bogus.txt");
			bogusFile.setTimestamp(Calendar.getInstance());
			files[files.length - 1] = bogusFile;
			return files;
		}).when(session).list("ftpSource/subFtpSource/");
		String dir = "ftpSource/";
		try {
			this.inboundMGetRecursive.send(new GenericMessage<Object>(dir + "*"));
			fail("expected exception");
		}
		catch (PartialSuccessException e) {
			assertThat(e.getDerivedInput()).hasSize(4);
			assertThat(e.getPartialResults()).hasSize(2);
			assertThat(e.getCause().getMessage())
					.contains("/ftpSource/subFtpSource/bogus.txt: No such file or directory.");
		}
	}

	@Test
	public void testMputPartial() throws Exception {
		Session<FTPFile> session = spyOnSession();
		doAnswer(invocation -> {
			throw new IOException("Failed to send localSource2");
		}).when(session).write(Mockito.any(InputStream.class), Mockito.contains("localSource2"));
		try {
			this.inboundMPut.send(new GenericMessage<File>(getSourceLocalDirectory()));
			fail("expected exception");
		}
		catch (PartialSuccessException e) {
			assertThat(e.getDerivedInput()).hasSize(3);
			assertThat(e.getPartialResults()).hasSize(1);
			assertThat(e.getPartialResults().iterator().next()).isEqualTo("ftpTarget/localSource1.txt");
			assertThat(e.getCause().getMessage()).contains("Failed to send localSource2");
		}
	}

	@Test
	public void testMputRecursivePartial() throws Exception {
		Session<FTPFile> session = spyOnSession();
		File sourceLocalSubDirectory = new File(getSourceLocalDirectory(), "subLocalSource");
		assertThat(sourceLocalSubDirectory.isDirectory()).isTrue();
		File extra = new File(sourceLocalSubDirectory, "subLocalSource2.txt");
		FileOutputStream writer = new FileOutputStream(extra);
		writer.write("foo".getBytes());
		writer.close();
		doAnswer(invocation -> {
			throw new IOException("Failed to send subLocalSource2");
		}).when(session).write(Mockito.any(InputStream.class), Mockito.contains("subLocalSource2"));
		try {
			this.inboundMPutRecursive.send(new GenericMessage<File>(getSourceLocalDirectory()));
			fail("expected exception");
		}
		catch (PartialSuccessException e) {
			assertThat(e.getDerivedInput()).hasSize(3);
			assertThat(e.getPartialResults()).hasSize(2);
			assertThat(e.getCause()).isInstanceOf(PartialSuccessException.class);
			PartialSuccessException cause = (PartialSuccessException) e.getCause();
			assertThat(cause.getDerivedInput()).hasSize(2);
			assertThat(cause.getPartialResults()).hasSize(1);
			assertThat(cause.getCause().getMessage()).contains("Failed to send subLocalSource2");
		}
		extra.delete();
	}

	private Session<FTPFile> spyOnSession() {
		Session<FTPFile> session = spy(this.ftpSessionFactory.getSession());
		session.close();
		@SuppressWarnings("unchecked")
		BlockingQueue<Session<FTPFile>> cache = TestUtils.getPropertyValue(ftpSessionFactory, "pool.available",
				BlockingQueue.class);
		assertThat(cache.poll()).isNotNull();
		cache.offer(session);
		@SuppressWarnings("unchecked")
		Set<Session<FTPFile>> allocated = TestUtils.getPropertyValue(ftpSessionFactory, "pool.allocated",
				Set.class);
		allocated.clear();
		allocated.add(session);
		return session;
	}

	private void assertLength6(FtpRemoteFileTemplate template) {
		FTPFile[] files = template.execute(session -> session.list("ftpTarget/appending.txt"));
		assertThat(files).hasSize(1);
		assertThat(files[0].getSize()).isEqualTo(6);
	}

	@Test
	public void testMessageSessionCallback() {
		this.inboundCallback.send(new GenericMessage<String>("foo"));
		Message<?> receive = this.output.receive(10000);
		assertThat(receive).isNotNull();
		assertThat(receive.getPayload()).isEqualTo("FOO");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testLsForNullDir() throws IOException {
		Session<FTPFile> session = ftpSessionFactory.getSession();
		((FTPClient) session.getClientInstance()).changeWorkingDirectory("ftpSource");
		session.close();

		this.inboundLs.send(new GenericMessage<String>("foo"));
		Message<?> receive = this.output.receive(10000);
		assertThat(receive).isNotNull();
		assertThat(receive.getPayload()).isInstanceOf(List.class);
		List<String> files = (List<String>) receive.getPayload();
		assertThat(files.size()).isEqualTo(2);
		assertThat(files).contains(" ftpSource1.txt", "ftpSource2.txt");

		FTPFile[] ftpFiles = ftpSessionFactory.getSession().list(null);
		for (FTPFile ftpFile : ftpFiles) {
			if (!ftpFile.isDirectory()) {
				assertThat(files.contains(ftpFile.getName())).isTrue();
			}
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testNlstAndWorkingDirExpression() throws IOException {
		this.inboundNlst.send(new GenericMessage<>("foo"));
		Message<?> receive = this.output.receive(10000);
		assertThat(receive).isNotNull();
		assertThat(receive.getPayload()).isInstanceOf(List.class);
		List<String> files = (List<String>) receive.getPayload();
		assertThat(files.size()).isEqualTo(3);
		assertThat(files).contains("subFtpSource", " ftpSource1.txt", "ftpSource2.txt");

		FTPFile[] ftpFiles = ftpSessionFactory.getSession().list(null);
		for (FTPFile ftpFile : ftpFiles) {
			if (!ftpFile.isDirectory()) {
				assertThat(files.contains(ftpFile.getName())).isTrue();
			}
		}
	}

	@Test
	public void testInboundChannelAdapterWithNullDir() throws IOException {
		Session<FTPFile> session = ftpSessionFactory.getSession();
		((FTPClient) session.getClientInstance()).changeWorkingDirectory("ftpSource");
		session.close();
		this.ftpInbound.start();

		Message<?> message = this.output.receive(10000);
		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isInstanceOf(File.class);
		assertThat(((File) message.getPayload()).getName()).isEqualTo(" ftpSource1.txt");

		message = this.output.receive(10000);
		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isInstanceOf(File.class);
		assertThat(((File) message.getPayload()).getName()).isEqualTo("ftpSource2.txt");

		assertThat(this.output.receive(10)).isNull();

		this.ftpInbound.stop();
	}

	public static class SortingFileListFilter implements FileListFilter<File> {

		@Override
		public List<File> filterFiles(File[] files) {
			File[] sorted = Arrays.copyOf(files, files.length);
			Arrays.sort(sorted, (o1, o2) -> {
				if (o1.isDirectory() && !o2.isDirectory()) {
					return 1;
				}
				else if (!o1.isDirectory() && o2.isDirectory()) {
					return -1;
				}
				else {
					return o1.getName().compareTo(o2.getName());
				}
			});
			return Arrays.asList(sorted);
		}

	}

	@SuppressWarnings("unused")
	private static final class TestMessageSessionCallback
			implements MessageSessionCallback<FTPFile, Object> {

		@Override
		public Object doInSession(Session<FTPFile> session, Message<?> requestMessage) throws IOException {
			return ((String) requestMessage.getPayload()).toUpperCase();
		}

	}

	public static class Config {

		private volatile String targetLocalDirectoryName;

		@Bean
		public SessionFactory<FTPFile> ftpSessionFactory() {
			return FtpServerOutboundTests.sessionFactory();
		}

		public String getTargetLocalDirectoryName() {
			return this.targetLocalDirectoryName;
		}

	}

}
