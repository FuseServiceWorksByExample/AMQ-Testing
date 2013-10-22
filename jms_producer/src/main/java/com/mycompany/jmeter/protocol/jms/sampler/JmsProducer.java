package com.mycompany.jmeter.protocol.jms.sampler;

import java.io.Serializable;
import java.util.UUID;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

public class JmsProducer extends AbstractJavaSamplerClient implements
		Serializable {
	private static final long serialVersionUID = 1L;

	private MessageProducer producer;
	private Session producerSession;
	private Connection producerConnection;

	private int total;

	private final static String MESSAGE = "MSH|^~\\&|EPIC|EPICADT|SMS|SMSADT|199912271408|CHARRIS|ADT^A04|1817457|D|2.5|PID||0493575^^^2^ID 1|454721||DOE^JOHN^^^^|DOE^JOHN^^^^|19480203|M||B|254 MYSTREET AVE^^MYTOWN^OH^44123^USA||(216)123-4567|||M|NON|400003403~1129086|NK1||ROE^MARIE^^^^|SPO||(216)123-4567||EC|||||||||||||||||||||||||||PV1||O|168 ~219~C~PMA^^^^^^^^^||||277^ALLEN MYLASTNAME^BONNIE^^^^|||||||||| ||2688684|||||||||||||||||||||||||199912271408||||||002376853";
	private TextMessage textMessage;

	public static void main(String[] args) {
		String host = "tcp://192.168.49.2:61616";
		String dest = "queue.TEST.TOPIC";
		boolean persistent = true;
		int aggregate = 10000;

		JmsProducer producer = new JmsProducer();
		try {

			producer.setupTest(host, dest, persistent, aggregate);
			long startTime = System.currentTimeMillis();
			producer.send(aggregate);
			long endTime = System.currentTimeMillis();
			System.out
					.println("Producer throughput = "
							+ ((float) aggregate / ((float) (endTime - startTime) / 1000f)));
			producer.teardownTest(null);
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
		defaultParameters.addArgument("DESTINATION", "TEST.TOPIC");
		defaultParameters.addArgument("AGGREGATE", "1000");
		defaultParameters.addArgument("PERSISTENT", "true");
		return defaultParameters;
	}

	public void setupTest(JavaSamplerContext context) {
		String host = context.getParameter("HOST");
		String destination = context.getParameter("DESTINATION");
		if ("AUTO".equals(destination)) {
			destination = generateTopicName();
		}
		int aggregate = Integer.parseInt(context.getParameter("AGGREGATE"));
		boolean persistent = Boolean.parseBoolean(context
				.getParameter("PERSISTENT"));
		setupTest(host, destination, persistent, aggregate);

	}

	public void teardownTest(JavaSamplerContext context) {

		try {
			producer.close();
			producerSession.close();
			producerConnection.close();
		} catch (Exception e) {
			getLogger().error(e.getMessage());
		}

	}

	private void setupTest(String host, String destination, boolean persistent,
			final int aggregate) {
		try {
			createProducer(host, destination);

			textMessage = producerSession.createTextMessage(MESSAGE);

			if (persistent) {
				producer.setDeliveryMode(DeliveryMode.PERSISTENT);
			} else {
				producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
			}

		} catch (Exception e) {
			getLogger().error(e.getMessage());
		}
	}

	private void createProducer(String host, String destination)
			throws JMSException {

		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
				host);
		Connection connection = connectionFactory.createConnection();
		connection.start();

		connection.setExceptionListener(new ExceptionListener() {

			@Override
			public void onException(JMSException e) {
				getLogger().error(e.getMessage());
			}

		});

		Session session = connection.createSession(false,
				Session.AUTO_ACKNOWLEDGE);

		Destination destinationObj = destination.startsWith("queue") ? session
				.createQueue(destination) : session.createTopic(destination);

		this.producer = session.createProducer(destinationObj);
		this.producerConnection = connection;
		this.producerSession = session;

	}

	private void send(JavaSamplerContext context) throws Exception {
		int aggregate = Integer.parseInt(context.getParameter("AGGREGATE"));
		send(aggregate);
	}

	private void send(int aggregate) throws Exception {
		total = 0;

		for (int i = 0; i < aggregate; ++i) {
			producer.send(textMessage);
			total++;
		}
	}

	public SampleResult runTest(JavaSamplerContext context) {
		SampleResult result = new SampleResult();

		try {

			result.sampleStart(); // start stopwatch

			send(context);

			result.sampleEnd(); // stop stopwatch

			result.setSuccessful(true);
			result.setResponseMessage("Sent  " + total + " messages total");
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