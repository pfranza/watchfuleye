package com.peterfranza;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.EnvironmentConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.peterfranza.util.CronInterval;
import com.peterfranza.util.GuiceJobFactory;
import com.peterfranza.util.MessageSender;
import com.peterfranza.util.ScheduleInterval;
import com.peterfranza.util.ServiceListener;
import com.peterfranza.util.TasksModule;

public class ServiceMonitor {

	private static boolean running = true;
	
	@Inject Provider<GuiceJobFactory> jobFactory;
	@Inject Provider<Set<Job>> jobProvider;
	
	public void run() throws Exception {
	
		SchedulerFactory schedulerFactory = new StdSchedulerFactory();
		Scheduler scheduler = schedulerFactory.getScheduler();
		scheduler.setJobFactory(jobFactory.get());
		
		for(Job j: jobProvider.get()) {
						
			JobDetail jobDetail = JobBuilder.newJob(j.getClass())
					.withIdentity(j.getClass().getSimpleName()+"Job").build();

			ScheduleInterval interval = j.getClass().getAnnotation(ScheduleInterval.class);
			if(interval != null) {
				Trigger trigger = TriggerBuilder
						.newTrigger().withIdentity(j.getClass().getSimpleName()+"TriggerId")
						.withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(interval.value())
								.repeatForever()).build();
				System.out.println("Schedule " + j.getClass().getSimpleName() + " @ " + interval.value() + "sec");
				scheduler.scheduleJob(jobDetail, trigger);
			}
			
			CronInterval cron = j.getClass().getAnnotation(CronInterval.class);
			if(interval != null) {
				Trigger trigger = TriggerBuilder
						.newTrigger().withIdentity(j.getClass().getSimpleName()+"TriggerId")
						.withSchedule(CronScheduleBuilder.cronSchedule(cron.value())).build();
				System.out.println("Schedule " + j.getClass().getSimpleName() + " @ " + interval.value() + "sec");
				scheduler.scheduleJob(jobDetail, trigger);
			}
			
		}
		
		scheduler.start();
		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		final CompositeConfiguration config = new CompositeConfiguration();
			config.addConfiguration(new EnvironmentConfiguration());
			config.addConfiguration(new SystemConfiguration());
		
		String mode = config.getString("mode", "service");
		if("broker".equalsIgnoreCase(mode)) {
			Broker.main(args);
		}
		
		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(args[0]);
        Connection connection = connectionFactory.createConnection();
        connection.start();
        
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic("WatchfulEye-ServiceMonitor");
        final MessageProducer producer = session.createProducer(topic);
      
    
        Injector injector = Guice.createInjector(new TasksModule(), new AbstractModule() {
			
			@Override
			protected void configure() {
				bind(Configuration.class).toInstance(config);
				bind(MessageSender.class).toInstance(new MessageSender() {
					
					public void send(TextMessage message) throws Exception{
						producer.send(message);
					}
					
					public TextMessage createMessage() throws Exception {
						return session.createTextMessage();
					}
				});
			}
		});
        
        if("client".equalsIgnoreCase(mode)) {
        	MessageConsumer consumer = session.createConsumer(topic);
        	consumer.setMessageListener(injector.getInstance(ServiceListener.class));
        } else {
        	injector.getInstance(ServiceMonitor.class).run();
        }
        while(running) {Thread.sleep(10000);}
        System.out.println("Closing");
        connection.close();
	}

}
