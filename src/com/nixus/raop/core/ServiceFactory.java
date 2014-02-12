package com.nixus.raop.core;

import java.util.*;

public interface ServiceFactory extends Service {

    public Collection<Class<?>> getServiceClasses();
    public Service createService(Class<? extends Service> type, Map<String,String> properties);

}

/*

database                autostart
auth                    autostart       requires=database
scanner                                 requires=database
normalizer                              requires=database
speaker.javasound       autostart
telnetd                 autostart
console                                 requires=database
format.mp3              autostart

FACTORIES
player
daap
http
zeroconf

*/
