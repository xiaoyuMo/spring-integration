/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.integration.websocket.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.config.IntegrationConfigurationInitializer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketConfiguration;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

/**
 * The WebSocket Integration infrastructure {@code beanFactory} initializer.
 *
 * @author Artem Bilan
 * @author Gary Russell
 *
 * @since 4.1
 */
public class WebSocketIntegrationConfigurationInitializer implements IntegrationConfigurationInitializer {

	private static final Log LOGGER = LogFactory.getLog(WebSocketIntegrationConfigurationInitializer.class);

	private static final boolean SERVLET_PRESENT =
			ClassUtils.isPresent("javax.servlet.Servlet",
					WebSocketIntegrationConfigurationInitializer.class.getClassLoader());

	private static final String WEB_SOCKET_HANDLER_MAPPING_BEAN_NAME = "integrationWebSocketHandlerMapping";

	@Override
	public void initialize(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (beanFactory instanceof BeanDefinitionRegistry) {
			this.registerEnableWebSocketIfNecessary((BeanDefinitionRegistry) beanFactory);
		}
		else {
			LOGGER.warn("'DelegatingWebSocketConfiguration' isn't registered because 'beanFactory'" +
					" isn't an instance of `BeanDefinitionRegistry`.");
		}
	}

	/**
	 * Register a {@link WebSocketHandlerMappingFactoryBean} which could also be overridden
	 * by the user by simply using {@link org.springframework.web.socket.config.annotation.EnableWebSocket}
	 * <p>
	 * In addition, checks if the {@code javax.servlet.Servlet} class is present on the classpath.
	 * When Spring Integration WebSocket support is used only as a WebSocket client,
	 * there is no reason to use and register the Spring WebSocket server components.
	 * <p>
	 * Note, there is no XML equivalent for
	 * the {@link org.springframework.web.socket.config.annotation .EnableWebSocket}
	 * in the Spring WebSocket. therefore this registration can be used to process
	 * {@link WebSocketConfigurer} implementations without annotation configuration.
	 * From other side it can be used to replace
	 * {@link org.springframework.web.socket.config.annotation.EnableWebSocket} in the Spring Integration
	 * applications when {@link org.springframework.integration.config.EnableIntegration} is in use.
	 */
	private void registerEnableWebSocketIfNecessary(BeanDefinitionRegistry registry) {
		if (SERVLET_PRESENT) {
			if (!registry.containsBeanDefinition("defaultSockJsTaskScheduler")) {
				BeanDefinitionBuilder sockJsTaskSchedulerBuilder =
						BeanDefinitionBuilder.genericBeanDefinition(ThreadPoolTaskScheduler.class)
								.addPropertyValue("threadNamePrefix", "SockJS-")
								.addPropertyValue("poolSize", Runtime.getRuntime().availableProcessors())
								.addPropertyValue("removeOnCancelPolicy", true);

				registry.registerBeanDefinition("defaultSockJsTaskScheduler",
						sockJsTaskSchedulerBuilder.getBeanDefinition());
			}

			if (!registry.containsBeanDefinition(DelegatingWebSocketConfiguration.class.getName()) &&
					!registry.containsBeanDefinition(WEB_SOCKET_HANDLER_MAPPING_BEAN_NAME)) {
				BeanDefinitionBuilder enableWebSocketBuilder =
						BeanDefinitionBuilder.genericBeanDefinition(WebSocketHandlerMappingFactoryBean.class)
								.setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
								.addPropertyReference("sockJsTaskScheduler", "defaultSockJsTaskScheduler");

				registry.registerBeanDefinition(WEB_SOCKET_HANDLER_MAPPING_BEAN_NAME,
						enableWebSocketBuilder.getBeanDefinition());
			}
		}
	}

	private static class WebSocketHandlerMappingFactoryBean extends AbstractFactoryBean<HandlerMapping>
			implements ApplicationContextAware {

		private final IntegrationServletWebSocketHandlerRegistry registry =
				new IntegrationServletWebSocketHandlerRegistry();

		private ThreadPoolTaskScheduler sockJsTaskScheduler;

		private ApplicationContext applicationContext;

		public void setSockJsTaskScheduler(ThreadPoolTaskScheduler sockJsTaskScheduler) {
			this.sockJsTaskScheduler = sockJsTaskScheduler;
		}

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.applicationContext = applicationContext;
		}

		@Override
		protected HandlerMapping createInstance() {
			BeanFactory beanFactory = getBeanFactory();
			if (beanFactory != null) {
				((ListableBeanFactory) beanFactory)
						.getBeansOfType(WebSocketConfigurer.class)
						.values()
						.forEach(configurer -> configurer.registerWebSocketHandlers(this.registry));
			}
			if (this.registry.requiresTaskScheduler()) {
				this.registry.setTaskScheduler(this.sockJsTaskScheduler);
			}
			AbstractHandlerMapping handlerMapping = this.registry.getHandlerMapping();
			handlerMapping.setApplicationContext(this.applicationContext);
			return handlerMapping;
		}

		@Override
		public Class<?> getObjectType() {
			return HandlerMapping.class;
		}

	}

	private static class IntegrationServletWebSocketHandlerRegistry extends ServletWebSocketHandlerRegistry {

		IntegrationServletWebSocketHandlerRegistry() {
			super();
		}

		@Override
		public boolean requiresTaskScheduler() { // NOSONAR visibility
			return super.requiresTaskScheduler();
		}

		@Override
		public void setTaskScheduler(TaskScheduler scheduler) { // NOSONAR visibility
			super.setTaskScheduler(scheduler);
		}

	}

}
