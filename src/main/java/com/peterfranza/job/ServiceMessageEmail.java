package com.peterfranza.job;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.cli.CommandLine;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.peterfranza.util.CronInterval;
import com.peterfranza.util.DataManager;
import com.peterfranza.util.RequiresArgument;

@CronInterval
@RequiresArgument("mailer")
public class ServiceMessageEmail implements Job {

	@Inject CommandLine options;
	@Inject DataManager dataManager;
	
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
			Thread.sleep(TimeUnit.SECONDS.toMillis(65));
			Properties props = new Properties();
	        Session session = Session.getDefaultInstance(props, null);
			
			Message msg = new MimeMessage(session);
	        msg.setFrom(new InternetAddress(options.getOptionValue("mf", "system@domain.com"), "System Monitor"));
	        for(String addr: options.getOptionValues("mt")) {
	        	msg.addRecipient(Message.RecipientType.TO,  new InternetAddress(addr, addr));
	        }
	        msg.setSubject("System Health Status: " + dataManager.getHealthStatus());
	        msg.setText(dataManager.getHealthMessage());
	        Transport.send(msg);
		} catch(Exception e) {
			throw new JobExecutionException(e);
		}
		
	}
	

}
