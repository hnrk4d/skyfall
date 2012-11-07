/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/
package org.ig4d.skyterm;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

public class VideoActivity extends Activity implements Callback, MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
	private static String TAG = "VID: ";

	private SurfaceView mPreview = null;
	private SurfaceHolder mPreviewHolder = null;

	private MediaRecorder mMediaRecorder;
	private static int VIDEO_LENGTH = 5000;
	private String mVideoFileName=new String("unnamed");
	private boolean mIsRecording=false;
	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        mMediaRecorder = new MediaRecorder();
        setContentView(R.layout.activity_video);

		mPreview = (SurfaceView) findViewById(R.id.video_preview);
		mPreviewHolder = mPreview.getHolder();
		mPreviewHolder.addCallback(this);
		mPreviewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		logln(TAG + "Video activity created.");
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onPause() {
		stopVideo();
		super.onPause();
	}

	private void stopVideo() {
		if(mIsRecording) {
			mMediaRecorder.stop();
			mMediaRecorder.reset();
			mMediaRecorder.release();
			mIsRecording=false;
			logln(TAG + "saved video to " + mVideoFileName);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(this).inflate(R.menu.activity_sky_term, menu);
		return (super.onCreateOptionsMenu(menu));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return (super.onOptionsItemSelected(item));
	}

	private boolean initRecorder() {
		mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        mMediaRecorder.setProfile(cpHigh);
        mVideoFileName=getOutputMediaFile("mp4").toString();
		mMediaRecorder.setOutputFile(mVideoFileName);
		mMediaRecorder.setMaxDuration(VIDEO_LENGTH);
		mMediaRecorder.setOnInfoListener(this);
		mMediaRecorder.setOnErrorListener(this);

		return true;
	}

    private void prepareRecorder() {
		mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
        try {
        	mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
        	logln(TAG + e.getMessage());
            finish();
        } catch (IOException e) {
        	logln(TAG + e.getMessage());
            finish();
        }
    }

	public void surfaceCreated(SurfaceHolder holder) {
	  initRecorder();
	  prepareRecorder();
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		mMediaRecorder.start();
		mIsRecording=true;
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		stopVideo();
	}

	private void logln(String str) {
		if(StaticData.mSkyTermService != null) {
			StaticData.mSkyTermService.logln(str);
		}
	}

	private File getOutputMediaFile(String extension) {
		String path=StaticData.EXT_SD_CARD;
		if(!(new File(StaticData.EXT_SD_CARD)).exists()) {
			path=Environment.getExternalStorageDirectory().getAbsolutePath();
		}
		path+=File.separator + "Pictures";
		File mediaStorageDir = new File(path);
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				logln(TAG +"failed to create directory");
				return null;
			}
		}
		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new Date());
		File mediaFile = new File(mediaStorageDir.getPath() + File.separator
				+ "VID_" + timeStamp + "."+extension);
		return mediaFile;
	}

	public void onInfo(MediaRecorder mr, int what, int extra) {
		if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
				what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
			stopVideo();
			// back to main view
			Intent intend = new Intent(VideoActivity.this, SkyTermActivity.class);
			intend.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intend);
			VideoActivity.this.finish();
		}
	}
	
	public void onError(MediaRecorder mr, int what, int extra) {
		stopVideo();
		// back to main view
		Intent intend = new Intent(VideoActivity.this, SkyTermActivity.class);
		intend.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intend);
		VideoActivity.this.finish();
	}
}
