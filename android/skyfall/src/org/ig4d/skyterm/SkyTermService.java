/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/
package org.ig4d.skyterm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Set;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.gsm.SmsManager;
import android.text.format.Time;

public class SkyTermService extends Service {
	BluetoothAdapter mBluetoothAdapter;
	BluetoothSocket mSocket;
	BluetoothDevice mDevice;
	OutputStream mOutputStream;
	InputStream mInputStream;
	Thread mWorkerThread;
	byte[] mReadBuffer;
	int mReadBufferPosition;
	int mCounter;
	boolean mCreated = false;
	volatile boolean mStopWorker;
    long mLastWriteTime = System.currentTimeMillis();
    long mLastReadTime = System.currentTimeMillis();
    long mStartTime = System.currentTimeMillis();
    private static int INTERACTION_TIMEOUT = 60*1000;
	Time mTime = new Time(Time.getCurrentTimezone());
	private static final int CLOSE_TIMEOUT = 1000;
	//TODO!
	private static final int PICTURE_CLIMBING_DELAY = 5*60*1000;
	private static final int SMS_CLIMBING_DELAY = 10*60*1000; //TODO: 10 min
	private static final int SMS_FAST_UPDATE_DELAY = 20*1000; //TODO: 30 sec
	private static final String mSMSNumber = "+4915155155707";
    private static final int STATUS_DELAY = 2*60*1000;
    private static final int UPDATE_DELAY = 500;
    
	private static final String ERR = "ERR: ";
	private static final String INFO = "INFO: ";
	private static final String STATUS = "STAT";
	private static final String DEVICE = "IG4DBT";
	private static enum State {
		STATE_START_UP, STATE_CONNECT, STATE_COMMUNICATE, STATE_CLOSE
	};

	private State mState = State.STATE_START_UP;
	private String mTitleString=new String();
    private Handler mHandler = new Handler();
    private int NUM_LOG_STRING = 100;
    private int mNumLogs=0;
	private String mLog = new String();
	private boolean mLogChanged=true;
	// Positioning
	private LocationManager mLocationManager;
	private Location mLocation = new Location(LocationManager.GPS_PROVIDER);
	private double mLongitude=-1.0, mLatitude=-1.0, mAltitude=-1.0;
	private int mPressure = -1, mTemperature = -1;
	private boolean mFreefallDetected=false;
	private int mStartPressure = -1;
	private boolean mFirstPressure=true;
	private WakeLock mWakeLock;
	//battery status
	//private int mBatteryScale = -1;
    private int mBatteryLevel = -1;
    //private int mBatteryVoltage = -1;
    //private int mBatteryTemperature = -1;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

    @Override
    public void onCreate() {
        super.onCreate();
  		if (!mCreated) {
			openLogFile();

			mHandler.postDelayed(mBTUpdate, 500);

			mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		    boolean gpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

		    if (!gpsEnabled) {
		    	logln(ERR + "GPS : GPS turned off! No location provider available.");
		    }
		    else {
		    	mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 20, mLocationListener);
		    	//mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, UPDATE_CYCLE, UPDATE_DISTANCE, mLocationListener);
		    	//mLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, UPDATE_CYCLE, UPDATE_DISTANCE, mLocationListener);
		    	mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		    	logln(INFO + "location provider started.");
		    }

			mHandler.postDelayed(mTakePicture, PICTURE_CLIMBING_DELAY);
			mHandler.postDelayed(mSMSPosition, SMS_CLIMBING_DELAY);
			mHandler.postDelayed(mUpdate, UPDATE_DELAY);

			mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SKYFALL");
	        mWakeLock.acquire();

	        BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
	            public void onReceive(Context context, Intent intent) {
	            	mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
	                //mBatteryScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
	                //mBatteryTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
	                //mBatteryVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
	            }
	        };
	        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	        registerReceiver(batteryReceiver, filter);
	        
	        mCreated = true;
			
