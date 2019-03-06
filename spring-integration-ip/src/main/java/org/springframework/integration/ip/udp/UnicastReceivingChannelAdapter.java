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

package org.springframework.integration.ip.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.ip.AbstractInternetProtocolReceivingChannelAdapter;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;

/**
 * A channel adapter to receive incoming UDP packets. Packets can optionally be preceded by a
 * 4 byte length field, used to validate that all data was received. Packets may also contain
 * information indicating an acknowledgment needs to be sent.
 *
 * @author Gary Russell
 * @since 2.0
 */
public class UnicastReceivingChannelAdapter extends AbstractInternetProtocolReceivingChannelAdapter {

	private volatile DatagramSocket socket;

	private final DatagramPacketMessageMapper mapper = new DatagramPacketMessageMapper();

	private volatile int soSendBufferSize = -1;

	private static Pattern addressPattern = Pattern.compile("([^:]*):([0-9]*)");


	/**
	 * Constructs a UnicastReceivingChannelAdapter that listens on the specified port.
	 * @param port The port.
	 */
	public UnicastReceivingChannelAdapter(int port) {
		super(port);
		this.mapper.setLengthCheck(false);
	}

	/**
	 * Constructs a UnicastReceivingChannelAdapter that listens for packets on
	 * the specified port. Enables setting the lengthCheck option, which expects
	 * a length to precede the incoming packets.
	 * @param port The port.
	 * @param lengthCheck If true, enables the lengthCheck Option.
	 */
	public UnicastReceivingChannelAdapter(int port, boolean lengthCheck) {
		super(port);
		this.mapper.setLengthCheck(lengthCheck);
	}

	/**
	 * @param lengthCheck if true, the incoming packet is expected to have a four
	 * byte binary length header.
	 * @since 5.0
	 */
	public void setLengthCheck(boolean lengthCheck) {
		this.mapper.setLengthCheck(lengthCheck);
	}

	@Override
	public boolean isLongLived() {
		return true;
	}

	@Override
	public int getPort() {
		if (this.socket == null) {
			return super.getPort();
		}
		else {
			return this.socket.getLocalPort();
		}
	}

	@Override
	protected void onInit() {
		super.onInit();
		this.mapper.setBeanFactory(this.getBeanFactory());
	}

	@Override
	public void run() {
		getSocket();

		ApplicationEventPublisher publisher = getApplicationEventPublisher();
		if (publisher != null) {
			publisher.publishEvent(new UdpServerListeningEvent(this, getPort()));
		}

		if (logger.isDebugEnabled()) {
			logger.debug("UDP Receiver running on port:" + this.getPort());
		}

		setListening(true);

		// Do as little as possible here so we can loop around and catch the next packet.
		// Just schedule the packet for processing.
		while (this.isActive()) {
			try {
				asyncSendMessage(receive());
			}
			catch (SocketTimeoutException e) {
				// continue
			}
			catch (SocketException e) {
				this.stop();
			}
			catch (Exception e) {
				if (e instanceof MessagingException) {
					throw (MessagingException) e;
				}
				throw new MessagingException("failed to receive DatagramPacket", e);
			}
		}
		setListening(false);
	}

	protected void sendAck(Message<byte[]> message) {
		MessageHeaders headers = message.getHeaders();
		Object id = headers.get(IpHeaders.ACK_ID);
		if (id == null) {
			logger.error("No " + IpHeaders.ACK_ID + " header; cannot send ack");
			return;
		}
		byte[] ack = id.toString().getBytes();
		String ackAddress = (headers.get(IpHeaders.ACK_ADDRESS, String.class)).trim(); // NOSONAR caller checks header
		Matcher mat = addressPattern.matcher(ackAddress);
		if (!mat.matches()) {
			throw new MessagingException(message,
					"Ack requested but could not decode acknowledgment address: " + ackAddress);
		}
		String host = mat.group(1);
		int port = Integer.parseInt(mat.group(2));
		InetSocketAddress whereTo = new InetSocketAddress(host, port);
		if (logger.isDebugEnabled()) {
			logger.debug("Sending ack for " + id + " to " + ackAddress);
		}
		try {
			DatagramPacket ackPack = new DatagramPacket(ack, ack.length, whereTo);
			DatagramSocket out = new DatagramSocket();
			if (this.soSendBufferSize > 0) {
				out.setSendBufferSize(this.soSendBufferSize);
			}
			out.send(ackPack);
			out.close();
		}
		catch (IOException e) {
			throw new MessagingException(message, "Failed to send acknowledgment to: " + ackAddress, e);
		}
	}

