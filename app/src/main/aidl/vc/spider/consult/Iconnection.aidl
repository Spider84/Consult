package vc.spider.consult;

import vc.spider.consult.IconnectionCallBack;
import android.hardware.usb.UsbDevice;

interface Iconnection {

	/**
     * Connect to device
     */
	boolean connect(in UsbDevice mDevice);
	boolean disconnect();

	int getState();

	boolean addReadParameter(byte param);
	void startStream();
	void stopStream();

	void registerCallBack(IconnectionCallBack cb);
	void removeCallBack(IconnectionCallBack cb);
}
