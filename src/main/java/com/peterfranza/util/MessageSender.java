package com.peterfranza.util;

import javax.jms.TextMessage;

public interface MessageSender {

	void send(TextMessage message) throws Exception;

	TextMessage createMessage() throws Exception;
	
}
