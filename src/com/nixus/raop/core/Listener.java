package com.nixus.raop.core;

/**
 * A QListener may be registered by calling {@link ServiceContext#addListener},
 * and once registered it will be notified of events. Services should
 * remove any listeners they add when they are unregistered
 */
public interface Listener {

    public void handleEvent(String name, java.util.Map<?,?> properties);

}
