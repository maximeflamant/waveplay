package com.nixus.raop.player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nixus.raop.core.Listener;
import com.nixus.raop.core.Service;
import com.nixus.raop.core.ServiceContext;
import com.nixus.raop.speaker.Speaker;

/**
 * A Player plays tracks to one or more speakers (technically a {@link SourceDataLine}
 * which may be a local speaker, an Airtunes speaker or some other type (eg RTSP, uPnP etc.)
 * Each player maintains it's own playlist and tracks may be cued, reordered, skipped
 * and so on as you'd expect.
 */
public class PlayerImpl implements Player, Runnable, Listener {

	private static final int BUFSIZE = 4096;
	private ServiceContext context;

	private int revision;
	private volatile boolean cancelled;

	private String mixername, mixervendor;
	private MultiLine multiline;
	private Thread thread;
	private volatile int volume;
	private List<String> pendingspeakernames;

	private boolean stopped = true;

	public PlayerImpl() {
		multiline = new MultiLine(this);
	}

	/**
	 * Called when the player has changed - track seek, skipped, paused or unpaused
	 */
	private void notifyPlayChanged() {
		revision++;
		context.fireEvent("playChange", new Object[] { });
	}


	/**
	 * Called when a non-play event is changed (shuffle, volume, repeat etc.)
	 */
	private void notifyStateChanged() {
		revision++;
		context.fireEvent("stateChange", new Object[] { });
	}


