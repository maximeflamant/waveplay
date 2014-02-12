package com.nixus.raop;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import android.os.Process;

import javax.jmdns.ServiceListener;

import org.apache.http.conn.util.InetAddressUtils;

import android.os.AsyncTask;
import android.util.Log;

import com.nixus.raop.core.PropertyManager;
import com.nixus.raop.core.ServiceContextImpl;
import com.nixus.raop.core.ServicesManager;
import com.nixus.raop.events.SpeakerEvent;
import com.nixus.raop.player.PlayerImpl;
import com.nixus.raop.speaker.airport.Finder;
import com.nixus.raop.speaker.airport.SpeakerImpl;
import com.nixus.raop.zeroconf.ZeroConfFactory;

/**
 * RaopHelper, helper for raop
 * @author Maxime Flamant
 *
 */
public class RaopHelper extends Observable implements ServicesManager{


	public static Map<String,ServiceContextImpl> contexts;

	public static PropertyManager pm;

	protected ServiceListener listener;

	private List<NativeRaopTask> raopTasks = new ArrayList<NativeRaopTask>();
	private HashMap<String,Boolean> raopSwitches = new HashMap<String,Boolean>();

	public final static String HOST_NAME = "android";

	private PlayerImpl pl;

	private static final String TAG = "RaopHelper";
	
	public RaopHelper(){
		super();
	}


	/* Speaker found
	 * @see com.nixus.raop.core.ServicesManager#addServiceContext(com.nixus.raop.core.ServiceContextImpl)
	 */
	public void addServiceContext(final ServiceContextImpl context) {
		contexts.put(context.getProperty("host")==null?context.getServiceName():context.getProperty("host"),context);

		if (context.getService() instanceof SpeakerImpl){

			setChanged();
			notifyObservers(new SpeakerEvent(SpeakerEvent.State.ON,context.getProperty("host"),context.getProperty("name").replace("speaker.airport.", ""),context.getProperty("protocol"),context.getProperty("port")));

		}
	}

	/* Speaker removed
	 * @see com.nixus.raop.core.ServicesManager#removeServiceContext(com.nixus.raop.core.ServiceContextImpl)
	 */
	public void removeServiceContext(ServiceContextImpl context) {
		//contexts.remove(context);
		//pm.remove(context.getProperty("name"));
	}

	/* Return speakers
	 * @see com.nixus.raop.core.ServicesManager#getServices()
	 */
	public Iterator<ServiceContextImpl> getServices() {
		return contexts.values().iterator();
	}

	/*
	 * Start Airplay devices discovery
	 */
	void startDeviceDiscovery() {

		this.setChanged();
		notifyObservers("Running");

		contexts = new HashMap<String, ServiceContextImpl>();

		pm =  new PropertyManager();
		pm.init();

		ServiceContextImpl sczcf = new ServiceContextImpl(new ZeroConfFactory(), "zeroconffactory",pm,this);
		sczcf.putProperty("implementation", "jmdns");
		sczcf.putProperty("bind", getLocalIpAddress());
		sczcf.putProperty("hostname", HOST_NAME);
		this.addServiceContext(sczcf);
		sczcf.start();


		ServiceContextImpl scaf = new ServiceContextImpl(new Finder(), "airportfactory",pm,this);
		this.addServiceContext(scaf);
		scaf.start();

		pl = new PlayerImpl();
		ServiceContextImpl scpl = new ServiceContextImpl(pl, "main",pm,this);
		this.addServiceContext(scpl);
		scpl.start();
	}

	/**
	 * Set value of raop switch control file
	 * @param swtch
	 */
	static void setRaopSwitch(String swtch) {
		FileWriter sfw;
		try {
			sfw = new FileWriter("/data/raop_switch");
			sfw.write(swtch);
			sfw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get local IP address
	 * @return local IP address
	 */
	static String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(inetAddress.getHostAddress())) {
						Log.v(TAG,"Found addr = " + inetAddress.getHostAddress().toString());
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex) {
			Log.e("MainPage", ex.toString());
		}
		return null;
	}

	/**
	 * Async task taking care of native raop streaming thread
	 * @author Maxime Flamant
	 *
	 */
	private class NativeRaopTask extends AsyncTask<String,String,String> {

		private String address;
		private String proto;
		private String port;
		

		public NativeRaopTask(String address, String proto, String port) {
			this.address =address;
			this.proto = proto;
			this.port = port;
		}

		@Override 
		protected String doInBackground(String... airportAddress) {
		    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
			raopSwitches.put((String) airportAddress[0], true);
			String[] args = new String[7];
			args[0] = "raop-play";
			args[1] = "--port";
			args[2] = this.port;
			args[3] = "--proto";
			args[4] = this.proto;
			args[5] = this.address;
			args[6] = "socket://";
			int pid = raopPlay(args);
			Log.v(TAG,"Process exited " + pid);
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			RaopHelper.setRaopSwitch("O");
			raopSwitches.put(address, false);
			Log.v(TAG,"Streaming terminated");
			RaopHelper.setRaopSwitch("O");
			setChanged();
			notifyObservers(new SpeakerEvent(address));
			super.onPostExecute(result);
		}
	}

	/**
	 * Start streaming on given airport address and port using the given protocol
	 * @param airportAddress airport address
	 * @param proto protocol (udp/tcp)
	 * @param port airport port
	 */
	public void startStreaming(String airportAddress, String proto,String port) {
		Log.v(TAG,"Starting streaming on " + airportAddress);

		NativeRaopTask raop = new NativeRaopTask(airportAddress,proto,port);
		raop.execute(airportAddress);
		raopTasks.add(raop);

		setChanged();
		notifyObservers("Streaming on " + getAirportName(airportAddress));

	}

	/**
	 * Stop streaming on given address
	 * @param airportAddress airport address
	 */
	public void stopStreaming(String airportAddress) {
		setRaopSwitch("0"); 
		raopSwitches.put(airportAddress, false);
		setChanged();
		notifyObservers("Not streaming");

	}

	
	/**
	 * Get Airport name for a given address
	 * @param airportAddress
	 * @return
	 */
	public String getAirportName(String airportAddress ){
		return contexts.get(airportAddress).getProperty("name").replace("speaker.airport.", "");
	}

	/**  
	 * A native method that is implemented by the
	 * 'raop_play' native library, which is packaged
	 * with this application.
	 * @param args raop_play args
	 * @return
	 */
	public native int raopPlay(String[] args);

	/*
	 * Load native libraries
	 */
	static {
		System.loadLibrary("ssl");
		System.loadLibrary("crypto");
		System.loadLibrary("samplerate");
		System.loadLibrary("raop-play");
	}


	/**
	 * Notification of readiness for streaming from native library
	 * @param ready
	 */
	public boolean notifyReady(boolean ready) {
		Log.v(TAG, "JNI process ready for streaming " + ready);
		if (ready)
			setRaopSwitch("1");
		else
			setRaopSwitch("0");

		return true;
	}
	/**
	 * Method to be invoked from native streaming library in order to 
	 * check if streaming is still active on given address
	 * @param address Airport address
	 */
	public boolean isActivated(String address) {
		return raopSwitches.get(address).booleanValue();
	}

}
