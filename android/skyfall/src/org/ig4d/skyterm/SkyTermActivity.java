/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/
package org.ig4d.skyterm;

import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;

public class SkyTermActivity extends Activity {

	private String mTitle;
	private String mVideo;
	private String mPicture;
	private EditText mTextbox;
	private ScrollView mScroller;
    private Handler mHandler = new Handler();
	private WakeLock mWakeLock;
    private static int UPDATE_DELAY = 200;

	//http://saigeethamn.blogspot.de/2009/09/android-developer-tutorial-for_04.html
    //http://developer.android.com/reference/android/app/Service.html

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sky_term);
		mTitle = getResources().getString(R.string.title_activity_sky_term);
		mVideo = getResources().getString(R.string.vid);
		mPicture = getResources().getString(R.string.pic);
		startService(new Intent(SkyTermActivity.this, SkyTermService.class));
		mTextbox = (EditText) findViewById(R.id.editText);
		mScroller = (ScrollView) findViewById(R.id.scroller);

		((Button)this.findViewById(R.id.stop_button)).setOnClickListener(stopOnClickListener);
		((Button)this.findViewById(R.id.button_climbing)).setOnClickListener(climbingOnClickListener);
		((Button)this.findViewById(R.id.button_release_balloon)).setOnClickListener(balloonOnClickListener);
		((Button)this.findViewById(R.id.button_unlock_parachute)).setOnClickListener(unlockParachuteOnClickListener);
		((Button)this.findViewById(R.id.button_video)).setOnClickListener(videoOnClickListener);
		((Button)this.findViewById(R.id.sms)).setOnClickListener(smsOnClickListener);

		//start service
		if(StaticData.mSkyTermService == null) {
			startService(new Intent(SkyTermActivity.this, SkyTermService.class));

			final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
	        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "");
	        mWakeLock.acquire();
		}
	
  		StaticData.mSkyTermActivity=this;
  	}

	@Override
	public void onDestroy() {
  		StaticData.mSkyTermActivity=null;
  		mWakeLock.release();
		super.onDestroy();
	}
			
	@Override
	public void onResume() {
		super.onResume();
  		mHandler.postDelayed(mUpdateTask, UPDATE_DELAY);
  	}

	@Override
	public void onPause() {
		super.onPause();
		mHandler.removeCallbacks(mUpdateTask);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_sky_term, menu);
		return true;
	}

	private Runnable mUpdateTask = new Runnable() {
		public void run() {
			SkyTermActivity.this.update();
			mHandler.postDelayed(mUpdateTask, UPDATE_DELAY);
		}
	};

	public void update() {
		String medium=mPicture;
		if(!StaticData.mTakePicture) {
			medium=mVideo;
		}
		medium=" - " + medium;
		if(StaticData.mSkyTermService != null) {
			String str=StaticData.mSkyTermService.getLogTitle();
			if(str.length() > 0) {
				setTitle(mTitle + medium + " ["+str+"]");
			}
			mTextbox.setText(StaticData.mSkyTermService.getLog());
			mScroller.smoothScrollTo(0, mTextbox.getBottom()); 
			}
		else {
			setTitle(mTitle + medium);
		}
	}
	
    private OnClickListener stopOnClickListener = new OnClickListener() {
        public void onClick(View v) {
        	mHandler.postDelayed(mStopTask, 200);
        }
    };  

    private OnClickListener climbingOnClickListener = new OnClickListener() {
        public void onClick(View v) {
        	if(StaticData.mSkyTermService != null) {
        		StaticData.mSkyTermService.setMode(SkyTermService.MODE_CLIMBING);
        	}
        }
    };

    private OnClickListener balloonOnClickListener = new OnClickListener() {
        public void onClick(View v) {
        	if(StaticData.mSkyTermService != null) {
        		StaticData.mSkyTermService.setMode(SkyTermService.MODE_FREE_FALL);
        	}
        }
    };

    private OnClickListener unlockParachuteOnClickListener = new OnClickListener() {
        public void onClick(View v) {
        	if(StaticData.mSkyTermService != null) {
        		StaticData.mSkyTermService.setMode(SkyTermService.MODE_PARACHUTE);
        	}
        }
    };

    private OnClickListener videoOnClickListener = new OnClickListener() {
        public void onClick(View v) {
        	StaticData.mTakePicture = StaticData.mTakePicture?false:true;
        }
    };

    private OnClickListener smsOnClickListener = new OnClickListener() {
        public void onClick(View v) {
        	StaticData.mSendSMS = StaticData.mSendSMS?false:true;
        }
    };

    private Runnable mStopTask = new Runnable() {
		public void run() {
    		stopService(new Intent(SkyTermActivity.this, SkyTermService.class));
			android.os.Process.killProcess(android.os.Process.myPid());
		}
	};
}
