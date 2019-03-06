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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.GatewayHeader;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.support.utils.IntegrationUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.ReflectionUtils;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Gary Russell
 * @author Artem Bilan
 */
public class GatewayProxyFactoryBeanTests {

	@Test
	public void testRequestReplyWithAnonymousChannel() throws Exception {
		QueueChannel requestChannel = new QueueChannel();
		startResponder(requestChannel);
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.setBeanName("testGateway");
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		String result = service.requestReply("foo");
		assertThat(result).isEqualTo("foobar");
	}

	@Test
	public void testRequestReplyWithAnonymousChannelConvertedTypeViaConversionService() throws Exception {
		QueueChannel requestChannel = new QueueChannel();
		startResponder(requestChannel);
		GenericConversionService cs = new DefaultConversionService();
		Converter<String, byte[]> stringToByteConverter =
				// Has to an interface (not lambda) to honor Mockito
				new Converter<String, byte[]>() {

					@Override
					public byte[] convert(String source) {
						return source.getBytes();
					}

				};
		stringToByteConverter = spy(stringToByteConverter);
		cs.addConverter(stringToByteConverter);
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerSingleton(IntegrationUtils.INTEGRATION_CONVERSION_SERVICE_BEAN_NAME, cs);

		proxyFactory.setBeanFactory(bf);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		byte[] result = service.requestReplyInBytes("foo");
		assertThat(result.length).isEqualTo(6);
		Mockito.verify(stringToByteConverter, Mockito.times(1)).convert(Mockito.any(String.class));
	}

	@Test
	public void testOneWay() throws Exception {
		final QueueChannel requestChannel = new QueueChannel();
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		service.oneWay("test");
		Message<?> message = requestChannel.receive(1000);
		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isEqualTo("test");
	}

	@Test
	public void testSolicitResponse() throws Exception {
		QueueChannel replyChannel = new QueueChannel();
		replyChannel.send(new GenericMessage<>("foo"));
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(new DirectChannel());
		proxyFactory.setDefaultReplyChannel(replyChannel);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		String result = service.solicitResponse();
		assertThat(result).isNotNull();
		assertThat(result).isEqualTo("foo");
	}

	@Test
	public void testReceiveMessage() throws Exception {
		QueueChannel replyChannel = new QueueChannel();
		replyChannel.send(new GenericMessage<>("foo"));
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultReplyChannel(replyChannel);

		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		Message<String> message = service.getMessage();
		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isEqualTo("foo");
	}

	@Test
	public void testRequestReplyWithTypeConversion() throws Exception {
		final QueueChannel requestChannel = new QueueChannel();
		new Thread(() -> {
			Message<?> input = requestChannel.receive();
			GenericMessage<String> reply = new GenericMessage<>(input.getPayload() + "456");
			((MessageChannel) input.getHeaders().getReplyChannel()).send(reply);
		}).start();
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		Integer result = service.requestReplyWithIntegers(123);
		assertThat(result).isEqualTo(new Integer(123456));
	}

