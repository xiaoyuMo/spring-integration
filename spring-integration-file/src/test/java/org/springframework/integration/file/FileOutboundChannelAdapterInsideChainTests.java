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
import java.io.IOException;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.FileCopyUtils;

/**
 * //INT-2275
 *
 * @author Artem Bilan
 * @author Gary Russell
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class FileOutboundChannelAdapterInsideChainTests {

	public static final String TEST_FILE_NAME = FileOutboundChannelAdapterInsideChainTests.class.getSimpleName();

	public static final String WORK_DIR_NAME = System.getProperty("java.io.tmpdir") + "/" + FileOutboundChannelAdapterInsideChainTests.class.getSimpleName() + "Dir";

	public static final String SAMPLE_CONTENT = "test";

	public static Properties placeholderProperties = new Properties();

	static {
		placeholderProperties.put("test.file", TEST_FILE_NAME);
		placeholderProperties.put("work.dir", WORK_DIR_NAME);
	}

	@Autowired
	private MessageChannel outboundChainChannel;

	private static File workDir;

	@BeforeClass
	public static void setupClass() {
		workDir = new File(WORK_DIR_NAME);
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

	@Test //INT-2275
	public void testFileOutboundChannelAdapterWithinChain() throws IOException {
		Message<String> message = MessageBuilder.withPayload(SAMPLE_CONTENT).build();
		outboundChainChannel.send(message);
		File testFile = new File(workDir, TEST_FILE_NAME);
		assertThat(testFile.exists()).isTrue();
		byte[] testFileContent = FileCopyUtils.copyToByteArray(testFile);
		assertThat(SAMPLE_CONTENT).isEqualTo(new String(testFileContent));
	}

}
