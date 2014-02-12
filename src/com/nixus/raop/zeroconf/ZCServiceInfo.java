package com.nixus.raop.zeroconf;

import java.util.Map;

public interface ZCServiceInfo {

    public String getType();
    public String getName();
    public String getHost();
    public int getPort();
    public Map<String,String> getProperties();
    public String getProtocol();

}
