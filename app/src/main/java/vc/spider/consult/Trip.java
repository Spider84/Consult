package vc.spider.consult;

import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import java.util.Timer;
import java.util.TimerTask;

public class Trip extends SherlockActivity {
    static private final String TAG=Trip.class.getCanonicalName();

    private Intent SERVICE_INTENT = null;
    private Iconnection ConnectionIf = null;
    private boolean mBind = false;

    private TextView tvCurrentTrip = null;
    private TextView tvCurrentHourTrip = null;
    private TripBar pbCurrentTrip = null;
    private TripBar pbCurrentHourTrip = null;
    private TextView tvDistance = null;
    private TextView tvDistanceMeasure = null;
    private TextView tvMeanTrip = null;
    private TextView tvTotalTrip = null;
    private TextView tvSpeed = null;
    private ViewGroup llLiterPerHour = null;
    private ViewGroup llLiterPer100 = null;
    private TextView tvStopTime = null;
    private ViewSwitcher vsGauge = null;
    private ListView lvDebug = null;

    private Timer mTimer = null;

    private IconnectionCallBack mCallback = new IconnectionCallBack.Stub() {
        @Override
        public void Disconnected() throws RemoteException {
            Log.d(TAG, "Adapter Disconnected");

        }

        @Override
        public void CUConnected(byte CUcode) throws RemoteException {
            Log.d(TAG, "Callback from Service");
            //someMethodInActivity();
        }

        @Override
        public void StreamReceived(final byte[] data, final int len) throws RemoteException {
            //Log.i(TAG, "Read " + len + " bytes: \n" + HexDump.dumpHexString(data) + "\n\n");
            /*runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String Line = String.format("%d:",len);
                    ArrayAdapter<String> adapter = (ArrayAdapter<String>)lvDebug.getAdapter();
                    for (int i=0;i<len;i++) {
                        Line += String.format(" 0x%02x",data[i]);
                    }
                    adapter.add(Line);
                    //adapter.notifyDataSetChanged();
                }
            });*/
            if (len==5) {
                //((ArrayAdapter<String>)lvDebug.getAdapter()).add(String.format("%d: 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x",len,data[0],data[1],data[2],data[3],data[4]));

                final int taho = (int)((((data[0]&0xFF)<<8) | (data[1]&0xFF))*12.5f);
                final int speed = data[2] * 2;
                final float inj = ((((data[3]&0xFF)<<8) | (data[4]&0xFF))/100f);
                //Log.i(TAG, "S:" + speed + " T:" + taho + " I:"+inj+"(" +(((data[3]&0xFF)<<8) | (data[4]&0xFF))+ ") C:"+(tickCount-prevTickCount)+" D:"+distance);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /*ArrayAdapter<String> adapter = (ArrayAdapter<String>)lvDebug.getAdapter();
                        adapter.add(String.format("%d: 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x", len, data[0], data[1], data[2], data[3], data[4]));
                        adapter.notifyDataSetChanged();*/
                        updateCounter(taho, speed, inj);
                    }
                });
            }
        }
    };

    private long prevTickCount=0;
    private float distance = 0;
    private float totaltrip = 0;
    private int lastProgress = 0;
    private int lastMeanProgress = 0;
    private long lastSpeedTime = 0;
    private int lastLperHour;

    private void updateCounter(int taho, int speed, float inj) {
        final long tickCount = System.currentTimeMillis();
        //final float l_p_ms = ((((taho/2f)*370)/60f/1000f)*6*inj)/1000;
        final float l_p_hour = ((taho/2f)*6*370*((inj/1000/60)/1000))*60;
        final float l_p_ms = l_p_hour/60f/60f/1000f;

        if (prevTickCount != 0) {
            totaltrip += l_p_ms * (tickCount - prevTickCount);
        }

        if (speed>5) {
            final float l_p_100 = ((100f / speed) * l_p_hour);
            if (prevTickCount != 0) {
                distance += ((speed * 1000) / 3600000f) * (tickCount - prevTickCount);
            }

            if (/*((tickCount-Math.abs(lastSpeedTime))/1000>60) && */(vsGauge.getDisplayedChild()!=1)) {
                vsGauge.setDisplayedChild(1);
            }
            lastSpeedTime = tickCount;

            final int new_progress = (int) (l_p_100 * 10);
            if (lastProgress!=new_progress) {
                //Log.i(TAG,"Update Progress "+lastProgress+" -> "+new_progress);
                ObjectAnimator animation = ObjectAnimator.ofInt(pbCurrentTrip, "progress", new_progress);
                animation.setDuration(300); // 0.5 second
                animation.setInterpolator(new LinearInterpolator());
                animation.start();
                lastProgress = new_progress;

                tvCurrentTrip.setText(String.format("%.1f", l_p_100));
            }

            if (distance<1000) {
                tvDistance.setText(String.format("%.2f", distance));
                tvDistanceMeasure.setText(R.string.meters);
            } else {
                tvDistanceMeasure.setText(R.string.km);
                if (distance < 10000)
                    tvDistance.setText(String.format("%.3f", distance / 1000f));
                else
                    tvDistance.setText(String.format("%.1f", distance / 1000f));
            }
            tvSpeed.setText(Integer.toString(speed));
        } else
        if ((lastSpeedTime<=0) || ((tickCount-Math.abs(lastSpeedTime))/1000>=30)) {
            if (vsGauge.getDisplayedChild()!=0) {
                vsGauge.setDisplayedChild(0);
            }
            if (lastSpeedTime==0) lastSpeedTime=-1*tickCount;
            int stopTime = (int)((tickCount-Math.abs(lastSpeedTime))/1000);
            tvStopTime.setText(String.format("%02d:%02d",stopTime/60,stopTime%60));

            final int new_progress = (int)l_p_hour*10;
            if (lastLperHour!=new_progress) {
                //Log.i(TAG,"Update Progress "+lastProgress+" -> "+new_progress);
                ObjectAnimator animation = ObjectAnimator.ofInt(pbCurrentHourTrip, "progress", new_progress);
                animation.setDuration(300); // 0.5 second
                animation.setInterpolator(new LinearInterpolator());
                animation.start();
                lastLperHour = new_progress;

                tvCurrentHourTrip.setText(String.format("%.1f", l_p_hour));
            }
        }
        prevTickCount = tickCount;

        if (distance>0) {
            final int MeanProgress = (int) ((totaltrip / distance) * 1000000);
            if (lastMeanProgress != MeanProgress) {
                ObjectAnimator animation = ObjectAnimator.ofInt(pbCurrentTrip, "secondaryProgress", MeanProgress);
                animation.setDuration(300); // 0.5 second
                animation.setInterpolator(new LinearInterpolator());
                animation.start();
                lastMeanProgress = MeanProgress;

                tvMeanTrip.setText(String.format("%.1f", (totaltrip / distance) * 100000));
            }
        }

        if (totaltrip<1)
            tvTotalTrip.setText(String.format("%.3f", totaltrip));
        else
            tvTotalTrip.setText(String.format("%.1f", totaltrip));
    }

    private ServiceConnection ServiceConn = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected()");

            ConnectionIf = Iconnection.Stub.asInterface(service);
            try {
                ConnectionIf.registerCallBack(mCallback);
                //BTServiceIf.RemoveNotifications();
                //BTServiceIf.DisableWarnings();
            }
            catch(RemoteException e){
            }
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startTrip();
                        }
                    });
                }
            }, 2000);

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected()");
            ConnectionIf = null;
        }
    };

    private void startTrip() {
        int state = -1;
        try {
            state = ConnectionIf.getState();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (state == service.STATE_IDLE) {
            try {
                ConnectionIf.addReadParameter(service.TAHO_MSB);
                ConnectionIf.addReadParameter(service.TAHO_LSB);
                ConnectionIf.addReadParameter(service.SPEED);
                ConnectionIf.addReadParameter(service.INJ_MSB);
                ConnectionIf.addReadParameter(service.INJ_LSB);
                ConnectionIf.startStream();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        Log.i(TAG, "onResume() " + ConnectionIf);

        if ((!mBind) || (ConnectionIf == null))
            try
            {
                Log.d(TAG,"SERVICE_INTENT = "+SERVICE_INTENT);
                if (SERVICE_INTENT==null) {
                    //SERVICE_INTENT = new Intent(service.class.getName());
                    SERVICE_INTENT = new Intent(this, service.class);
                }
                startService(SERVICE_INTENT);
                mBind = bindService(SERVICE_INTENT,ServiceConn,BIND_AUTO_CREATE);
                Log.d(TAG,"bindService = "+mBind);
            } catch (Exception e) {
                Log.e(TAG,"bindService = ",e);
            }

        if (ConnectionIf!=null) {
            startTrip();
        }

        if (mTimer==null) {
            mTimer = new Timer();
            mTimer.schedule(new MyTimerTask(), 1000, 1000);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            /*if (keepScreenOn)
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);*/
    }

    class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final int max = pbCurrentTrip.getMax();
                    final int progress = pbCurrentTrip.getProgress();
                    if ((max>300) && ((max/2)>progress)) {
                        //Log.i(TAG,"Timer: "+max+" "+progress);
                        //pbCurrentTrip.setMax(Math.max(300, max-10));
                        ObjectAnimator animation = ObjectAnimator.ofInt(pbCurrentTrip, "max", Math.max(300, max/2));
                        animation.setDuration(1000); // 0.5 second
                        animation.setInterpolator(new LinearInterpolator());
                        animation.start();
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
        try {
           ConnectionIf.stopStream();
           ConnectionIf.disconnect();
        } catch (RemoteException e) {
           e.printStackTrace();
        }
        if (mBind) {
            if (SERVICE_INTENT==null) SERVICE_INTENT = new Intent(this, service.class);
            stopService(SERVICE_INTENT);
            unbindService(ServiceConn);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip);

        vsGauge = (ViewSwitcher) findViewById(R.id.vsGauge);
        vsGauge.setDisplayedChild(0);

        llLiterPerHour = (ViewGroup) findViewById(R.id.llLeterPerHour);
        //llLiterPerHour.setVisibility(View.GONE);
        llLiterPer100 = (ViewGroup) findViewById(R.id.llLeterPer100);
        //llLiterPerHour.setVisibility(View.VISIBLE);

        lvDebug = (ListView) findViewById(R.id.lvDebug);
        /*ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,R.layout.listitem);
        lvDebug.setAdapter(adapter);*/
        lvDebug.setVisibility(View.GONE);
        lvDebug = null;

        tvCurrentTrip = (TextView) findViewById(R.id.tvCurrentTrip);
        if (tvCurrentTrip!=null) {
            tvCurrentTrip.setText("--,-");
        }
        tvCurrentHourTrip = (TextView) findViewById(R.id.tvCurrentHourTrip);
        if (tvCurrentHourTrip!=null) {
            tvCurrentHourTrip.setText("--,-");
        }
        pbCurrentTrip = (TripBar) findViewById(R.id.pbCurrentTrip);
        if (pbCurrentTrip!=null) {
            pbCurrentTrip.setProgress(0);
            pbCurrentTrip.setSecondaryProgress(0);
        }
        pbCurrentHourTrip = (TripBar) findViewById(R.id.pbCurrentHourTrip);
        if (pbCurrentHourTrip!=null) {
            pbCurrentHourTrip.setProgress(0);
            pbCurrentHourTrip.setSecondaryProgress(0);
        }
        tvDistance = (TextView) findViewById(R.id.tvDistance);
        if (tvDistance!=null) {
            tvDistance.setText("--,-");
        }

        tvDistanceMeasure = (TextView) findViewById(R.id.tvDistanceMeasure);
        if (tvDistanceMeasure!=null) {
            tvDistanceMeasure.setText(R.string.meters);
        }

        tvMeanTrip = (TextView) findViewById(R.id.tvMeanTrip);
        if (tvMeanTrip!=null) {
            tvMeanTrip.setText("--,-");
        }
        tvTotalTrip = (TextView) findViewById(R.id.tvTotalTrip);
        if (tvTotalTrip!=null) {
            tvTotalTrip.setText("00,0");
        }
        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvStopTime = (TextView) findViewById(R.id.tvStopTime);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getSupportMenuInflater().inflate(R.menu.menu_trip, menu);
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