	/**
	 * The main play thread. Should be bulletproof
	 */
	public void run() {
		byte[] buf = new byte[BUFSIZE];
		InputStream in = null;
		int streamoffset;
		try {
			context.fireEvent("started", null);
			in = new FileInputStream(new File("/sdcard/gio.wav"));

			streamoffset = 0;
			multiline.open(44100, 16,2, true, false);

			while (!cancelled) {


				while (!stopped) {
					setVolume(getVolume());

					int n;
					if (!multiline.hasSpeakers()) {
						cancelled = true;
						context.fireEvent("error", new Object[] { "exception", new IllegalStateException("No Speakers for \""+getDisplayName()+"\"") });
					} else if ((n = in.read(buf, 0, buf.length)) >= 0) {
						streamoffset += n;
						multiline.write(buf, 0, n);;
					} else {
						context.debug("Player ends track");
						context.fireEvent("stopPlaying", new Object[] { "track", 0});
					}
					context.debug("Player ends loop:  cancelled="+cancelled);
				} 
			}

		}
		catch (Exception e) {
			context.warn("Play failed", e);
			if (in!=null) {
				try { in.close(); } catch (Exception e2) {} 
				in = null;
			}
			if (multiline.isOpen()) {
				try {
					multiline.flush();
					multiline.close();
				} catch (Exception e2) {}
			}

		}
		finally {

			try {
				if (multiline.isOpen()) {
					multiline.drain();
					multiline.close();
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}



	}




	public synchronized void interrupt() {
		stopped = true;
		cancelled = true;
		notifyAll();
	}

	//----------------------------------------------------------------------------------


	public synchronized void stop() {
		stopped = true;
		notifyAll();
	}


	public void setVolume(int volume) {
		volume = Math.max(0, Math.min(100, volume));
		float trackgain = 0;
		multiline.setVolume(volume, trackgain);
		if (volume != this.volume) {
			this.volume = volume;
			context.putProperty("volume", Integer.toString(volume));
			notifyStateChanged();
		}
	}

	public int getVolume() {
		return volume;
	}

	public int getRevision() {
		return revision;
	}

	public String getDisplayName() {
		return context.getProperty("display");
	}


	//-------------------------------------------------------------------------------

	public Collection<Speaker> getAvailableSpeakers() {
		Collection<Speaker> speakers = new ArrayList<Speaker>();
		Speaker[] all = context.getServices(Speaker.class, null);
		for (int i=0;i<all.length;i++) {
			Speaker speaker = all[i];
			if (speaker.getPlayer() == null || speaker.getPlayer() == this) {
				speakers.add(speaker);
			}
		}
		return speakers;
	}

	public void addSpeaker(Speaker speaker) {
		Collection<Speaker> speakers = multiline.getSpeakers();
		if (speakers.add(speaker)) {
			multiline.setSpeakers(speakers);
			updateSpeakerProperties();
		}
	}

	public void removeSpeaker(Speaker speaker) {
		Collection<Speaker> speakers = multiline.getSpeakers();
		if (speakers.remove(speaker)) {
			multiline.setSpeakers(speakers);
			updateSpeakerProperties();
		}
	}

	public Collection<Speaker> getSpeakers() {
		return multiline.getSpeakers();
	}

	private void updateSpeakerProperties() {
		int j = 1;
		Collection<Speaker> speakers = getSpeakers();
		for (Iterator<Speaker> i = speakers.iterator();i.hasNext();) {
			Speaker speaker = i.next();
			getContext().putProperty("speaker."+(j++), speaker.getContext().getServiceName());
		}
		while (getContext().getProperty("speaker."+j)!=null) {
			getContext().putProperty("speaker."+(j++), null);
		}
		notifyStateChanged();
	}

	//-----------------------------------------------------------------------------

	public void startService(final ServiceContext context) {
		this.context = context;
		this.cancelled = false;
		this.pendingspeakernames = new ArrayList<String>();

		String volstate = context.getProperty("volume");
		try {
			volume = Integer.parseInt(volstate);
		} catch (Exception e) {
			volume = 50;
		}


		thread = new Thread(this, "qTunes-"+context.getServiceName());
		thread.start();

		for (Iterator<String> i = pendingspeakernames.iterator();i.hasNext();) {
			String speakername = i.next();
			Speaker speaker = context.getService(Speaker.class, "name=\""+speakername+"\"");
			if (speaker != null) {
				i.remove();
				try {
					speaker.setPlayer(PlayerImpl.this);
				} catch (Exception e) {
					context.warn("Can't set speaker \""+speakername+"\"", e);
				}
			} else {
				context.warn("Can't find speaker \""+speakername+"\"", null);
			}
		}
		// Add speakers we expected on load but weren't started until after
		// we've started.
		context.addListener(this);
	}

	public void stopService(ServiceContext context) {
		cancelled = true;
		thread.interrupt();
		try {
			thread.join();
		} catch (InterruptedException e) { }
		multiline = null;
		context.removeListener(this);
	}

	public ServiceContext getContext() {
		return context;
	}

	public Map<String,Object> reportState() {
		Map<String,Object> map = new LinkedHashMap<String,Object>();
		map.put("name", getDisplayName());
		map.put("revision", getRevision());
		map.put("volume", getVolume());
		Collection<Speaker> speakers = getSpeakers();
		List<String> speakerlist = new ArrayList<String>();
		for (Iterator<Speaker> i = speakers.iterator();i.hasNext();) {
			Speaker speaker = i.next();
			speakerlist.add(speaker.getContext().getProperty("name"));
		}
		map.put("speakers", speakerlist);
		//        context.debug("PLAYER="+map);
		return map;
	}

	public void handleEvent(String name, java.util.Map<?,?> properties) {
		Service service = null;
		if ((service=(Service)properties.get("service")) instanceof Speaker) {
			Speaker speaker = (Speaker)service;
			if (name.equals("started")) {
				String speakername = speaker.getContext().getServiceName();
				if (speaker.getPlayer() == null && pendingspeakernames.remove(speakername)) {
					speaker.setPlayer(this);
				}
			} else if (speaker.getPlayer() == PlayerImpl.this && name.equals("speakerError")) {
				stop();
				context.fireEvent("error", new Object[] { "exception", properties.get("exception"), "speaker", speaker });
			}
		}
	}

	public void play(){
		this.cancelled = false;
		this.stopped = false;
	}
	
	public void setEnabled(boolean enabled){
		this.stopped = !enabled;
	}
	
	public boolean isEnabled(){
		return !stopped;
	}

}
