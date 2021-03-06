/*
 * Copyright 2002-2011 the original author or authors.
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
package org.springframework.integration.ip.addons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.Message;
import org.springframework.integration.MessagingException;
import org.springframework.integration.core.PollableChannel;
import org.springframework.integration.core.SubscribableChannel;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnection;
import org.springframework.integration.message.GenericMessage;
import org.springframework.test.annotation.ExpectedException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @since 2.1
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class CachingTcpConnectionFactoryTests {

	@Autowired
	SubscribableChannel outbound;

	@Autowired
	PollableChannel inbound;

	@Autowired
	AbstractServerConnectionFactory serverCf;

	@Test
	public void testReuse() throws Exception {
		AbstractClientConnectionFactory factory = mock(AbstractClientConnectionFactory.class);
		when(factory.isRunning()).thenReturn(true);
		TcpConnection mockConn1 = makeMockConnection("conn1");
		TcpConnection mockConn2 = makeMockConnection("conn2");
		when(factory.getConnection()).thenReturn(mockConn1).thenReturn(mockConn2);
		CachingTcpConnectionFactory cachingFactory = new CachingTcpConnectionFactory(factory);
		cachingFactory.start();
		TcpConnection conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn1.toString(), conn1.toString());
		conn1.close();
		conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn1.toString(), conn1.toString());
		TcpConnection conn2 = cachingFactory.getConnection();
		conn1.close();
		conn2.close();
		assertEquals("Cached:" + mockConn2.toString(), conn2.toString());
	}

	@Test
	public void testReuseClosed() throws Exception {
		AbstractClientConnectionFactory factory = mock(AbstractClientConnectionFactory.class);
		when(factory.isRunning()).thenReturn(true);
		TcpConnection mockConn1 = makeMockConnection("conn1");
		TcpConnection mockConn2 = makeMockConnection("conn2");
		when(factory.getConnection()).thenReturn(mockConn1).thenReturn(mockConn2);
		CachingTcpConnectionFactory cachingFactory = new CachingTcpConnectionFactory(factory);
		cachingFactory.start();
		TcpConnection conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn1.toString(), conn1.toString());
		conn1.close();
		conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn1.toString(), conn1.toString());
		TcpConnection conn2 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn2.toString(), conn2.toString());
		conn1.close();
		conn2.close();
		when(mockConn1.isOpen()).thenReturn(false);
		conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn2.toString(), conn1.toString());
		conn1.close();
	}

	@Test @ExpectedException(MessagingException.class)
	public void testLimit() throws Exception {
		AbstractClientConnectionFactory factory = mock(AbstractClientConnectionFactory.class);
		when(factory.isRunning()).thenReturn(true);
		TcpConnection mockConn1 = makeMockConnection("conn1");
		TcpConnection mockConn2 = makeMockConnection("conn2");
		when(factory.getConnection()).thenReturn(mockConn1).thenReturn(mockConn2);
		CachingTcpConnectionFactory cachingFactory = new CachingTcpConnectionFactory(factory);
		cachingFactory.start();
		TcpConnection conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn1.toString(), conn1.toString());
		conn1.close();
		conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn1.toString(), conn1.toString());
		final TcpConnection conn2 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn2.toString(), conn2.toString());
		@SuppressWarnings("unused")
		TcpConnection conn3 = cachingFactory.getConnection();
	}

	@Test
	public void testLimitWithTimeout() throws Exception {
		AbstractClientConnectionFactory factory = mock(AbstractClientConnectionFactory.class);
		when(factory.isRunning()).thenReturn(true);
		TcpConnection mockConn1 = makeMockConnection("conn1");
		TcpConnection mockConn2 = makeMockConnection("conn2");
		when(factory.getConnection()).thenReturn(mockConn1).thenReturn(mockConn2);
		CachingTcpConnectionFactory cachingFactory = new CachingTcpConnectionFactory(factory);
		cachingFactory.start();
		TcpConnection conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn1.toString(), conn1.toString());
		conn1.close();
		conn1 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn1.toString(), conn1.toString());
		final TcpConnection conn2 = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn2.toString(), conn2.toString());
		cachingFactory.setAvailableTimeout(1000);
		new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {}
				conn2.close();
			}
		}).start();
		TcpConnection conn2a = cachingFactory.getConnection();
		assertEquals("Cached:" + mockConn2.toString(), conn2a.toString());
		try {
			@SuppressWarnings("unused")
			TcpConnection conn3 = cachingFactory.getConnection();
			fail("Expected exception");
		} catch (MessagingException e) {}
	}

	/**
	 * @return
	 */
	private TcpConnection makeMockConnection(String name) {
		TcpConnection mockConn1 = mock(TcpConnection.class);
		when(mockConn1.getConnectionId()).thenReturn(name);
		when(mockConn1.toString()).thenReturn(name);
		when(mockConn1.isOpen()).thenReturn(true);
		doThrow(new RuntimeException("close() not expected")).when(mockConn1).close();
		return mockConn1;
	}

	@Test
	public void integrationTest() throws Exception {
		int n = 0;
		while (!serverCf.isListening()) {
			Thread.sleep(100);
			n++;
			if (n > 10000) {
				fail("Server didn't begin listening");
			}
		}
		outbound.send(new GenericMessage<String>("Hello, world!"));
		Message<?> m = inbound.receive(1000);
		assertNotNull(m);
		String connectionId = m.getHeaders().get(IpHeaders.CONNECTION_ID, String.class);
		outbound.send(new GenericMessage<String>("Hello, world!"));
		m = inbound.receive(1000);
		assertNotNull(m);
		assertEquals(connectionId, m.getHeaders().get(IpHeaders.CONNECTION_ID, String.class));

	}
}
