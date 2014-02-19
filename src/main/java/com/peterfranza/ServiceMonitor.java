package com.peterfranza;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.security.SimpleAuthenticationPlugin;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import com.google.inject.name.Names;
import com.peterfranza.util.CronInterval;
import com.peterfranza.util.GuiceJobFactory;
import com.peterfranza.util.MessageSender;
import com.peterfranza.util.RequiresArgument;
import com.peterfranza.util.ScheduleInterval;
import com.peterfranza.util.ServiceListener;
import com.peterfranza.util.TasksModule;

public class ServiceMonitor {

	private static boolean running = true;
	
	@Inject Provider<GuiceJobFactory> jobFactory;
	@Inject Provider<Set<Job>> jobProvider;
	@Inject CommandLine commandLine;
	
	public void run() throws Exception {
	
		SchedulerFactory schedulerFactory = new StdSchedulerFactory();
		Scheduler scheduler = schedulerFactory.getScheduler();
		scheduler.setJobFactory(jobFactory.get());
		
		for(Job j: jobProvider.get()) {
						
			JobDetail jobDetail = JobBuilder.newJob(j.getClass())
					.withIdentity(j.getClass().getSimpleName()+"Job").build();

			if(shouldInstall(j.getClass())) {
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
				if(cron != null) {
					Trigger trigger = TriggerBuilder
							.newTrigger().withIdentity(j.getClass().getSimpleName()+"TriggerId")
							.withSchedule(CronScheduleBuilder.cronSchedule(cron.value())).build();
					System.out.println("Schedule " + j.getClass().getSimpleName() + " @ " + interval.value() + "sec");
					scheduler.scheduleJob(jobDetail, trigger);
				}
			}
			
		}
		
		scheduler.start();
		
	}
	
	private boolean shouldInstall(Class<? extends Job> j) {
		if(j.getAnnotation(RequiresArgument.class) != null) {
			RequiresArgument req = j.getClass().getAnnotation(RequiresArgument.class);
			return commandLine.hasOption(req.value());
		}
		return true;
	}

	public static void broker(String endpoint, String username, String password) throws Exception {
		
		
		SimpleAuthenticationPlugin auth = new SimpleAuthenticationPlugin();
		auth.setAnonymousAccessAllowed(false);

		HashMap<String, String> users = new HashMap<String, String>();	
		users.put(username, password);

		Map<String, Set<Principal>> userGroups = new HashMap<String, Set<Principal>>();
			userGroups.put(username, new HashSet<Principal>());
		
		auth.setUserPasswords(users);
		auth.setUserGroups(userGroups);

		BrokerService broker = new BrokerService();
		broker.setPlugins(new BrokerPlugin[]{auth});
		broker.addConnector(endpoint);
		broker.setPersistent(false);
		broker.start();
		System.out.println("Broker Started " + endpoint);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
			
		final CommandLine cmd = setupCommandLine(args);
		if(cmd == null) {return;}
		
		
		if(cmd.hasOption("broker")) {
	       broker(cmd.getOptionValue("endpoint"), cmd.getOptionValue("username"), cmd.getOptionValue("password"));
	       Thread.sleep(1000);
		}
		
		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(cmd.getOptionValue("username"), cmd.getOptionValue("password"), cmd.getOptionValue("endpoint"));
        Connection connection = connectionFactory.createConnection();
        connection.start();
        
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic("WatchfulEye-ServiceMonitor");
        final MessageProducer producer = session.createProducer(topic);
      
    
        Injector injector = Guice.createInjector(new TasksModule(), new AbstractModule() {
			
			@Override
			protected void configure() {
				
				bind(CommandLine.class).toInstance(cmd);
				
				bind(String.class).annotatedWith(Names.named("endpoint")).toInstance(cmd.getOptionValue("endpoint"));
				bind(String.class).annotatedWith(Names.named("username")).toInstance(cmd.getOptionValue("username"));
				bind(String.class).annotatedWith(Names.named("password")).toInstance(cmd.getOptionValue("password"));
				bind(String.class).annotatedWith(Names.named("hostname")).toInstance(cmd.getOptionValue("hostname", getHostName()));
				
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
        
       
        
        if(cmd.hasOption("mailer")) {
        	MessageConsumer consumer = session.createConsumer(topic);
        	consumer.setMessageListener(injector.getInstance(ServiceListener.class));
        }
        
        if (cmd.hasOption("monitor")) {
        	injector.getInstance(ServiceMonitor.class).run();
        }
        
        while(running) {Thread.sleep(10000);}
        System.out.println("Exiting");
        connection.close();
	}

	protected static String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	public static CommandLine setupCommandLine(String[] args)
			throws ParseException {
		
		CommandLineParser parser = new GnuParser();
		
		Options options = new Options();
		options.addOption(new Option("b", "broker", false, "Start a Message Broker"));
		options.addOption("mailer", false, "Start the mailer task");
		options.addOption("monitor", false, "Monitor the current system");
		
		options.addOption(require(new Option("u", "username", true, "Authentication Username")));
		options.addOption(require(new Option("p", "password", true, "Authentication Password")));
		options.addOption(require(new Option("e", "endpoint", true, "Broker Endpoint")));
		options.addOption(require(new Option("h", "hostname", true, "Machines Hostname")));

		try {		
			return parser.parse( options, args);
		} catch(Exception e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(" ", options, true);
			return null;
		}
	}

	private static Option require(Option option) {
		option.setRequired(true);
		return option;
	}

}
