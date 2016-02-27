package vc.spider.consult;

import android.hardware.usb.UsbDevice;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

public class Facade extends Iconnection.Stub {
    private final String TAG=Facade.class.getCanonicalName();
    private final vc.spider.consult.service service;

    final RemoteCallbackList<IconnectionCallBack> mCallbacks = new RemoteCallbackList<IconnectionCallBack>();
    /**
     * Create an BTFacade.
     *
     * @param service the service providing the facade
     */
    public Facade(final vc.spider.consult.service aService) {
        service = aService;
    }

    @Override
    public boolean connect(UsbDevice mDevice) throws RemoteException {
        Log.d(TAG,"connect()");
        return service.connect(mDevice);
    }

    @Override
    public boolean disconnect() throws RemoteException {
        return service.disconnect();
    }

    @Override
    public int getState()
    {
        return service.getState();
    }

    @Override
    public boolean addReadParameter(byte param) throws RemoteException {
        return service.sendParameter(param);
    }

    @Override
    public void startStream() throws RemoteException {
        service.startStream();
    }

    @Override
    public void stopStream() throws RemoteException {
        service.stopStream();
    }

    @Override
    public void registerCallBack(IconnectionCallBack cb) throws RemoteException {
        if(cb!=null){
            Log.d(TAG, "registerCallBack registering");
            mCallbacks.register(cb);
        }
    }

    @Override
    public void removeCallBack(IconnectionCallBack cb) throws RemoteException {
        if(cb!=null){
            mCallbacks.unregister(cb);
        }
    }
}

