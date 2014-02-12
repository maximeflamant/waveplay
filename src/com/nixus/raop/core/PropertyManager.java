package com.nixus.raop.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

import com.nixus.raop.player.PlayerImpl;
import com.nixus.raop.speaker.airport.Finder;
import com.nixus.raop.zeroconf.ZeroConfFactory;


public class PropertyManager {

    private Map<String,ServiceProperties> all;
    private File file;
    private Timer writetimer;
    private volatile boolean propertiesdirty;

    public PropertyManager() {
        this.all = new LinkedHashMap<String,ServiceProperties>();
        this.writetimer = new Timer("qTunes-main-PropertyFileUpdater", true);
    }


    synchronized void close() {
        writetimer.cancel();
        write();
    }

    synchronized void create(String servicename, Class<? extends Service> serviceclass, Map<String,String> properties, boolean permanent) {
        if (all.containsKey(servicename)) {
            throw new IllegalStateException("Service \""+servicename+"\" already exists");
        }
        ServiceProperties sp = new ServiceProperties(servicename, serviceclass.getName(), permanent);
        for (Iterator<Map.Entry<String,String>> i = properties.entrySet().iterator();i.hasNext();) {
            Map.Entry<String,String> e = i.next();
            sp.put(e.getKey(), e.getValue());
        }
        all.put(servicename, sp);
        if (permanent) {
//            System.out.println("QUEUEING FOR CREATE "+servicename);
            queuewrite();
        }
    }

    public synchronized void remove(String key) {
        all.remove(key);
    }

    synchronized boolean isPermanent(String servicename) {
        ServiceProperties sp = all.get(servicename);
        return sp != null && sp.isPermanent();
    }

    synchronized String get(String key) {
        return all.get(null).get(key);
    }

    synchronized String get(String servicename, String key) {
        ServiceProperties m = all.get(servicename);
        return m==null ? null : m.get(key);
    }

    synchronized void put(String servicename, String key, String value) {
        ServiceProperties sp = all.get(servicename);
        if (sp.put(key, value) && sp.isPermanent()) {
//            System.out.println("QUEUEING FOR PUT "+servicename+" "+key+" "+value);
            queuewrite();
        }
    }

    synchronized Collection<String> getNames(String servicename) {
        return Collections.unmodifiableCollection(all.get(servicename).keySet());
    }

    private synchronized void queuewrite() {
        if (!propertiesdirty) {
//            propertiesdirty = true;
//            writetimer.schedule(new TimerTask() {
//                public void run() {
//                    write();
//                }
//            }, 1000);
        }
    }

