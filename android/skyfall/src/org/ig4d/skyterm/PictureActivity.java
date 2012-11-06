package org.ig4d.skyterm;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class PictureActivity extends Activity {
	private static String TAG = "PIC: ";

	private SurfaceView mPreview = null;
	private SurfaceHolder mPreviewHolder = null;
	private Camera mCamera = null;
	private boolean mInPreview = false;
	private boolean mCameraConfigured = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_picture);

		mPreview = (SurfaceView) findViewById(R.id.camera_preview);
		mPreviewHolder = mPreview.getHolder();
		mPreviewHolder.addCallback(surfaceCallback);
		mPreviewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		logln(TAG + "Picture activity created.");
	}

	@Override
	public void onResume() {
		super.onResume();

		mCamera = StaticData.getCameraInstance();

		startPreview();
		mPreview.postDelayed(mCameraTimer, 200);
	}

	@Override
	public void onPause() {
		if (mInPreview) {
			mCamera.stopPreview();
		}

		mCamera.release();
		mCamera = null;
		mInPreview = false;

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(this).inflate(R.menu.activity_sky_term, menu);
		return (super.onCreateOptionsMenu(menu));
	}

	private Runnable mCameraTimer = new Runnable() {
		public void run() {
			if (mInPreview) {
				mCamera.takePicture(null, null, photoCallback);
				mInPreview = false;
			}
		}
	};

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return (super.onOptionsItemSelected(item));
	}

	private Camera.Size getBestPreviewSize(int width, int height,
			Camera.Parameters parameters) {
		Camera.Size result = null;

		for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
			if (size.width <= width && size.height <= height) {
				if (result == null) {
					result = size;
				} else {
					int resultArea = result.width * result.height;
					int newArea = size.width * size.height;

					if (newArea > resultArea) {
						result = size;
					}
				}
			}
		}

		return (result);
	}

	private Camera.Size getBiggestPictureSize(Camera.Parameters parameters) {
		Camera.Size result = null;

		for (Camera.Size size : parameters.getSupportedPictureSizes()) {
			if (result == null) {
				result = size;
			} else {
				int resultArea = result.width * result.height;
				int newArea = size.width * size.height;

				if (newArea > resultArea) {
					result = size;
				}
			}
		}

		return (result);
	}

	private void initPreview(int width, int height) {
		if (mCamera != null && mPreviewHolder.getSurface() != null) {
			try {
				mCamera.setPreviewDisplay(mPreviewHolder);
			} catch (Throwable t) {
				logln(TAG + "Exception in setPreviewDisplay():"
						+ t.getMessage());
			}

			if (!mCameraConfigured) {
				Camera.Parameters parameters = mCamera.getParameters();
				Camera.Size size = getBestPreviewSize(width, height, parameters);
				Camera.Size pictureSize = getBiggestPictureSize(parameters);

				if (size != null && pictureSize != null) {
					parameters.setPreviewSize(size.width, size.height);
					parameters.setPictureSize(pictureSize.width,
							pictureSize.height);
					parameters.setPictureFormat(ImageFormat.JPEG);
					mCamera.setParameters(parameters);
					mCameraConfigured = true;
				}
			}
		}
	}

	private void startPreview() {
		if (mCameraConfigured && mCamera != null && !mInPreview) {
			mCamera.startPreview();
			mInPreview = true;
		}
	}

	SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
		public void surfaceCreated(SurfaceHolder holder) {
			// no-op -- wait until surfaceChanged()
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			initPreview(width, height);
			startPreview();
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			// no-op
		}
	};

	Camera.PictureCallback photoCallback = new Camera.PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			saveImg(data);
			camera.startPreview();
			mInPreview = true;

			// back to main view
			Intent intend = new Intent(PictureActivity.this, SkyTermActivity.class);
			intend.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intend);

			PictureActivity.this.finish();
		}
	};

	protected void saveImg(byte[]... jpeg) {
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String path=StaticData.EXT_SD_CARD;
		if(!(new File(StaticData.EXT_SD_CARD)).exists()) {
			path=Environment.getExternalStorageDirectory().getAbsolutePath();
		}
		path+=File.separator + "Pictures";
		File photo = new File(path, "SKY_" + timeStamp + ".jpg");

		if (photo.exists()) {
			photo.delete();
		}
		(new File(path)).mkdirs();

		try {
			FileOutputStream fos = new FileOutputStream(photo.getPath());

			fos.write(jpeg[0]);
			fos.close();
			logln(TAG + "saved picture to " + photo.getAbsolutePath());
		} catch (java.io.IOException e) {
			logln(TAG + "Exception in photoCallback:" + e.getMessage());
		}
	}

	private void logln(String str) {
		if(StaticData.mSkyTermService != null) {
			StaticData.mSkyTermService.logln(str);
		}
	}
}
