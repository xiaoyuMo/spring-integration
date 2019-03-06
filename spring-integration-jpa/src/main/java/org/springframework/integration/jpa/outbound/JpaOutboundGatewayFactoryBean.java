/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.integration.jpa.outbound;

import java.util.List;

import org.aopalliance.aop.Advice;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.integration.jpa.core.JpaExecutor;
import org.springframework.integration.jpa.support.OutboundGatewayType;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * The {@link JpaOutboundGatewayFactoryBean} creates instances of the
 * {@link JpaOutboundGateway}. Optionally this
 * {@link org.springframework.beans.factory.FactoryBean} will add Aop Advices (e.g.
 * {@link org.springframework.transaction.interceptor.TransactionInterceptor} to the
 * {@link JpaOutboundGateway} instance.
 *
 * @author Amol Nayak
 * @author Gunnar Hillert
 * @author Gary Russell
 * @author Artem Bilan
 * @since 2.2
 *
 */
public class JpaOutboundGatewayFactoryBean extends AbstractFactoryBean<JpaOutboundGateway> {

	private JpaExecutor jpaExecutor;

	private OutboundGatewayType gatewayType = OutboundGatewayType.UPDATING;

	/**
	 * &lt;request-handler-advice-chain /&gt; only applies to the handleRequestMessage.
	 */
	private List<Advice> adviceChain;

	private boolean producesReply = true;

	private MessageChannel outputChannel;

	private int order;

	private long replyTimeout;

	private boolean requiresReply = false;

	private String componentName;

	public JpaOutboundGatewayFactoryBean() {
	}

	public void setJpaExecutor(JpaExecutor jpaExecutor) {
		this.jpaExecutor = jpaExecutor;
	}

	public void setGatewayType(OutboundGatewayType gatewayType) {
		this.gatewayType = gatewayType;
	}

	public void setAdviceChain(List<Advice> adviceChain) {
		this.adviceChain = adviceChain;
	}

	public void setProducesReply(boolean producesReply) {
		this.producesReply = producesReply;
	}

	public void setOutputChannel(MessageChannel outputChannel) {
		this.outputChannel = outputChannel;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * Specifies the time the gateway will wait to send the result to the reply channel.
	 * Only applies when the reply channel itself might block the send
	 * (for example a bounded QueueChannel that is currently full).
	 * By default the Gateway will wait indefinitely.
	 * @param replyTimeout The timeout in milliseconds
	 */
	public void setReplyTimeout(long replyTimeout) {
		this.replyTimeout = replyTimeout;
	}

	public void setRequiresReply(boolean requiresReply) {
		this.requiresReply = requiresReply;
	}

	/**
	 * Sets the name of the handler component.
	 * @param componentName The component name.
	 */
	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	@Override
	public Class<?> getObjectType() {
		return MessageHandler.class;
	}

	@Override
	protected JpaOutboundGateway createInstance() {
		JpaOutboundGateway jpaOutboundGateway = new JpaOutboundGateway(this.jpaExecutor);
		jpaOutboundGateway.setGatewayType(this.gatewayType);
		jpaOutboundGateway.setProducesReply(this.producesReply);
		jpaOutboundGateway.setOutputChannel(this.outputChannel);
		jpaOutboundGateway.setOrder(this.order);
		jpaOutboundGateway.setSendTimeout(this.replyTimeout);
		jpaOutboundGateway.setRequiresReply(this.requiresReply);
		jpaOutboundGateway.setComponentName(this.componentName);
		if (this.adviceChain != null) {
			jpaOutboundGateway.setAdviceChain(this.adviceChain);
		}
		BeanFactory beanFactory = getBeanFactory();
		if (beanFactory != null) {
			jpaOutboundGateway.setBeanFactory(beanFactory);
		}
		jpaOutboundGateway.afterPropertiesSet();
		return jpaOutboundGateway;
	}

}
