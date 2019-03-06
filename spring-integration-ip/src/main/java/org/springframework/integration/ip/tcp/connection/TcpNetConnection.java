/*
 * Copyright 2001-2019 the original author or authors.
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

package org.springframework.integration.ip.tcp.connection;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.serializer.Serializer;
import org.springframework.integration.ip.tcp.serializer.SoftEndOfStreamException;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.util.Assert;

/**
 * A TcpConnection that uses and underlying {@link Socket}.
 *
 * @author Gary Russell
 * @since 2.0
 *
 */
public class TcpNetConnection extends TcpConnectionSupport implements SchedulingAwareRunnable {

	private final Socket socket;

	private volatile OutputStream socketOutputStream;

	private volatile long lastRead = System.currentTimeMillis();

	private volatile long lastSend;

	/**
	 * Constructs a TcpNetConnection for the socket.
	 * @param socket the socket
	 * @param server if true this connection was created as
	 * a result of an incoming request.
	 * @param lookupHost true if hostname lookup should be performed, otherwise the connection will
	 * be identified using the ip address.
	 * @param applicationEventPublisher the publisher to which OPEN, CLOSE and EXCEPTION events will
	 * be sent; may be null if event publishing is not required.
	 * @param connectionFactoryName the name of the connection factory creating this connection; used
	 * during event publishing, may be null, in which case "unknown" will be used.
	 */
	public TcpNetConnection(Socket socket, boolean server, boolean lookupHost,
			ApplicationEventPublisher applicationEventPublisher, String connectionFactoryName) {
		super(socket, server, lookupHost, applicationEventPublisher, connectionFactoryName);
		this.socket = socket;
	}

	@Override
	public boolean isLongLived() {
		return true;
	}

	/**
	 * Closes this connection.
	 */
	@Override
	public void close() {
		setNoReadErrorOnClose(true);
		try {
			this.socket.close();
		}
		catch (Exception e) {
		}
		super.close();
	}

	@Override
	public boolean isOpen() {
		return !this.socket.isClosed();
	}

	@Override
	@SuppressWarnings("unchecked")
	public synchronized void send(Message<?> message) throws Exception {
		if (this.socketOutputStream == null) {
			int writeBufferSize = this.socket.getSendBufferSize();
			this.socketOutputStream = new BufferedOutputStream(this.socket.getOutputStream(),
					writeBufferSize > 0 ? writeBufferSize : 8192);
		}
		Object object = getMapper().fromMessage(message);
		Assert.state(object != null, "Mapper mapped the message to 'null'.");
		this.lastSend = System.currentTimeMillis();
		try {
			((Serializer<Object>) getSerializer()).serialize(object, this.socketOutputStream);
			this.socketOutputStream.flush();
		}
		catch (Exception e) {
			publishConnectionExceptionEvent(new MessagingException(message, "Failed TCP serialization", e));
			closeConnection(true);
			throw e;
		}
		if (logger.isDebugEnabled()) {
			logger.debug(getConnectionId() + " Message sent " + message);
		}
	}

	@Override
	public Object getPayload() throws Exception {
		return getDeserializer()
				.deserialize(inputStream());
	}

	@Override
	public int getPort() {
		return this.socket.getPort();
	}

	@Override
	@Nullable
	public Object getDeserializerStateKey() {
		try {
			return inputStream();
		}
		catch (Exception e) {
			return null;
		}
	}

	@Override
	@Nullable
	public SSLSession getSslSession() {
		if (this.socket instanceof SSLSocket) {
			return ((SSLSocket) this.socket).getSession();
		}
		else {
			return null;
		}
	}

	/**
	 * Subclasses can override this, for example to wrap the input stream.
	 * @return the input stream.
	 * @throws IOException if an exception occurs.
	 * @since 5.0
	 */
	protected InputStream inputStream() throws IOException {
		return this.socket.getInputStream();
	}

	/**
	 * If there is no listener, this method exits. When there is a listener, the method
	 * runs in a loop reading input from the connection's stream, data is converted to an
	 * object using the {@link org.springframework.core.serializer.Deserializer} and the
	 * listener's {@link TcpListener#onMessage(Message)} method is called.
	 */
	@Override
	public void run() {
		boolean okToRun = true;
		if (logger.isDebugEnabled()) {
			logger.debug(getConnectionId() + " Reading...");
		}
		while (okToRun) {
			Message<?> message = null;
			try {
				message = getMapper().toMessage(this);
				this.lastRead = System.currentTimeMillis();
			}
			catch (Exception e) {
				publishConnectionExceptionEvent(e);
				if (handleReadException(e)) {
					okToRun = false;
				}
			}
			if (okToRun && message != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Message received " + message);
				}
				try {
					TcpListener listener = getListener();
					if (listener == null) {
						throw new NoListenerException("No listener");
					}
					listener.onMessage(message);
				}
				catch (NoListenerException nle) { // could also be thrown by an interceptor
					if (logger.isWarnEnabled()) {
						logger.warn("Unexpected message - no endpoint registered with connection interceptor: "
								+ getConnectionId()
								+ " - "
								+ message);
					}
				}
				catch (Exception e2) {
					logger.error("Exception sending message: " + message, e2);
				}
			}
		}
	}

	protected boolean handleReadException(Exception e) {
		boolean doClose = true;
		/*
		 * For client connections, we have to wait for 2 timeouts if the last
		 * send was within the current timeout.
		 */
		if (!isServer() && e instanceof SocketTimeoutException) {
			long now = System.currentTimeMillis();
			try {
				int soTimeout = this.socket.getSoTimeout();
				if (now - this.lastSend < soTimeout && now - this.lastRead < soTimeout * 2) {
					doClose = false;
				}
				if (!doClose && logger.isDebugEnabled()) {
					logger.debug("Skipping a socket timeout because we have a recent send " + getConnectionId());
				}
			}
			catch (SocketException e1) {
				logger.error("Error accessing soTimeout", e1);
			}
		}
		if (doClose) {
			boolean noReadErrorOnClose = isNoReadErrorOnClose();
			closeConnection(true);
			if (!(e instanceof SoftEndOfStreamException)) {
				if (e instanceof SocketTimeoutException) {
					if (logger.isDebugEnabled()) {
						logger.debug("Closed socket after timeout:" + getConnectionId());
					}
				}
				else {
					if (noReadErrorOnClose) {
						if (logger.isTraceEnabled()) {
							logger.trace("Read exception " +
									getConnectionId(), e);
						}
						else if (logger.isDebugEnabled()) {
							logger.debug("Read exception " +
									getConnectionId() + " " +
									e.getClass().getSimpleName() +
									":" + (e.getCause() != null ? e.getCause() + ":" : "") + e.getMessage());
						}
					}
					else if (logger.isTraceEnabled()) {
						logger.error("Read exception " +
								getConnectionId(), e);
					}
					else {
						logger.error("Read exception " +
								getConnectionId() + " " +
								e.getClass().getSimpleName() +
								":" + (e.getCause() != null ? e.getCause() + ":" : "") + e.getMessage());
					}
				}
				sendExceptionToListener(e);
			}
		}
		return doClose;
	}

}
