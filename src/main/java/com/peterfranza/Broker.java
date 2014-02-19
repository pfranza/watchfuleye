package com.peterfranza;

import org.apache.activemq.broker.BrokerService;

public class Broker {

	public static void main(String[] args) throws Exception {
		BrokerService broker = new BrokerService();
		broker.addConnector(args[0]);
		broker.start();
	}

}
