package de.mangelow.throughput;
/***
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

public class NotificationService extends Service {

	private final String TAG = "TP";
	private final boolean D = false;

	private Context context;
	private Resources res;

	private Handler handler;

	private Builder nb;
	public static int NOTIFICATION_ID = 347893278;

	private TelephonyManager tmanager;
	private WifiManager wmanager;

	private String last_connection = null;
	private int signalstrength = -1;

	private long last_rx = TrafficStats.getTotalRxBytes();
	private long last_tx = TrafficStats.getTotalTxBytes();

	private ArrayList<App> apps;

	private boolean screenOff = false;

	private int MAX_CHAR = 18;
	
	private ResultReceiver mResultReceiver;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		if(intent!=null)mResultReceiver = intent.getParcelableExtra("receiver");

		return START_STICKY;
	}
	@Override
	public IBinder onBind(Intent intent) {		
		return null;
	}
	@Override
	public void onCreate() {		
		super.onCreate();		
		if(D)Log.d(TAG, "Service started");

		context = getApplicationContext();
		res = context.getResources();

		if(tmanager==null) {
			tmanager =(TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
			tmanager.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
		}

		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(mBroadcastReceiver, filter);

		int [] refresh_values = res.getIntArray(R.array.refresh_values);
		long refresh = (long) refresh_values[MainActivity.loadIntPref(context, MainActivity.REFRESH, MainActivity.REFRESH_DEFAULT)];

		modifyNotification(R.drawable.ic_stat_zero, null, "", "", new Intent());

		if(handler==null) {
			handler = new Handler();
			handler.postDelayed(mRunnable, refresh);
		}

	}
	@Override
	public void onDestroy() {
		if(D)Log.d(TAG, "Service stopped");

		if(tmanager!=null) {
			tmanager.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_NONE);
			tmanager = null;
		}

		if(handler!=null)handler = null;
		if(mBroadcastReceiver!=null) {
			unregisterReceiver(mBroadcastReceiver);
			mBroadcastReceiver = null;
		}
		removeNotification();

	}
	private final Runnable mRunnable = new Runnable() {

		public void run() {

			int drawable = R.drawable.ic_stat_zero;
			String ticker = null;
			String title = "";
			String subtitle = "";
			Intent i = new Intent();

			//

			int procent = -1;
			String quality_string = "";

			String subtype = "";
			String ipaddress = "";

			//

			boolean enabled = MainActivity.loadBooleanPref(context, MainActivity.ENABLED, MainActivity.ENABLED_DEFAULT);

			boolean showipaddress = MainActivity.loadBooleanPref(context, MainActivity.SHOWIPADDRESS, MainActivity.SHOWIPADDRESS_DEFAULT);

			boolean showssidsubtype = MainActivity.loadBooleanPref(context, MainActivity.SHOWSSIDSUBTYPE, MainActivity.SHOWSSIDSUBTYPE_DEFAULT);
			boolean showcells = MainActivity.loadBooleanPref(context, MainActivity.SHOWCELLS, MainActivity.SHOWCELLS_DEFAULT);
			boolean showwifilinkspeed = MainActivity.loadBooleanPref(context, MainActivity.SHOWWIFILINKSPEED, MainActivity.SHOWWIFILINKSPEED_DEFAULT);
			boolean showsignalstrength = MainActivity.loadBooleanPref(context, MainActivity.SHOWSIGNALSTRENGTH, MainActivity.SHOWSIGNALSTRENGTH_DEFAULT);
			boolean showonairplanemode = MainActivity.loadBooleanPref(context, MainActivity.SHOWONAIRPLANEMODE, MainActivity.SHOWONAIRPLANEMODE_DEFAULT);
			boolean showappname = MainActivity.loadBooleanPref(context, MainActivity.SHOWAPPNAME, MainActivity.SHOWAPPNAME_DEFAULT);

			int [] refresh_values = res.getIntArray(R.array.refresh_values);
			long refresh = (long) refresh_values[MainActivity.loadIntPref(context, MainActivity.REFRESH, MainActivity.REFRESH_DEFAULT)];

			int ontap = MainActivity.loadIntPref(context, MainActivity.ONTAP, MainActivity.ONTAP_DEFAULT);

			int [] threshold_values = res.getIntArray(R.array.threshold_values);
			int threshold = MainActivity.loadIntPref(context, MainActivity.THRESHOLD, MainActivity.THRESHOLD_DEFAULT);

			if(ontap==1)i = new Intent(context, MainActivity.class);

			try {

				if(getNetworkState(ConnectivityManager.TYPE_MOBILE)) {

					if(ontap==0) {
						i = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
						i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						i.setAction(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS); 
					}

					List<NeighboringCellInfo> l_ncells = tmanager.getNeighboringCellInfo();
					int ncells = l_ncells.size();

					subtype = getNetworkSubType();				
					if(showipaddress)ipaddress = getIPAddress();

					if(signalstrength>-1) {

						if(signalstrength==99)procent = -1;

						final int MIN_RSSI = 0;
						final int MAX_RSSI = 31;

						int range = MAX_RSSI - MIN_RSSI;

						procent = 100 - ((MAX_RSSI - signalstrength) * 100 / range);
						if(procent>99)procent = 100;

						if(procent>-1) {
							if(showcells)quality_string = res.getQuantityString(R.plurals.cells, ncells, ncells);
							if(showsignalstrength) {
								if(quality_string.length()>0)quality_string += " | ";
								quality_string += procent + "%";
							}
							if(showipaddress) {
								if(quality_string.length()>0)quality_string += " | ";
								quality_string += ipaddress;
							}
						}

					}

					if(last_connection==null||!last_connection.equals(subtype)) {
						last_connection = subtype;
						if(showssidsubtype)ticker = subtype + " | ";
						if(quality_string.length()>0)ticker += quality_string;
					}

					if(showssidsubtype)subtitle = subtype + " | ";
					if(quality_string.length()>0)subtitle += quality_string;

				}
				else if(getNetworkState(ConnectivityManager.TYPE_WIFI)) {

					if(ontap==0)i = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);

					if(wmanager==null)wmanager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
					WifiInfo winfo = wmanager.getConnectionInfo();

					subtype = winfo.getSSID();
					if(subtype!=null&&subtype.length()>MAX_CHAR)subtype = subtype.substring(0,MAX_CHAR-1);
					
					if(showipaddress)ipaddress = getIPAddress();


					//

					int linkspeed = winfo.getLinkSpeed();
					int rssi = wmanager.getConnectionInfo().getRssi();

					final int MIN = -100;
					final int MAX = -55;
					int range = MAX - MIN;	    		

					procent = 100 - ((MAX - rssi) * 100 / range);	    		
					if(procent>99)procent = 100;

					if(procent>-1) {
						if(showwifilinkspeed)quality_string = linkspeed + "Mbit";
						if(showsignalstrength) {
							if(quality_string.length()>0)quality_string += " | ";
							quality_string += procent + "%";
						}
						if(showipaddress) {
							if(quality_string.length()>0)quality_string += " | ";
							quality_string += ipaddress;
						}
					}

					//

					if(last_connection==null||!last_connection.equals(subtype)) {
						last_connection = subtype;
						if(showssidsubtype)ticker = subtype + " | ";
						if(quality_string.length()>0)ticker += quality_string;
					}

					if(showssidsubtype)subtitle = subtype;
					if(quality_string.length()>0) {
						if(subtitle.length()>0)subtitle += " | ";
						subtitle += quality_string;
					}

				}
				else if(showonairplanemode && isAirplaneModeOn(context)) {
					drawable = R.drawable.ic_stat_apmode;
					title = res.getString(R.string.airplane_mode);

					if(ontap==0)i = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);

					if(last_connection==null||!last_connection.equals(subtype)) {
						last_connection = title;
						ticker = title;
					}
				}
				else {

					last_connection = null;
					removeNotification();
				}

				if(last_connection != null) {

					long mStartRX = TrafficStats.getTotalRxBytes();
					long mStartTX = TrafficStats.getTotalTxBytes();

					if (mStartRX != TrafficStats.UNSUPPORTED || mStartTX != TrafficStats.UNSUPPORTED) {

						if(!last_connection.equals(res.getString(R.string.airplane_mode))) {

							boolean showbitsorbytes = MainActivity.loadBooleanPref(context, MainActivity.SHOWBITSORBYTES, MainActivity.SHOWBITSORBYTES_DEFAULT);

							long rxBytes = mStartRX - last_rx;
							last_rx = TrafficStats.getTotalRxBytes();

							long in = 0;
							if(rxBytes<0)in = 0;					

							float divide = (float) refresh / 1000;
							try {
								in = (long) (rxBytes / divide);
							} catch (Exception e) {
								if(D)e.printStackTrace();
							}
							if(in<threshold_values[threshold])in = 0;

							String per = refresh + "ms";
							if(divide % 1 == 0) {
								per = (int) divide + "s";
								if(divide==1.0f)per = "s";
							}

							String humanreadable = humanReadableByteCount(in, showbitsorbytes);
							if(humanreadable.contains("-"))humanreadable="0.0";
							title = humanreadable + "/" + per  + " " +  res.getString(R.string.in);

							long txBytes = mStartTX - last_tx;
							last_tx = TrafficStats.getTotalTxBytes();

							long out = 0;
							if(txBytes<0)out = 0;					

							divide = (float) refresh / 1000;
							try {
								out = (long) (txBytes / divide);
							} catch (Exception e) {
								if(D)e.printStackTrace();
							}
							if(out<threshold_values[threshold])out = 0;

							humanreadable = humanReadableByteCount(out, showbitsorbytes);
							if(humanreadable.contains("-"))humanreadable="0.0";
							title += " | " + humanreadable + "/" + per + " " + res.getString(R.string.out);

							if(in==0&&out>0)drawable = R.drawable.ic_stat_out;
							if(out==0&&in>0)drawable = R.drawable.ic_stat_in;
							if(in>0&&out>0)drawable = R.drawable.ic_stat_inout;
							if(in==0&&out==0)drawable = R.drawable.ic_stat_zero;

							if(showappname&&in>threshold_values[threshold]||out>threshold_values[threshold]) {

								if(apps==null)apps = getApplications(context);
								
								if (apps != null) {
									String appnames_string = "";

									String name_in = "";
									long in_last = 0;
									int in_count = 0;

									String name_out = "";
									long out_last = 0;
									int out_count = 0;

									for (int j = 0; j < apps.size(); j++) {

										App app = apps.get(j);
										int uid = app.getUID();
										String packagename = app.getPackagename();

										//

										long app_in = TrafficStats.getUidRxBytes(uid);
										long app_out = TrafficStats.getUidTxBytes(uid);

										String name = "";
										if(app_in>0||app_out>0) name = packagename;
										
										if(app_in>0) {

											long app_last_rx = app_in;
											try {
												app_last_rx = app.getLastRx();
											} catch (Exception e) {}

											long app_rxBytes = app_in - app_last_rx;
											if(app_rxBytes>threshold_values[threshold]) {
												in_count++;
												if(app_rxBytes>in_last) {
													name_in = name;
													if(D)Log.d(TAG, "in - " + packagename + " - " + humanReadableByteCount(app_rxBytes, showbitsorbytes));
												}
											}
										}
										app.setLastRx(app_in);

										//

										if(app_out>0) {

											long app_last_tx = app_out;
											try {
												app_last_tx = app.getLastTx();
											} catch (Exception e) {}

											long app_txBytes = app_out - app_last_tx;
											if(app_txBytes>threshold_values[threshold]) {
												out_count++;
												if(app_txBytes>out_last) {
													name_out = name;
													if(D)Log.d(TAG, "out - " + packagename + " - " + humanReadableByteCount(app_txBytes, showbitsorbytes));
												}
											}
										}			
										app.setLastTx(app_out);
																			
									}
									
									if(name_in.length()>0&&name_out.length()>0&&name_in.equals(name_out)) {
										appnames_string = "↕" + name_in;
										int total_count = in_count + out_count;
										if(total_count>2)appnames_string += " +" + (total_count-2);
									}
									else {
										if(name_in.length()>0) {
											appnames_string = "↓" + name_in;
											if(in_count>1)appnames_string += " +" + (in_count-1);
										}
										if(name_out.length()>0) {
											if(name_in.length()>0)appnames_string += " ";
											appnames_string += "↑" + name_out;
											if(out_count>1)appnames_string += " +" + (out_count-1);
										}
									}	
									
									String divider = " | ";
									if(subtitle.length()==0||appnames_string.length()==0)divider = "";
									subtitle = appnames_string + divider + subtitle;
								}
							}
						}
					}
					else {
						title = res.getString(R.string.traffic_unsupported);
					}

					if(enabled) {
						modifyNotification(drawable, ticker, title, subtitle, i);
					}
					else {
						removeNotification();
					}

				}

				//

				if(handler!=null&&!screenOff&&enabled)handler.postDelayed(mRunnable, refresh);

			}
			catch (Exception e){
				if(D)e.printStackTrace();
				stopSelf();
			}
		}

	};

	private class MyPhoneStateListener extends PhoneStateListener {
		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			super.onSignalStrengthsChanged(signalStrength);
			signalstrength = signalStrength.getGsmSignalStrength();
		}
	};

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				if(D)Log.d(TAG, "ACTION_SCREEN_OFF");

				screenOff = true;
			}
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				if(D)Log.d(TAG, "ACTION_SCREEN_ON");

				screenOff = false;

				int [] refresh_values = res.getIntArray(R.array.refresh_values);
				long refresh = (long) refresh_values[MainActivity.loadIntPref(context, MainActivity.REFRESH, MainActivity.REFRESH_DEFAULT)];

				if(handler!=null)handler.postDelayed(mRunnable, refresh);
			}

		}
	};

	//

	@SuppressWarnings("deprecation")
	private void modifyNotification(int drawable, String ticker, String title, String subtitle, Intent i) {
		
		boolean showticker = MainActivity.loadBooleanPref(context, MainActivity.SHOWTICKER, MainActivity.SHOWTICKER_DEFAULT);
		if(!showticker)ticker = null;

		NotificationManager nmanager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, NOTIFICATION_ID);
		Notification n = null;

		if (Build.VERSION.SDK_INT < 11) {

			n = new Notification(drawable, ticker, System.currentTimeMillis());
			n.flags |= Notification.FLAG_NO_CLEAR;	
			n.setLatestEventInfo(this, title, subtitle, pi);

		}
		else {

			if(nb==null) {
				nb = new Notification.Builder(context);
				nb.setPriority(Notification.PRIORITY_LOW);
				nb.setAutoCancel(true);
			}

			nb.setColor(Color.BLACK);
			nb.setSmallIcon(drawable);
			if(ticker!=null)nb.setTicker(ticker);
			nb.setContentTitle(title);
			nb.setContentText(subtitle);
			nb.setContentIntent(pi);

			n = nb.build();
			n.flags = Notification.FLAG_NO_CLEAR;

		}		

		nmanager.notify(NOTIFICATION_ID, n);
		
		//
		
		if(mResultReceiver!=null) {

			Bundle bundle = new Bundle();
			bundle.putInt("drawable", drawable);
			bundle.putString("title", title);
			bundle.putString("subtitle", subtitle);
			mResultReceiver.send(0, bundle);

		}

	}	
	private void removeNotification() {

		NotificationManager nmanager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nmanager.cancel(NOTIFICATION_ID);
		
		if(mResultReceiver!=null) {

			Bundle bundle = new Bundle();
			bundle.putInt("drawable", R.drawable.ic_stat_zero);
			bundle.putString("title", "");
			bundle.putString("subtitle", "");
			mResultReceiver.send(0, bundle);

		}
	}

	//

	@SuppressWarnings("deprecation")
	private boolean isAirplaneModeOn(Context context) {

		if (Build.VERSION.SDK_INT < 17) {
			return Settings.System.getInt(context.getContentResolver(), 
					Settings.System.AIRPLANE_MODE_ON, 0) != 0;          
		} else {
			return Settings.Global.getInt(context.getContentResolver(), 
					Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
		}       
	}
	private boolean getNetworkState(int type) {
		ConnectivityManager connect = null;
		connect =  (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

		if(connect != null)
		{
			NetworkInfo result = connect.getNetworkInfo(type);
			if (result != null && result.isConnected())
			{
				return true;
			}
			else 
			{
				return false;
			}
		}
		else
			return false;
	}
	private String getIPAddress() {

		try {
			List<NetworkInterface>  nilist = Collections.list(NetworkInterface.getNetworkInterfaces());
			if(nilist.size() > 0){
				for (NetworkInterface ni: nilist){
					List<InetAddress>  ialist = Collections.list(ni.getInetAddresses());
					if(ialist.size()>0){
						for (InetAddress address: ialist){
							if(!address.isLoopbackAddress() && address instanceof Inet4Address)return address.getHostAddress();
						}
					}

				}
			}

		} catch (SocketException ex) {
			if(D)ex.printStackTrace();
		}

		return "";

	}
	private String getNetworkSubType() {

		String subtype = null;

		int networkType = tmanager.getNetworkType();

		switch (networkType) {
		case 7:
			subtype = "1xRTT";
			break;      
		case 4:
			subtype = "CDMA";
			break;      
		case 2:
			subtype = "EDGE";
			break;  
		case 14:
			subtype = "eHRPD";
			break;      
		case 5:
			subtype = "EVDO rev. 0";
			break;  
		case 6:
			subtype = "EVDO rev. A";
			break;  
		case 12:
			subtype = "EVDO rev. B";
			break;  
		case 1:
			subtype = "GPRS";
			break;      
		case 8:
			subtype = "HSDPA";
			break;      
		case 10:
			subtype = "HSPA";
			break;          
		case 15:
			subtype = "HSPA+";
			break;          
		case 9:
			subtype = "HSUPA";
			break;          
		case 11:
			subtype = "iDen";
			break;
		case 13:
			subtype = "LTE";
			break;
		case 3:
			subtype = "UMTS";
			break;          
		case 0:
			subtype = "Unknown";
			break;
		}

		return subtype;
	}

	private String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
		return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
	private ArrayList<App> getApplications(Context context) {

		ArrayList<App> apps = new ArrayList<App>();

		PackageManager pmanager = context.getPackageManager();
		List<ApplicationInfo> current_apps = pmanager.getInstalledApplications(0);
		if(D)Log.d(TAG, "total apps - " + current_apps.size());

		for (ApplicationInfo appInfo : current_apps) {

			int uid = appInfo.uid;
			String processname = appInfo.processName;
			String packagename = (String) pmanager.getApplicationLabel(appInfo);

			long last_rx = TrafficStats.getUidRxBytes(uid);
			long last_tx = TrafficStats.getUidTxBytes(uid);

			if(last_rx>0||last_tx>0) {
				App app = new App();
				app.setUID(uid);
				app.setProcessname(processname);
				app.setPackagename(packagename);
				app.setLastRx(last_rx);
				app.setLastTx(last_tx);
				apps.add(app);
			}
		}

		return apps;
	}
}