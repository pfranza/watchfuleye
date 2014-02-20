package com.peterfranza.job.messages;

public class Message {

	public String systemName;
	public FileSystem[] fileSystems;
	public ServiceEndpoint[] endpoints;
	public transient long timestamp;
	
	
	public static class FileSystem {
		public String label;
		public String freeSpace;
	}
	
	public static class ServiceEndpoint {
		public String label;
		public String status;
	}

}
