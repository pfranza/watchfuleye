package com.peterfranza.job;

import java.io.File;
import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Named;
import javax.jms.TextMessage;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.gson.Gson;
import com.peterfranza.job.messages.Message;
import com.peterfranza.job.messages.Message.FileSystem;
import com.peterfranza.util.MessageSender;
import com.peterfranza.util.ScheduleInterval;

@ScheduleInterval(60)
public class SystemStatusJob implements Job {

	@Inject MessageSender sender;
	@Inject @Named("hostname") String hostname;
	
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
			TextMessage message = sender.createMessage();
			message.setText(collectSystemStatistics());
			sender.send(message);  
		} catch (Exception e) {
			throw new JobExecutionException(e);
		}
	}

	public String collectSystemStatistics() throws Exception {
		Message message = new Message();
			message.systemName = hostname;
			ArrayList<FileSystem> list = new ArrayList<FileSystem>();
			for(File sysDrive : File.listRoots()){
				FileSystem f = new FileSystem();
				f.label = sysDrive.getAbsolutePath();
				f.freeSpace = "" + sysDrive.getFreeSpace();
				list.add(f);
			} 
			message.fileSystems = list.toArray(new Message.FileSystem[0]);
		return new Gson().toJson(message);
	}	

	
}
