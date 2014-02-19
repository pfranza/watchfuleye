package com.peterfranza.util;

import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.google.gson.Gson;

public class ServiceListener implements MessageListener {

	@Inject DataManager dataManager;
	
	public void onMessage(Message message) {
		try {
			com.peterfranza.job.messages.Message m = new Gson().fromJson(((TextMessage)message).getText(), com.peterfranza.job.messages.Message.class);
			dataManager.addRecords(m);
		} catch (JMSException e) {
			e.printStackTrace();
		}
	}	

}
