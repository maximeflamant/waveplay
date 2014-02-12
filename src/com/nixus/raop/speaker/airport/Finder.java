package com.nixus.raop.speaker.airport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.nixus.raop.core.Service;
import com.nixus.raop.core.ServiceContext;
import com.nixus.raop.speaker.Speaker;
import com.nixus.raop.zeroconf.ZCServiceInfo;
import com.nixus.raop.zeroconf.ZeroConf;

public class Finder implements Service, Runnable {

    private ServiceContext context;
    private Thread thread;
    private volatile boolean cancelled;
    private int delay;
    private Set<SpeakerRef> all = new HashSet<SpeakerRef>();

    public void run() {
        Set<SpeakerRef> copy = new HashSet<SpeakerRef>();
        while (!cancelled) {
            ZeroConf zeroconf = context.getService(ZeroConf.class);
            copy.clear();
            copy.addAll(all);
            if (zeroconf!=null) {
                ZCServiceInfo[] services = zeroconf.list("_raop._tcp", 6000);
                if (services!=null) {
                    for (int i=0;i<services.length;i++) {
                        SpeakerRef ref = new SpeakerRef(services[i].getName(), services[i].getHost(), services[i].getPort(),services[i].getProtocol());
                        if (!copy.remove(ref)) {
                            addSpeaker(ref);
                        }
                    }
                }
            }
            for (Iterator<SpeakerRef> i = copy.iterator();i.hasNext();) {
                removeSpeaker(i.next());
                i.remove();
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {}
        }
        for (Iterator<SpeakerRef> i = copy.iterator();i.hasNext();) {
            removeSpeaker(i.next());
        }
    }

    private void addSpeaker(SpeakerRef ref) {
        context.info("Found Airport "+ref);
        all.add(ref);
        if (context.getService(Speaker.class, "name='"+ref.name+"'") != null) {
            context.info("Duplicate Airport \""+ref.name+"\" already exists");
            ref.name += "+";
        }
        Map<String,String> properties = new HashMap<String,String>();
        properties.put("name", "speaker.airport."+ref.name);
        properties.put("display", ref.name);
        properties.put("delay", Integer.toString(delay));
        properties.put("uid", Integer.toString((int)ref.uid));
        properties.put("host", ref.host);
        properties.put("port", Integer.toString(ref.port));
        properties.put("protocol", ref.protocol);
        ServiceContext sc = context.addService(new Class[] { Speaker.class }, new SpeakerImpl(), properties, false);
        sc.start();
    }

    private void removeSpeaker(SpeakerRef ref) {
        context.info("Removing Airport "+ref);
        Speaker speaker = context.getService(Speaker.class, "name=\"speaker.airport."+ref.name+"\"");
        if (speaker!=null) {
            speaker.getContext().stop();
            speaker.getContext().removeService();
            all.remove(ref);
        }
    }

    //----------------------------------------------------------------------------

    public void startService(ServiceContext context) {
        this.context = context;
        thread = new Thread(this, "qTunes-"+context.getServiceName());
        try {
            this.delay = Integer.parseInt(context.getProperty("delay"));
        } catch (Exception e) {
            this.delay = 725;
        }
        thread.start();
    }

    public void stopService(ServiceContext context) {
        cancelled = true;
        thread.interrupt();
        thread = null;
    }

    public ServiceContext getContext() {
        return context;
    }

    private static class SpeakerRef {

        String name, host;
        int port;
        long uid;
		String protocol;

        SpeakerRef(String name, String host, int port, String protocol) {
            this.host = host;
            this.port = port;
            this.name = name;
            int ix = name.indexOf('@');
            if (ix > 0) {
                try {
                    uid = Long.parseLong(name.substring(0, ix), 16);
                    this.name = name.substring(ix+1);
                } catch (Exception e) { }
            }
            this.protocol = protocol;
        }

        public String toString() {
            return "{name="+name+", uid="+uid+", host="+host+":"+port+", protocol="+protocol+"}";
        }

        public boolean equals(Object o) {
            if (o instanceof SpeakerRef) {
                SpeakerRef ref = (SpeakerRef)o;
                return ref.host.equals(host) && ref.port==port;
            }
            return false;
        }

        public int hashCode() {
            return (host==null?"":host).hashCode() ^ port;
        }

    }

    public Map<String,Object> reportState() {
        return null;
    }

}
