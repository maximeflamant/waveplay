package com.nixus.raop.player;

import java.util.Collection;

import com.nixus.raop.core.Service;
import com.nixus.raop.speaker.Speaker;

/**
 * A Player plays tracks to one or more speakers which may be local speaker
 * (probably a {@link SourceDataLine}), an Airtunes speaker or some other type (eg RTSP, uPnP etc.)
 * Each player maintains it's own playlist and tracks may be cued, reordered, skipped
 * and so on as you'd expect.
 */
public interface Player extends Service {




    /**
     * Set the current volume
     * @param volume the volume, between 0 and 100
     */

    public void setVolume(int volume);

    /**
     * Return the current volume
     * @return the volume, between 0 and 100
     */

    public int getVolume();

    //-------------------------------------------------------------------------------

    /**
     * Get the collection of speakers that could be used by this Player and that
     * aren't currently in use elsewhere.
     */
    public Collection<Speaker> getAvailableSpeakers();

    /**
     * Add a Speaker to the Player. Should only be called by {@link Speaker#setPlayer}.
     */
    public void addSpeaker(Speaker speaker);

    /**
     * Remove a Speaker to the Player. Should only be called by {@link Speaker#setPlayer}.
     */
    public void removeSpeaker(Speaker speaker);

    /**
     * Return the list of {@link Speaker} objects current in use by this Player
     */
    public Collection<Speaker> getSpeakers();


    /**
     * Return the revision number. This should be increased every time the Player's state is
     * updated.
     */
    public int getRevision();

    /**
     * Return a nice name for this Player for display to the user
     */
    public String getDisplayName();

}
