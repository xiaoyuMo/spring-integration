/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.integration.support.management;

import org.springframework.context.Lifecycle;
import org.springframework.util.Assert;

/**
 * Adds {@link TrackableComponent}.
 *
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 2.0
 */
public class TrackableRouterMetrics extends RouterMetrics implements TrackableComponent {

	private final TrackableComponent trackable;

	public TrackableRouterMetrics(Lifecycle lifecycle, MappingMessageRouterManagement delegate) {
		super(lifecycle, delegate);
		Assert.isInstanceOf(TrackableComponent.class, delegate);
		this.trackable = (TrackableComponent) delegate;
	}

	@Override
	public String getBeanName() {
		return this.trackable.getBeanName();
	}

	@Override
	public String getComponentName() {
		return this.trackable.getComponentName();
	}

	@Override
	public String getComponentType() {
		return this.trackable.getComponentType();
	}

	@Override
	public void setShouldTrack(boolean shouldTrack) {
		this.trackable.setShouldTrack(shouldTrack);
	}

}
