package vc.spider.consult;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class service extends Service {

    private final String TAG=service.class.getCanonicalName();
    private Facade FacadeIf;

    static final int STATE_IDLE = 0;
    static final int STATE_SELECT_ECU = 1;
    static final int STATE_GET_INFO = 2;
    static final int STATE_GET_STREM = 3;

    public static final byte TAHO_MSB = 0x00;
    public static final byte TAHO_LSB = 0x01;
    public static final byte SPEED = 0x0B;
    public static final byte INJ_MSB = 0x14;
    public static final byte INJ_LSB = 0x15;

    public static final byte CMD_GET_INFO = (byte)0xD0;
    public static final byte CMD_GET_VALUE = (byte)0x5A;
    public static final byte CMD_START_STREAM = (byte)0xF0;
    public static final byte CMD_STOP_STREAM = (byte)0x30;

    private PendingIntent mPermissionIntent = null;
    private UsbSerialPort sPort = null;
    private SerialInputOutputManager mSerialIoManager = null;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private ConnectThread mConnectThread = null;

    public service() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        registerReceiver(mReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        /*registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB_MR1) {
            registerReceiver(mReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
            registerReceiver(mReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
        }*/

        FacadeIf = new Facade(this);
        Log.d(TAG, "Created");
    }

    private void handleStart(Intent intent, int startId) {
        Log.d(TAG, "Start");
        if (intent == null) return;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        handleStart(intent, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleStart(intent, startId);
        return START_NOT_STICKY;
    }

    @Override
    public void onLowMemory() {
        Log.i(TAG, "onLowMemory()");
        return;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onUnbind (Intent intent) {
        Log.i(TAG, "onUnbind()");
        return true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG,"onBind() "+intent.getAction());
        /*if(Iconnection.class.getName().equals(intent.getAction())){
            return FacadeIf;
        }
        return null;*/
        return FacadeIf;
    }

    public boolean connect(UsbDevice mDevice) {
        if (mConnectThread!=null) {
            mConnectThread.interrupt();
            mConnectThread = null;
        }
        final UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (mDevice!= null)
            if (USBConnect(mUsbManager, mDevice))
                return true;
        else {
            final HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
            for (Map.Entry<String, UsbDevice> e : deviceList.entrySet()) {
                if (USBConnect(mUsbManager, e.getValue()))
                    return true;
            }
        }
        return false;
    }

    public boolean disconnect() {
        if (mConnectThread!=null) {
            if (mConnectThread.c_state==STATE_GET_STREM) {
                mConnectThread.stopStream();
            }
            mConnectThread.interrupt();
            mConnectThread = null;
            return true;
        }
        return false;
    }

    private boolean USBConnect(UsbManager mUsbManager, UsbDevice usbDevice)
    {
        final UsbSerialProber mSerialProber = UsbSerialProber.getDefaultProber();
        final UsbSerialDriver mSerialDriver =  mSerialProber.probeDevice(usbDevice);
        if (mSerialDriver == null) return false;

        if (!mUsbManager.hasPermission(usbDevice)) {
            mUsbManager.requestPermission(usbDevice, mPermissionIntent);
            return true;
        }

        Log.d(TAG, "mSerialDriver = " + mSerialDriver.getClass().toString());
        if (mSerialDriver != null) {
            final List<UsbSerialPort> mPorts = mSerialDriver.getPorts();
            //Log.d(TAG,"Port Count = "+mPorts.size());
            sPort = mPorts.get(0);
            if (sPort!=null) {
                try {
                    final SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    final int mBaudRate = Integer.parseInt(mSettings.getString("usb_baud", "9600"));
                    sPort.open(mUsbManager.openDevice(usbDevice));
                    //Log.d(TAG,"Setting 8N1 baud rate: "+mBaudRate);
                    sPort.setParameters(mBaudRate,UsbSerialPort.DATABITS_8,UsbSerialPort.STOPBITS_1,UsbSerialPort.PARITY_NONE);
                } catch (IOException e) {
                    Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                    if (sPort!=null)
                        try {
                            sPort.close();
                        } catch (Exception e2) {
                            Log.e(TAG, "No device to close: " + e.getMessage(), e);
                        }
                }
                //Log.i(TAG, "Starting io manager ..");
                mConnectThread=new ConnectThread(this,sPort);
                mConnectThread.start();
                return true;
            }
        }
        return false;
    }

    private final BTBroadcastReceiver mReceiver = new BTBroadcastReceiver();

    public int getState() {
        if (mConnectThread == null) return -1;
        return (mConnectThread.c_state);
    }

    public void startStream() {
        if (mConnectThread != null) mConnectThread.starStream();
    }

    public boolean sendParameter(byte param) throws RemoteException {
        if (mConnectThread != null) {
            if (mConnectThread.sendCMD((byte) CMD_GET_VALUE, param)) {
                if (!mConnectThread.cmdAccepted())
                    throw new RemoteException();
                else
                    return true;
            }
        }
        return false;
    }

    public void stopStream() {
        if (mConnectThread != null)
            if (mConnectThread.c_state==STATE_GET_STREM) {
                mConnectThread.stopStream();
            }
    }

    private class BTBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive(" + action + ")");
            if (ACTION_USB_PERMISSION.equals(action)) synchronized (this) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    //if (mState == STATE_CONNECTING && !mDevice_mode.equals("bt")) {
                    final UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbDevice != null) {
                        final UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                        USBConnect(mUsbManager, usbDevice);
                    }
                    //}
                } else {
                    //if (mState == STATE_CONNECTING) connectionFailed();
                }
            }
        }
    }

        private class ConnectThread extends Thread {
            private UsbSerialPort sPort;
            private SerialInputOutputManager mSerialIoManager;
            private vc.spider.consult.service service;
            private final Semaphore waitCmd = new Semaphore(1);
            private int frame_num = 0;
            private int frame_len = 0;
            private boolean cmd_accepted = false;

            private int c_state = STATE_SELECT_ECU;
            private byte last_cmd = (byte)0xFF;
            long retry_timeout = 0;

            private final SerialInputOutputManager.Listener mListener = new usbListener(service);

            synchronized public boolean cmdAccepted() {
                try {
                    final boolean flag = waitCmd.tryAcquire(5, TimeUnit.SECONDS);
                    Log.d(TAG, "Accepted()" + flag);
                    //waitCmd.acquire();
                    //Log.d(TAG, "Accepted");
                    if (flag) waitCmd.release();
                    return flag;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.d(TAG, "InterruptedException");
                }
                return false;
            }

            private class usbListener implements SerialInputOutputManager.Listener {
                private final vc.spider.consult.service service;

                public usbListener(vc.spider.consult.service service) {
                    this.service = service;
                }

                @Override
                public void onRunError(Exception e) {
                    if (mConnectThread != null) {
                        mConnectThread.interrupt();
                        mConnectThread = null;
                    }
                    Log.d(TAG, "Runner stopped.");
                    service.stopSelf();
                }

                @Override
                public void onNewData(final byte[] data) {
                    //Log.i(TAG, "Read " + data.length + " bytes: \n" + HexDump.dumpHexString(data) + "\n\n");
                    for (int i = 0; i < data.length; i++) {
                        byte c = data[i];
                    }

                    //if (mParser != null) mParser.write(data,data.length);
            /*DemoActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DemoActivity.this.updateReceivedData(data);
                }
            });*/
                }
            }

            ;

            public ConnectThread(vc.spider.consult.service service, UsbSerialPort sPort) {
                this.service = service;
                this.sPort = sPort;
                /*this.mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
                final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
                mExecutor.submit(mSerialIoManager);*/
            }

            private void write(final byte[] data) throws IOException {
                /*String Line = String.format("Write %d:",data.length);
                for (int i=0;i<data.length;i++) {
                    Line += String.format(" 0x%02x", data[i]);
                }
                Log.d(TAG,Line);*/
                sPort.write(data, 200);
            }

            public boolean sendCMD(byte cmd) {
                final byte[] data = {cmd};
                try {
                    frame_num=0;
                    frame_len=0;
                    cmd_accepted = false;
                    if (cmd==CMD_START_STREAM) {
                        c_state=STATE_GET_STREM;
                    } else
                    if (cmd!=CMD_STOP_STREAM)
                        last_cmd = cmd;
                    write(data);
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }

            public boolean stopStream() {
                Log.d(TAG, "stopStream()");
                return sendCMD(CMD_STOP_STREAM);
            }

            public boolean starStream() {
                Log.d(TAG, "starStream()");
                return sendCMD(CMD_START_STREAM);
            }

            synchronized public boolean sendCMD(byte cmd, byte param) {
                final byte[] data = {cmd, param};
                try {
                    if (waitCmd.availablePermits()>0) {
                        waitCmd.acquire();
                        Log.d(TAG, "Sending...");
                        frame_num = 0;
                        frame_len = 1;
                        cmd_accepted = false;
                        write(data);
                        last_cmd = cmd;
                        return true;
                    } else {
                        Log.d(TAG, "SendCMD() " + cmd + " false " + currentThread());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return false;
            }

            private void notifyFrame(byte[] frame_data, int len)
            {
                /*String Line = String.format("%d:",len);
                for (int i=0;i<len;i++) {
                    Line += String.format(" 0x%02x", frame_data[i]);
                }
                Log.d(TAG,Line);*/

                synchronized (service) {
                    if ((service != null) && (service.FacadeIf != null) && (service.FacadeIf.mCallbacks != null))
                        try {
                            // this is very important - if u miss it u ll end in exception
                            int N = service.FacadeIf.mCallbacks.beginBroadcast();
                            // now for time being we will consider only one activity is bound to the service, so hardcode 0
                            for (int n = 0; n < N; n++) {
                                /*byte[] new_frame = new byte[len];
                                System.arraycopy(frame_data, 0, new_frame , 0, len);*/
                                service.FacadeIf.mCallbacks.getBroadcastItem(n).StreamReceived(frame_data, len);
                            }
                            service.FacadeIf.mCallbacks.finishBroadcast();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                }
            }

            private void checkFrameData(final byte[] frame_data, final int len) {
                //Log.d(TAG,"checkFrameData("+last_cmd+")");
                switch (last_cmd) {
                    case CMD_GET_INFO:
                        last_cmd = CMD_STOP_STREAM;
                        if (stopStream()) {
                            c_state = STATE_IDLE;
                            retry_timeout = System.currentTimeMillis() + (10 * 60 * 1000);
                            notifyFrame(frame_data,len);
                        }
                        break;
                    case CMD_GET_VALUE:
                    default:
                        notifyFrame(frame_data,len);
                        break;
                }
            }

            public void run() {
                //final ByteBuffer mFrameBuffer = ByteBuffer.allocateDirect(512);
                //final ByteBuffer mReadBuffer = ByteBuffer.allocateDirect(256);
                setName("ConnectThread");
                Log.i(TAG, "ConnectThread");
                boolean inStream = false;
                final byte[] tmpRead = new byte[32];
                final byte[] frameRead = new byte[32];
                int frameLen = 0;

                while (!isInterrupted()) {
                    int len = 0;
                    try {
                        len = sPort.read(tmpRead, 1000);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (len > 0) {
                        //mReadBuffer.position(0);
                        //mReadBuffer.put(tmpRead);
/*                        String Line = String.format("Read %d:",len);
                        for (int i=0;i<len;i++) {
                            Line += String.format(" 0x%02x", tmpRead[i]);
                        }
                        Log.d(TAG,Line);*/
                        //mReadBuffer.position(0);
                        for (int i = 0; i < len; i++) {
                            final byte c = tmpRead[i];
                            if ((c == (byte) 0xFF) && (frame_num==0)) {
                                //Log.d(TAG, "Stream Data");
                                inStream = true;
                                frame_num=1;
                            } else
                            if (inStream) {
                                if (frame_num==1) {
                                    //Log.d(TAG, "Stream Len: "+c);
                                    frame_len = c;
                                    frame_num++;
                                    frameLen = 0;
                                } else {
                                    //mFrameBuffer.put(c);
                                    frameRead[frameLen++]=c;
                                    if (/*mFrameBuffer.position()*/frameLen >= frame_len) {
                                        //Log.d(TAG, "Stream Frame: "+frame_len);
                                        inStream = false;
                                        frame_num = 0;
                                        retry_timeout = System.currentTimeMillis() + 10000;
                                                /*final byte[] buffer = new byte[mFrameBuffer.position()];
                                                mFrameBuffer.position(0);
                                                mFrameBuffer.get(buffer);*/
                                        //mFrameBuffer.get()
                                        checkFrameData(/*mFrameBuffer.array(), mFrameBuffer.position()*/frameRead, frameLen);
                                        //mFrameBuffer.clear();
                                    }
                                }
                            } else
                            switch (c_state) {
                                case STATE_SELECT_ECU:
                                    if (((frame_num++)<2) && ((c != (byte)0x00) && (c != (byte) 0x10))) {
                                        c_state = STATE_IDLE;
                                        //waitCmd.release();
                                    } else
                                    if (c == (byte) 0x10) {
                                        frame_num = 0;
                                        Log.i(TAG, "ECU Selected");
                                        c_state = STATE_GET_INFO;
                                        synchronized (service) {
                                            if ((service != null) && (service.FacadeIf != null) && (service.FacadeIf.mCallbacks != null))
                                                try {
                                                    // this is very important - if u miss it u ll end in exception
                                                    int N = service.FacadeIf.mCallbacks.beginBroadcast();
                                                    // now for time being we will consider only one activity is bound to the service, so hardcode 0
                                                    for (int n = 0; n < N; n++)
                                                        service.FacadeIf.mCallbacks.getBroadcastItem(n).CUConnected(c);
                                                    service.FacadeIf.mCallbacks.finishBroadcast();
                                                } catch (RemoteException e) {
                                                    e.printStackTrace();
                                                }
                                        }
                                        //waitCmd.release();
                                    }
                                    break;
                                case STATE_GET_INFO:
                                    if (c == (byte) 0x2F) {
                                        Log.i(TAG, "ECU Info Accepted");
                                        //waitCmd.release();
                                        starStream();
                                    }
                                    break;
                                case STATE_GET_STREM:
                                    switch (frame_num) {
                                        case 0:
                                            if (c == (byte) 0xFF) frame_num++; else
                                            if (c == (byte)((byte)0xFF-CMD_STOP_STREAM)) {
                                                c_state = STATE_IDLE;
                                            }
                                            break;
                                        case 1:
                                            frame_len = c;
                                            frame_num++;
                                            frameLen = 0;
                                            break;
                                        default:
                                            //mFrameBuffer.put(c);
                                            frameRead[frameLen++]=c;
                                            if (/*mFrameBuffer.position()*/frameLen >= frame_len) {
                                                retry_timeout = System.currentTimeMillis() + 10000;
                                                /*final byte[] buffer = new byte[mFrameBuffer.position()];
                                                mFrameBuffer.position(0);
                                                mFrameBuffer.get(buffer);*/
                                                checkFrameData(/*mFrameBuffer.array(), mFrameBuffer.position()*/frameRead, frameLen);
                                                //mFrameBuffer.clear();
                                                frame_num = 0;
                                            }
                                            break;
                                    }
                                    break;
                                default:
                                    //Log.d(TAG, String.format("Resp: 0x%02x 0x%02x %d %d",c,(byte)((byte)0xFF-last_cmd), frame_num, frame_len));
                                    if ((c==(byte)((byte)0xFF-last_cmd)) && (frame_num==0)) {
                                        cmd_accepted = true;
                                    }
                                    if (cmd_accepted && (frame_num==frame_len)) {
                                        cmd_accepted = false;
                                        frame_len = 0;
                                        frame_num = 0;
                                        //Log.d(TAG, "release(1) " + waitCmd.availablePermits() + " " + currentThread());
                                        waitCmd.release();
                                    } else
                                        frame_num++;
                                    break;
                            }
                        }
                        //mReadBuffer.clear();
                    }
                    switch (c_state) {
                        case STATE_IDLE:
                            if (retry_timeout > System.currentTimeMillis()) break;
                            Log.d(TAG, "Idle...");
                            c_state = STATE_GET_INFO;
                            break;
                        case STATE_SELECT_ECU:
                            if (retry_timeout > System.currentTimeMillis()) break;
                            //this.mSerialIoManager.writeAsync(data);
                            Log.d(TAG, "connecting to ECU...");
                            frame_num = 0;
                            try {
                                final byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xEF};
                                write(data);
                                retry_timeout = System.currentTimeMillis() + 3000;
                            } catch (IOException e) {
                                e.printStackTrace();
                                Thread.currentThread().interrupt();
                            }
                            break;
                        case STATE_GET_INFO:
                            if (retry_timeout > System.currentTimeMillis()) break;
                            //this.mSerialIoManager.writeAsync(data);
                            Log.d(TAG, "Get Info...");
                            if (sendCMD(CMD_GET_INFO))
                                retry_timeout = System.currentTimeMillis() + 3000;
                            break;
                        case STATE_GET_STREM:
                            //if (retry_timeout > System.currentTimeMillis()) break;
                            //this.mSerialIoManager.writeAsync(data);
                            //starStream();
                            break;
                    }
                }
                if (c_state==STATE_GET_STREM) stopStream();

                    /*try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }*/
                Log.d(TAG, "Connection Thread Stoped");

                synchronized (service) {
                    if ((service != null) && (service.FacadeIf != null) && (service.FacadeIf.mCallbacks != null))
                        try {
                            // this is very important - if u miss it u ll end in exception
                            int N = service.FacadeIf.mCallbacks.beginBroadcast();
                            // now for time being we will consider only one activity is bound to the service, so hardcode 0
                            for (int n = 0; n < N; n++) {
                                /*byte[] new_frame = new byte[len];
                                System.arraycopy(frame_data, 0, new_frame , 0, len);*/
                                service.FacadeIf.mCallbacks.getBroadcastItem(n).Disconnected();
                            }
                            service.FacadeIf.mCallbacks.finishBroadcast();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                }
            }
        }
    }