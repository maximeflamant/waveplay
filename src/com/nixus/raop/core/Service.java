package com.nixus.raop.core;

import java.util.*;

/**
 * The QTunes project is entirely made up of Services, which can be started
 * and stopped (relatively) dynamically. Each Service must implement this
 * interface
 */
public interface Service {

    /**
     * Start the Service
     * @param context the ServiceContext for this Service
     */
    public void startService(ServiceContext context);

    /**
     * Stop the Service
     * @param context the ServiceContext for this Service (same as was passed into start)
     */
    public void stopService(ServiceContext context);

    /**
     * Return the ServiceContext that was passed into {@link #startService}
     */
    public ServiceContext getContext();

    /**
     * Return a Map describing the state of this Service, for serialization back to any
     * client that needs to know (eg webplayer) - so values should be serializable objects,
     * eg Lists, Maps or simple objects. If no useful state, return null.
     */
    public Map<String,Object> reportState();

}
