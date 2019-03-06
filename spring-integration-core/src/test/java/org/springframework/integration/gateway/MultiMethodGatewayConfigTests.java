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

package org.springframework.integration.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Artem Bilan
 *
 * @since 2.0
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class MultiMethodGatewayConfigTests {

	@Autowired
	private ApplicationContext applicationContext;


	@Test
	public void validateGatewayMethods() {
		TestGateway gateway = this.applicationContext.getBean("myGateway", TestGateway.class);
		String parentClassName = "org.springframework.integration.gateway.MultiMethodGatewayConfigTests";
		assertThat(parentClassName + "$TestBeanA:oleg").isEqualTo(gateway.echo("oleg"));
		assertThat(parentClassName + "$TestBeanB:oleg").isEqualTo(gateway.echoUpperCase("oleg"));
		assertThat(parentClassName + "$TestBeanC:oleg").isEqualTo(gateway.echoViaDefault("oleg"));
	}


	public static class TestBeanA {

		public String echo(String str) {
			return this.getClass().getName() + ":" + str;
		}

	}

	public static class TestBeanB {

		public String echo(String str) {
			return this.getClass().getName() + ":" + str;
		}

	}

	public static class TestBeanC {

		public String echo(String str) {
			return this.getClass().getName() + ":" + str;
		}

	}

	public interface TestGateway {

		String echo(String str);

		String echoViaDefault(String str);

		String echoUpperCase(String str);

	}

}
