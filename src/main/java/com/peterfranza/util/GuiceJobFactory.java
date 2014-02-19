package com.peterfranza.util;

import javax.inject.Inject;

import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Injector;

public class GuiceJobFactory implements JobFactory {  

	@Inject  
    private Injector injector;  
    
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler)  
            throws SchedulerException {  
        return (Job) injector.getInstance(bundle.getJobDetail().getJobClass());  
    }  
	
}