			if(StaticData.EMULATION) {
				mEmulation.update(mStartTime);
			}
		}
  		StaticData.mSkyTermService=this;
    }
    
    @Override
    public void onDestroy() {
    	StaticData.mSkyTermService=null;
    	closeLogFile();
		mWakeLock.release();
    	super.onDestroy();
    }

	private Runnable mBTUpdate = new Runnable() {
		public void run() {
			switch (mState) {
			// ------------------------------------------------------------------------------------------------
			case STATE_START_UP: {
				mState = State.STATE_CONNECT;
				mHandler.postDelayed(mBTUpdate, 100); // start connect
			}
				break;
			// ------------------------------------------------------------------------------------------------
			case STATE_CONNECT: {
				if (mBluetoothAdapter != null
						&& (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)) {
					// wait until device is turned off
					mHandler.postDelayed(mBTUpdate, 1000); // try again after x sec
				}
				try {
					if (findBT() && openBT()) {
						mState = State.STATE_COMMUNICATE;
						beginListenForData();
						mHandler.postDelayed(mBTUpdate, 1000); // start communication
					} else {
						logln(ERR + "find/open BT failed");
						closeBT();
						mState = State.STATE_CONNECT;
						mHandler.postDelayed(mBTUpdate, CLOSE_TIMEOUT); // try again after x sec
					}
				} catch (IOException ex) {
					logln(ERR + ex.getMessage());
					closeBT();
					mState = State.STATE_CONNECT;
					mHandler.postDelayed(mBTUpdate, CLOSE_TIMEOUT); // try again after x sec
				}
			}
				break;
			// ------------------------------------------------------------------------------------------------
			case STATE_COMMUNICATE: {
				if (mStopWorker == true) {
					// something failed in the worker thread, we try to
					// reestablish the communication
					logln(ERR + "failed to listen for data");
					closeBT();
					mState = State.STATE_CONNECT;
					mHandler.postDelayed(mBTUpdate, CLOSE_TIMEOUT); // try again after x sec
				} else {
					try {
						String cmd="mo"+getMode();
						sendCmd(cmd);
					} catch (IOException ex) {
						logln(ERR + "COMM " + ex.getMessage());
						closeBT();
						mState = State.STATE_CONNECT;
					}
					mHandler.postDelayed(mBTUpdate, 1000);
				}
			}
				break;
			// ------------------------------------------------------------------------------------------------
			case STATE_CLOSE: {
				closeBT();
				mState = State.STATE_CONNECT;
				mHandler.postDelayed(mBTUpdate, CLOSE_TIMEOUT); // try again after x sec
			}
				break;
			}
		}
	};
	
	private boolean findBT() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			logln(ERR + "No bluetooth adapter available");
			return false;
		}

		if (!mBluetoothAdapter.isEnabled()) {
			mBluetoothAdapter.enable();
			try {
				Thread.sleep(100);
			} catch (Throwable e) {
			}
		}

		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
				.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				if (device.getName().equals(DEVICE)) {
					mDevice = device;
					logln(INFO + "Bluetooth device '" + DEVICE
							+ "' is a paired device");
					return true;
				}
			}
		}
		logln(ERR + "Bluetooth device '" + DEVICE + "' not found");
		return false;
	}

	private boolean openBT() throws IOException {
		UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // Standard
																				// SerialPortService
																				// ID
		if (mDevice != null) {
			mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
			mSocket.connect();
			mOutputStream = mSocket.getOutputStream();
			mInputStream = mSocket.getInputStream();
			logln(INFO + "Bluetooth opened");
			return true;
		} else {
			logln(ERR + "Bluetooth device '" + DEVICE + "' unavailable");
		}
		return false;
	}

	private void beginListenForData() {
		final Handler handler = new Handler();
		final byte delimiter = '\n';

		mStopWorker = false;
		mReadBufferPosition = 0;
		mReadBuffer = new byte[1024];
		mWorkerThread = new Thread(new Runnable() {
			public synchronized boolean readData() {
				boolean result = true;
				try {
					int bytesAvailable = mInputStream.available();
					if (bytesAvailable > 0) {
						byte[] packetBytes = new byte[bytesAvailable];
						mInputStream.read(packetBytes);
						for (int i = 0; i < bytesAvailable; i++) {
							byte b = packetBytes[i];
							if (b == delimiter) {
								byte[] encodedBytes = new byte[mReadBufferPosition];
								System.arraycopy(mReadBuffer, 0, encodedBytes,
										0, encodedBytes.length);
								String data = new String(encodedBytes,
										"US-ASCII");
								final String str = data.replaceAll(
										"[^A-Za-z0-9: ]", "");
								mReadBufferPosition = 0;

								handler.post(new Runnable() {
									public void run() {
										// process data
										process(str);
									}
								});
							} else {
								mReadBuffer[mReadBufferPosition++] = b;
							}
						}
						mLastReadTime=System.currentTimeMillis();
					}
				} catch (IOException ex) {
					logln(ERR + "WORK " + ex.getMessage());
					result = false;
				}
				return result;
			}

			public void run() {
				while (!Thread.currentThread().isInterrupted() && !mStopWorker) {
					int i;
					boolean res = false;
					for (i = 0; i < 3 && !res; ++i) {
						res = readData();
					}
					if (!res) {
						// failed too often
						mStopWorker = true;
					}
					try {
						Thread.sleep(100);
					} catch (Throwable e) {
					}
				}
			}
		});

		mWorkerThread.start();
	}

	long mLastLogTime = 0;
	private void process(String cmd) {
		if (cmd.toLowerCase().startsWith("log:")) {
			logln(INFO + cmd);
		} else if (cmd.startsWith("ack")) {
			logTitle("-");
			String[] parts = cmd.split(":");
			if(parts.length >= 2) {
				try {
					mAckMode=Integer.parseInt(parts[1]);
					if(mAckMode != mMode) {
						logln(ERR + "response mode different from host mode ("+mAckMode+", "+mMode+")");
					}
				} catch(Throwable e) {}
			}
			if(parts.length >= 3) {
				try {
					if(!StaticData.EMULATION) {
						mTemperature=Integer.parseInt(parts[2]);
					}
				} catch(Throwable e) {}
			}
			if(parts.length >= 4) {
				try {
					if(!StaticData.EMULATION) {
						mPressure=Integer.parseInt(parts[3]);
					}
				} catch(Throwable e) {}
			}
			if(parts.length >= 5) {
				try {
					mFreefallDetected=(Integer.parseInt(parts[4])!=0)?true:false;
				} catch(Throwable e) {}
			}
		}
		long time=System.currentTimeMillis();
		if(time - mLastLogTime > STATUS_DELAY) {
			mLastLogTime = time;
			logln(STATUS);
		}
	}

	private synchronized void sendCmd(String msg) throws IOException {
		byte[] bytes = (msg).getBytes("US-ASCII");
		byte xor = 0;
		for (int i = 0; i < bytes.length; ++i) {
			xor ^= bytes[i];
			mOutputStream.write(bytes[i]);
			try {
				Thread.sleep(10);
			} catch (Throwable e) {
			}
		}
		mOutputStream.write(xor);
		try {
			Thread.sleep(10);
		} catch (Throwable e) {
		}
		mOutputStream.write('\n');
		logTitle("+");
		mLastWriteTime=System.currentTimeMillis();
		if(Math.abs(mLastWriteTime - mLastReadTime) > INTERACTION_TIMEOUT) {
			connectionProbablyLost();
			mLastReadTime = mLastWriteTime;
		}
	}

	private void connectionProbablyLost() {
		logln(ERR + "Connection to Arduino probably lost");
	}
	
	private void closeBT() {
		mStopWorker = true;
		try {
			Thread.sleep(100);
		} catch (Throwable e) {
		}
		if (mOutputStream != null) {
			try {
				mOutputStream.close();
				mOutputStream = null;
			} catch (Throwable ex) {
				logln(ERR + ex.getMessage());
			}
		}
		if (mInputStream != null) {
			try {
				mInputStream.close();
				mInputStream = null;
			} catch (Throwable ex) {
				logln(ERR + ex.getMessage());
			}
		}
		if (mSocket != null) {
			try {
				mSocket.close();
				mSocket = null;
			} catch (Throwable ex) {
				logln(ERR + ex.getMessage());
			}
		}
		mDevice = null;
		if (mBluetoothAdapter != null) {
			mBluetoothAdapter.disable();
		}
		System.gc();
		logln(INFO + "Bluetooth closing ...");
	}

	public synchronized void logln(String text) {
		mTime.setToNow();
		String str = mTime.format("%k:%M:%S") + " [" +
				String.format("lat=%.2f", mLatitude) + ", " +
				String.format("lon=%.2f", mLongitude) + ", " +
				String.format("bar=%d", mPressure) + ", " +
				String.format("alt=%.2f", mAltitude) + ", " +
				String.format("vol=%d", mBatteryLevel) + ", " +
				String.format("tem=%.1f", ((double)mTemperature)/10.0) + ", " +
				String.format("fre=%d", mFreefallDetected?1:0) + "] : " + text + "\n";

		if(mNumLogs >= NUM_LOG_STRING) {
			mNumLogs=0;
			mLog=new String();
		}
		
		mLog+=str;
		mNumLogs++;
		writeToFile(str);
		mLogChanged=true;
	}

	public synchronized void log(String text) {
		mLog+=text;
		writeToFile(text);
		mLogChanged=true;
	}

	public synchronized boolean hasLogChanged() {
		return mLogChanged;
	}
	
	public synchronized void clearLogChanged() {
		mLogChanged = false;
	}

	public synchronized String getLog() {
		return new String(mLog);
	}

	private FileOutputStream mF;
	private PrintWriter mPw;

	private boolean openLogFile() {
		String path=StaticData.EXT_SD_CARD;
		if(!(new File(StaticData.EXT_SD_CARD)).exists()) {
			path=Environment.getExternalStorageDirectory().getAbsolutePath();
		}
		path+=File.separator + "download";
		File dir = new File(path);
		dir.mkdirs();
		File ofile = new File(dir, "skyterm_log.txt");
		if (ofile.exists()) {
			int i;
			for(i=0; i<1000; ++i) {
				File nfile = new File(dir, "skyterm_log."+i+".txt");
				if(!nfile.exists()) {
					ofile.renameTo(nfile);
					break;
				}
			}
		}
		File file = new File(dir, "skyterm_log.txt");

		try {
			mF = new FileOutputStream(file);
			mPw = new PrintWriter(mF);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			mPw = null;
			mF = null;
			return false;
		}
		return true;
	}

	private void closeLogFile() {
		try {
			if (mPw != null) {
				mPw.flush();
				mPw.close();
			}
			if (mF != null) {
				mF.close();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeToFile(String str) {
		if (mPw != null) {
			mPw.print(str);
			mPw.flush();
		}
	}

//  private int mPictureCounter = 0;
	private Runnable mTakePicture = new Runnable() {
		public void run() {
			//TODO!
//			mPictureCounter = (mPictureCounter+1)%7;
//			if(mPictureCounter == 0) {
//				StaticData.mTakePicture=false;
//			}
//			else {
//				StaticData.mTakePicture=true;
//			}

			Intent intent=null;
			if(StaticData.mTakePicture) {
				if(StaticData.mPictureActivity != null) {
					logln(INFO + "picture activity is still running -> killed (1)");
					StaticData.mPictureActivity.finish();
				}
				if(StaticData.mVideoActivity != null) {
					logln(INFO + "video activity still running -> we wait");
				}
				else {
					intent = new Intent(getBaseContext(), PictureActivity.class);
				}
			}
			else {
				if(StaticData.mPictureActivity != null) {
					logln(INFO + "picture activity is still running -> killed (2)");
					StaticData.mPictureActivity.finish();
				}
				if(StaticData.mVideoActivity != null) {
					logln(INFO + "video activity is still running -> killed (2)");
					StaticData.mVideoActivity.finish();
				}
				intent = new Intent(getBaseContext(), VideoActivity.class);
			}
			if(intent != null) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
			}
			if(getMode() == MODE_CLIMBING) {
				mHandler.postDelayed(mTakePicture, PICTURE_CLIMBING_DELAY);
			}
			else {
				mHandler.postDelayed(mTakePicture, 10 * 60 * 1000);
			}
		}
	};

	private Runnable mSMSPosition = new Runnable() {
		public void run() {
			if(StaticData.mSendSMS) {
				String msg="SkyFall "
						+ mTime.format("%k:%M:%S") + " [" +
						String.format("lat=%.6f", mLatitude) + ", " +
						String.format("lon=%.6f", mLongitude) + ", " +
						String.format("bar=%d", mPressure) + ", " +
						String.format("alt=%.4f", mAltitude) + ", " +
						String.format("vol=%d", mBatteryLevel) + ", " +
						String.format("tem=%.1f", ((double)mTemperature)/10.0) + "]";
				sendSMS(mSMSNumber, msg);
				logln(INFO+"send SMS: "+msg);
			}
			if(mMode == MODE_CLIMBING || mMode == MODE_DONE) {
				mHandler.postDelayed(mSMSPosition, SMS_CLIMBING_DELAY);
			}
			else {
				mHandler.postDelayed(mSMSPosition, SMS_FAST_UPDATE_DELAY);
			}
		}
	};

    private void getPosition() {
    	if(!StaticData.EMULATION) {
    		try {mLatitude=mLocation.getLatitude();} catch (Throwable e) {}
    		try {mLongitude=mLocation.getLongitude();} catch (Throwable e) {}
    		try {mAltitude=mLocation.getAltitude();} catch (Throwable e) {}
    	}
    }
	
	private void sendSMS(String phoneNumber, String message) {
		try {
	       SmsManager sms = SmsManager.getDefault();
	       sms.sendTextMessage(phoneNumber, null, message, null, null);
			} catch (Exception e) {
				logln(INFO+"SMS send failed");
			}
	    }
	   
	private synchronized void logTitle(String str) {
    	int len=20;
    	mTitleString += str;
    	if(mTitleString.length() > len) {
    		mTitleString=mTitleString.substring(mTitleString.length() - len);
    	}
    }
    
    public synchronized String getLogTitle() {
    	return new String(mTitleString);
    }

    LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
          // Called when a new location is found by the network location provider
          mLocation = location;
          getPosition();
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };

    /////////////////////////////// STATE MACHINE ///////////////////////////////
    
	private int mAckMode = 0;
	public final static int MODE_NONE = 0;
	public final static int MODE_CLIMBING = 1;
	public final static int MODE_FREE_FALL = 2;
	public final static int MODE_PARACHUTE_1 = 3;
	public final static int MODE_PARACHUTE_2 = 4;
	public final static int MODE_PARACHUTE_3 = 5;
	public final static int MODE_DONE = 6;
	public final static int MODE_TEST_BALLOON = 7;
	public final static int MODE_TEST_PARACHUTE = 8;
	public final static int MAX_MODE = 8;
	private int mMode = MODE_CLIMBING;

	private double mSummitAltitude = 0.0; //ever achieved summit height
	private boolean mSecurityMode = true; //security mode is set for a height very close to the ground, here we don't open the parachute under no circumstances
	
	public synchronized boolean setMode(int mode) {
		if (0 <= mode && mode <= MAX_MODE) {
			logln(INFO + "old mode:" + mMode + " , new mode:" + mode);
			mMode = mode;
			return true;
		}
		return false;
	}

	public synchronized int getMode() {
		return mMode;
	}

	private Runnable mUpdate = new Runnable() {
		public void run() {
			long time=System.currentTimeMillis();

			if(StaticData.EMULATION) {
				mEmulation.update(time);
			}

			if(mFirstPressure && mPressure >=0) {
				mFirstPressure=false;
				mStartPressure = mPressure;
			}

			switch(mMode) {
			case MODE_CLIMBING :
			{
				boolean mode_changed=false;
				int new_mode=MODE_CLIMBING;
				//update summit altitude
				if(mAltitude > mSummitAltitude) {
					mSummitAltitude = mAltitude;
				}
				//we have to reach a minimal height -> unlock security setting for parachute
				if(mSecurityMode) {
					if(mStartPressure - mPressure > 20000) {
						mSecurityMode=false;
						logln(INFO+"security mode turned off");
					}
				}
				//if battery level goes down under a critical level we move to the next mode
				if(mBatteryLevel >= 0 && mBatteryLevel < 15) {
					mode_changed=true;
					new_mode=MODE_FREE_FALL;
					logln(INFO+"battery level below 15% -> FREE FALL");
				}
				if(!mSecurityMode) {
					//do we fall? balloon exploded?
					if(mSummitAltitude - mAltitude > 250) {
						//we lost substantial height
						mode_changed=true;
						new_mode=MODE_FREE_FALL;
						logln(INFO+"altitude << summit altitude -> FREE FALL");
					}
					if(mFreefallDetected) {
						//ADXL345 detected a free fall
						mode_changed=true;
						new_mode=MODE_FREE_FALL;
						logln(INFO+"ADXL345 freefall detected -> FREE FALL");
					}
					//TODO!
					/*else if(time - mStartTime > 6 * 60 * 60 * 1000) {
						//balloon is traveling for a long, long time, let's release the balloon
						mode_changed=true;
						new_mode=MODE_FREE_FALL;
						logln(INFO+"travel time exceeded -> FREE FALL");
					}*/
					if(mStartPressure - mPressure < 15000) {
						//we are approaching the ground -> open parachute
						mode_changed=true;
						new_mode=MODE_PARACHUTE_1;
						logln(INFO+"air pressure close to ground pressure -> PARACHUTE (1)");
					}
				}

				if(mode_changed) {
					setMode(new_mode);
					//remove handlers ...
					mHandler.removeCallbacks(mSMSPosition);
					mHandler.removeCallbacks(mTakePicture);
					//.. and reschedule at a faster cycle
					StaticData.mTakePicture = false; // video from now
					if(StaticData.mTakingPicture) {
						mHandler.postDelayed(mTakePicture, 2000); //should be longer than taking a photo
					}
					else {
						mHandler.postDelayed(mTakePicture, 250); //start almost immediately
					}
					mHandler.postDelayed(mSMSPosition, 15 *1000); //much faster SMS updates
				}
				} break;
			case MODE_FREE_FALL :
			{
				boolean mode_changed=false;
				int new_mode=MODE_FREE_FALL;
				if(mStartPressure - mPressure < 10000) {
					//we are approaching the ground -> open parachute
					mode_changed=true;
					new_mode=MODE_PARACHUTE_1;
					logln(INFO+"air pressure close to ground pressure -> PARACHUTE (2)");
				}
				else if(mBatteryLevel >= 0 && mBatteryLevel < 8) {
					mode_changed=true;
					new_mode=MODE_PARACHUTE_1;
					logln(INFO+"battery level below 8% -> PARACHUTE");
				}

				if(mode_changed) {
					StaticData.mTakePicture = true; // back to picture mode for the next shot
					setMode(new_mode);
				}
			} break;
			case MODE_PARACHUTE_1 :
			{
				boolean mode_changed=false;
				int new_mode=MODE_PARACHUTE_1;
				if(mStartPressure - mPressure < 8000) {
					//we are approaching the ground -> open parachute
					mode_changed=true;
					new_mode=MODE_PARACHUTE_2;
					logln(INFO+"air pressure close to ground pressure -> PARACHUTE (3)");
				}
				else if(mBatteryLevel >= 0 && mBatteryLevel < 6) {
					mode_changed=true;
					new_mode=MODE_PARACHUTE_2;
					logln(INFO+"battery level below 6% -> PARACHUTE");
				}

				if(mode_changed) {
					StaticData.mTakePicture = true; // back to picture mode for the next shot
					setMode(new_mode);
				}
			} break;
			case MODE_PARACHUTE_2 :
			{
				boolean mode_changed=false;
				int new_mode=MODE_PARACHUTE_2;
				if(mStartPressure - mPressure < 6000) {
					//we are approaching the ground -> open parachute
					mode_changed=true;
					new_mode=MODE_PARACHUTE_3;
					logln(INFO+"air pressure close to ground pressure -> PARACHUTE (4)");
				}
				else if(mBatteryLevel >= 0 && mBatteryLevel < 4) {
					mode_changed=true;
					new_mode=MODE_PARACHUTE_3;
					logln(INFO+"battery level below 4% -> PARACHUTE");
				}

				if(mode_changed) {
					StaticData.mTakePicture = true; // back to picture mode for the next shot
					setMode(new_mode);
				}
			} break;
			case MODE_PARACHUTE_3 :
			{
				boolean mode_changed=false;
				int new_mode=MODE_PARACHUTE_3;
				if(mStartPressure - mPressure < 4000) {
					//we are approaching the ground -> open parachute
					mode_changed=true;
					new_mode=MODE_DONE;
					logln(INFO+"air pressure close to ground pressure -> PARACHUTE (5)");
				}
				else if(mBatteryLevel >= 0 && mBatteryLevel < 2) {
					mode_changed=true;
					new_mode=MODE_DONE;
					logln(INFO+"battery level below 2% -> PARACHUTE");
				}

				if(mode_changed) {
					StaticData.mTakePicture = true; // back to picture mode for the next shot
					setMode(new_mode);
				}
			} break;
			case MODE_DONE :
			{
				//nothing specific to do
			} break;
			case MODE_TEST_BALLOON :
			case MODE_TEST_PARACHUTE :
			{
				//nothing specific to do
			} break;
			}
			mHandler.postDelayed(mUpdate, UPDATE_DELAY);
		}
	};
	
	class Emulator {
		private final double CLIMB_TIME = 4*60*60*1000;
		private final double FALL_TIME = 30*60*1000;
		private final double LONGITUDE = 116.39043, LATITUDE = 39.922902;
		private final double START_ALTITUDE = 52.0, SUMMIT_ALTITUDE = 39000.0;
		private final double START_PRESSURE = 101000, SUMMIT_PRESSURE = 0;
		private final double START_TEMPERATURE = 225, SUMMIT_TEMPERATURE = -60;
		public void update(long time) {
			double dt=time - mStartTime;
			if(dt<=CLIMB_TIME) {
				//climbing simulation
				if(dt<0.0) dt=0;
				double frac=dt/CLIMB_TIME;
				if(frac > 1.0) frac=1.0;
				mLongitude=LONGITUDE;
				mLatitude=LATITUDE;
				mAltitude = START_ALTITUDE + frac*(SUMMIT_ALTITUDE - START_ALTITUDE);
				mPressure = (int)(START_PRESSURE + frac*(SUMMIT_PRESSURE - START_PRESSURE));
				mTemperature = (int)(START_TEMPERATURE + frac*(SUMMIT_TEMPERATURE - START_TEMPERATURE));			
			}
			else {
				//fall simulation
				double t=dt-CLIMB_TIME;
				if(t<0.0) t=0;
				double frac=t/FALL_TIME;
				if(frac > 1.0) frac=1.0;
				mLongitude=LONGITUDE;
				mLatitude=LATITUDE;
				mAltitude = START_ALTITUDE + (1.0-frac)*(SUMMIT_ALTITUDE - START_ALTITUDE);
				mPressure = (int)(START_PRESSURE + (1.0-frac)*(SUMMIT_PRESSURE - START_PRESSURE));
				mTemperature = (int)(START_TEMPERATURE + (1.0-frac)*(SUMMIT_TEMPERATURE - START_TEMPERATURE));			
			}
		}
	};
	
	Emulator mEmulation = new Emulator();
}
