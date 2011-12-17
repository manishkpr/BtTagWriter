/**
 * TagWriter.java (bttagwriter)
 *
 * Copyright 2011 Sami Viitanen <sami.viitanen@gmail.com>
 * All rights reserved.
 */

package fi.siika.bttagwriter;

import java.io.IOException;
import java.util.Arrays;

import android.nfc.FormatException;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.TagTechnology;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * TagWriter provides thread and code that will take care of the tag write
 * process.
 * @author Sami Viitanen <sami.viitanen@gmail.com>
 */
public class TagWriter extends Thread {
	
	/**
	 * Class containing information written to NFC tags
	 */
	public static class TagInformation implements Cloneable {
		/**
		 * Bluetooth address of device ("00:00:00:00:00:00" format)
		 */
		public String address;
		/**
		 * Bluetooth name of device
		 */
		public String name;
		
		@Override
		public Object clone () {
			try {
				return super.clone();
			} catch (CloneNotSupportedException e) {
				return null;
			}
		}
	}
	
	private Handler mHandler = null;
	private TagInformation mInfo = null;
	private Tag mTag = null;
	private String mTagClass;
	private boolean mCancelled = false;
	
	public final static int HANDLER_MSG_SUCCESS = 0;
	public final static int HANDLER_MSG_CANCELLED = 1;
	public final static int HANDLER_MSG_CONNECTION_LOST = -1;
	public final static int HANDLER_MSG_FAILED_TO_FORMAT = -2;
	public final static int HANDLER_MSG_TOO_SMALL = -3;
	public final static int HANDLER_MSG_TAG_NOT_ACCEPTED = -4;
	
	private final static int START_CC_MIFARE_UL_PAGE = 3;
	private final static int START_NDEF_MIFARE_UL_PAGE = 4;
	private final static byte CC_NDEF_BYTE = (byte)0xE1;
	private final static byte CC_NDEF_VERSION_1_1_BYTE = (byte)0x11;
	
	private final static byte CC_NO_SECURITY_BYTE = (byte)0x00;
	
	/**
	 * Construct new TagWriter
	 * @param handler Handler used to send messages
	 */
	public TagWriter (Handler handler) {
		mHandler = handler;
	}
	
	/**
	 * Start write process to given tag. Will call run if initialization was
	 * success.
	 * @param tag Tag now connected with device
	 * @param information Information written to tag
	 * @return true if write process and thread was started
	 */
	public boolean writeToTag (Tag tag, TagInformation information) {
		
		MifareUltralight mul = MifareUltralight.get(tag);
		if (mul != null) {
			mTag = tag;
			mTagClass = MifareUltralight.class.getName();
		} else {
			mTag = null;
		}
		
		if (mTag != null) {
			mInfo = (TagInformation)(information.clone());
			mCancelled = false;
			run();
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Implementation of Thread run.
	 */
	@Override
	public void run() {
		int message = HANDLER_MSG_SUCCESS;
		
		try {
			if (mTagClass.equals(MifareUltralight.class.getName())) {
				MifareUltralight mul = MifareUltralight.get(mTag);
				writeToMifareUltraLight(mul);
			} else if (mTagClass.equals(NdefFormatable.class.getName())) {
				NdefFormatable.get(mTag).format (
					BtTagGenerator.generateNdefMessageForBtTag(mInfo.name,
					mInfo.address, (short)128));
			} else {
				message = HANDLER_MSG_TAG_NOT_ACCEPTED;
			}
		} catch (IOException e) {
			if (mCancelled) {
				message = HANDLER_MSG_CANCELLED;
			} else {
				Log.w(getClass().getName(), e.getMessage());
				message = HANDLER_MSG_CONNECTION_LOST;
			}
		} catch (FormatException e) {
			Log.e(getClass().getName(),"Failed to format");
			message = HANDLER_MSG_FAILED_TO_FORMAT;
		} catch (Exception e) {
			Log.w(getClass().getName(), e.getMessage());
		}
		
		if (mHandler != null) {
			mHandler.sendMessage(mHandler.obtainMessage(message));
		}
	}
	
	private void writeToMifareUltraLight(MifareUltralight tag)
		throws IOException {
		
		tag.connect();
		
		int ndefSizeLimitPages = 36;
		if (tag.getType() == MifareUltralight.TYPE_ULTRALIGHT) {
			Log.d (getClass().getSimpleName(), "Assume UL size 12 pages");
			ndefSizeLimitPages = 12;
		} else {
			Log.d (getClass().getSimpleName(), "Assume UL size 36 pages");
		}
		
		byte[] ndefMessage = BtTagGenerator.generateNdefMessageForBtTag(
			mInfo.name, mInfo.address,
			(short)(ndefSizeLimitPages*MifareUltralight.PAGE_SIZE)
			).toByteArray();
		
		byte[] data = new byte[ndefMessage.length + 2];
		data[0] = 0x03;
		data[1] = (byte)(ndefMessage.length);
		for (int i = 0; i < ndefMessage.length; ++i) {
			data[2+i] = ndefMessage[i];
		}
		
		int pages = data.length / MifareUltralight.PAGE_SIZE;
		if (data.length % MifareUltralight.PAGE_SIZE != 0) {
			pages += 1;
		}
		
		if (ndefSizeLimitPages < pages) {
			throw new IOException("Too small tag");
		}
		
		for (int i = 0; i < pages; ++i) {
			int index = 4*i;
			//This will auto fill with 0x00s if we index out
			byte[] page = Arrays.copyOfRange(data, index, index + 4);
			StringBuilder sb = new StringBuilder();
			//sb.append("Write page ").append(START_NDEF_MIFARE_UL_PAGE + i);
			//Log.d(getClass().getSimpleName(),sb.toString());
			tag.writePage (START_NDEF_MIFARE_UL_PAGE + i, page);
		}
		
		//And finally write header
		tag.writePage(START_CC_MIFARE_UL_PAGE, new byte[] {
			CC_NDEF_BYTE, CC_NDEF_VERSION_1_1_BYTE,
			(byte)(ndefSizeLimitPages * MifareUltralight.PAGE_SIZE / 8),
			CC_NO_SECURITY_BYTE});
		
		tag.close();
		
		Log.d(getClass().getName(),"Mifare Ultralight written");
	}
	
	/**
	 * Cancel current write process if started
	 */
	public void cancel() {
		if (mTag != null) {
			try {
				if (mTagClass.equals(MifareUltralight.class.getName())) {
					MifareUltralight.get(mTag).close();
				} else if (mTagClass.equals(NdefFormatable.class.getName())) {
					NdefFormatable.get(mTag).close();
				}
			} catch (IOException e) {
			}
		}
	}
}
