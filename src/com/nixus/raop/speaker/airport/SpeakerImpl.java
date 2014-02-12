package com.nixus.raop.speaker.airport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nixus.raop.core.ServiceContext;
import com.nixus.raop.player.Player;
import com.nixus.raop.speaker.Speaker;

public class SpeakerImpl implements Speaker {

    private ServiceContext context;
    private volatile AirtunesSpeaker speaker;
    private volatile AirtunesManagerService managerservice;
    private Player player;
    private InetAddress host;
    private int volumeadjust;
    private boolean bigEndian;
    private int holdlen, delay;
    private byte[] holdbuf = new byte[1408];

    public String getDisplayName() {
        return context.getProperty("display");
    }

    private int getPort() {
        return Integer.parseInt(context.getProperty("port"));
    }

    private InetAddress getHost() {
        return host;
    }
    
    public Exception getError() {
        AirtunesSpeaker tempspeaker = speaker;
        return tempspeaker==null ? null : tempspeaker.getError();
    }

    public synchronized Player getPlayer() {
        return player;
    }

    public synchronized void setPlayer(Player player) {
        if (player == this.player) {
            return;
        }
        if (this.player != null) {
            this.player.removeSpeaker(this);
        }
        this.player = player;
        if (player != null) {
            player.addSpeaker(this);
        }
    }

    public int getUniqueId() {
        try {
            return Integer.parseInt(context.getProperty("uid"));
        } catch (Exception e) {
            return getDisplayName().hashCode() & 0x7FFFFFFF;
        }
    }

    public int getDelay() {
        return delay; 
    }

    public int getBufferSize() {
        return 1408;
    }

    public void write(byte[] buf, int off, int len) {
        if (!isOpen()) {
            throw new IllegalStateException("Not open for "+context.getServiceName());
        }
        if (managerservice.isListening(this)) {
            int size = 1408;
            if (holdlen != 0 && holdlen + len >= size) {
                int len1 = size - holdlen;
                System.arraycopy(buf, off, holdbuf, holdlen, len1);
                managerservice.getManager().sendAudioPacket(holdbuf, 0, size, bigEndian);
                holdlen = 0;
                off += len1;
                len -= len1;
            }
            while (len >= size) {
                managerservice.getManager().sendAudioPacket(buf, off, size, bigEndian);
                off += size;
                len -= size;
            }
            if (len != 0) {
                System.arraycopy(buf, off, holdbuf, holdlen, len);
                holdlen += len;
            }
        }
    }

    public void flush() {
        if (!isOpen()) {
            throw new IllegalStateException("Not open for "+context.getServiceName());
        }
        if (managerservice.isListening(this)) {
            managerservice.getManager().clear();
        }
        holdlen = 0;
    }

    public void drain() {
        if (!isOpen()) {
            throw new IllegalStateException("Not open for "+context.getServiceName());
        }
        if (managerservice.isListening(this)) {
            managerservice.getManager().drain();
        }
        holdlen = 0;
    }

    public boolean isOpen() {
        return managerservice != null;
    }

    public void close() {
        if (!isOpen()) {
            throw new IllegalStateException("Not open for "+context.getServiceName());
        }
        synchronized(managerservice.getManager()) {
            managerservice.getManager().removeSpeaker(speaker);
            managerservice.remove(this);
            speaker = null;
            holdlen = 0;
            managerservice = null;
        }
    }

    public void open(float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian) {
        if (sampleRate!=44100 || sampleSizeInBits!=16 || channels!=2 || !signed) {
            throw new IllegalArgumentException("Wrong params");
        }
        if (managerservice == null) {
            String playername = player.getContext().getServiceName();
            managerservice = context.getService(AirtunesManagerService.class, "name='airportmanager."+playername+"'");
            if (managerservice == null) {
                Map<String,String> props = new HashMap<String,String>();
                props.put("name", "airportmanager."+playername);
                props.put("playername", playername);
                ServiceContext newcontext = context.addService(new Class[] { AirtunesManagerService.class }, new AirtunesManagerService(), props, false);
                newcontext.start();
                managerservice = (AirtunesManagerService)newcontext.getService();
            }
        }
        this.bigEndian = bigEndian;
        holdlen = 0;
        synchronized(managerservice.getManager()) {
            speaker = managerservice.getManager().addSpeaker(getDisplayName(), getHost(), getPort(), null, context);
            managerservice.add(this);
        }
    }

    public void setGain(float gain) {
        if (speaker != null) {
            speaker.setGain(gain);
        }
    }

    public void setVolumeAdjustment(int volumeadjust) {
        this.volumeadjust = volumeadjust;
        context.putProperty("volumeadjust", Integer.toString(volumeadjust));
    }

    public int getVolumeAdjustment() {
        return volumeadjust;
    }

    public boolean hasGain() {
        return true;
    }

    //----------------------------------------------------------------------------

    public ServiceContext getContext() {
        return context;
    }

    public void startService(ServiceContext context) {
        this.context = context;
        try {
            volumeadjust = Integer.parseInt(context.getProperty("volumeadjust"));
        } catch (Exception e) { }
        try {
            delay = Integer.parseInt(context.getProperty("delay"));
        } catch (Exception e) { }
        try {
            host = InetAddress.getByName(context.getProperty("host"));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopService(ServiceContext context) {
        if (isOpen()) {
            close();
        }
    }

    public Map<String,Object> reportState() {
        Map<String,Object> map = new LinkedHashMap<String,Object>();
        map.put("name", getDisplayName());
        map.put("volumeadjust", getVolumeAdjustment());
        map.put("hasgain", hasGain());
        return map;
    }
}
