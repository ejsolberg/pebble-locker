package com.lukekorth.pebblelocker;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.lukekorth.pebblelocker.PebbleLocker.CustomDeviceAdminReceiver;

public class Locker {
	
	private Context mContext;
	private SharedPreferences mPrefs;
	private Logger mLogger;
	private String mUniq;
	private DevicePolicyManager mDPM;
	
	public Locker(Context context, String tag) {
		mContext = context;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		mLogger = new Logger(context);
		mUniq = tag;
		mDPM = ((DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE));
	}
	
	public boolean isActiveAdmin() {
		return mDPM.isAdminActive(new ComponentName(mContext, CustomDeviceAdminReceiver.class));
	}
	
	public void lockIfEnabled() {
		lockIfEnabled(true);
	}

	public void lockIfEnabled(boolean forceLock) {
		if (isActiveAdmin()) {
			if (mPrefs.getBoolean("key_enable_locker", false)) {
				if (!connectedToDeviceOrWifi()) {
					mDPM.resetPassword(mPrefs.getString("key_password", ""), DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
					mPrefs.edit()
						.putBoolean(ConnectionReceiver.LOCKED, true)
						.putBoolean(ConnectionReceiver.UNLOCK, false)
						.commit();
					
					mLogger.log(mUniq, "Locked!");

					if (forceLock && mPrefs.getBoolean("key_force_lock", false))
						mDPM.lockNow();
				}
			} else {
				mLogger.log(mUniq, "key_enable_locker is false");
			}
		} else {
			mLogger.log(mUniq, "Not an active admin");
		}
	}

	public void unlockIfEnabled() {
		if (isActiveAdmin()) {
			if (mPrefs.getBoolean("key_enable_locker", false)) {
				if(connectedToDeviceOrWifi()) {
					if(isDeviceOnLockscreen()) {
						mPrefs.edit().putBoolean(ConnectionReceiver.UNLOCK, true).commit();
						mLogger.log(mUniq, "Screen is on lockscreen, setting unlock true for future unlock");
					} else {
						try {
							mDPM.resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
							
							mPrefs.edit().putBoolean(ConnectionReceiver.LOCKED, false).commit();
						} catch (IllegalArgumentException e) {
							mLogger.log(mUniq, "There was an exception when setting the password to blank, setting it back. " + Log.getStackTraceString(e));
							
							mDPM.resetPassword(mPrefs.getString("key_password", ""), DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
							mPrefs.edit().putBoolean(ConnectionReceiver.LOCKED, true).commit();
						}
						
						mPrefs.edit().putBoolean(ConnectionReceiver.UNLOCK, false).commit();
						
						mLogger.log(mUniq, "Unlocked!");	
					}
				}
			} else {
				mLogger.log(mUniq, "key_enable_locker is false");
			}
		} else {
			mLogger.log(mUniq, "Not an active admin");
		}
	}
	
	public boolean isDeviceOnLockscreen() {
		boolean keyguard = ((KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();
		boolean screen = ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).isScreenOn();
		
		mLogger.log(mUniq, "Keyguard is showing: " + keyguard + " Screen is on: " + screen);
		
		return keyguard && screen;
	}
	
	private boolean connectedToDeviceOrWifi() {		
		boolean pebble = false;
		boolean bluetooth = false;
		boolean wifi = false;
		
		if(mPrefs.getBoolean("pebble", true))
			pebble = isWatchConnected();
		else
			mLogger.log(mUniq, "Unlock via any Pebble is not enabled");

		String bluetoothAddress = mPrefs.getString("bluetooth", "");
		mLogger.log(mUniq, "Connected bluetooth address: " + bluetoothAddress);
		if(mPrefs.getBoolean(bluetoothAddress, false))
			bluetooth = true;

		WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		if(wifiInfo != null) {
			if(wifiInfo.getSSID() != null) {
				String ssid = WiFiNetworks.stripQuotes(wifiInfo.getSSID());
				
				mLogger.log(mUniq, "Wifi network " + ssid + " is connected: " + Base64.encodeToString(ssid.getBytes(), Base64.DEFAULT).trim());
								
				if(mPrefs.getBoolean(Base64.encodeToString(ssid.getBytes(), Base64.DEFAULT).trim(), false))
					wifi = true;
			} else {
				mLogger.log(mUniq, "wifiInfo.getSSID is null");
			}
		} else {
			mLogger.log(mUniq, "wifiInfo is null");
		}
		
		mLogger.log(mUniq, "Pebble: " + pebble + " Bluetooth: " + bluetooth + " Wifi: " + wifi);
		
		return (pebble || bluetooth || wifi);
	}

	public boolean isWatchConnected() {
		Cursor c = null;
		try {
			c = mContext.getApplicationContext().getContentResolver().query(Uri.parse("content://com.getpebble.android.provider/state"), null, null, null, null);
		} catch (Exception e) {
			mLogger.log(mUniq, "Exception getting Pebble connection status: " + e);
		}
		
		if (c == null)
			return false;
		
		if(!c.moveToNext()) {
			c.close();
			return false;
		}

		boolean connected = (c.getInt(0) == 1);
		c.close();
		return connected;
	}
}
