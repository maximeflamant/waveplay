package com.nixus.raop.zeroconf;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.nixus.raop.core.Service;
import com.nixus.raop.core.ServiceContext;
import com.nixus.raop.core.ServiceFactory;

public class ZeroConfFactory implements ServiceFactory {
    
    private ServiceContext context;

    public void startService(ServiceContext context) {
        this.context = context;
    }

    public void stopService(ServiceContext context) {
    }

    public ServiceContext getContext() {
        return context;
    }

    public Collection<Class<?>> getServiceClasses() {
        return Collections.<Class<?>>singleton(ZeroConf.class);
    }

    public Service createService(Class<? extends Service> type, Map<String,String> properties) {
        ZeroConf service = null;
        String implementation = context.getProperty("implementation");
        if (type==ZeroConf.class) {
            if (service==null && (implementation==null || implementation.equals("apple"))) {
                try {
                    service = (ZeroConf)Class.forName("com.nixus.raop.zeroconf.spi.AppleZeroConf").newInstance();
                    properties.put("name", "zeroconf.apple");
                } catch (Throwable e) {}
            }
            if (service==null && (implementation==null || implementation.equals("jmdns"))) {
                try {
                    service = (ZeroConf)Class.forName("com.nixus.raop.zeroconf.spi.JmDNSZeroConf").newInstance();
                    properties.put("name", "zeroconf.jmdns");
                    properties.put("bind", context.getProperty("bind"));
                    properties.put("hostname", context.getProperty("hostname"));
                } catch (Throwable e) {}
            }
        }
        return service;
    }

    public Map<String,Object> reportState() {
        return null;
    }
}
