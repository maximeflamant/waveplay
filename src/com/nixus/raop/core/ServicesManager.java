package com.nixus.raop.core;

import java.util.Iterator;


public interface ServicesManager {
	public void addServiceContext(ServiceContextImpl context);
	public void removeServiceContext(ServiceContextImpl context);
	public Iterator<ServiceContextImpl> getServices();
}