    private synchronized void write() {
        if (propertiesdirty) {
            propertiesdirty = false;
            try {
                File temp = File.createTempFile("qt-"+file.getName(), null, file.getAbsoluteFile().getParentFile());
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), "UTF-8"));
                for (Iterator<ServiceProperties> i = all.values().iterator();i.hasNext();) {
                    ServiceProperties sp = i.next();
                    if (sp.isPermanent()) {
                        sp.write(writer);
                        writer.newLine();
                    }
                }
                writer.close();
                if (!temp.renameTo(file)) {
                    throw new IllegalStateException("Unable to rename \""+temp+"\" to \""+file+"\"");
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to update \""+file+"\"", e);
            }
        }
    }

    //------------------------------------------------------------------------------

    public class ServiceProperties {

        private final List<Prop> lines;
        private final Map<String,Prop> props;
        private final boolean permanent;
        private final String servicename, classname;

        ServiceProperties(String servicename, String classname, boolean permanent) {
            this.props = new LinkedHashMap<String,Prop>();
            this.lines = new ArrayList<Prop>();
            this.servicename = servicename;
            this.classname = classname;
            this.permanent = permanent;
            if (servicename!=null) {
                Prop prop = new Prop(null, null, "["+servicename+" "+classname+"]", 0, true);
                lines.add(prop);
            }
        }

        void write(BufferedWriter w) throws IOException {
            for (int i=0;i<lines.size();i++) {
                w.write(lines.get(i).toString());
                w.newLine();
            }
        }

        String getServiceName() {
            return servicename;
        }

        String getClassName() {
            return classname;
        }

        boolean isPermanent() {
            return permanent;
        }

        void addLine(String line) {
            Prop prop = new Prop(line);
            if (prop.key!=null) {
                props.put(prop.key, prop);
            }
            prop.line = lines.size();
            lines.add(prop);
        }
        
        void close() {
            while (lines.size() > 0 && lines.get(lines.size()-1).toString().length()==0) {
                lines.remove(lines.size()-1);
            }
        }

        synchronized boolean put(String key, String value) {
            Prop prop = props.get(key);
            if (value==null && prop==null) {
                return false;
            } else if (value==null && prop!=null) {
                props.remove(key);
                lines.remove(prop.line);
                for (int i=prop.line;i < lines.size();i++) {
                    lines.get(i).line--;
                }
                return permanent;
            } else if (prop==null) {
                props.put(key, prop = new Prop(key, value, "", lines.size(), false));
                lines.add(prop);
                return permanent;
            } else if (value!=null && !prop.readonly && !prop.value.equals(value)) {
                prop.value = value;
                return permanent;
            } else {
                return false;
            }
        }

        synchronized String get(String key) {
            if (servicename!=null && key.equals("name")) {
                return servicename;
            } else if (servicename!=null && key.equals("class")) {
                return classname;
            } else {
                Prop prop = props.get(key);
                return prop==null ? null : prop.value;
            }
        }

        synchronized Set<String> keySet() {
            return new LinkedHashSet<String>(props.keySet());
        }
    }

    private static class Prop {

        String key, comment, value;
        boolean readonly;
        int line;

        Prop(String key, String value, String comment, int line, boolean readonly) {
            this.key = key;
            this.value = value;
            this.comment = comment;
            this.line = line;
            this.readonly = readonly;
        }

        Prop(String s) {
            char quote = 0;
            StringBuilder sb = new StringBuilder();
            int state = 0, i = 0;

            i = readSpaces(s, i);
            i = readQuotedString(s, i, sb);
            if (sb.length() > 0) {
                key = sb.toString();
                sb.setLength(0);
                i = readSpaces(s, i);
                char c = s.charAt(i++);
                if (c=='=') {
                    readonly = false;
                } else if (c==':' && i<s.length()-2 && s.charAt(i++)=='=') {
                    readonly = true;
                } else {
                    throw new IllegalArgumentException();
                }
                i = readSpaces(s, i);
                int i2 = readQuotedString(s, i, sb);
                i = readSpaces(s, i2);
                if (i==s.length() || s.charAt(i)=='#') {
                    value = sb.toString();
                    comment = s.substring(i2);
                } else {
                    throw new IllegalArgumentException();
                }
            } else {
                comment = s;
            }
//            System.out.println("READ("+key+") "+this);
        }

        private static int readSpaces(String s, int i) {
            char c;
            while (i < s.length() && ((c=s.charAt(i))==' ' || c=='\t')) {
                i++;
            }
            return i;
        }

        private static boolean isIdentifierPart(char c) {
            return Character.isUnicodeIdentifierPart(c) || "[]!@£$%^&*()-+<>,./?~\u20ac|\"\\'".indexOf(c) >= 0;
        }

        private static int readQuotedString(String s, int i, StringBuilder sb) {
            char quote = 0, c;
            while (i < s.length() && (isIdentifierPart(c=s.charAt(i)) || quote!=0)) {
                if (c=='\\') {
                    c = s.charAt(++i);
                    if (c=='n') {
                        sb.append('\n');
                    } else if (c=='r') {
                        sb.append('\r');
                    } else if (c=='t') {
                        sb.append('\t');
                    } else if (c=='u') {
                        try {
                            c = (char)Integer.parseInt(s.substring(i+1, i+5), 16);
                            sb.append(c);
                            i += 4;
                        } catch (Exception e) {
                            sb.append(c);
                        }
                    } else {
                        sb.append(c);
                    }
                } else if ((c=='\'' || c=='"') && (quote==0 || quote==c)) {
                    quote = quote==0 ? c : 0;
                } else {
                    sb.append(c);
                }
                i++;
            }
            if (quote!=0) {
                throw new IllegalArgumentException("Unmatched quote");
            }
            return i;
        }

        private static String quote(String s) {
            boolean needsquote = false;
            for (int i=0;i<s.length() && !needsquote;i++) {
                needsquote |= !isIdentifierPart(s.charAt(i));
            }
            StringBuilder sb = new StringBuilder();
            if (needsquote) {
                sb.append('"');
            }
            for (int i=0;i<s.length();i++) {
                char c = s.charAt(i);
                if (c=='\n') {
                    sb.append("\\n");
                } else if (c=='\r') {
                    sb.append("\\r");
                } else if (c=='\"') {
                    sb.append("\\\"");
                } else if (c < 0x20 || c >= 0x80) {
                    String t = "000" + Integer.toHexString(c);
                    sb.append("\\u" + t.substring(t.length() - 4));
                } else {
                    sb.append(c);
                }
            }
            if (needsquote) {
                sb.append('"');
            }
            return sb.toString();
        }

        public String toString() {
            if (key==null) {
                return comment;
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(quote(key));
                if (readonly) {
                    sb.append(" := ");
                } else {
                    sb.append(" = ");
                }
                sb.append(quote(value));
                sb.append(comment);
                return sb.toString();
            }
        }

    }

	public void put(String serviceName, ServiceProperties serviceProperties) {
		all.put(serviceName, serviceProperties);
	}


	public void init() {

		all.put("zeroconffactory",new ServiceProperties("zeroconffactory", ZeroConfFactory.class.getName(), true));
		all.put("airportfactory",new ServiceProperties("airportfactory", Finder.class.getName(), true));
		all.put("main",new ServiceProperties("main", PlayerImpl.class.getName(), true));
		
	}
}
