package com.mycompany.jmeter.protocol.jms.sampler;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

//Only supporting topics for now
public class JmsConsumer extends AbstractJavaSamplerClient implements
Serializable {
	private static final long serialVersionUID = 1L;
	
	private static final long TIMEOUT = 5000L;

	private MessageConsumer consumer;
	private Session consumerSession;
	private Connection consumerConnection;
	

	private final AtomicInteger count = new AtomicInteger(0);

	public static void main(String[] args) {
		String host = "tcp://192.168.49.2:61616";
		String dest = "queue.TEST.JMS";
		boolean durable = false;
		String clientId = "me";
		int aggregate = 1000;

		JmsConsumer consumer = new JmsConsumer();
		try {
			consumer.setupTest(host, dest, aggregate, durable, clientId);
			consumer.receive(aggregate);
			consumer.teardownTest(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String generateTopicName() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	@Override
	public Arguments getDefaultParameters() {
		Arguments defaultParameters = new Arguments();
		defaultParameters.addArgument("HOST", "tcp://localhost:61616");
		defaultParameters.addArgument("CLIENT_ID",
				"${__time(YMDHMS)}${__threadNum}");
		defaultParameters.addArgument("DESTINATION", "AUTO");
		defaultParameters.addArgument("AGGREGATE", "1000");
		defaultParameters.addArgument("DURABLE", "false");
		return defaultParameters;
	}

	public void setupTest(JavaSamplerContext context) {
		String host = context.getParameter("HOST");
		
		host += "?socketBufferSize=131072";
		
		String clientId = context.getParameter("CLIENT_ID");
		String destination = context.getParameter("DESTINATION");
		if ("AUTO".equals(destination)) {
			destination = generateTopicName();
		}
		boolean durable = Boolean.parseBoolean(context.getParameter("DURABLE"));
		int aggregate = Integer.parseInt(context.getParameter("AGGREGATE"));
		setupTest(host, destination, aggregate, durable, clientId);

	}

	public void teardownTest(JavaSamplerContext context) {

		try {
			consumer.close();
			consumerSession.close();
			consumerConnection.close();
		} catch (Exception e) {
			getLogger().error(e.getMessage());
		}

	}

	private void setupTest(String host, String destination, final int aggregate, boolean durable, final String clientId) {
		try {
			
			createConsumer(host, destination, clientId, durable);

			consumer.setMessageListener(new MessageListener() {

				@Override
				public void onMessage(Message message) {
					try {
						//getLogger().debug(clientId + " consumed " + count.get());
						if (count.incrementAndGet() == aggregate) {
							synchronized (count) {
								count.notify();
							}
						}
						message.acknowledge();
					} catch (Exception e) {
						getLogger().error(e.getMessage());
					}

				}
			});
		} catch (Exception e) {
			getLogger().error(e.getMessage());
		}
	}

	private void createConsumer(String host, String destination, String clientId, boolean durable)
			throws JMSException {

		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
				host);
		
		connectionFactory.setAlwaysSessionAsync(false);
		
		connectionFactory.setDispatchAsync(false);

		
		Connection connection = connectionFactory.createConnection();
		connection.setClientID(clientId);
		connection.start();

		connection.setExceptionListener(new ExceptionListener() {

			@Override
			public void onException(JMSException e) {
				getLogger().error(e.getMessage());
			}

		});

		Session session = connection.createSession(false,
				Session.AUTO_ACKNOWLEDGE);

		Destination destinationObj = (destination.startsWith("queue") || (destination.contains("VirtualTopic")))
				?session.createQueue(destination):session.createTopic(destination);

		if(!durable){
			this.consumer = session.createConsumer(destinationObj);
		} else {
			this.consumer = session.createDurableSubscriber((Topic) destinationObj, clientId + " Subscription");
		}
		this.consumerConnection = connection;
		this.consumerSession = session;

	}

	private void receive(JavaSamplerContext context) throws Exception {
		int aggregate = Integer.parseInt(context.getParameter("AGGREGATE"));
		receive(aggregate);
	}

	private void receive(int aggregate)
			throws Exception {
		
		synchronized (count) {
			
			while (count.get() < aggregate) {
				
				try {
					count.wait(TIMEOUT);
					if(count.get() < aggregate){
						getLogger().error("JMS consumer timed out while waiting for a message. The test has been aborted.");
						return;
					}
				} catch (InterruptedException ie) {
					continue;
				}
				
			}
		}
	}

	public SampleResult runTest(JavaSamplerContext context) {
		SampleResult result = new SampleResult();

		try {
			
			count.set(1);

			result.sampleStart(); // start stopwatch

			receive(context);

			result.sampleEnd(); // stop stopwatch

			result.setSuccessful(true);
			result.setResponseMessage("Received "
					+ count.get() + " messages");
			result.setResponseCode("OK");
		} catch (Exception e) {
			result.sampleEnd(); // stop stopwatch
			result.setSuccessful(false);
			result.setResponseMessage("Exception: " + e);

			// get stack trace as a String to return as document data
			java.io.StringWriter stringWriter = new java.io.StringWriter();
			e.printStackTrace(new java.io.PrintWriter(stringWriter));
			result.setResponseData(stringWriter.toString(), null);
			result.setDataType(org.apache.jmeter.samplers.SampleResult.TEXT);
			result.setResponseCode("FAILED");
		}

		return result;
	}
}