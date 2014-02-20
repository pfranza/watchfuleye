package com.peterfranza.job;

import javax.inject.Inject;

import org.apache.commons.cli.CommandLine;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.peterfranza.util.DataManager;
import com.peterfranza.util.RequiresArgument;
import com.peterfranza.util.ScheduleInterval;

@ScheduleInterval(60)
@RequiresArgument("verbose")
public class ServiceStatusPrinter implements Job {

	@Inject CommandLine options;
	@Inject DataManager dataManager;
	
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
			System.out.println(dataManager.getHealthMessage());
		} catch (Exception e) {
			throw new JobExecutionException(e); 
		}		
	}
	

}
