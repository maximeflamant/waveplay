package com.nixus.raop.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.nixus.raop.speaker.Speaker;

/**
 * A Wrapper around multiple Speaker objects. Done this way
 * so a Player can play to multiple unsynchronzed speakers at once,
 * not sure if that's useful
 *
 * These methods are called from player thread:
 *    close drain flush write isOpen open hasSpeakers
 * Other methods are called from other threads. If they need to interact they must
 * do so carefully and synchronize on this(for mods involving no change to bitrate)
 * or tracklock(for mods involving bitrate)
 */
class MultiLine {
    
//    org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger("multiline");
    private static class SpeakerState {
        long tail = Long.MIN_VALUE;
        int bytedelay;
    }

    private Object tracklock = new Object();    // Just for synchronizing on
    private volatile int volume, msdelay;
    private volatile float trackgain;
    private PlayerImpl player;
    private byte[] queue = new byte[704000];    // ~4 secs
    private volatile long head, tail;

    private Map<Speaker,SpeakerState> speakers = new ConcurrentHashMap<Speaker,SpeakerState>();
    private float sampleRate;
    private int bitsPerChannel, channels, bytespersecond, maxbytedelay;
    private boolean signed, bigEndian;

    MultiLine(PlayerImpl player) {
        this.player = player;
    }

    // -------------------------------------------------------------------------------------
    // Methods called from any thread
    // -------------------------------------------------------------------------------------

    boolean setSpeakers(Collection<Speaker> newspeakers) {
        if (newspeakers.size()==0) {
            return false;
        }
        Collection<Speaker> add = new HashSet<Speaker>(newspeakers);
        add.removeAll(speakers.keySet());
        Collection<Speaker> rem = new HashSet<Speaker>(speakers.keySet());
        rem.removeAll(newspeakers);

        Map<Speaker,SpeakerState> tempspeakers = new ConcurrentHashMap<Speaker,SpeakerState>(speakers);
        for (Iterator<Speaker> i = add.iterator();i.hasNext();) {
            Speaker speaker = i.next();
            SpeakerState state = new SpeakerState();
            tempspeakers.put(speaker, state);
        }
        for (Iterator<Speaker> i = rem.iterator();i.hasNext();) {
            Speaker speaker = i.next();
            tempspeakers.remove(speaker);
        }

        int tempmaxbytedelay = 0;
        for (Iterator<Map.Entry<Speaker,SpeakerState>> i = tempspeakers.entrySet().iterator();i.hasNext();) {
            Map.Entry<Speaker,SpeakerState> e = i.next();
            Speaker speaker = e.getKey();
            SpeakerState state = e.getValue();
            tempmaxbytedelay = Math.max(maxbytedelay, state.bytedelay);
        }

        synchronized(tracklock) {
            if (isOpen()) {
                for (Iterator<Speaker> i = add.iterator();i.hasNext();) {
                    Speaker speaker = i.next();
                    if (!openSpeaker(speaker, tempspeakers.get(speaker))) {
                        tempspeakers.remove(speaker);
                    }
                }
            }
        }
        synchronized(this) {
            speakers = tempspeakers;
            maxbytedelay = tempmaxbytedelay;
        }
        for (Iterator<Speaker> i = rem.iterator();i.hasNext();) {
            Speaker speaker = i.next();
            if (speaker.isOpen()) {
//                log.debug("STOP, FLUSH, CLOSE "+speaker.getDisplayName());
                speaker.close();
            }
        }

        return !add.isEmpty() || !rem.isEmpty();
    }

    Collection<Speaker> getSpeakers() {
        return new ArrayList<Speaker>(speakers.keySet());
    }

    private float getSpeakerGain(Speaker speaker, int volume, float trackgain) {
        int svol = Math.max(0, Math.min(100, volume + speaker.getVolumeAdjustment()));
        double gain = svol==0 ? Float.NaN : Math.log(svol==0 ? 0.0001 : svol/100f) / Math.log(10.0) * 20;
        return (float)(gain + trackgain);
    }

    void setVolume(int volume, float trackgain) {
        this.volume = volume;
        this.trackgain = trackgain;
        for (Iterator<Speaker> i = speakers.keySet().iterator();i.hasNext();) {
            Speaker speaker = i.next();
            speaker.setGain(getSpeakerGain(speaker, volume, trackgain));
        }
    }

