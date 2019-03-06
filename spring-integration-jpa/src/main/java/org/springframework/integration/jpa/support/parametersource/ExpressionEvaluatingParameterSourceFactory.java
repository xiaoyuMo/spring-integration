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

package org.springframework.integration.jpa.support.parametersource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.integration.jpa.support.JpaParameter;
import org.springframework.integration.jpa.support.parametersource.ExpressionEvaluatingParameterSourceUtils.ParameterExpressionEvaluator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A SpEL expression based {@link ParameterSourceFactory} implementation.
 *
 * @author Gunnar Hillert
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 2.2
 */
public class ExpressionEvaluatingParameterSourceFactory implements ParameterSourceFactory {

	private static final Log LOGGER = LogFactory.getLog(ExpressionEvaluatingParameterSourceFactory.class);

	private static final Object ERROR = new Object();

	private final ParameterExpressionEvaluator expressionEvaluator = new ParameterExpressionEvaluator();

	private final List<JpaParameter> parameters = new ArrayList<>();

	public ExpressionEvaluatingParameterSourceFactory() {
		this(null);
	}

	public ExpressionEvaluatingParameterSourceFactory(@Nullable BeanFactory beanFactory) {
		if (beanFactory != null) {
			this.expressionEvaluator.setBeanFactory(beanFactory);
		}
	}

	/**
	 * Define the (optional) parameter values.
	 * @param parameters the parameters to be set
	 */
	public void setParameters(List<JpaParameter> parameters) {
		Assert.notEmpty(parameters, "parameters must not be null or empty.");

		for (JpaParameter parameter : parameters) {
			Assert.notNull(parameter, "The provided list (parameters) cannot contain null values.");
		}

		this.parameters.addAll(parameters);
		this.expressionEvaluator.getEvaluationContext().setVariable("staticParameters",
				ExpressionEvaluatingParameterSourceUtils.convertStaticParameters(parameters));

	}

	@Override
	public PositionSupportingParameterSource createParameterSource(final Object input) {
		return new ExpressionEvaluatingParameterSource(input, this.parameters, this.expressionEvaluator);
	}


	protected class ExpressionEvaluatingParameterSource implements PositionSupportingParameterSource {

		private final Object input;

		private final Map<String, Object> values = new HashMap<>();

		private final List<JpaParameter> parameters;

		private final Map<String, JpaParameter> parametersMap;

		private final ParameterExpressionEvaluator expressionEvaluator;

		protected ExpressionEvaluatingParameterSource(Object input, List<JpaParameter> parameters,
				ParameterExpressionEvaluator expressionEvaluator) {

			this.input = input;
			this.expressionEvaluator = expressionEvaluator;
			this.parameters = parameters;
			this.parametersMap = new HashMap<>(parameters.size());
			for (JpaParameter parameter : parameters) {
				this.parametersMap.put(parameter.getName(), parameter);
			}
			this.values.putAll(ExpressionEvaluatingParameterSourceUtils.convertStaticParameters(parameters));

		}

		@Override
		public Object getValueByPosition(int position) {

			Assert.isTrue(position >= 0, "The position must be non-negative.");

			if (position <= this.parameters.size()) {

				final JpaParameter parameter = this.parameters.get(position);

				if (parameter.getValue() != null) {
					return parameter.getValue();
				}

				if (parameter.getExpression() != null) {
					Expression expression;

					if (this.input instanceof Collection<?>) {
						expression = parameter.getProjectionExpression();
					}
					else {
						expression = parameter.getSpelExpression();
					}

					final Object value = this.expressionEvaluator.evaluateExpression(expression, this.input);
					if (parameter.getName() != null) {
						this.values.put(parameter.getName(), value);
					}
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Resolved expression " + expression + " to " + value);
					}
					return value;

				}

			}

			return null;

		}

		@Override
		public Object getValue(String paramName) {
			if (this.values.containsKey(paramName)) {
				return this.values.get(paramName);
			}

			if (!this.parametersMap.containsKey(paramName)) {
				JpaParameter parameter = new JpaParameter(paramName, null, paramName);
				ExpressionEvaluatingParameterSourceFactory.this.parameters.add(parameter);
				this.parametersMap.put(paramName, parameter);
			}

			JpaParameter jpaParameter = this.parametersMap.get(paramName);

			Expression expression;

			if (this.input instanceof Collection<?>) {
				expression = jpaParameter.getProjectionExpression();
			}
			else {
				expression = jpaParameter.getSpelExpression();
			}

			final Object value = this.expressionEvaluator.evaluateExpression(expression, this.input);
			this.values.put(paramName, value);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Resolved expression " + expression + " to " + value);
			}
			return value;
		}

		@Override
		public boolean hasValue(String paramName) {
			try {
				final Object value = getValue(paramName);
				if (ERROR.equals(value)) {
					return false;
				}
			}
			catch (ExpressionException e) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Could not evaluate expression", e);
				}
				this.values.put(paramName, ERROR);
				return false;
			}
			return true;
		}

	}

}
