package com.nixus.raop.speaker;


import com.nixus.raop.core.Service;
import com.nixus.raop.player.Player;

/**
 * A Speaker is an output device, which may be a local JavaSound speaker,
 * an Airtunes speaker, a network speaker or similar. A {@link Player} has
 * one or more speakers and a {@link Speaker} may be assigned to exactly
 * one Player, or unassigned. Writing to a Speaker should <b>never</b> throw
 * an Exception of any sort - instead the internal {@link #getException Exception}
 * should be set. This is reset when the Speaker is opened.
 */
public interface Speaker extends Service {

    /**
     * Return the number of ms delay for this speaker, between a packet
     * being written and it being heard
     */
    public int getDelay();

    /**
     * Return the size of the speaker buffer in bytes.
     */
    public int getBufferSize();

    /**
     * Return a nice name for the Speaker for display to the user
     */
    public String getDisplayName();

    /**
     * Return a unique ID for the speaker
     */
    int getUniqueId();

    /**
     * Returns true if this speaker can have its gain adjusted
     */
    public boolean hasGain();

    /**
     * Set the gain of this speaker. A value of NaN means "no audio".
     * If this method fails, set the Error.
     */
    public void setGain(float gain);

    /**
     * Set the player for the speaker. This method should
     * call {@link Player#removeSpeaker} on the old player
     * and {@link Player#addSpeaker} on the new one, and
     * anyone moving speakers between players should call
     * this method rather than those methods directly.
     */
    public void setPlayer(Player player);

    /**
     * Get the Player currently assigned to this speaker
     */
    public Player getPlayer();

    /**
     * If this speaker has failed for some reason, return the Exception.
     * Otherwise return null.
     */
    public Exception getError();

    /**
     * Get the volume adjustment that applies to this speaker (+ve or -ve, 0
     * for no adjustment
     */
    public int getVolumeAdjustment();

    /**
     * Set the volume adjustment that applies to this speaker, which should be
     * be added to the gain for this speaker.
     */
    public void setVolumeAdjustment(int volumeadjust);

    /**
     * Open the Speaker and start it. Reset any error that may have been set.
     */
    public void open(float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian);

    /**
     * Write data to the speaker. If it fails, set the error
     */
    public void write(byte[] buf, int off, int len);

    /**
     * Immediately stop the speaker and discard any cached data written to it
     * If it fails, set the error.
     */
    public void flush();

    /**
     * Block until the data already written to the speaker completes
     * If it fails, set the error.
     */
    public void drain();

    /**
     * Close the speaker, immediately stopping it and discarding any data.
     * Does not throw exception or fail.
     */
    public void close();

    /**
     * True after open has been called, false after close has been called
     */
    public boolean isOpen();

}
