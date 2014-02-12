package com.nixus.raop.core;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;



public class ServiceContextImpl implements ServiceContext {

	private Service service;
	private Logger log;
	private volatile boolean active;
	private final String servicename;
	
	private PropertyManager props;
	private ServicesManager manager;

	public ServiceContextImpl( Service service, String servicename, PropertyManager props,ServicesManager manager) {
		this.service = service;
		this.log = Logger.getLogger(service.getClass().getCanonicalName());
		this.servicename = servicename;
		this.props = props;
		this.manager = manager;
	}


	public Service getService() {
		return service;
	}

	public String getServiceName() {
		return servicename;
	}

	public void debug(String message) {
		log.fine(message);
	}

	public void debug(String message, Throwable e) {
		log.log(Level.FINE,message,e);
	}

	public void info(String message) {
		log.info(message);
	}

	public void warn(String message, Throwable e) {
		log.log(Level.WARNING,message, e);
	}

	public void error(String message, Throwable e) {
		log.log(Level.SEVERE, message, e);
	}


	/**
	 * Fire an event - the Listeners registered with the server will be notified.
	 * Properties can be specified easily with the second parameter, which is a sequence
	 * of [key, value] entries - eg
	 * <pre>
	 *  qtunes.fireEvent("stateChanged", new Object[] { "track", track, "user", user });
	 * </pre>
	 * @param o an array of [key, value]
	 */
	public void fireEvent(String topic, Object[] o) {

	}


	public <E extends Service> E getService(Class<E> type) {
		return getService(type, null);
	}

//	@SuppressWarnings("unchecked")
//	public <E extends Service> E getService(Class<E> type, String filter) {
//		if (type == ZeroConf.class)
//			        	return (E) new AppleZeroConf();
////			return (E) new JmDNSZeroConf();
//		
//		return null;
//	}
	
	  @SuppressWarnings("unchecked")
	    public <E extends Service> E getService(Class<E> type, String filter) {
	        ServiceMatcher matcher = filter==null ? null : new ServiceMatcher(filter);
	        for (Iterator<ServiceContextImpl> i = manager.getServices();i.hasNext();) {
	            Service service = i.next().getService();
	            if ((type==null || type.isAssignableFrom(service.getClass())) && (matcher==null || matcher.matches(service))) {
	                return (E)service;
	            }
	        }
	        for (Iterator<ServiceContextImpl> i = manager.getServices();i.hasNext();) {
	            Service t = i.next().getService();
	            if (t instanceof ServiceFactory) {
	                ServiceFactory factory = (ServiceFactory)t;
	                Map<String,String> properties = matcher==null ? new HashMap<String,String>() : matcher.getProperties();
	                Service service = factory.createService(type, properties);
	                if (service!=null) {
	                    Class<?>[] classes = factory.getServiceClasses().toArray(new Class[0]);
	                    addService(classes, service, properties, false).start();
	                    return (E)service;
	                }
	            }
	        }
	        return null;
	    }

	@SuppressWarnings("unchecked")
	public <E extends Service> E[] getServices(Class<E> type, String filter) {
		ServiceMatcher matcher = filter==null ? null : new ServiceMatcher(filter);
		List<Object> all = new ArrayList<Object>();
		        for (Iterator<ServiceContextImpl> i = manager.getServices();i.hasNext();) {
		            Service service = i.next().getService();
		            if ((type==null || type.isAssignableFrom(service.getClass())) && (matcher==null || matcher.matches(service))) {
		                all.add(service);
		            }
		        }
		return all.toArray((E[])Array.newInstance(type, all.size()));
	}

	public void start() {
		info("Starting \""+getServiceName()+"\"");
		service.startService(this);
		active = true;
		fireEvent("started", null);
	}

	public void stop() {
		info("Stopping \""+getServiceName()+"\"");
		active = false;
		service.stopService(this);
		fireEvent("stopped", null);
		        if (!props.isPermanent(getServiceName())) {
		            props.remove(getServiceName());
		        }
	}

	public boolean isActive() {
		return active;
	}

	public ServiceContext addService(Class<?>[] classes, Service service, Map<String,String> properties, boolean permanent) {
		for (int i=0;i<classes.length;i++) {
			if (!classes[i].isAssignableFrom(service.getClass())) {
				throw new IllegalArgumentException("Service does not implement "+classes[i].getName());
			}
		}
		String servicename = properties.remove("name");
		if (servicename == null) {
			throw new IllegalStateException("No \"name\" property");
		}
		props.create(servicename, service.getClass(), properties, permanent);
		ServiceContextImpl impl = new ServiceContextImpl(service, servicename,props,manager);
		manager.addServiceContext(impl);
		return impl;
	}

	public void removeService() {
		manager.removeServiceContext(this);
	}

	public String[] getServiceNames() {
		List<String> names = new ArrayList<String>();
		        for (Iterator<ServiceContextImpl> i = manager.getServices();i.hasNext();) {
		            Service service = i.next().getService();
		            if (service.getContext() != null) {
		                names.add(service.getContext().getServiceName());
		            }
		        }
		return (String[])names.toArray(new String[0]);
	}

	public void quit() {
		//        qtunes.quit();
	}

//	@Override
//	public String getSoftwareName() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public String getServerName() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Date getServerBuildDate() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public void addListener(Listener listener) {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public void removeListener(Listener listener) {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public String[] getPropertyNames() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public String getGlobalProperty(String key) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//
//
//	@Override
//	public void putProperty(String key, String value) {
//		// TODO Auto-generated method stub
//
//	}

	
	 public String[] getPropertyNames() {
	        return props.getNames(getServiceName()).toArray(new String[0]);
	    }

	    public String getGlobalProperty(String key) {
	        return props.get(null, key);
	    }

	    public String getProperty(String key) {
	        return props.get(getServiceName(), key);
	    }

	    public void putProperty(String key, String value) {
	        props.put(getServiceName(), key, value);
	    }


	
		public String getSoftwareName() {
			// TODO Auto-generated method stub
			return null;
		}


		
		public String getServerName() {
			// TODO Auto-generated method stub
			return null;
		}


		public Date getServerBuildDate() {
			// TODO Auto-generated method stub
			return null;
		}


		public void addListener(Listener listener) {
			// TODO Auto-generated method stub
			
		}


		public void removeListener(Listener listener) {
			// TODO Auto-generated method stub
			
		}

}
