package com.nixus.raop.player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.nixus.raop.core.Service;
import com.nixus.raop.core.ServiceContext;
import com.nixus.raop.core.ServiceFactory;

public class PlayerFactory implements ServiceFactory {
    
    private ServiceContext context;
    private Class<? extends Player> productclass;

    @SuppressWarnings("unchecked")
    public void startService(ServiceContext context) {
        this.context = context;
        try {
            this.productclass = (Class<? extends Player>)Class.forName(context.getProperty("product"));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid \"product\" property \""+context.getProperty("product")+"\"", e);
        }
    }

    public void stopService(ServiceContext context) {
    }

    public ServiceContext getContext() {
        return context;
    }

    public Collection<Class<?>> getServiceClasses() {
        return Collections.<Class<?>>singleton(Player.class);
    }

    public Service createService(Class<? extends Service> type, Map<String,String> properties) {
        Player service = null;
        if (type==Player.class) {
            try {
                service = productclass.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return service;
    }

    public Map<String,Object> reportState() {
        return null;
    }

}
