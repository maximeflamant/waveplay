package com.nixus.raop;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.nixus.raop.core.ServiceContextImpl;
import com.nixus.raop.events.SpeakerEvent;

public class MainActivity extends Activity implements OnClickListener,Observer{


	protected static LibraryAdapter discoveredDevicesAdapter;
	private NotificationManager mNotificationManager;
	private Builder mBuilder;


	private RaopHelper raopHelper;

	private Map<String,ToggleButton> toggleBtnsByIp = new HashMap<String,ToggleButton>();


	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		ToggleButton tb = (ToggleButton) findViewById(R.id.toggleButton1);
		tb.setOnClickListener(this);

		ListView discoveredDevicesList = (ListView)findViewById(R.id.list);
		discoveredDevicesAdapter = new LibraryAdapter(this);
		discoveredDevicesList.setAdapter(discoveredDevicesAdapter);

		initNotifications();

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

			public void run() {
				RaopHelper.setRaopSwitch("O");
			}
		}));
		
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

			public void uncaughtException(Thread thread, Throwable ex) {
	        	System.err.println(ex.getMessage());
				RaopHelper.setRaopSwitch("O");	
			}
	    });

		this.raopHelper = new RaopHelper();
		this.raopHelper.addObserver(this);
		this.raopHelper.startDeviceDiscovery();

	}


	/**
	 * Initialize Android status bar notifications
	 */
	private void initNotifications() {
		mBuilder =
				new NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.airplay_w)
		.setContentTitle("Waveplay notification");

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = new Intent(this, MainActivity.class);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(MainActivity.class);
		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent =
				stackBuilder.getPendingIntent(
						0,
						PendingIntent.FLAG_UPDATE_CURRENT
						);
		mBuilder.setContentIntent(resultPendingIntent);
		mNotificationManager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/**
	 * Adapter for Airplay devices list view
	 * @author Maxime Flamant
	 *
	 */
	public class LibraryAdapter extends BaseAdapter {

		protected Context context;
		protected LayoutInflater inflater;

		public View footerView;

		protected List<SpeakerEvent> known = new LinkedList<SpeakerEvent>();

		public LibraryAdapter(Context context) {
			this.context = context;
			this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public void notifyFound(SpeakerEvent context) {
			known.add(context);
		}

		public Object getItem(int position) {
			return known.get(position);
		}

		public boolean hasStableIds() {
			return true;
		}

		public int getCount() {
			return known.size();
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {


			final SpeakerEvent speaker = (SpeakerEvent)this.getItem(position);
			final String airportName = speaker.getName();
			final String airportAddress = speaker.getIp();
			final String protocol = speaker.getProtocol();
			final String port = speaker.getPort();



			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			View rowView = inflater.inflate(R.layout.device_list_element, parent, false);
			TextView textView = (TextView) rowView.findViewById(R.id.textView1);
			final ToggleButton tb = (ToggleButton) rowView.findViewById(R.id.toggleButton1);

			textView.setText(airportName);

			toggleBtnsByIp.put(airportAddress, tb);

			tb.setOnClickListener(new OnClickListener() {


				public void onClick(View v) {
					if (tb.isChecked()){
						raopHelper.startStreaming(airportAddress,protocol,port);
					}
					else {
						raopHelper.stopStreaming(airportAddress);
					}
				}


			});

			return rowView;

		}


	}

	/* Global switch action
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 */
	public void onClick(View arg0) {
		RaopHelper.setRaopSwitch("0");
		System.exit(0);
	}


	/* (non-Javadoc)
	 * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
	 */
	public void update(Observable observable, final Object data) {

		if (data instanceof String){
			mBuilder.setContentText(data.toString());
			// mId allows you to update the notification later on.
			mNotificationManager.notify(0, mBuilder.build());
		}

		if (data instanceof SpeakerEvent){

			final SpeakerEvent speakerEvent = (SpeakerEvent) data;
			if (speakerEvent.getCurrentState() ==  SpeakerEvent.State.OFF)
				runOnUiThread(new Runnable() {
					public void run() {
						toggleBtnsByIp.get(speakerEvent.getIp()).setChecked(false);
					}
				});
			else
				runOnUiThread(new Runnable() {
					public void run() {
						discoveredDevicesAdapter.notifyFound(speakerEvent);
						discoveredDevicesAdapter.notifyDataSetChanged();
					}
				});
		}


	}


}