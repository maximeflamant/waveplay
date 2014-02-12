package com.nixus.raop.events;

public class SpeakerEvent {

	public enum State {ON,OFF};
	
	private State currentState = State.OFF;
	private String ip = "";
	private String name;
	private String protocol;
	private String port;
	
	
	
	public SpeakerEvent(State currentState, String ip, String name,  String protocol,String port) {
		super();
		this.currentState = currentState;
		this.ip = ip;
		this.name = name;
		this.protocol = protocol;
		this.port = port;
	}
	
	public SpeakerEvent(String ip, String name, String protocol,String port) {
		super();
		this.ip = ip;
		this.name = name;
		this.protocol = protocol;
		this.port = port;
	}
	
	public SpeakerEvent(String ip) {
		super();
		this.ip = ip;
	}
	
	public State getCurrentState() {
		return currentState;
	}
	public void setCurrentState(State currentState) {
		this.currentState = currentState;
	}
	public String getIp() {
		return ip;
	}
	public void setIp(String ip) {
		this.ip = ip;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getPort() {
		return this.port;
	}
	
	
}
