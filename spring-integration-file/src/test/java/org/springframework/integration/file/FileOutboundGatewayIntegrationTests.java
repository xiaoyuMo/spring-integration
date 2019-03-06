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

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.FileCopyUtils;

/**
 * @author Alex Peters
 * @author Mark Fisher
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class FileOutboundGatewayIntegrationTests {

	@Qualifier("copyInput")
	@Autowired
	MessageChannel copyInputChannel;

	@Qualifier("moveInput")
	@Autowired
	MessageChannel moveInputChannel;

	@Qualifier("moveInput")
	@Autowired
	MessageChannel fileOutboundGatewayInsideChain;

	@Qualifier("output")
	@Autowired
	QueueChannel outputChannel;

	@Autowired
	BeanFactory beanFactory;

	static final String DEFAULT_ENCODING = "UTF-8";

	static final String SAMPLE_CONTENT = "HelloWorld\n????????";

	Message<File> message;

	File sourceFile;

	static File workDir;

	@BeforeClass
	public static void setupClass() {
		workDir = new File(System.getProperty("java.io.tmpdir"), "anyDir");
		workDir.mkdir();
		workDir.deleteOnExit();
	}

	@AfterClass
	public static void cleanUp() {
		if (workDir != null && workDir.exists()) {
			for (File file : workDir.listFiles()) {
				file.delete();
			}
		}
		workDir.delete();
	}

	@Before
	public void setUp() throws Exception {
		sourceFile = File.createTempFile("anyFile", ".txt");
		sourceFile.deleteOnExit();
		FileCopyUtils.copy(SAMPLE_CONTENT.getBytes(DEFAULT_ENCODING),
				new FileOutputStream(sourceFile, false));
		message = MessageBuilder.withPayload(sourceFile).build();
	}

	@After
	public void tearDown() {
		sourceFile.delete();
	}


	@Test
	public void instancesCreated() {
		assertThat(beanFactory.getBean("copier")).isNotNull();
		assertThat(beanFactory.getBean("mover")).isNotNull();
	}

	@Test
	public void copy() {
		copyInputChannel.send(message);
		List<Message<?>> result = outputChannel.clear();
		assertThat(result.size()).isEqualTo(1);
		Message<?> resultMessage = result.get(0);
		File payloadFile = (File) resultMessage.getPayload();
		assertThat(payloadFile).isNotEqualTo(sourceFile);
		assertThat(resultMessage.getHeaders().get(FileHeaders.ORIGINAL_FILE, File.class)).isEqualTo(sourceFile);
		assertThat(sourceFile.exists()).isTrue();
		assertThat(payloadFile.exists()).isTrue();
	}

	@Test
	public void move() {
		moveInputChannel.send(message);
		List<Message<?>> result = outputChannel.clear();
		assertThat(result.size()).isEqualTo(1);
		Message<?> resultMessage = result.get(0);
		File payloadFile = (File) resultMessage.getPayload();
		assertThat(payloadFile).isNotEqualTo(sourceFile);
		assertThat(resultMessage.getHeaders().get(FileHeaders.ORIGINAL_FILE, File.class)).isEqualTo(sourceFile);
		assertThat(sourceFile.exists()).isFalse();
		assertThat(payloadFile.exists()).isTrue();
	}

	@Test //INT-1029
	public void moveInsideTheChain() {
		// INT-2755
		Object bean = this.beanFactory
				.getBean("org.springframework.integration.handler.MessageHandlerChain#0$child" +
						".file-outbound-gateway-within-chain.handler");
		assertThat(bean instanceof FileWritingMessageHandler).isTrue();

		fileOutboundGatewayInsideChain.send(message);
		List<Message<?>> result = outputChannel.clear();
		assertThat(result.size()).isEqualTo(1);
		Message<?> resultMessage = result.get(0);
		File payloadFile = (File) resultMessage.getPayload();
		assertThat(payloadFile).isNotEqualTo(sourceFile);
		assertThat(resultMessage.getHeaders().get(FileHeaders.ORIGINAL_FILE, File.class)).isEqualTo(sourceFile);
		assertThat(sourceFile.exists()).isFalse();
		assertThat(payloadFile.exists()).isTrue();
	}

}
