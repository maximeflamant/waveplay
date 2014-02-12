package com.nixus.raop.speaker.airport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.nixus.raop.core.ServiceContext;

// Disconnection can happen because of
// a) no toucho
// b) no connect

class AirtunesSpeaker {

    private static final int VALIDITY = 9000, EXPIRE = 3000;

    private String cipherkey, cipheriv;
    private int timingport, controlport;
    private volatile Exception error;
    private ServiceContext context;
    private volatile int touch;
    private InetSocketAddress artsp, aserver, atiming, acontrol;
    private DatagramSocket audio;
    private Socket rtsp;
    private String name, jacktype, servertype, clientsessionid;
    private InetAddress host;
    private int cseq, latency;
    private Map<String,String> globalheaders;
    private float gain = - 123;

    AirtunesSpeaker(String name, InetAddress host, int port, String password, ServiceContext context) {
        this.artsp = new InetSocketAddress(host, port);
        this.host = host;
        this.name = name;
        this.globalheaders = new LinkedHashMap<String,String>();
        this.context = context;
    }

    void touch() {
        touch = Math.max(touch, (int)(System.currentTimeMillis() / 1000));
    }

    private void setError(Exception e) {
        this.error = e;
        if (this.error !=null) {
            invalidate();
            context.warn("Speaker Error", e);
            context.fireEvent("speakerError", new Object[] { "exception", e });
        }
    }

    Exception getError() {
        return this.error;
    }

    ServiceContext getContext() {
        return context;
    }

    synchronized boolean isConnected() {
        if ((System.currentTimeMillis() - EXPIRE) / 1000 > touch) {
            invalidate();
            return false;
        } else {
            return true;
        }
    }

    synchronized void ensureConnected() {
        if (error == null && !isConnected()) {
            reconnect();
        }
    }

    boolean isDigital() {
        return "digital".equals(jacktype);
    }

    String getServerType() {
        return servertype;
    }

    //-----------------------------------------------------------------------

    synchronized void connect(String cipherkey, String cipheriv, int controlport, int timingport) {
        this.error = null;
        this.cipherkey = cipherkey;
        this.cipheriv = cipheriv;
        this.controlport = controlport;
        this.timingport = timingport;
        reconnect();
    }

    synchronized void start(int rtpseq, int rtptime) {
        if (error == null) {
            try {
                rtspRecord(rtpseq, rtptime);
            } catch (IOException e) {
                reconnect();
                if (error == null) {
                    try {
                        rtspRecord(rtpseq, rtptime);
                    } catch (IOException e2) {
                        setError(e2);
                    }
                }
            }
        }
    }

    synchronized void stop(int rtpseq, int rtptime) {
        if (error == null) {
            try {
                rtspFlush(rtpseq, rtptime);
            } catch (IOException e) {
                reconnect();
                if (error == null) {
                    try {
                        rtspFlush(rtpseq, rtptime);
                    } catch (IOException e2) {
                        setError(e2);
                    }
                }
            }
        }
    }

    synchronized void setGain(float gain) {
        if (error == null && cipherkey != null) {
            gain = gain==gain ? Math.max(-30, Math.min(0, gain)) : -144;
            String val = new DecimalFormat("#0.000000", new DecimalFormatSymbols(Locale.ENGLISH)).format(gain);
            try {
                rtspSetParameter("volume: "+val+"\r\n");
            } catch (IOException e) {
                reconnect();
                if (error == null) {
                    try {
                        rtspSetParameter("volume: "+val+"\r\n");
                    } catch (IOException e2) {
                        setError(e2);
                    }
                }
            }
        }
    }


    synchronized void disconnect() {
        if (error == null) {
            try {
                rtspTeardown();
            } catch (IOException e) { }
            cipherkey = cipheriv = null;
            invalidate();
        }
    }

