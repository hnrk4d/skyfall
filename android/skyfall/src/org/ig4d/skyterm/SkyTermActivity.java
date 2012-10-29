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
		startService(new Intent(SkyTermActivity.this, SkyTermService.class));
		mTextbox = (EditText) findViewById(R.id.editText);
		mScroller = (ScrollView) findViewById(R.id.scroller);

		((Button)this.findViewById(R.id.stop_button)).setOnClickListener(stopOnClickListener);
		((Button)this.findViewById(R.id.button_long_lat)).setOnClickListener(longLatOnClickListener);
		((Button)this.findViewById(R.id.button_unlock_parachute)).setOnClickListener(unlockParachuteOnClickListener);
		
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
		if(StaticData.mSkyTermService != null) {
			String str=StaticData.mSkyTermService.getLogTitle();
			if(str.length() > 0) {
				setTitle(mTitle+" ["+str+"]");
			}
			mTextbox.setText(StaticData.mSkyTermService.getLog());
			mScroller.smoothScrollTo(0, mTextbox.getBottom()); 
			}
		else {
			setTitle(mTitle);
		}
	}
	
    private OnClickListener stopOnClickListener = new OnClickListener() {
        public void onClick(View v) {
    		//stop service
    		stopService(new Intent(SkyTermActivity.this, SkyTermService.class));
			android.os.Process.killProcess(android.os.Process.myPid());
        }
    };  

    private OnClickListener longLatOnClickListener = new OnClickListener() {
        public void onClick(View v) {
        }
    };

    private OnClickListener unlockParachuteOnClickListener = new OnClickListener() {
        public void onClick(View v) {
        }
    };
}
