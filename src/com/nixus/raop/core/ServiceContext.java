package com.nixus.raop.core;

import java.io.IOException;
import java.io.File;
import java.util.Map;
import java.util.Date;

public interface ServiceContext {

    public void debug(String message, Throwable e);

    public void debug(String message);

    public void info(String message);

    public void warn(String message, Throwable e);

    public void error(String message, Throwable e);

    /**
     * Return the name and version of the Software
     */
    public String getSoftwareName();

    /**
     * Return the logical name of the Server 
     */
    public String getServerName();

    /**
     * Return the build date of the Server
     */
    public Date getServerBuildDate();

    /**
     * Return the unique logical name of this Service 
     */
    public String getServiceName();

    /**
     * Get the Service for this ServiceContext
     */
    public Service getService();

    /**
     * Get the first {@link Service} object of the specified type
     */
    public <E extends Service> E getService(Class<E> type);

    /**
     * Get the first {@link Service} object of the specified type
     */

    public <E extends Service> E getService(Class<E> type, String criteria);

    /**
     * Get a list of all {@link Service} objects of the specified type
     */

    public <E extends Service> E[] getServices(Class<E> type, String criteria);

    /**
     * Add a {@link Listener} to the Server, it will be notified of events
     */
    public void addListener(Listener listener);

    /**
     * Remove a {@link Listener} to the Server, it will be notified of events
     */
    public void removeListener(Listener listener);

    /**
     * Fire an event - the Listeners registered with the server will be notified.
     * Properties can be specified easily with the second parameter, which is a sequence
     * of [key, value] entries - eg
     * <pre>
     *  qtunes.fireEvent("player.add, new Object[] { "track", track, "user", user });
     * </pre>
     * @param o an array of [key, value]
     */
    public void fireEvent(String name, Object[] o);

    /**
     * Return a list of properties of this Service
     */
    public String[] getPropertyNames();

    /**
     * Get a system-wide property
     */
    public String getGlobalProperty(String key);

    /**
     * Get a property of this Service
     */
    public String getProperty(String key);

    /**
     * Set a property of this Service
     */
    public void putProperty(String key, String value);

    public void start();

    public void stop();

    /**
     * Add a service
     */
    public ServiceContext addService(Class<?>[] classes, Service service, Map<String,String> properties, boolean permanent);

    /**
     * Remove a service
     */
    public void removeService();

    /**
     * Return the list of Service names
     */
    public String[] getServiceNames();

    /**
     * Return true if the Service has been started
     */
    public boolean isActive();

}