    private void reconnect() {
        try {
            if (cipherkey == null) {
                throw new IOException("Not connected");
            }
            context.fireEvent("speakerOpening", null);
            SecureRandom rng = new SecureRandom();
            String sci = "0000000000000000" + Long.toString(Math.abs(rng.nextLong()), 16);
            globalheaders.put("Client-Instance", sci.substring(sci.length()-16).toUpperCase());
            globalheaders.put("User-Agent", "iTunes/4.6 (Macintosh; U; PPC Mac OS X 10.3)");
            rtsp = new Socket();
            rtsp.connect(artsp);
            clientsessionid = new DecimalFormat("0000000000").format(Math.abs(rng.nextInt()));

            byte[] challenge = new byte[16];
            rng.nextBytes(challenge);
            String sdp = "v=0\r\n"+
                         "o=iTunes "+clientsessionid+" 0 IN IP4 "+host.getHostAddress()+"\r\n" +
                         "s=iTunes\r\n" +
                         "c=IN IP4 "+InetAddress.getLocalHost().getHostAddress()+"\r\n" +
                         "t=0 0\r\n" +
                         "m=audio 0 RTP/AVP 96\r\n" +
                         "a=rtpmap:96 AppleLossless\r\n" +
                         "a=fmtp:96 4096 0 16 40 10 14 2 255 0 0 44100\r\n" +
                         "a=rsaaeskey:"+cipherkey+"\r\n" +
                         "a=aesiv:"+cipheriv+"\r\n";

            rtspAnnounce(sdp, base64encode(challenge, false));
            rtspSetup(controlport, timingport);
            audio = new DatagramSocket();
            touch = (int)((System.currentTimeMillis() + VALIDITY) / 1000);
            context.fireEvent("speakerOpen", null);
        } catch (IOException e) {
            setError(e);
        }
    }

    private void invalidate() {
        try {
            rtsp.close();
        } catch (IOException e) {}
        rtsp = null;
        touch = 0;
        atiming = aserver = acontrol = null;
    }

    int getLatency() {
        return latency;
    }

    InetSocketAddress getServerAddress() {
        return aserver;
    }

    InetSocketAddress getControlAddress() {
        return acontrol;
    }

    InetSocketAddress getTimingAddress() {
        return atiming;
    }

    DatagramSocket getAudioSocket() {
        return audio;
    }

    float getGain() {
        return gain;
    }

    //--------------------------------------------------------------------

    private synchronized Map<String,String> execRTSP(String cmd, String content, String[] newheaders) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(rtsp.getOutputStream(), "ISO-8859-1");
        Map<String,String> writeheaders = new HashMap<String,String>(globalheaders);
        writeheaders.put("CSeq", Integer.toString(++cseq));
        if (newheaders!=null) {
            for (int i=0;i<newheaders.length;i+=2) {
                writeheaders.put(newheaders[i], newheaders[i+1]);
            }
        }
        if (content != null) {
            writeheaders.put("Content-Length", Integer.toString(content.length()));
        }
//        System.out.println("WRITE: "+cmd+" rtsp://"+host.getHostAddress()+"/"+clientsessionid+": "+writeheaders+" = "+content);

        writer.write(cmd+" rtsp://"+host.getHostAddress()+"/"+clientsessionid+" RTSP/1.0\r\n");
        for (Iterator i = writeheaders.entrySet().iterator();i.hasNext();) {
            Map.Entry e = (Map.Entry)i.next();
            writer.write(e.getKey()+": "+e.getValue()+"\r\n");
        }
        writer.write("\r\n");
        if (content != null) {
            writer.write(content);
        }
        writer.flush();

