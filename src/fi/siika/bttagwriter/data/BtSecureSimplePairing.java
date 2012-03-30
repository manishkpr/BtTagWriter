/**
 * BtSecureSimplePairing.java (bttagwriter)
 *
 * Copyright 2011 Sami Viitanen <sami.viitanen@gmail.com>
 * All rights reserved.
 */
package fi.siika.bttagwriter.data;

import java.nio.ByteBuffer;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;
import android.util.Log;
import fi.siika.bttagwriter.exceptions.OutOfSpaceException;

/**
 * Class providing parse and generate functions for Bluetooth Secure Simple
 * Pairing binaries
 */
public class BtSecureSimplePairing {
	
	public final static String MIME_TYPE = "application/vnd.bluetooth.ep.oob";
	
	/**
	 * Using this would give 14 bytes more in small tags. But proper with
	 * proper mime mifare ultralight can still fit the address. So this is
	 * not used yet.
	 */
	//public final static String SHORT_MIME_TYPE = "application/x-btc";
	
	
	/**
	 * Check if given mime type 
	 * @param mime Mime type string checked
	 * @return true if mime is one of those used with this data
	 */
	public static boolean validMimeType (String mime) {
		return (MIME_TYPE.equals(mime));
	}
	
	/*
	 * Magic values are from:
	 * https://www.bluetooth.org/Technical/AssignedNumbers/generic_access_profile.htm
	 */
	private final static byte BYTE_SHORTENED_LOCAL_NAME = 0x08;
	private final static byte BYTE_COMPLETE_LOCAL_NAME = 0x09; 
	private final static byte BYTE_CLASS_OF_DEVICE = 0x0D;
	private final static byte BYTE_SIMPLE_PAIRING_HASH = 0x0E;
	private final static byte BYTE_SIMPLE_PAIRING_RANDOMIZER = 0x0F;
	
	private final static byte BYTE_MANUFACTURER_SPECIFIC_DATA = -1; //-1 = 0xFF
	
	private final static String TAG = "BtSecureSimplePairing";
	
	private final static short SPACE_TOTAL_LEN_BYTES = 2;
	private final static short SPACE_ADDRESS_BYTES = 6;
	
	/*!
	 * Minimum space needed in bytes
	 */
	public final static short MIN_SIZE_IN_BYTES =
		SPACE_TOTAL_LEN_BYTES + SPACE_ADDRESS_BYTES;
	
	/**
	 * Class containing the data we care about
	 */
	public static class Data {
		private String mName;
		private String mAddress;
		private byte[] mDeviceClass;
		private byte[] mHash;
		private final byte[] mRandomizer;
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
		throws OutOfSpaceException {
				
		//TODO: Is 30k bytes enough? I assume so ;) (16th bit can't be used)
		short len = MIN_SIZE_IN_BYTES;
		
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
			Log.w (TAG, "Not enough space in tag for content");
			throw new OutOfSpaceException("Not enough space for BT data");
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
			data[++index] = addressBuffer[i];
		}
		
		// complete local name
		if (nameBytes != null) {
			data[++index] = (byte)(nameBytes.length + 0x01);
			data[++index] = BYTE_COMPLETE_LOCAL_NAME;
			for (int i = 0; i < nameBytes.length; ++i) {
				data[++index] = nameBytes[i];
			}
		}
		
		// manufacturer specific data
		if (manBytes != null) {
			data[++index] = (byte)(1 + manBytes.length);
			data[++index] = BYTE_MANUFACTURER_SPECIFIC_DATA;
			for (int i = 0; i < manBytes.length; ++i) {
				data[++index] = manBytes[i];
			}
		}
		
		// class of device
		if (classBytes != null) {
			data[++index] = (byte)(1 + classBytes.length);
			data[++index] = BYTE_CLASS_OF_DEVICE;
			for (int i = 0; i < classBytes.length; ++i) {
				data[++index] = classBytes[i];
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
    		addressBuffer[i] = (binaryData[7-i]) & 0xff;
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
    	
    	//Read the rest
    	for (int i = 8; i < binaryData.length; ++i) {
    		int dataLen = (binaryData[i]); //this includes type and data
    		int dataType = (binaryData[i+1]); //type bit
    		
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
    			Log.w(TAG, new StringBuilder().append(
    				"Unknown element: ").append(dataType).toString());
    		}
    		
    	}
    	
    	Log.d (TAG, "Parsed data: '" + data.getAddress() + "' '"
    		+ data.getName() + "'");
		
		return data;
	}
}