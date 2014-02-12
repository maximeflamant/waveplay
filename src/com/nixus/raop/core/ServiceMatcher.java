package com.nixus.raop.core;

import java.util.HashMap;
import java.util.Map;

public final class ServiceMatcher extends AbstractMatcher<Service> {

    private Map<String,String> propertymap;

    public ServiceMatcher(String filter) {
        super(filter);
    }

    public Object getProperty(Service service, String key) {
        if (key.equals("class")) {
            return service.getClass();
        } else if (service.getContext() == null) {
            return null;
        } else if (key.equals("alive")) {
            return service.getContext().isActive() ? Boolean.TRUE : Boolean.FALSE;
        } else {
            return service.getContext().getProperty(key);
        }
    }

    @SuppressWarnings("unchecked")
    public boolean test(String key, String op, String testval, Object propval) {
        if (op.equals("=")) {
            if (propertymap==null) {
                propertymap = new HashMap<String,String>();
            }
            propertymap.put(key, testval);
        }
        if (key.equals("class") && (op.equals("=") || op.equals("!=")) && propval instanceof Class) {
            try {
                Class testclass = Class.forName(testval);
                return testclass.isAssignableFrom((Class)propval) == op.equals("=");
            } catch (ClassNotFoundException e) {
                return op.equals("!=");
            }
        } else {
            return super.test(key, op, testval, propval);
        }
    }

    public Map<String,String> getProperties() {
        if (propertymap==null) {
            matches(new Service() {
                public void startService(ServiceContext context) { }
                public void stopService(ServiceContext context) { }
                public ServiceContext getContext() { return null; }
                public Map<String,Object> reportState() { return null; }
            });
        }
        return propertymap;
    }

}
