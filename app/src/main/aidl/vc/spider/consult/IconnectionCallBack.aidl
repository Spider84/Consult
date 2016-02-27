// IconnectionCallBack.aidl
package vc.spider.consult;

// Declare any non-default types here with import statements

interface IconnectionCallBack {
    void CUConnected(byte CUcode);
    void StreamReceived(out byte[] data,int len);
    void Disconnected();
}
