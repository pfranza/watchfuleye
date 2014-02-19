package com.peterfranza.job;

import java.net.HttpURLConnection;
import java.net.URL;

import javax.inject.Inject;

import org.apache.commons.net.util.Base64;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.peterfranza.util.MessageSender;
import com.peterfranza.util.ScheduleInterval;

@ScheduleInterval(60)
public class WebserviceStatusJob implements Job {

	@Inject MessageSender sender;
	
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
//		try {
//			TextMessage message = sender.createMessage();
//			message.setText(collectSystemStatistics());
//			sender.send(message);  
//			System.out.println("Sent message '" + message.getText() + "'");
//		} catch (Exception e) {
//			throw new JobExecutionException(e);
//		}
	}

//	public String collectSystemStatistics() {
//		return "HELLO JMS WORLD";
//	}	

	public static void main(String[] args) throws Exception {
		URL u = new URL("url");
		HttpURLConnection connection = (HttpURLConnection)u.openConnection();
		String userpass = "pfranza:&password";
		String basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes())).trim();
		connection.setRequestProperty ("Authorization", basicAuth);
		connection.setRequestMethod("GET");
		connection.connect();

		int code = connection.getResponseCode();
		System.out.println(code);
	}
	
}
