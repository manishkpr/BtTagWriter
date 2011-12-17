/**
 * BtSecureSimplePairing.java (bttagwriter)
 *
 * Copyright 2011 Sami Viitanen <sami.viitanen@gmail.com>
 * All rights reserved.
 */
package fi.siika.bttagwriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.util.Log;

/**
 * Class providing parse and generate functions for Bluetooth Secure Simple
 * Pairing binaries
 */
public class BtSecureSimplePairing {
	
	public final static String MIME_TYPE = "application/vnd.bluetooth.ep.oob";
	
	/*
	 * Magic values are from:
	 * https://www.bluetooth.org/Technical/AssignedNumbers/generic_access_profile.htm
	 */
	private final static byte BYTE_SHORTENED_LOCAL_NAME = 0x08;
	private final static byte BYTE_COMPLETE_LOCAL_NAME = 0x09; 
	private final static byte BYTE_CLASS_OF_DEVICE = 0x0D;
	private final static byte BYTE_SIMPLE_PAIRING_HASH = 0x0E;
	private final static byte BYTE_SIMPLE_PAIRING_RANDOMIZER = 0x0F;
	private final static byte BYTE_MANUFACTURER_SPECIFIC_DATA = -1;
	
	private final static String DEBUG_TAG = "BtSecureSimplePairing";
	
	private final static short SPACE_TOTAL_LEN_BYTES = 2;
	private final static short SPACE_ADDRESS_BYTES = 6;
	private final static short SPACE_DEVICE_CLASS_BYTES = 5;
	private final static short SPACE_MIN_BYTES =
		SPACE_TOTAL_LEN_BYTES + SPACE_ADDRESS_BYTES;
	
	/**
	 * Class containing the data we care about
	 */
	public static class Data {
		private String mName;
		private String mAddress;
		private byte[] mDeviceClass;
		private byte[] mHash;
		private byte[] mRandomizer;
		private byte[] mManufacturerData;
		
		public Data() {
			mName = "";
			mAddress = "00:00:00:00:00:00";
			mDeviceClass = new byte[0];
			mHash = new byte[0];
			mRandomizer = new byte[0];
			mManufacturerData = new byte[0];
		}
		
		public void setName(String name) {
			mName = name;
		}
		
		public String getName () {
			return mName;
		}
		
		public byte[] getNameBuffer () {
			byte[] ret = null;
			
			if (mName.isEmpty() == false) {
				try {
					ret = mName.getBytes("UTF-8");
				} catch (Exception e) {
					ret = null;
				}
			}
			
			return ret;
		}
		
		/**
		 * Will only accept valid addresses. But both lower and upper case
		 * letters are accepted.
		 * @param address Address in string format (e.g. "00:00:00:00:00:00")
		 */
		public void setAddress (String address) {
			String modAddress = address.toUpperCase();
			if (BluetoothAdapter.checkBluetoothAddress(modAddress)) {
				mAddress = modAddress;
			}
		}
		
		public String getAddress () {
			return mAddress;
		}
		
		public byte[] getAddressBuffer () {
			byte[] ret = new byte[6];
			String[] parts = mAddress.split(":");
			for (int i = 0; i < 6; ++i) {
				ret[i] = (byte)Short.parseShort(parts[5-i], 16);
			}
			return ret;
		}
		
		public void setDeviceClass (byte[] deviceClass) {
			if (deviceClass.length == 3) {
				mDeviceClass = deviceClass;
			}
		}
		
		public byte[] getDeviceClass () {
			return mDeviceClass;
		}
		
		public boolean hasDeviceClass() {
			return mDeviceClass.length == 3;
		}
		
		public void setHash (byte[] hash) {
			mHash = hash;
		}
		
		public void setRandomizer (byte[] randomizer) {
			mHash = randomizer;
		}
		
		public byte[] getHash() {
			return mHash;
		}
		
		public byte[] getRandomizer() {
			return mRandomizer;
		}
		
		public void setManufacturerData (byte[] data) {
			mManufacturerData = data;
		}
		
		public byte[] getManufacturerData() {
			return mManufacturerData;
		}
		
		private final static String MANUFACTURER_DATA_PIN_PREFIX = "PIN";
		
		/**
		 * This is temporary solution! Must move to use hash and randomizer
		 * if possible!
		 * @return Pin in string format if defined inside manufacturer data
		 * as this application assumes it.
		 */
		public String getTempPin() {
			String ret = null;
			try {
				ret = new String(mManufacturerData, "UTF-8");
				if (ret.startsWith (MANUFACTURER_DATA_PIN_PREFIX)) {
					ret = ret.substring(MANUFACTURER_DATA_PIN_PREFIX.length());
				} else {
					ret = null;
				}
			} catch (Exception e) {}
			return ret;
		}
		
		/**
		 * This is temporary solution! Must move to use hash and randomizer
		 * if possible!
		 * @param pin Pin stored under manufacturer data as this application
		 * assumes it.
		 */
		public void setTempPin (String pin) {
			String str = MANUFACTURER_DATA_PIN_PREFIX + pin;
			try {
				mManufacturerData = str.getBytes("UTF-8");
			} catch (Exception e) {}
		}
		
	};
	
	/*
	 * How binary data is constructed:
	 * First two bytes = total length
	 * Next six bytes = address
	 * Repeat next until end:
	 * first byte = "data length"
	 * second byte = type
	 * "data length" bytes = data 
	 */
	
