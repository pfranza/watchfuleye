package com.peterfranza.job;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.peterfranza.util.CronInterval;

@CronInterval
public class ServiceMessageEmail implements Job {

	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
			Properties props = new Properties();
	        Session session = Session.getDefaultInstance(props, null);
			
			Message msg = new MimeMessage(session);
	        msg.setFrom(new InternetAddress("admin@atltgames.com", "System Monitor"));
	        msg.addRecipient(Message.RecipientType.TO,  new InternetAddress("pfranza@atltgames.com", "pfranza@atltgames.com"));
	        msg.setSubject("System Health Status: " + getHealthStatus());
	        msg.setText(getHealthMessage());
	        Transport.send(msg);
		} catch(Exception e) {
			throw new JobExecutionException(e);
		}
		
	}

	private String getHealthStatus() {
		return "Good";
	}
	
	private static String getHealthMessage() throws Exception {
		
		MustacheFactory mf = new DefaultMustacheFactory();
		Mustache mustache = mf.compile("template.mustache");
		StringWriter writer = new StringWriter();
		mustache.execute(writer, createHealthSet()).flush();
		return writer.toString();
	}
	
	private static HealthMessage createHealthSet() {
		return new HealthMessage();
	}

	
	
	private static class HealthMessage {
		List<HealthMessageRecord> records() {
			return Arrays.asList(
				      new HealthMessageRecord("Item 1", "OK"),
				      new HealthMessageRecord("Item 2", "DOWN", new HealthMessageRecordFileSystem("1", "2"), new HealthMessageRecordFileSystem("3", "4"))
				    );
		}
		
		List<HealthMessageRecord> services() {
			return Arrays.asList(
				      new HealthMessageRecord("Item 3", "OK"),
				      new HealthMessageRecord("Item 4", "DOWN", new HealthMessageRecordFileSystem("1", "2"), new HealthMessageRecordFileSystem("3", "4"))
				    );
		}
	}
		
	private static class HealthMessageRecord {
		public String name;
		public String status;
		
		List<HealthMessageRecordFileSystem> filesystems;
		
		public HealthMessageRecord(String name, String status, HealthMessageRecordFileSystem ... fileSystems) {
			this.name = name;
			this.status = status;
			this.filesystems = Arrays.asList(fileSystems);
		}
	}
	
	private static class HealthMessageRecordFileSystem {
		public String label;
		public String freespace;
		
		public HealthMessageRecordFileSystem(String label, String freespace) {
			this.label = label;
			this.freespace = freespace;
		}
		
		
	}
	
	public static void main(String[] args) throws Exception {
		new ServiceMessageEmail().execute(null); 
	}

}
