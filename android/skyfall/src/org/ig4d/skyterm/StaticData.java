package org.ig4d.skyterm;

import android.hardware.Camera;

public class StaticData {
	public static SkyTermService mSkyTermService = null;
	public static SkyTermActivity mSkyTermActivity = null;
	public static boolean mTakePicture = true;

	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}
}
