package com.nixus.raop.speaker.airport;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.RSAPublicKeySpec;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.nixus.raop.core.ServiceContext;

/**
 * Marshalling service for one or more individual Airports.
 * Protocol details: see http://blog.technologeek.org/airtunes-v2
 *                       http://git.zx2c4.com/Airtunes2/about
 */
public class AirtunesManager {

    private static final int SYNCFREQUENCY = 126, PACKETSIZE = 352, QUEUELENGTH = 100;
    private static final int MSECMULT = 4294967;
    private static final long PACKETSPERNANO = 352l * 1000 * 1000 * 1000 / 44100;
    private static long epoch; // 2208988800l - 1281457956l;

    private final String name;
    private final ServiceContext context;
    private int rtptime, rtpseq, packetcount, ssrc;
    private boolean speakerstarted;
    private DatagramSocket controlsocket, timingsocket;
    private Thread timingthread;
    private ScheduledThreadPoolExecutor stpe;
    private Cipher cipher;
    private String cipherkey, cipheriv;
    private ArrayBlockingQueue<byte[]> packetqueue;
    private Map<String,AirtunesSpeaker> speakers = new ConcurrentHashMap<String,AirtunesSpeaker>();

    AirtunesManager(String name, ServiceContext context) {
        this.name = name;
        this.context = context;
    }

    private synchronized void start() {
        context.debug("Starting AirtunesManager for \""+name+"\"");
        SecureRandom rng = new SecureRandom();
        try {
            byte[] rawcipherkey = new byte[16];
            byte[] rawcipheriv = new byte[16];
            rng.nextBytes(rawcipherkey);
            rng.nextBytes(rawcipheriv);
            this.cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(rawcipherkey, "AES"), new IvParameterSpec(rawcipheriv));
            this.cipherkey = AirtunesSpeaker.base64encode(doRSAEncrypt(rawcipherkey), false);
            this.cipheriv  = AirtunesSpeaker.base64encode(rawcipheriv, false);
        } catch (GeneralSecurityException e) {
        	e.printStackTrace();
            throw new RuntimeException(e);
        }

        this.rtpseq = rng.nextInt(8192); 
        this.rtptime = rng.nextInt(65536); 
        this.ssrc = rng.nextInt(); 
        this.packetcount = 0;
        this.speakerstarted = false;
        this.epoch = 0;
        this.packetqueue = new ArrayBlockingQueue<byte[]>(QUEUELENGTH);

        int controlport = 6001;
        do {
            try {
                controlsocket = new DatagramSocket(controlport);
            } catch (Exception e) {
                controlport++;
            }
        } while (controlsocket == null && controlport < 65000);
        int timingport = controlport + 1;
        do {
            try {
                timingsocket = new DatagramSocket(timingport);
            } catch (Exception e) {
                timingport++;
            }
        } while (timingsocket == null && timingport < 65000);

        if (controlsocket==null) {
            throw new IllegalStateException("Can't open ports");
        }
        timingthread = createTimingThread();
        timingthread.start();

