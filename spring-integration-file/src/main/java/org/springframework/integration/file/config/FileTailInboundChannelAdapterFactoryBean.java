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

import java.io.File;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.file.tail.ApacheCommonsFileTailingMessageProducer;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport;
import org.springframework.integration.file.tail.OSDelegatingFileTailingMessageProducer;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @author Ali Shahbour
 *
 * @since 3.0
 *
 */
public class FileTailInboundChannelAdapterFactoryBean extends AbstractFactoryBean<FileTailingMessageProducerSupport>
		implements BeanNameAware, SmartLifecycle, ApplicationEventPublisherAware {

	private volatile String nativeOptions;

	private volatile boolean enableStatusReader = true;

	private volatile Long idleEventInterval;

	private volatile File file;

	private volatile TaskExecutor taskExecutor;

	private volatile TaskScheduler taskScheduler;

	private volatile Long delay;

	private volatile Long fileDelay;

	private volatile Boolean end;

	private volatile Boolean reopen;

	private volatile FileTailingMessageProducerSupport tailAdapter;

	private volatile String beanName;

	private volatile MessageChannel outputChannel;

	private volatile MessageChannel errorChannel;

	private volatile Boolean autoStartup;

	private volatile Integer phase;

	private volatile ApplicationEventPublisher applicationEventPublisher;

	public void setNativeOptions(String nativeOptions) {
		if (StringUtils.hasText(nativeOptions)) {
			this.nativeOptions = nativeOptions;
		}
	}

	/**
	 * If false, thread for capturing stderr will not be started
	 * and stderr output will be ignored
	 * @param enableStatusReader true or false
	 * @since 4.3.6
	 */
	public void setEnableStatusReader(boolean enableStatusReader) {
		this.enableStatusReader = enableStatusReader;
	}

	/**
	 * How often to emit {@link FileTailingMessageProducerSupport.FileTailingIdleEvent}s in milliseconds.
	 * @param idleEventInterval the interval.
	 * @since 5.0
	 */
	public void setIdleEventInterval(long idleEventInterval) {
		this.idleEventInterval = idleEventInterval;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public void setTaskExecutor(TaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	public void setTaskScheduler(TaskScheduler taskScheduler) {
		this.taskScheduler = taskScheduler;
	}

	public void setDelay(Long delay) {
		this.delay = delay;
	}

	public void setFileDelay(Long fileDelay) {
		this.fileDelay = fileDelay;
	}

	public void setEnd(Boolean end) {
		this.end = end;
	}

	public void setReopen(Boolean reopen) {
		this.reopen = reopen;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	public void setOutputChannel(MessageChannel outputChannel) {
		this.outputChannel = outputChannel;
	}

	public void setErrorChannel(MessageChannel errorChannel) {
		this.errorChannel = errorChannel;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	public void setPhase(int phase) {
		this.phase = phase;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void start() {
		if (this.tailAdapter != null) {
			this.tailAdapter.start();
		}
	}

	@Override
	public void stop() {
		if (this.tailAdapter != null) {
			this.tailAdapter.stop();
		}
	}

	@Override
	public boolean isRunning() {
		return this.tailAdapter != null && this.tailAdapter.isRunning();
	}

	@Override
	public int getPhase() {
		if (this.tailAdapter != null) {
			return this.tailAdapter.getPhase();
		}
		return 0;
	}

	@Override
	public boolean isAutoStartup() {
		return this.tailAdapter != null && this.tailAdapter.isAutoStartup();
	}

	@Override
	public void stop(Runnable callback) {
		if (this.tailAdapter != null) {
			this.tailAdapter.stop(callback);
		}
		else {
			callback.run();
		}
	}

	@Override
	public Class<?> getObjectType() {
		return this.tailAdapter == null ? FileTailingMessageProducerSupport.class : this.tailAdapter.getClass();
	}

	@Override
	protected FileTailingMessageProducerSupport createInstance() throws Exception {
		FileTailingMessageProducerSupport adapter;
		if (this.delay == null && this.end == null && this.reopen == null) {
			adapter = new OSDelegatingFileTailingMessageProducer();
			((OSDelegatingFileTailingMessageProducer) adapter).setEnableStatusReader(this.enableStatusReader);
			if (this.nativeOptions != null) {
				((OSDelegatingFileTailingMessageProducer) adapter).setOptions(this.nativeOptions);
			}
		}
		else {
			Assert.isTrue(this.nativeOptions == null,
					"'native-options' is not allowed with 'delay', 'end', or 'reopen'");
			adapter = new ApacheCommonsFileTailingMessageProducer();
			if (this.delay != null) {
				((ApacheCommonsFileTailingMessageProducer) adapter).setPollingDelay(this.delay);
			}
			if (this.end != null) {
				((ApacheCommonsFileTailingMessageProducer) adapter).setEnd(this.end);
			}
			if (this.reopen != null) {
				((ApacheCommonsFileTailingMessageProducer) adapter).setReopen(this.reopen);
			}
		}
		adapter.setFile(this.file);
		if (this.taskExecutor != null) {
			adapter.setTaskExecutor(this.taskExecutor);
		}
		if (this.taskScheduler != null) {
			adapter.setTaskScheduler(this.taskScheduler);
		}
		if (this.fileDelay != null) {
			adapter.setTailAttemptsDelay(this.fileDelay);
		}
		if (this.idleEventInterval != null) {
			adapter.setIdleEventInterval(this.idleEventInterval);
		}
		adapter.setOutputChannel(this.outputChannel);
		adapter.setErrorChannel(this.errorChannel);
		adapter.setBeanName(this.beanName);
		if (this.autoStartup != null) {
			adapter.setAutoStartup(this.autoStartup);
		}
		if (this.phase != null) {
			adapter.setPhase(this.phase);
		}
		if (this.applicationEventPublisher != null) {
			adapter.setApplicationEventPublisher(this.applicationEventPublisher);
		}
		if (getBeanFactory() != null) {
			adapter.setBeanFactory(getBeanFactory()); // NOSONAR never null
		}
		adapter.afterPropertiesSet();
		this.tailAdapter = adapter;
		return adapter;
	}

}