	protected boolean asyncSendMessage(final DatagramPacket packet) {
		Executor taskExecutor = getTaskExecutor();
		if (taskExecutor != null) {
			try {
				taskExecutor.execute(() -> doSend(packet));
			}
			catch (RejectedExecutionException e) {
				if (logger.isDebugEnabled()) {
					logger.debug("Adapter stopped, sending on main thread");
				}
				doSend(packet);
			}
		}
		return true;
	}

	protected void doSend(final DatagramPacket packet) {
		Message<byte[]> message = null;
		try {
			message = this.mapper.toMessage(packet);
			if (logger.isDebugEnabled()) {
				logger.debug("Received:" + message);
			}
		}
		catch (Exception e) {
			logger.error("Failed to map packet to message ", e);
		}
		if (message != null) {
			if (message.getHeaders().containsKey(IpHeaders.ACK_ADDRESS)) {
				sendAck(message);
			}
			try {
				sendMessage(message);
			}
			catch (Exception e) {
				this.logger.error("Failed to send message " + message, e);
			}
		}
	}

	protected DatagramPacket receive() throws Exception {
		final byte[] buffer = new byte[this.getReceiveBufferSize()];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		getSocket().receive(packet);
		return packet;
	}

	/**
	 * @param socket the socket to set
	 */
	public void setSocket(DatagramSocket socket) {
		this.socket = socket;
	}

	@Nullable
	protected DatagramSocket getTheSocket() {
		return this.socket;
	}

	public synchronized DatagramSocket getSocket() {
		if (this.socket == null) {
			try {
				DatagramSocket datagramSocket = null;
				String localAddress = this.getLocalAddress();
				int port = super.getPort();
				if (localAddress == null) {
					datagramSocket = port == 0 ? new DatagramSocket() : new DatagramSocket(port);
				}
				else {
					InetAddress whichNic = InetAddress.getByName(localAddress);
					datagramSocket = new DatagramSocket(new InetSocketAddress(whichNic, port));
				}
				setSocketAttributes(datagramSocket);
				this.socket = datagramSocket;
			}
			catch (IOException e) {
				throw new MessagingException("failed to create DatagramSocket", e);
			}
		}
		return this.socket;
	}

	/**
	 * Sets timeout and receive buffer size
	 *
	 * @param socket The socket.
	 * @throws SocketException Any socket exception.
	 */
	protected void setSocketAttributes(DatagramSocket socket)
			throws SocketException {
		socket.setSoTimeout(this.getSoTimeout());
		int soReceiveBufferSize = this.getSoReceiveBufferSize();
		if (soReceiveBufferSize > 0) {
			socket.setReceiveBufferSize(soReceiveBufferSize);
		}
	}

	@Override
	protected void doStop() {
		super.doStop();
		try {
			DatagramSocket datagramSocket = this.socket;
			this.socket = null;
			datagramSocket.close();
		}
		catch (Exception e) {
			// ignore
		}
	}

	@Override
	public void setSoSendBufferSize(int soSendBufferSize) {
		this.soSendBufferSize = soSendBufferSize;
	}

	public void setLookupHost(boolean lookupHost) {
		this.mapper.setLookupHost(lookupHost);
	}

	@Override
	public String getComponentType() {
		return "ip:udp-inbound-channel-adapter";
	}

}
