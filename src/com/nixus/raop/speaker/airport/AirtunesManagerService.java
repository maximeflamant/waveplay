package com.nixus.raop.speaker.airport;

import java.util.ArrayList;
import java.util.Map;

import com.nixus.raop.core.Service;
import com.nixus.raop.core.ServiceContext;

public class AirtunesManagerService implements Service {

    private ServiceContext context;
    private ArrayList<SpeakerImpl> speakers;
    private AirtunesManager manager;

    AirtunesManagerService() {
    }

    public void startService(ServiceContext context) {
        this.context = context;
        speakers = new ArrayList<SpeakerImpl>();
        manager = new AirtunesManager(context.getProperty("playername"), context);
    }

    public void stopService(ServiceContext context) {
    }

    public ServiceContext getContext() {
        return context;
    }

    AirtunesManager getManager() {
        return manager;
    }

    void add(SpeakerImpl speaker) {
        synchronized(speakers) {
            if (!speakers.contains(speaker)) {
                speakers.add(speaker);
            }
        }
    }

    void remove(SpeakerImpl speaker) {
        synchronized(speakers) {
            speakers.remove(speaker);
        }
    }

    boolean isListening(SpeakerImpl speaker) {
        synchronized(speakers) {
            return !speakers.isEmpty() && speaker == speakers.get(0);
        }
    }

    public Map<String,Object> reportState() {
        return null;
    }

}