    // -------------------------------------------------------------------------------------
    // Methods called from both threads - don't change anything, be synchronized or volatile
    // -------------------------------------------------------------------------------------

    int getDelay() {
        return msdelay;
    }

    boolean isOpen() {
        return sampleRate != 0;
    }

    private boolean openSpeaker(Speaker speaker, SpeakerState state) {
        synchronized(tracklock) {
            speaker.open(sampleRate, bitsPerChannel, channels, signed, bigEndian);
        }
        if (speaker.getError() == null) {
//            log.debug("OPENED: setting gain to "+getSpeakerGain(speaker, volume, trackgain));
            speaker.setGain(getSpeakerGain(speaker, volume, trackgain));
            state.bytedelay = speaker.getDelay() * bytespersecond / 1000 / speaker.getBufferSize() * speaker.getBufferSize();
//            log.debug("OPEN("+sampleRate+", "+bitsPerChannel+", "+channels+", "+signed+", "+bigEndian+") & START "+speaker.getDisplayName()+" delay="+state.bytedelay);
            return true;
        } else {
            return false;
        }
    }

    // -------------------------------------------------------------------------------------
    // Methods called from player thread only
    // -------------------------------------------------------------------------------------

    synchronized boolean hasSpeakers() {
        return !speakers.isEmpty();
    }

    synchronized void open(float sampleRate, int bitsPerChannel, int channels, boolean signed, boolean bigEndian) {
        synchronized(tracklock) {
            this.sampleRate = sampleRate;
            this.bitsPerChannel = bitsPerChannel;
            this.channels = channels;
            this.signed = signed;
            this.bigEndian = bigEndian;
        }

        maxbytedelay = 0;
        this.bytespersecond = (int)Math.round(sampleRate * bitsPerChannel * channels) >> 3;
        for (Iterator<Map.Entry<Speaker,SpeakerState>> i = speakers.entrySet().iterator();i.hasNext();) {
            Map.Entry<Speaker,SpeakerState> e = i.next();
            Speaker speaker = e.getKey();
            SpeakerState state = e.getValue();
            if (!openSpeaker(speaker, state)) {
                i.remove();
            } else {
                maxbytedelay = Math.max(maxbytedelay, state.bytedelay);
            }
        }
    }

    synchronized void close() {
        for (Iterator<Map.Entry<Speaker,SpeakerState>> i = speakers.entrySet().iterator();i.hasNext();) {
            Map.Entry<Speaker,SpeakerState> e = i.next();
            Speaker speaker = e.getKey();
            SpeakerState state = e.getValue();
            speaker.close();
        }
        sampleRate = 0;
        bytespersecond = 0;
        update();
    }

    private boolean isValid() {
        boolean valid = false;
        for (Iterator<Speaker> i = speakers.keySet().iterator();!valid && i.hasNext();) {
            Speaker speaker = i.next();
            if (speaker.getError() == null) {
                valid = true;
            } else {
                Collection<Speaker> speakers = getSpeakers();
                speakers.remove(speaker);
                setSpeakers(speakers);
            }
        }
        return valid;
    }

    synchronized void write(byte[] buf, int off, int len) {
        if (isValid()) {
            int avail = queue.length - (int)(head-tail);
            if (len > avail) {
                throw new IllegalStateException(len+" > available="+avail+" queuesize="+queue.length+" head="+head+" tail="+tail);
            }
            int qh = (int)(head % queue.length);
            int len1 = qh + len - queue.length;
            if (len1 <= 0) {
//                log.debug("write: head="+head+" ql="+queue.length+" writing "+len+" to "+qh);
                System.arraycopy(buf, off, queue, qh, len);
            } else {
//                log.debug("write: head="+head+" ql="+queue.length+" writing "+(len-len1)+" to "+qh+" then "+len1+" to 0");
                System.arraycopy(buf, off, queue, qh, len - len1);
                System.arraycopy(buf, off + len - len1, queue, 0, len1);
            }
            head += len;
            push();
        }
    }

