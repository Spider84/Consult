package vc.spider.consult;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class connecting extends SherlockActivity {
    //boolean isDebuggable =  ( 0 != ( getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE ) );
    static private final String TAG=connecting.class.getCanonicalName();

    private Intent SERVICE_INTENT = null;
    private Iconnection ConnectionIf = null;
    private boolean mBind = false;
    private UsbDevice mDevice = null;
    private PendingIntent mDisconnectIntent = null;

    private final USBBroadcastReceiver mReceiver = new USBBroadcastReceiver();

    private class USBBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive(" + action + ")");
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) synchronized (this) {
                final UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if ((usbDevice!=null) && (mDevice!=null) && (usbDevice.getDeviceName().compareTo(mDevice.getDeviceName())==0))
                    if (ConnectionIf != null)
                        try {
                            ConnectionIf.disconnect();
                        }catch (Exception e) {

                        }
                /*if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    //if (mState == STATE_CONNECTING && !mDevice_mode.equals("bt")) {
                    final UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbDevice != null) {
                        final UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                        USBConnect(mUsbManager, usbDevice);
                    }
                    //}
                } else {
                    //if (mState == STATE_CONNECTING) connectionFailed();
                }*/
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDisconnectIntent = PendingIntent.getBroadcast(this, 0, new Intent(UsbManager.ACTION_USB_DEVICE_DETACHED), 0);
        registerReceiver(mReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
        setContentView(R.layout.activity_connecting);
    }

    private IconnectionCallBack mCallback = new IconnectionCallBack.Stub() {
        @Override
        public void CUConnected(byte CUcode) throws RemoteException {
            Log.d(TAG, "Callback from Service");
            //someMethodInActivity();
        }

        @Override
        public void StreamReceived(byte[] data, int len) throws RemoteException {
            Log.d(TAG, "Stream From Service");
            connectionComplite();
        }

        @Override
        public void Disconnected() throws RemoteException {
            Log.d(TAG, "Adapter Disconnected");
            onBackPressed();
        }
    };

    void connectionComplite()
    {
        closeService();
        startActivity(new Intent(this, Trip.class));
        finish();
    }

    private ServiceConnection ServiceConn = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG,"onServiceConnected()");

            ConnectionIf = Iconnection.Stub.asInterface(service);
            try {
                ConnectionIf.registerCallBack(mCallback);
                ConnectionIf.connect(mDevice);
                //BTServiceIf.RemoveNotifications();
                //BTServiceIf.DisableWarnings();
            }
            catch(RemoteException e){
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG,"onServiceDisconnected()");
            ConnectionIf = null;
        }
    };

    @Override
    public void onBackPressed()
    {
        if (ConnectionIf != null)
            try {
                    ConnectionIf.disconnect();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        if (SERVICE_INTENT==null) SERVICE_INTENT = new Intent(this, service.class);
        stopService(SERVICE_INTENT);
        closeService();
        finish();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        //checkForCrashes();
        //checkForUpdates();

        Log.i(TAG, "onResume() " + ConnectionIf);

        Intent intent = getIntent();
        Log.d(TAG, "intent: " + intent);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            mDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
           /*if(mDevice!= null)
                        mUsbManager.requestPermission(mDevice, mPermissionIntent);*/
        }

        if ((!mBind) || (ConnectionIf == null))
            try
            {
                Log.d(TAG, "SERVICE_INTENT = " + SERVICE_INTENT);
                if (SERVICE_INTENT==null) {
                    //SERVICE_INTENT = new Intent(service.class.getName());
                    SERVICE_INTENT = new Intent(this, service.class);
                }
                startService(SERVICE_INTENT);
                mBind = bindService(SERVICE_INTENT,ServiceConn,BIND_AUTO_CREATE);
                Log.d(TAG, "bindService = "+mBind);
            } catch (Exception e) {
                Log.e(TAG,"bindService = ",e);
            }

        Log.d(TAG,"ConnectionIf = "+ConnectionIf);
        if (ConnectionIf!=null) {
            try {
                ConnectionIf.registerCallBack(mCallback);
                ConnectionIf.connect(mDevice);
                //BTServiceIf.RemoveNotifications();
                //BTServiceIf.DisableWarnings();
            }
            catch(RemoteException e){
            }
        }
            /*if (keepScreenOn)
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);*/
    }

    private void closeService()
    {
        if (ConnectionIf != null)
        try {
            ConnectionIf.removeCallBack(mCallback);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (mBind) {
            unbindService(ServiceConn);
            ConnectionIf=null;
            mBind=false;
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy()");
        unregisterReceiver(mReceiver);
        closeService();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getSupportMenuInflater().inflate(R.menu.menu_connecting, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