	@Test
	public void testRequestReplyWithRendezvousChannelInApplicationContext() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"gatewayWithRendezvousChannel.xml", GatewayProxyFactoryBeanTests.class);
		TestService service = (TestService) context.getBean("proxy");
		String result = service.requestReply("foo");
		assertThat(result).isEqualTo("foo!!!");
		context.close();
	}

	@Test
	public void testRequestReplyWithResponseCorrelatorInApplicationContext() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"gatewayWithResponseCorrelator.xml", GatewayProxyFactoryBeanTests.class);
		TestService service = (TestService) context.getBean("proxy");
		String result = service.requestReply("foo");
		assertThat(result).isEqualTo("foo!!!");
		TestChannelInterceptor interceptor = (TestChannelInterceptor) context.getBean("interceptor");
		assertThat(interceptor.getSentCount()).isEqualTo(1);
		assertThat(interceptor.getReceivedCount()).isEqualTo(1);
		context.close();
	}

	@Test
	public void testMultipleMessagesWithResponseCorrelator() throws InterruptedException {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"gatewayWithResponseCorrelator.xml", GatewayProxyFactoryBeanTests.class);
		int numRequests = 500;
		final TestService service = (TestService) context.getBean("proxy");
		final String[] results = new String[numRequests];
		final CountDownLatch latch = new CountDownLatch(numRequests);
		ExecutorService executor = Executors.newFixedThreadPool(numRequests);
		for (int i = 0; i < numRequests; i++) {
			final int count = i;
			executor.execute(() -> {
				// add some randomness to the ordering of requests
				try {
					Thread.sleep(new Random().nextInt(100));
				}
				catch (InterruptedException e) {
					// ignore
				}
				results[count] = service.requestReply("test-" + count);
				latch.countDown();
			});
		}
		assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
		for (int i = 0; i < numRequests; i++) {
			assertThat(results[i]).isEqualTo("test-" + i + "!!!");
		}
		TestChannelInterceptor interceptor = (TestChannelInterceptor) context.getBean("interceptor");
		assertThat(interceptor.getSentCount()).isEqualTo(numRequests);
		assertThat(interceptor.getReceivedCount()).isEqualTo(numRequests);
		context.close();
		executor.shutdownNow();
	}

	@Test
	public void testMessageAsMethodArgument() throws Exception {
		QueueChannel requestChannel = new QueueChannel();
		startResponder(requestChannel);
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		String result = service.requestReplyWithMessageParameter(new GenericMessage<>("foo"));
		assertThat(result).isEqualTo("foobar");
	}

	@Test
	public void testNoArgMethodWithPayloadAnnotation() throws Exception {
		QueueChannel requestChannel = new QueueChannel();
		startResponder(requestChannel);
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		String result = service.requestReplyWithPayloadAnnotation();
		assertThat(result).isEqualTo("requestReplyWithPayloadAnnotation0bar");
	}

	@Test
	public void testMessageAsReturnValue() throws Exception {
		final QueueChannel requestChannel = new QueueChannel();
		new Thread(() -> {
			Message<?> input = requestChannel.receive();
			GenericMessage<String> reply = new GenericMessage<>(input.getPayload() + "bar");
			((MessageChannel) input.getHeaders().getReplyChannel()).send(reply);
		}).start();
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		Message<?> result = service.requestReplyWithMessageReturnValue("foo");
		assertThat(result.getPayload()).isEqualTo("foobar");
	}

	@Test
	public void testServiceMustBeInterface() {
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		int count = 0;
		try {
			proxyFactory.setServiceInterface(TestService.class);
			count++;
			proxyFactory.setServiceInterface(String.class);
			count++;
		}
		catch (IllegalArgumentException e) {
			// expected
		}
		assertThat(count).isEqualTo(1);
	}

	@Test
	public void testProxiedToStringMethod() throws Exception {
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setDefaultRequestChannel(new DirectChannel());
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		Object proxy = proxyFactory.getObject();
		String expected = "gateway proxy for";
		assertThat(proxy.toString().substring(0, expected.length())).isEqualTo(expected);
	}

	@Test(expected = TestException.class)
	public void testCheckedExceptionRethrownAsIs() throws Exception {
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		DirectChannel channel = new DirectChannel();
		EventDrivenConsumer consumer = new EventDrivenConsumer(channel, new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) {
				Method method = ReflectionUtils.findMethod(
						GatewayProxyFactoryBeanTests.class, "throwTestException");
				ReflectionUtils.invokeMethod(method, this);
			}
		});
		consumer.start();
		proxyFactory.setDefaultRequestChannel(channel);
		proxyFactory.setServiceInterface(TestExceptionThrowingInterface.class);
		proxyFactory.setBeanName("testGateway");
		proxyFactory.setBeanFactory(mock(BeanFactory.class));
		proxyFactory.afterPropertiesSet();
		TestExceptionThrowingInterface proxy = (TestExceptionThrowingInterface) proxyFactory.getObject();
		proxy.throwCheckedException("test");
	}


	private static void startResponder(final PollableChannel requestChannel) {
		new Thread(() -> {
			Message<?> input = requestChannel.receive();
			GenericMessage<String> reply = new GenericMessage<>(input.getPayload() + "bar");
			((MessageChannel) input.getHeaders().getReplyChannel()).send(reply);
		}).start();
	}

	@Test
	public void testProgrammaticWiring() throws Exception {
		GatewayProxyFactoryBean gpfb = new GatewayProxyFactoryBean();
		gpfb.setBeanFactory(mock(BeanFactory.class));
		gpfb.setServiceInterface(TestEchoService.class);
		QueueChannel drc = new QueueChannel();
		gpfb.setDefaultRequestChannel(drc);
		gpfb.setDefaultReplyTimeout(0L);
		GatewayMethodMetadata meta = new GatewayMethodMetadata();
		meta.setHeaderExpressions(Collections.singletonMap("foo", new LiteralExpression("bar")));
		gpfb.setGlobalMethodMetadata(meta);
		gpfb.afterPropertiesSet();
		((TestEchoService) gpfb.getObject()).echo("foo");
		Message<?> message = drc.receive(0);
		assertThat(message).isNotNull();
		String bar = (String) message.getHeaders().get("foo");
		assertThat(bar).isNotNull();
		assertThat(bar).isEqualTo("bar");
	}

	@Test
	public void testIdHeaderOverrideHeaderExpression() {
		GatewayProxyFactoryBean gpfb = new GatewayProxyFactoryBean();
		gpfb.setBeanFactory(mock(BeanFactory.class));

		GatewayMethodMetadata meta = new GatewayMethodMetadata();
		meta.setHeaderExpressions(Collections.singletonMap(MessageHeaders.ID, new LiteralExpression("bar")));
		gpfb.setGlobalMethodMetadata(meta);

		try {
			gpfb.afterPropertiesSet();
			fail("BeanInitializationException expected");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanInitializationException.class);
			assertThat(e.getMessage())
					.contains("Messaging Gateway cannot override 'id' and 'timestamp' read-only headers");
		}
	}

	@Test
	public void testIdHeaderOverrideGatewayHeaderAnnotation() {
		GatewayProxyFactoryBean gpfb = new GatewayProxyFactoryBean();
		gpfb.setBeanFactory(mock(BeanFactory.class));
		gpfb.setServiceInterface(HeadersOverwriteService.class);

		try {
			gpfb.afterPropertiesSet();
			fail("BeanInitializationException expected");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanInitializationException.class);
			assertThat(e.getMessage())
					.contains("Messaging Gateway cannot override 'id' and 'timestamp' read-only headers");
		}
	}

	@Test
	public void testTimeStampHeaderOverrideParamHeaderAnnotation() {
		GatewayProxyFactoryBean gpfb = new GatewayProxyFactoryBean();
		gpfb.setBeanFactory(mock(BeanFactory.class));
		gpfb.setServiceInterface(HeadersParamService.class);

		try {
			gpfb.afterPropertiesSet();
			fail("BeanInitializationException expected");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(BeanInitializationException.class);
			assertThat(e.getMessage())
					.contains("Messaging Gateway cannot override 'id' and 'timestamp' read-only headers");
		}
	}

	//	@Test
	//	public void testHistory() throws Exception {
	//		GenericApplicationContext context = new GenericApplicationContext();
	//		context.getBeanFactory().registerSingleton("historyWriter", new MessageHistoryWriter());
	//		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
	//		proxyFactory.setBeanFactory(context);
	//		proxyFactory.setBeanName("testGateway");
	//		DirectChannel channel = new DirectChannel();
	//		channel.setBeanName("testChannel");
	//		channel.setBeanFactory(context);
	//		channel.afterPropertiesSet();
	//		BridgeHandler bridgeHandler = new BridgeHandler();
	//		bridgeHandler.setBeanFactory(context);
	//		bridgeHandler.afterPropertiesSet();
	//		bridgeHandler.setBeanName("testBridge");
	//		EventDrivenConsumer consumer = new EventDrivenConsumer(channel, bridgeHandler);
	//		consumer.setBeanFactory(context);
	//		consumer.afterPropertiesSet();
	//		consumer.start();
	//		proxyFactory.setDefaultRequestChannel(channel);
	//		proxyFactory.setServiceInterface(TestEchoService.class);
	//		proxyFactory.afterPropertiesSet();
	//		TestEchoService proxy = (TestEchoService) proxyFactory.getObject();
	//		Message<?> message = proxy.echo("test");
	//		Iterator<MessageHistoryEvent> historyIterator = message.getHeaders().getHistory().iterator();
	//		MessageHistoryEvent event1 = historyIterator.next();
	//		MessageHistoryEvent event2 = historyIterator.next();
	//		MessageHistoryEvent event3 = historyIterator.next();
	//
	//		//assertEquals("echo", event1.getAttribute("method", String.class));
	//		assertEquals("gateway", event1.getType());
	//		assertEquals("testGateway", event1.getName());
	//		assertEquals("channel", event2.getType());
	//		assertEquals("testChannel", event2.getName());
	//		assertEquals("bridge", event3.getType());
	//		assertEquals("testBridge", event3.getName());
	//	}

	@Test
	public void autowiredGateway() {
		new ClassPathXmlApplicationContext("gatewayAutowiring.xml", GatewayProxyFactoryBeanTests.class).close();
	}

	@Test
	public void testOverriddenMethod() {
		GatewayProxyFactoryBean gpfb = new GatewayProxyFactoryBean(InheritChild.class);
		gpfb.setBeanFactory(mock(BeanFactory.class));
		gpfb.afterPropertiesSet();
		Map<Method, MessagingGatewaySupport> gateways = gpfb.getGateways();
		assertThat(gateways.size()).isEqualTo(2);
	}

	public static void throwTestException() throws TestException {
		throw new TestException();
	}


	interface TestEchoService {

		Message<?> echo(String s);

	}


	interface HeadersOverwriteService {

		@Gateway(headers = @GatewayHeader(name = MessageHeaders.ID, value = "id"))
		Message<?> echo(String s);

	}

	interface HeadersParamService {

		Message<?> echo(String s, @Header(MessageHeaders.TIMESTAMP) String foo);

	}


	interface TestExceptionThrowingInterface {

		String throwCheckedException(String s) throws TestException;

	}

	interface InheritSuper {

		String overridden(String in);

		String NotOverridden(String in);

	}

	interface InheritChild extends InheritSuper {

		@Override
		String overridden(String in);

	}

	@SuppressWarnings("serial")
	static class TestException extends Exception {

	}


	public static class TestClient {

		@SuppressWarnings("unused")
		private final TestService service;

		@Autowired
		public TestClient(TestService service) {
			this.service = service;
		}

	}

}