	/**
	 * Generate binary Bluetooth Secure Simple Pairing content
	 * @param input Information stored to binary output
	 * @param maxLength How big byte array can be returned
	 * @return Return binary content
	 */
	public static byte[] generate(Data input, short maxLength)
		throws IOException {
		
		//TODO: Is 30k bytes enough? I assume so ;) (16th bit can't be used)
		short len = SPACE_MIN_BYTES;
		
		byte[] manBytes = null;
		byte[] classBytes = null;
		byte[] nameBytes = null;
		boolean moreIn = true;
		
		// Manufacturer data is most important (as for now it contains PIN)
		if (moreIn && len < maxLength) {
			manBytes = input.getManufacturerData();
			if (manBytes == null) {
				moreIn = true;
			} else if ((len + 2 + manBytes.length) <= maxLength) {
				len += 2 + manBytes.length;
			} else {
				manBytes = null;
				moreIn = false;
			}
		}
		
		// Device class is also important, as I will be used later to separate
		// different ways to connect the device.
		if (moreIn && len < maxLength) {
			classBytes = input.getDeviceClass();
			if (classBytes == null) {
				moreIn = true;
			} else if ((len + 2 + classBytes.length) <= maxLength) {
				len += 2 + classBytes.length;
			} else {
				classBytes = null;
				moreIn = false;
			}
		}
		
		// Name is nice to have but takes lots of space
		if (moreIn && len < maxLength) {
			nameBytes = input.getNameBuffer();
			if (nameBytes == null) {
				moreIn = true;
			} else if ((len + 2 + nameBytes.length) <= maxLength) {
				len += 2 + nameBytes.length;
			} else {
				nameBytes = null;
				moreIn = false;
			}
		}
		
		//Still check that we are inside the limits
		if (len > maxLength) {
			Log.w (DEBUG_TAG, "Not enough space in tag for content");
			throw new IOException("Not enough space");
		}
		
		byte data[] = new byte[len];
		int index = -1;
		
		// total length (2 bytes)
		ByteBuffer buffer = ByteBuffer.allocate(2);
		buffer.putShort(len);
		data[++index] = buffer.get(0);
		data[++index] = buffer.get(1);
		
		// address
		byte[] addressBuffer = input.getAddressBuffer ();
		for (int i = 0; i < addressBuffer.length; ++i) {
			data[++index] = (byte)addressBuffer[i];
		}
		
		// complete local name
		if (nameBytes != null) {
			data[++index] = (byte)(nameBytes.length + 0x01);
			data[++index] = BYTE_COMPLETE_LOCAL_NAME;
			for (int i = 0; i < nameBytes.length; ++i) {
				data[++index] = (byte)nameBytes[i];
			}
		}
		
		// manufacturer specific data
		if (manBytes != null) {
			data[++index] = (byte)(1 + manBytes.length);
			data[++index] = BYTE_MANUFACTURER_SPECIFIC_DATA;
			for (int i = 0; i < manBytes.length; ++i) {
				data[++index] = (byte)manBytes[i];
			}
		}
		
		// class of device
		if (classBytes != null) {
			data[++index] = (byte)(1 + classBytes.length);
			data[++index] = BYTE_CLASS_OF_DEVICE;
			for (int i = 0; i < classBytes.length; ++i) {
				data[++index] = (byte)classBytes[i];
			}
		}

		return data;
	}
	
	/**
	 * Parse binary Bluetooth Secure Simple Pairing content
	 * @param binaryData Binary data
	 * @return Binary data converted to class for easy access
	 */
	public static Data parse (byte[] binaryData) throws Exception {
		Data data = new Data();
		
		//TODO: ignoring for now length bytes 0 and 1
		
		//TODO: There has to be nicer way to do this!!!
    	int[] addressBuffer = new int[6];
    	for (int i = 0; i < 6; i++) {
    		addressBuffer[i] = (int)(binaryData[7-i]) & 0xff;
    	}
    	StringBuilder sb = new StringBuilder();
    	for (int i = 0; i < 6; ++i) {
    		if (i > 0) {
    			sb.append(":");
    		}
    		if (addressBuffer[i] < 0x10) {
    			sb.append("0");
    		}

    		sb.append (Integer.toHexString(addressBuffer[i]));
    	}
    	data.setAddress(sb.toString());
    	Log.d (DEBUG_TAG, "Address: " + data.getAddress());
    	
    	//Read the rest
    	for (int i = 8; i < binaryData.length; ++i) {
    		int dataLen = (int)(binaryData[i]); //this includes type and data
    		int dataType = (int)(binaryData[i+1]); //type bit
    		
    		/*
    		Log.d (DEBUG_TAG, new StringBuilder().append("Element: ").append(
    			dataLen).append(" ").append(dataType).toString());
    		*/
    		
    		byte[] dataArray = Arrays.copyOfRange(binaryData, i+2,
    			i + 1 + dataLen);
    		
    		i = i + dataLen; //Update index for next round (for will add 1)
    		
    		switch (dataType) {
    		case BYTE_COMPLETE_LOCAL_NAME:
    			data.setName(new String(dataArray, "UTF-8"));
    			break;
    		case BYTE_SHORTENED_LOCAL_NAME:
    			//Do not override complete name if it exists
    			if (data.getName().isEmpty()) {
    				data.setName(new String(dataArray, "UTF-8"));
    			}
    			break;
    		case BYTE_CLASS_OF_DEVICE:
    			data.setDeviceClass(dataArray);
    			break;
    		case BYTE_SIMPLE_PAIRING_RANDOMIZER:
    			data.setRandomizer(dataArray);
    			break;
    		case BYTE_SIMPLE_PAIRING_HASH:
    			data.setHash(dataArray);
    			break;
    		default:
    			//There are many known elements we ignore here
    			Log.w(DEBUG_TAG, new StringBuilder().append(
    				"Unknown element: ").append(dataType).toString());
    		}
    		
    	}
    	
    	Log.d (DEBUG_TAG, "Parsed data: '" + data.getAddress() + "' '"
    		+ data.getName() + "'");
		
		return data;
	}
}