    private void push() {
        long newtail = Long.MAX_VALUE;
        // This loop may not push anything if current amount is less than any speaker
        // requires in it's buffer - possible if write has just finished short buffer
        // at end of song.
        for (Iterator<Map.Entry<Speaker,SpeakerState>> i = speakers.entrySet().iterator();i.hasNext();) {
            Map.Entry<Speaker,SpeakerState> e = i.next();
            Speaker speaker = e.getKey();
            SpeakerState state = e.getValue();
            int len = speaker.getBufferSize();
            int qt;
            if (state.tail == Long.MIN_VALUE) {
                state.tail = Math.max(0, tail - state.bytedelay);
            }
            while ((qt=(int)((state.tail - (maxbytedelay-state.bytedelay)) % queue.length)) >= 0 && len <= (head-state.tail)) {
                int len1 = qt + len - queue.length;
                if (len1 <= 0) {
//                    log.debug("push: head="+head+" tail="+state.tail+" writing "+len+" to "+speaker.getDisplayName()+" from "+qt);
                    speaker.write(queue, qt, len);
                } else {
                    speaker.write(queue, qt, len - len1);
                    speaker.write(queue, 0, len1);
//                    log.debug("push: head="+head+" tail="+state.tail+" writing "+(len-len1)+" to "+speaker.getDisplayName()+" from "+qt+" then "+len1+" from 0");
                }
                state.tail += len;
                newtail = Math.min(newtail, state.tail - state.bytedelay);
            }
            if (qt < 0) {
                state.tail = head;
            }
        }
        if (newtail != Long.MAX_VALUE) {
            tail = newtail;
        }
        update();
    }

    /**
     * Called to clear the buffer of any pending data before moving to
     * a new song. Not for pause!
     */
    synchronized void flush() {
        if (isValid()) {
            for (Iterator<Map.Entry<Speaker,SpeakerState>> i = speakers.entrySet().iterator();i.hasNext();) {
                Map.Entry<Speaker,SpeakerState> e = i.next();
                Speaker speaker = e.getKey();
                SpeakerState state = e.getValue();
                speaker.flush();
//                log.debug("FLUSH "+speaker.getDisplayName());
                state.tail = 0;
            }
        }
        head = tail = 0;
        update();
    }

    /**
     * Called to stop playback but not clear the buffer. For pausing.
     */
    synchronized void stop() {
        if (isValid()) {
            for (Iterator<Map.Entry<Speaker,SpeakerState>> i = speakers.entrySet().iterator();i.hasNext();) {
                Map.Entry<Speaker,SpeakerState> e = i.next();
                Speaker speaker = e.getKey();
                SpeakerState state = e.getValue();
                speaker.flush();
//                log.debug("STOP "+speaker.getDisplayName());
                state.tail = state.tail - state.bytedelay;
            }
        }
    }

    /**
     * Called to push any remaining buffered data out to the speakers.
     * For when the audio stream has ended and nothing follows it.
     */
    synchronized void drain() {
        int minbuf = Integer.MAX_VALUE, maxbuf = 0;
        for (Iterator<Map.Entry<Speaker,SpeakerState>> i = speakers.entrySet().iterator();i.hasNext();) {
            Map.Entry<Speaker,SpeakerState> e = i.next();
            Speaker speaker = e.getKey();
            SpeakerState state = e.getValue();
            int speakerbuf = speaker.getBufferSize();
            minbuf = Math.min(minbuf, speakerbuf);
            maxbuf = Math.max(maxbuf, speakerbuf);
        }
        long newhead = tail + maxbuf + maxbytedelay;
//        log.debug("draining: head="+head+" tail="+tail+" newhead="+newhead);
        while (head < newhead) {
            for (int j=0;j<minbuf;j++) {
                queue[(int)(head++ % queue.length)] = 0;
            }
            push();
        }
        for (Iterator<Map.Entry<Speaker,SpeakerState>> i = speakers.entrySet().iterator();i.hasNext();) {
            Map.Entry<Speaker,SpeakerState> e = i.next();
            Speaker speaker = e.getKey();
            SpeakerState state = e.getValue();
            speaker.drain();
//            log.debug("DRAIN "+speaker.getDisplayName());
            state.tail = 0;
        }
        head = tail = 0;
        update();
    }

    private void update() {
        msdelay = (int)(bytespersecond==0 ? 0 : (head-tail) * 1000 / bytespersecond);
    }


}
