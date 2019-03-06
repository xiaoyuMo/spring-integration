/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.integration.http.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.http.config.EnableIntegrationGraphController;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * @author Artem Bilan
 * @author Gary Russell
 * @since 4.3
 */
@RunWith(SpringRunner.class)
@WebAppConfiguration
@TestPropertySource(properties = "spring.application.name:testApplication")
@DirtiesContext
public class IntegrationGraphControllerTests {

	@Autowired
	private WebApplicationContext wac;

	private MockMvc mockMvc;

	@Before
	public void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
	}

	@Test
	public void testIntegrationGraphGet() throws Exception {
		this.mockMvc.perform(get("/testIntegration")
				.header(HttpHeaders.ORIGIN, "http://foo.bar.com")
				.accept(MediaType.parseMediaType("application/json;charset=UTF-8")))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/json;charset=UTF-8"))
				.andExpect(handler().handlerType(IntegrationGraphController.class))
				.andExpect(handler().methodName("getGraph"))
				.andExpect(jsonPath("$.nodes..name")
						.value(Matchers.containsInAnyOrder("nullChannel", "errorChannel",
								"_org.springframework.integration.errorLogger")))
				//				.andDo(print())
				.andExpect(jsonPath("$.contentDescriptor.name").value("testApplication"))
				.andExpect(jsonPath("$.links").exists());
	}

	@Test
	public void testIntegrationGraphControllerParser() throws Exception {
		ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(
				"IntegrationGraphControllerParserTests-context.xml", getClass());


		HandlerMapping handlerMapping =
				context.getBean(RequestMappingHandlerMapping.class.getName(), HandlerMapping.class);

		HandlerAdapter handlerAdapter = context.getBean(RequestMappingHandlerAdapter.class);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("GET");
		request.setRequestURI("/foo");
		request.addHeader(HttpHeaders.ORIGIN, "http://foo.bar.com");
		MockHttpServletResponse response = new MockHttpServletResponse();

		HandlerExecutionChain executionChain = handlerMapping.getHandler(request);
		assertThat(executionChain).isNotNull();

		Object handler = executionChain.getHandler();

		for (HandlerInterceptor handlerInterceptor : executionChain.getInterceptors()) {
			// Assert the CORS config
			assertThat(handlerInterceptor.preHandle(request, response, handler)).isTrue();
		}

		handlerAdapter.handle(request, response, handler);
		assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
		assertThat(response.getContentAsString()).contains("\"name\":\"nullChannel\",");
		assertThat(response.getContentAsString()).doesNotContain("\"name\":\"myChannel\",");

		context.getBeanFactory().registerSingleton("myChannel", new DirectChannel());

		request = new MockHttpServletRequest();
		request.setMethod("GET");
		request.setRequestURI("/foo/refresh");
		response = new MockHttpServletResponse();

		executionChain = handlerMapping.getHandler(request);
		assertThat(executionChain).isNotNull();

		handler = executionChain.getHandler();

		for (HandlerInterceptor handlerInterceptor : executionChain.getInterceptors()) {
			// Assert the CORS config
			assertThat(handlerInterceptor.preHandle(request, response, handler)).isTrue();
		}

		handlerAdapter.handle(request, response, handler);
		assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
		assertThat(response.getContentAsString()).contains("\"name\":\"myChannel\",");

		context.close();
	}

	@Configuration
	@EnableWebMvc
	@EnableIntegration
	@EnableIntegrationManagement(statsEnabled = "_org.springframework.integration.errorLogger.handler",
			countsEnabled = "!*",
			defaultLoggingEnabled = "false")
	@EnableIntegrationGraphController(path = "/testIntegration", allowedOrigins = "http://foo.bar.com")
	public static class ContextConfiguration {

	}

}