        stpe = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(null, r, "qTunes-airtunes-"+name+"-audiosender");
                thread.setPriority(Thread.MAX_PRIORITY - 1);
                return thread;
            }
        });
        stpe.scheduleAtFixedRate(createSender(), PACKETSPERNANO, PACKETSPERNANO, TimeUnit.NANOSECONDS);

        // Write constants to packetdata arrays, for speed
        for (int i=0;i<packetdata.length;i++) {
            byte[] data = packetdata[i];
            data[0] = (byte)(0x80);
            writeInt(ssrc, data, 8);
        }
        cipherinput[0] = (byte)0x20;
        cipherinput[1] = 0;
    }

    private byte[] doRSAEncrypt(byte[] plaintextbytes) throws GeneralSecurityException {
        BigInteger modulus = new BigInteger("E7D744F2A2E2788B6C1F55A08EB70544A8FA7945AA8BE6C62CE5F51CBDD4DC6842FE3D1083DD2EDEC1BFD4252DC02E6F398BDF0E6148EA84855E2E442DA6D62664F674A1F304929ADE4F6893EF2DF6E711A8C77A0D91C9D980822E50D12922AFEA40EA9F0E14C0F76938C5F3882FC0323DD9FE55155F51BB5921C201629FD73352D5E2EFAABF9BA048D7B813A2B6767F6C3CCF1EB4CE673D037B0D2EA30C5FFFEB06F8D08ADDE409571A9C689FEF10728855DD8CFB9A8BEF5C8943EF3B5FAA15DDE698BEDDF3599603EB3E6F61372BB628F6559F599A78BF500687AA7F4976C0562D412956F8989E18A6355BD81597825E0FC875343EC782117625CDBF98447B", 16);
        BigInteger exponent = new BigInteger("65537");
        KeyFactory keyfactory = KeyFactory.getInstance("RSA");
        PublicKey key = keyfactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
        // Following cipher name has only been tested with Sun JCE Provider:
        // the naming scheme appears idiosyncratic so may not work with others.
        
        Cipher keycipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA1AndMGF1Padding", "Crypto");
        keycipher.init(Cipher.ENCRYPT_MODE, key);
        return keycipher.doFinal(plaintextbytes);
    }

    private synchronized void stop() {
        stpe.shutdownNow();
        stpe = null;
        controlsocket.close();
        timingsocket.close();
        controlsocket = timingsocket = null;
        timingthread.interrupt();
        packetqueue = null;
        context.debug("Stopped AirtunesManager for \""+name+"\"");
    }

    /**
     * Add the named speaker to this set
     */
    AirtunesSpeaker addSpeaker(String name, InetAddress host, int port, String password, ServiceContext context) {
        synchronized(speakers) {
            if (speakers.isEmpty()) {
                start();
            }
            AirtunesSpeaker speaker = new AirtunesSpeaker(name, host, port, password, context);
            speakers.put(name, speaker);
            int controlport = ((InetSocketAddress)controlsocket.getLocalSocketAddress()).getPort();
            int timingport = ((InetSocketAddress)timingsocket.getLocalSocketAddress()).getPort();
            speaker.connect(cipherkey, cipheriv, controlport, timingport);
            speaker.start(rtpseq, rtptime);
            return speaker;
        }
    }

    /**
     * Remove the named Speaker from this set
     */
    void removeSpeaker(AirtunesSpeaker speaker) {
        synchronized(speakers) {
            for (Iterator<Map.Entry<String,AirtunesSpeaker>> i = speakers.entrySet().iterator();i.hasNext();) {
                Map.Entry<String,AirtunesSpeaker> e = i.next();
                if (e.getValue()==speaker) {
                    i.remove();
                    speaker.disconnect();
                    if (speakers.isEmpty()) {
                        stop();
                    }
                }
            }
        }
    }

    /**
     * iTunes uses a monotonic system clock, we don't have access to that
     * so we're going to initialize the epch based on the first timing
     * packet we get
     */
    private synchronized static void initializeEpoch(long ntptime) {
        if (epoch==0) {
            epoch = (ntptime>>>32l) - (System.currentTimeMillis() / 1000);
        }
    }

    private static long toNTPTime(long ms) {
        long secs = (ms/1000) + epoch;
        long frac = (ms%1000) * MSECMULT;
        return (secs << 32) + frac;
    }

    private static long fromNTPTime(long ntptime) {
        long secs = ((ntptime>>>32l) - epoch) & 0xFFFFFFFFl;
        long msecs = (ntptime&0xFFFFFFFFl) / MSECMULT;
        return (secs * 1000) + msecs;
    }

    private static String dumpPacket(byte[] data) {
        StringBuffer sb = new StringBuffer(data.length * 2);
        for (int i=0;i<data.length;i++) {
            String s = Integer.toHexString(data[i]&0xFF);
            if (s.length()==1) sb.append('0');
            sb.append(s);
        }
        return sb.toString();
    }

    private Thread createTimingThread() {
        return new Thread("qTunes-airtunes-"+name+"-timer") {
            public void run() {
                DatagramSocket socket;
                byte[] data = new byte[32];
                DatagramPacket packet = new DatagramPacket(data, 32);
                while ((socket=timingsocket)!=null) {
                    try {
                        socket.receive(packet);
                        long receivetime = System.currentTimeMillis();
                        if (data[0]==(byte)0x80 && data[1]==(byte)0xd2) {
//                            System.out.println("TIMER rx "+dumpPacket(data));
                            long ntptime = readLong(data, 24);
                            initializeEpoch(ntptime);
                            data[1] = (byte)0xd3;
                            System.arraycopy(data, 24, data, 8, 8);
                            writeLong(toNTPTime(receivetime), data, 16);
                            for (Iterator<AirtunesSpeaker> i = speakers.values().iterator();i.hasNext();) {
                                AirtunesSpeaker speaker = i.next();
                                if (packet.getSocketAddress().equals(speaker.getTimingAddress())) {
                                    speaker.touch();
                                    break;
                                }
                            }
                            writeLong(toNTPTime(System.currentTimeMillis()), data, 24);
//                            System.out.println("TIMER tx "+dumpPacket(data));
                            socket.send(packet);
                        }
                    } catch (Exception e) {
                        if (!e.getMessage().equals("Socket closed")) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
    }

    private static void writeInt(int val, byte[] buf, int off) {
        buf[off+0] = (byte)(val>>24);
        buf[off+1] = (byte)(val>>16);
        buf[off+2] = (byte)(val>>8);
        buf[off+3] = (byte)(val>>0);
    }

    private static void writeLong(long val, byte[] buf, int off) {
        buf[off+0] = (byte)(val>>>56);
        buf[off+1] = (byte)(val>>>48);
        buf[off+2] = (byte)(val>>>40);
        buf[off+3] = (byte)(val>>>32);
        buf[off+4] = (byte)(val>>>24);
        buf[off+5] = (byte)(val>>>16);
        buf[off+6] = (byte)(val>>>8);
        buf[off+7] = (byte)(val>>>0);
    }

    private static long readLong(byte[] buf, int off) {
        return ((long)readInt(buf, off)<<32) + (readInt(buf, off+4)&0xFFFFFFFFl);
    }

    private static long readInt(byte[] buf, int i) {
        return ((buf[i]&0xFF) << 24) + ((buf[i+1]&0xFF) << 16) + ((buf[i+2]&0xFF) << 8) + (buf[i+3]&0xFF);
    }

    private void sendSyncPacket(boolean first) throws IOException {
        byte[] data = new byte[20];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        data[0] = (byte)(first ? 0x90 : 0x80);
        data[1] = (byte)0xd4;
        data[2] = (byte)0;
        data[3] = (byte)0x07;
        writeInt(rtptime, data, 16);
        writeLong(toNTPTime(System.currentTimeMillis()), data, 8);

        for (Iterator<AirtunesSpeaker> i = speakers.values().iterator();i.hasNext();) {
            AirtunesSpeaker speaker = i.next();
            packet.setSocketAddress(speaker.getControlAddress());
            writeInt(rtptime - speaker.getLatency(), data, 4);
//            System.out.println("SYNC  tx "+dumpPacket(data));
            controlsocket.send(packet);
        }
    }

    /**
     * Clear anything in the packet queue and stop playing immediately
     */
    public synchronized void clear() {
        packetqueue.clear();
        packetcount = 0;
        if (speakerstarted) {
            speakerstarted = false;
            for (Iterator<AirtunesSpeaker> j = speakers.values().iterator();j.hasNext();) {
                AirtunesSpeaker speaker = j.next();
                speaker.stop(rtpseq, rtptime);
                speaker.getContext().fireEvent("speakerStopped", null);
            }
        }
    }

    /**
     * Drain the queue until all playing has finished
     */
    public synchronized void drain() {
        while (!packetqueue.isEmpty()) {
            synchronized(packetqueue) {
                try {
                    packetqueue.wait();
                } catch (InterruptedException e) {}
            }
        }
        packetcount = 0;
        if (speakerstarted) {
            for (Iterator<AirtunesSpeaker> j = speakers.values().iterator();j.hasNext();) {
                AirtunesSpeaker speaker = j.next();
                speaker.getContext().fireEvent("speakerStopped", null);
            }
        }
        speakerstarted = false;
    }

    private Runnable createSender() {
        return new Runnable() {
            byte[] b = new byte[PACKETSIZE*4 + 15];
            DatagramPacket packet = new DatagramPacket(b, b.length);
            public void run() {
                byte[] data;
                ServiceContext context = null;
                if ((data=packetqueue.poll())!=null) {
                    try {
                        if ((packetcount % SYNCFREQUENCY)==0) {
                            sendSyncPacket(packetcount==0);
                        }
                        data[2] = (byte)(rtpseq>>8);
                        data[3] = (byte)(rtpseq);
                        writeInt(rtptime, data, 4);
                        packet.setData(data);
                        for (Iterator<AirtunesSpeaker> j = speakers.values().iterator();j.hasNext();) {
                            AirtunesSpeaker speaker = j.next();
                            if (speaker.isConnected()) {
                                packet.setSocketAddress(speaker.getServerAddress());
                                context = speaker.getContext();
                                speaker.getAudioSocket().send(packet);
                            }
                        }
                        rtpseq++;
                        rtptime += PACKETSIZE;
                        packetcount++;
                    } catch (IOException e) {
                        if (context!=null) {
                            context.warn("Audio send failed", e);
                        } else {
                            e.printStackTrace();
                        }
                    }
                } else {
                    synchronized(packetqueue) {
                        packetqueue.notifyAll();
                    }
                }
            }
        };
    }

    //-----------------------------------------------------------------------------------
    // Profiling shows this is where the load is. Preallocate what we can - we know
    // length of queue so we can allocate enough (plus a couple just in case) in advance.
    // Certain fields of each packet are fixed, so we can set those in start(). Makes
    // quite a difference to garbage collection, which adds latency.
    //-----------------------------------------------------------------------------------

    private byte[][] packetdata = new byte[QUEUELENGTH+5][PACKETSIZE*4 + 15];
    private int packetdatahead;
    private byte[] cipherinput = new byte[1408];

    /**
     * Send an audio packet to the speakers
     */
    public synchronized void sendAudioPacket(byte[] buf, int off, int len, boolean bigendian) {
        if (len != PACKETSIZE*4) {
            throw new IllegalArgumentException("length must be "+(PACKETSIZE*4));
        }

        final byte[] data = packetdata[packetdatahead];
        packetdatahead = (packetdatahead+1)%packetdata.length;

        // Rest of data and cipherinput was initialized in start()
        data[1] = (byte)(packetcount==0 ? 0xe0 : 0x60);

        byte v1 = 1;
        int highbyte = bigendian ? 0 : 1;
        int i;
        for (i=0;i<len - 2;i+=2) {
            byte v2 = buf[off + i + highbyte];
            cipherinput[i + 2] = (byte)(((v2&0x80) >> 7) | (v1 << 1));
            v1 = buf[off + i + (1 - highbyte)];
            cipherinput[i + 3] = (byte)(((v1&0x80) >> 7) | (v2 << 1));
        }
        try {
            cipher.doFinal(cipherinput, 0, cipherinput.length, data, 12);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        byte v2 = buf[off + i + highbyte];
        data[data.length - 3] = (byte)(((v2&0x80) >> 7) | (v1 << 1));
        v1 = buf[off + i + (1 - highbyte)];
        data[data.length - 2] = (byte)(((v1&0x80) >> 7) | (v2 << 1));
        data[data.length - 1] = (byte)(v1<<1);

        if (!speakerstarted) {
            speakerstarted = true;
            for (Iterator<AirtunesSpeaker> j = speakers.values().iterator();j.hasNext();) {
                AirtunesSpeaker speaker = j.next();
                speaker.getContext().fireEvent("speakerStarted", null);
            }
        }
        try {
            packetqueue.put(data);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