        BufferedReader in = new BufferedReader(new InputStreamReader(rtsp.getInputStream(), "ISO-8859-1"));
        String line = in.readLine();
        if (line==null || line.length()==0) {
            throw new IOException("No response");
        } else if (line.startsWith("RTSP/1.0 453 ")) {
            throw new IOException("Speaker \""+name+"\" already in use");
        } else if (!line.equals("RTSP/1.0 200 OK")) {
            throw new IOException("Received \""+line+"\"");
        } else {
            String key = null;
            Map<String,String> map = new LinkedHashMap<String,String>();
            while ((line=in.readLine())!=null && line.length() > 0) {
                if (key != null && Character.isWhitespace(line.charAt(0))) {
                    map.put(key, map.get(key)+line);
                } else {
                    int i = line.indexOf(":");
                    if (i == -1) {
                        throw new IOException("Request failed, bad header");
                    }
                    key = line.substring(0, i);
                    map.put(key, line.substring(i+1).trim());
                }
            }
//            System.out.println("READ "+map);
            return map;
        }
    }

    private static Map<String,String> splitProperties(String s) {
        Map<String,String> out = new LinkedHashMap<String,String>();
        String[] z = s.split(";");
        for (int i=0; i<z.length; i++) {
            String[] kv = z[i].split("=");
            if (kv.length==2) {
                out.put(kv[0], kv[1]);
            } else {
                out.put(kv[0], null);
            }
        }
        return out;
    }

    private void rtspAnnounce(String sdp, String challenge) throws IOException {
        execRTSP("ANNOUNCE", sdp, new String[] {
            "Content-Type", "application/sdp",
            "Apple-Challenge", challenge,
        });
    }

    private void rtspSetup(int controlport, int timingport) throws IOException {
        String transport = "RTP/AVP/UDP;unicast;interleaved=0-1;mode=record;control_port="+controlport+";timing_port="+timingport;
        Map<String,String> res = execRTSP("SETUP", null, new String[] {
            "Transport", transport,
        });
        globalheaders.put("Session", res.get("Session"));

        this.servertype = res.get("Server");
        Map<String,String> props = splitProperties(res.get("Transport"));
        this.aserver = new InetSocketAddress(host, Integer.parseInt(props.get("server_port")));
        this.atiming = new InetSocketAddress(host, Integer.parseInt(props.get("timing_port")));
        this.acontrol = new InetSocketAddress(host, Integer.parseInt(props.get("control_port")));
        props = splitProperties(res.get("Audio-Jack-Status"));
        if (!props.containsKey("connected")) {
            throw new IOException("No speaker connected to Airport");
        }
        this.jacktype = props.get("type");
    }

    private void rtspRecord(int rtpseq, int rtptime) throws IOException {
        if (!globalheaders.containsKey("Session")) {
            throw new IllegalStateException("No Session");
        }
        Map<String,String> res = execRTSP("RECORD", null, new String[] {
            "Range", "npt=0-",
            "RTP-Info", "seq="+rtpseq+";rtptime="+rtptime,
        });
        latency = Integer.parseInt(res.get("Audio-Latency"));
    }

    private void rtspSetParameter(String parameter) throws IOException {
        execRTSP("SET_PARAMETER", parameter, new String[] {
            "Content-Type", "text/parameters",
        });
    }

    private void rtspFlush(int rtpseq, int rtptime) throws IOException {
        execRTSP("FLUSH", null, new String[] {
            "Range", "npt=0-",
            "RTP-Info", "seq="+rtpseq+";rtptime="+rtptime,
        });
    }

    private void rtspTeardown() throws IOException {
        execRTSP("TEARDOWN", null, null);
    }

    private void rtspOptions() throws IOException {
        execRTSP("OPTIONS", null, null);
    }


    static String base64encode(byte[] in, boolean addequals) {
        final int len = in.length;
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        final int olen = (len*4+2)/3;
        StringBuffer sb = new StringBuffer(((len+2)/3)*4);
        for (int i=0;i<len;) {
            int i0 = in[i++] & 0xff;
            int i1 = i < len ? in[i++] & 0xff : 0;
            int i2 = i < len ? in[i++] & 0xff : 0;
            int o0 = i0 >>> 2;
            int o1 = ((i0 & 3)  << 4) | (i1 >>> 4);
            int o2 = ((i1 & 0xf) << 2) | (i2 >>> 6);
            int o3 = i2 & 0x3f;
            sb.append(chars.charAt(o0));
            sb.append(chars.charAt(o1));
            sb.append(sb.length() < olen ? chars.charAt(o2) : addequals ? '=' : ' ');
            sb.append(sb.length() < olen ? chars.charAt(o3) : addequals ? '=' : ' ');
        }
        return sb.toString().trim();
    }

}
