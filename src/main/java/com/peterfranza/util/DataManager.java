package com.peterfranza.util;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.peterfranza.job.messages.Message;
import com.peterfranza.job.messages.Message.FileSystem;
import com.peterfranza.job.messages.Message.ServiceEndpoint;

@Singleton
public class DataManager {

	private HashMap<String, Message> machines = new HashMap<String, Message>();
	private HashMap<String, ServiceEndpoint> endpoints = new HashMap<String, ServiceEndpoint>();
	
	public void addRecords(Message m) {
		
		m.timestamp = System.currentTimeMillis();
		
		if(m.fileSystems != null && m.fileSystems.length > 0) {
			machines.put(m.systemName, m);
		} 
		
		if(m.endpoints != null && m.endpoints.length > 0) {
			for(ServiceEndpoint s: m.endpoints) {
				String key = m.systemName + " => " + s.label;
				s.label = key;
				endpoints.put(key, s);
			}	
		}
	}
	
	public Collection<Message> getSystemMessages() {
		return machines.values();
	}
	
	public Collection<ServiceEndpoint> getServiceEndpoints() {
		return endpoints.values();
	}

	public String getHealthStatus() {
		boolean good = true;
		for(Message m: getSystemMessages()) {
			if(!isRecordCurrent(m)) {
				good = false;
			}
		}
		
		for(ServiceEndpoint m: getServiceEndpoints()) {
			if(!m.status.equalsIgnoreCase("OK")) {
				good = false;
			}
		}
		
		return good ? "GOOD" : "Problems Detected";
	}

	public boolean isRecordCurrent(Message m) {
		return m.timestamp >= System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);
	}
	
	public String getHealthMessage() throws Exception {		
		MustacheFactory mf = new DefaultMustacheFactory();
		Mustache mustache = mf.compile("template.mustache");
		StringWriter writer = new StringWriter();
		mustache.execute(writer, createHealthSet()).flush();
		return writer.toString();
	}
	
	private HealthMessage createHealthSet() {
		HealthMessage msg = new HealthMessage();
		for(ServiceEndpoint s: getServiceEndpoints()) {
			msg.services.add(new HealthMessageRecord(s.label, s.status));
		}
		
		for(com.peterfranza.job.messages.Message m: getSystemMessages()) {
			msg.records.add(new HealthMessageRecord(m.systemName, isRecordCurrent(m) ? "UP" : "DOWN", as(m.fileSystems)));
		}
		return msg;
	}

	
	
	private HealthMessageRecordFileSystem[] as(FileSystem[] fileSystems) {
		ArrayList<HealthMessageRecordFileSystem> records = new ArrayList<HealthMessageRecordFileSystem>();
		for(FileSystem fs: fileSystems) {
			records.add(new HealthMessageRecordFileSystem(fs.label, FileUtils.byteCountToDisplaySize(Long.valueOf(fs.freeSpace))));
		}
		return records.toArray(new HealthMessageRecordFileSystem[0]);
	}



	private static class HealthMessage {
		
		List<HealthMessageRecord> records = new ArrayList<HealthMessageRecord>();
		List<HealthMessageRecord> services = new ArrayList<HealthMessageRecord>();
		
	}
		
	@SuppressWarnings("unused")
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
	
	@SuppressWarnings("unused")
	private static class HealthMessageRecordFileSystem {
		public String label;
		public String freespace;
		
		public HealthMessageRecordFileSystem(String label, String freespace) {
			this.label = label;
			this.freespace = freespace;
		}
		
		
	}
	
}
