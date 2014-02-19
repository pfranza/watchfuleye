package com.peterfranza.job;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Named;
import javax.jms.TextMessage;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.net.util.Base64;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.gson.Gson;
import com.peterfranza.job.messages.Message;
import com.peterfranza.job.messages.Message.ServiceEndpoint;
import com.peterfranza.util.MessageSender;
import com.peterfranza.util.ScheduleInterval;

@ScheduleInterval(60)
public class WebserviceStatusJob implements Job {

	@Inject MessageSender sender;
	@Inject @Named("hostname") String hostname;
	@Inject CommandLine commandLine;
	
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		
		try {
			TextMessage message = sender.createMessage();
			message.setText(collectSystemStatistics());
			sender.send(message);  
			System.out.println("Sent message '" + message.getText() + "'");
		} catch (Exception e) {
			throw new JobExecutionException(e);
		}
	}

	public String collectSystemStatistics() throws Exception {
		Message message = new Message();
		message.systemName = hostname;
		ArrayList<ServiceEndpoint> list = new ArrayList<ServiceEndpoint>();
		for(String url: commandLine.getOptionValues("ws")) {
			URL u = new URL(url.substring(url.indexOf("||") + 2));
			System.out.println(url.substring(url.indexOf("||") + 2));
			HttpURLConnection connection = (HttpURLConnection)u.openConnection();
			if(url.contains("@")) {
				String userpass = url.substring(url.indexOf("//")+2, url.indexOf("@"));
				String basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes())).trim();
				connection.setRequestProperty ("Authorization", basicAuth);
			}
			connection.setRequestMethod("GET");
			connection.connect();

			int code = connection.getResponseCode();
			
			ServiceEndpoint e = new ServiceEndpoint();
				e.label = url.substring(0, url.indexOf("||"));
				e.status = code == 200 ? "OK" : "Error:" + code;
				list.add(e);
		}
		message.endpoints = list.toArray(new Message.ServiceEndpoint[0]);
		return new Gson().toJson(message);
	}	

	
}
