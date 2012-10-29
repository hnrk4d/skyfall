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
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
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
	Time mTime = new Time(Time.getCurrentTimezone());
	private static final int CLOSE_TIMEOUT = 1000;
	private static final int PICTURE_DELAY = 10000;

	private static final String ERR = "ERR: ";
	private static final String INFO = "INFO: ";
	private static final String DEVICE = "japanboxz";
	private static enum State {
		STATE_START_UP, STATE_CONNECT, STATE_COMMUNICATE, STATE_CLOSE
	};

	private State mState = State.STATE_START_UP;
	private String mTitleString=new String();
    private Handler mHandler = new Handler();
    private int NUM_LOG_STRING = 100;
    private int mNumLogs=0;
	private String mLog = new String();
	// Positioning
	// private LocationProvider mLocationProvider =
	// LocationManager.GPS_PROVIDER;
	
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

			// mLocationManager.requestLocationUpdates(mLocationProvider, 0, 0,
			// mLocationListener);

			mHandler.postDelayed(mTakePicture, PICTURE_DELAY);
			mCreated = true;
		}
  		StaticData.mSkyTermService=this;
    }
    
    @Override
    public void onDestroy() {
    	StaticData.mSkyTermService=null;
    	closeLogFile();
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
						sendCmd("ping");
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
	
	boolean findBT() {
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
			// Intent enableBluetooth = new
			// Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			// startActivityForResult(enableBluetooth, 0);
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

	boolean openBT() throws IOException {
		UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // Standard
																				// SerialPortService
																				// ID
		if (mDevice != null) {
			mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
			mSocket.connect();
			mOutputStream = mSocket.getOutputStream();
			mInputStream = mSocket.getInputStream();

			beginListenForData();

			logln(INFO + "Bluetooth opened");
			return true;
		} else {
			logln(ERR + "Bluetooth device '" + DEVICE + "' unavailable");
		}
		return false;
	}

	void beginListenForData() {
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
						Thread.sleep(10);
					} catch (Throwable e) {
					}
				}
			}
		});

		mWorkerThread.start();
	}

	private void process(String cmd) {
		if (cmd.toLowerCase().startsWith("log:")) {
			logln(INFO + cmd);
		} else if (cmd.compareToIgnoreCase("ack") == 0) {
			logTitle("-");
		}
	}

	synchronized void sendCmd(String msg) throws IOException {
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
	}

	void closeBT() {
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
		// mBluetoothAdapter=null;
		System.gc();
		logln(INFO + "Bluetooth closing ...");
	}

	public synchronized void logln(String text) {
		mTime.setToNow();
		String str = mTime.format("%k:%M:%S") + ":" + text + "\n";

		if(mNumLogs >= NUM_LOG_STRING) {
			mNumLogs=0;
			mLog=new String();
		}
		
		mLog+=str;
		mNumLogs++;
		writeToFile(str);
	}

	public synchronized void log(String text) {
		mLog+=text;
		writeToFile(text);
	}

	public synchronized String getLog() {
		return new String(mLog);
	}

	private FileOutputStream mF;
	private PrintWriter mPw;

	private boolean openLogFile() {
		File root;
		boolean externalStorageAvailable = false;
		boolean externalStorageWriteable = false;
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			externalStorageAvailable = externalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			externalStorageAvailable = true;
			externalStorageWriteable = false;
		} else {
			// Something else is wrong. It may be one of many other states, but
			// all we need
			// to know is we can neither read nor write
			externalStorageAvailable = externalStorageWriteable = false;
		}

		if (externalStorageAvailable && externalStorageWriteable) {
			root = android.os.Environment.getExternalStorageDirectory();
		} else {
			root = android.os.Environment.getDataDirectory();
		}

		File dir = new File(root.getAbsolutePath() + "/download");
		dir.mkdirs();

		File ofile = new File(dir, "skyterm_log.txt");
		if (ofile.exists()) {
			File nfile = new File(dir, "skyterm_log_old.txt");
			ofile.renameTo(nfile);
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

	private Runnable mTakePicture = new Runnable() {
		public void run() {
			Intent intend = new Intent(getBaseContext(), PictureActivity.class);
			intend.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intend);
			mHandler.postDelayed(mTakePicture, PICTURE_DELAY);
		}
	};

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
}
