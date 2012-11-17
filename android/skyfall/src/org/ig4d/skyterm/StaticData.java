/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/
package org.ig4d.skyterm;

import android.hardware.Camera;

public class StaticData {
	public static SkyTermService mSkyTermService = null;
	public static SkyTermActivity mSkyTermActivity = null;

	public static boolean mTakePicture = true;
	//TODO!
	public static boolean mSendSMS = false;
	public static boolean mTakingPicture = false;

	public static String EXT_SD_CARD = "/sdcard/external_sd"; //Samsung specific
	
	final static String FOCUS=Camera.Parameters.FOCUS_MODE_INFINITY;
	//final static String FOCUS=Camera.Parameters.FOCUS_MODE_MACRO;

    final static boolean EMULATION = true;
	
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
