package com.peterfranza.util;

import org.quartz.Job;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.peterfranza.job.SystemStatusJob;
import com.peterfranza.job.WebserviceStatusJob;

public class TasksModule extends AbstractModule {

	@Override
	protected void configure() {
		Multibinder<Job> jobBinder = Multibinder.newSetBinder(binder(), Job.class);
	    	jobBinder.addBinding().to(SystemStatusJob.class);
	    	jobBinder.addBinding().to(WebserviceStatusJob.class);
	}

}
