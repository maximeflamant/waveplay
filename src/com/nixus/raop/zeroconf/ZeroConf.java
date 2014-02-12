package com.nixus.raop.zeroconf;

import java.util.Map;

import com.nixus.raop.core.Service;

/**
 * A unified API which wraps the interface for the Apple "com.apple.dnssd" package
 * or the "javax.jmdns" package, whichever is available. JmDNS prior to version 3
 * has serious problems, so Apple's interface is used if it's available.
 * Usage:
 * <pre>
 * ZeroConf zeroconf = ZeroConf.getInstance();
 * ZCService service = zeroconf.register("_test._tcp", "MyTestService", 12345, map);
 * zeroconf.unregister(service)
 *
 * ZCServiceInfo[] list = zeroconf.list("_test._tcp");
 * for (int i=0;i<list.length;i++) {
 *    String name = list[i].getName();  // MyTestService
 *    String type = list[i].getType();  // _test._tcp
 *    String host = list[i].getHost();  // hostname or IP address
 *    int port = list[i].getPort();
 *    Map properties = list[i].getProperties();   // may be null
 * }
 *
 * zeroconf.close();
 * </pre>
 */
public interface ZeroConf extends Service {
    
    public abstract ZCService register(String type, String name, int port, Map<String,String> properties);

    public abstract void unregister(ZCService service);

    /**
     * Search for a maximum of <i>ms</i> ms for services of the
     * specified type. If ms < 0 this method will wait forever
     */
    public abstract ZCServiceInfo[] list(String type, int ms);

}
