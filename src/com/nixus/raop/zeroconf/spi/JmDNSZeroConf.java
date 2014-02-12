package com.nixus.raop.zeroconf.spi;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import com.nixus.raop.core.ServiceContext;
import com.nixus.raop.zeroconf.ZCService;
import com.nixus.raop.zeroconf.ZCServiceInfo;
import com.nixus.raop.zeroconf.ZeroConf;

public class JmDNSZeroConf implements ZeroConf {

	private ServiceContext context;
	private JmDNS jmdns;

	@SuppressWarnings("unchecked")
	public ZCService register(String type, String name, int port, Map<String,String> properties) {
		try {
			type += ".local.";
			Hashtable t = properties==null ? new Hashtable() : new Hashtable(properties);
			ServiceInfo info = ServiceInfo.create(type, name, port, 0, 0, t);
			getJmDNS().registerService(info);
			return new JmDNSService(info);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void unregister(ZCService service) {
		getJmDNS().unregisterService(((JmDNSService)service).wrapped);
	}

	public ZCServiceInfo[] list(String type, int ms) {
		type = type + ".local.";
		ServiceInfo[] list = getJmDNS().list(type, ms);
		ZCServiceInfo[] outlist = new ZCServiceInfo[list.length];
		for (int i=0;i<list.length;i++) {
			outlist[i] = new JmDNSServiceInfo(list[i]);
		}
		return outlist;
	}

	private static class JmDNSServiceInfo implements ZCServiceInfo {
		final ServiceInfo wrapped;
		private String host;
		JmDNSServiceInfo(ServiceInfo info) {
			this.wrapped = info;
		}

		public String getType() {
			return wrapped.getType();
		}

		public String getName() {
			return wrapped.getName();
		}

		public synchronized String getHost() {
			if (host == null) {
				InetAddress[] a = wrapped.getInetAddresses();
				for (int i=0;host==null && i<a.length;i++) {
					if (a[i] instanceof Inet4Address && !a[i].isLinkLocalAddress() && !a[i].isLoopbackAddress()) {
						host = a[i].getHostAddress();
					}
				}
			}
			return host;
		}

		public int getPort() {
			return wrapped.getPort();
		}

		public Map<String,String> getProperties() {
			Map<String,String> m = new LinkedHashMap<String,String>();
			for (Enumeration e = wrapped.getPropertyNames();e.hasMoreElements();) {
				String key = (String)e.nextElement();
				m.put(key, wrapped.getPropertyString(key));
			}
			return m;
		}
		
		public String getProtocol(){
			return getProperties().get("tp")==null?"":getProperties().get("tp").toLowerCase();
		}
	}

	private static class JmDNSService implements ZCService {
		final ServiceInfo wrapped;
		JmDNSService(ServiceInfo info) {
			this.wrapped = info;
		}
	}

	private synchronized JmDNS getJmDNS() {
		try {
			if (jmdns == null) {
				InetAddress bind = null;
				
				if (context.getProperty("bind") != null) {
					try {
						bind = InetAddress.getByName(context.getProperty("bind"));
					} catch (Exception e) { }
				}
				if (context.getProperty("hostname") == null) {
					jmdns = JmDNS.create(bind);
				} else {
					jmdns = JmDNS.create(bind, context.getProperty("hostname"));
				}

			}
			return jmdns;
		} catch (Exception e) { // API change safe
			if (e instanceof RuntimeException) {
				throw (RuntimeException)e;
			} else {
				throw new RuntimeException(e);
			}
		}
	}

	public void startService(ServiceContext context) {
		this.context = context;
		getJmDNS();
	}

	public void stopService(ServiceContext context) {
		try {
			getJmDNS().close();
		} catch (Exception e) {
			if (e instanceof RuntimeException) {
				throw (RuntimeException)e;
			} else {
				throw new RuntimeException(e);
			}
		}
	}

	public ServiceContext getContext() {
		return context;
	}

	public Map<String,Object> reportState() {
		return null;
	}

}
