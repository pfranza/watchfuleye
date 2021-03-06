package com.peterfranza;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import com.peterfranza.util.Registration;
import com.peterfranza.util.RequiresArgument;
import com.peterfranza.util.ScheduleInterval;
import com.peterfranza.util.ServiceListener;
import com.peterfranza.util.TasksModule;

public class ServiceMonitor {

	private static boolean running = true;
	
	@Inject Provider<GuiceJobFactory> jobFactory;
	@Inject Provider<Set<Job>> jobProvider;
	@Inject CommandLine commandLine;
	
	public Registration run() throws Exception {
	
		SchedulerFactory schedulerFactory = new StdSchedulerFactory();
		final Scheduler scheduler = schedulerFactory.getScheduler();
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
					System.out.println("Schedule " + j.getClass().getSimpleName() + " @ " + cron.value());
					scheduler.scheduleJob(jobDetail, trigger);
				}
			}
			
		}
		
		scheduler.start();
		return new Registration() {
			public void unregister() {
				try {scheduler.shutdown();
				} catch (Exception e) {}
				System.out.println("Schedular Stopped");
			}
		};
	}
	
	private boolean shouldInstall(Class<? extends Job> j) {
		RequiresArgument req = j.getAnnotation(RequiresArgument.class);
		if(req != null) {
			return commandLine.hasOption(req.value());
		}
		return true;
	}

	public static Registration broker(final String endpoint, final String username, final String password) throws Exception {
		
		return new Registration() {
			
			BrokerService broker = new BrokerService();
			boolean running = true;
			
			{
				new Thread(new Runnable() {
					
					public void run() {
						try {
							while(running) {

								final SimpleAuthenticationPlugin auth = new SimpleAuthenticationPlugin();
								auth.setAnonymousAccessAllowed(false);

								HashMap<String, String> users = new HashMap<String, String>();	
								users.put(username, password);

								Map<String, Set<Principal>> userGroups = new HashMap<String, Set<Principal>>();
								userGroups.put(username, new HashSet<Principal>());

								auth.setUserPasswords(users);
								auth.setUserGroups(userGroups);

								broker.setPlugins(new BrokerPlugin[]{auth});

								broker.setTransportConnectorURIs(new String[]{endpoint});
								broker.setPersistent(false);
								broker.setBrokerName(ServiceMonitor.class.getSimpleName());
								broker.start();

								System.out.println("Broker Started " + endpoint);
								Thread.sleep(TimeUnit.HOURS.toMillis(1));
								broker.stop();
								broker.waitUntilStopped();
								System.out.println("Broker Recycling");
								broker = new BrokerService();
								Thread.sleep(2000);
							}
						} catch(Exception e) {}
					}
				}).start();
				broker.waitUntilStarted();
			}
			
			public void unregister() {
				try {
					running= false;
					broker.stop();
					broker.waitUntilStopped();
				} catch (Exception e) {}
				System.out.println("Broker Stopped " + endpoint);
			}
		};
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
			
		final CommandLine cmd = setupCommandLine(args);
		if(cmd == null) {return;}
		
		String brokerendpoint = cmd.getOptionValue("endpoint");
		String endpoint = cmd.getOptionValue("endpoint");
		
		if(!endpoint.contains("://")) {
			brokerendpoint = "tcp://" + brokerendpoint;
			endpoint = "tcp://" + endpoint;
		}
		
		long recycle = Long.valueOf(cmd.getOptionValue("recycle", "72"));
		
		ArrayList<Registration> registrations = new ArrayList<Registration>();
		while(running) {

			try { 

				for(Registration registration: registrations) {
					registration.unregister();
				}
				registrations.clear();

				if(cmd.hasOption("broker")) {
					registrations.add(broker(brokerendpoint, cmd.getOptionValue("username"), cmd.getOptionValue("password")));
					Thread.sleep(1000); 
				} 

				if(!endpoint.toLowerCase().contains("failover")) {
					endpoint = "failover:("+endpoint+")";
				}

				System.out.print("Connecting to: " + endpoint);
				final ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(cmd.getOptionValue("username"), cmd.getOptionValue("password"), endpoint);
				final Connection connection = connectionFactory.createConnection();
				connection.start();
				System.out.println(" ... done.");

				final Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
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



				if(cmd.hasOption("mailer") || cmd.hasOption("verbose")) {
					MessageConsumer consumer = session.createConsumer(topic);
					consumer.setMessageListener(injector.getInstance(ServiceListener.class));
				}

				registrations.add(injector.getInstance(ServiceMonitor.class).run());

				registrations.add(new Registration() {

					public void unregister() {
						try {
							producer.close();
							session.close();
							connection.close();
							System.out.println("Closing JMS Connection");
						} catch(Exception e){}
					}
				});

				Thread.sleep(TimeUnit.HOURS.toMillis(recycle));
				System.out.println("Recycling Connections");
			} catch(Exception e) {
				e.printStackTrace();
			}

		}
        
        
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
		options.addOption("verbose", false, "Print Status to Console Every Minute");
		options.addOption("monitor", false, "Monitor the current system");
		options.addOption("recycle", true, "How often system recycles connections");
		
		options.addOption(require(new Option("u", "username", true, "Authentication Username")));
		options.addOption(require(new Option("p", "password", true, "Authentication Password")));
		options.addOption(require(new Option("e", "endpoint", true, "Broker Endpoint")));
		options.addOption(require(new Option("h", "hostname", true, "Machines Hostname")));
		options.addOption(multi(new Option("ws", "webservices", true, "Web Services To Moniter")));

		options.addOption(new Option("mf", "mailfrom", true, "Address Mail Comes From (required if -mailer)"));
		options.addOption(multi(new Option("mt", "mailto", true, "Mail Recipients (required if -mailer)")));
			
		

		try {		
			
			CommandLine opt = parser.parse( options, args);
			if(opt.hasOption("mailer")) {
				if(!(opt.hasOption("mf") && opt.hasOption("mt"))) {
					throw new RuntimeException("Missing Required Options");
				}
			}
			return opt;
		} catch(Exception e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(" ", options, true);
			return null;
		}
	}

	private static Option multi(Option option) {
		option.setArgs(Option.UNLIMITED_VALUES);
		return option;
	}

	private static Option require(Option option) {
		option.setRequired(true);
		return option;
	}

}
