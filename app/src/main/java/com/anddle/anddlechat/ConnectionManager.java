package com.anddle.anddlechat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Gym on 16/6/12.
 */
public class ConnectionManager {

    private final static String TAG = "ConnectionManager";

    public static final int CONNECT_STATE_IDLE = 0;
    public static final int CONNECT_STATE_CONNECTING = 1;
    public static final int CONNECT_STATE_CONNECTED = 2;
    public static final int LISTEN_STATE_IDLE = 3;
    public static final int LISTEN_STATE_LISTENING = 4;

    private static final String BT_NAME = "AnddleChat";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");//("fa87c0d0-afac-11de-8a39-0800200c9a66");

    private int mConnectState = CONNECT_STATE_IDLE;
    private int mListenState = LISTEN_STATE_IDLE;
    private ConnectionListener mConnectionListener;
    private final BluetoothAdapter mBluetoothAdapter;

    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;

    public interface ConnectionListener {

        public void onConnectStateChange(int oldState, int State);
        public void onListenStateChange(int oldState, int State);
        public void onSendData(boolean suc, byte[] data);
        public void onReadData(byte [] data);
    }

    public ConnectionManager(ConnectionListener cl) {
        mConnectionListener = cl;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void startListen() {

        Log.d(TAG, "ConnectionManager startListen");

        if(mAcceptThread != null) {
            mAcceptThread.cancel();
        }

        mAcceptThread = new AcceptThread();
        mAcceptThread.start();
    }

    public void stopListen() {

        Log.d(TAG, "ConnectionManager stopListen");

        if(mAcceptThread != null) {
            mAcceptThread.cancel();
        }
    }

    public synchronized void connect(String deviceAddr) {

        Log.d(TAG, "ConnectionManager about to connect BT device at:" + deviceAddr);
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
        }

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddr);

        try {

            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(BT_UUID);
            connected(socket, true);

        } catch (IOException e) {
            Log.e(TAG, "Connect failed", e);
        }

    }

    public void disconnect() {

        Log.d(TAG, "ConnectionManager disconnect connection");

        if(mConnectedThread != null) {
            mConnectedThread.cancel();
        }
    }

    public synchronized int getCurrentConnectState() {
        return mConnectState;
    }

    public synchronized int getCurrentListenState() {
        return mListenState;
    }

    public synchronized boolean sendData(byte[] data) {
        if(mConnectedThread != null && mConnectState == CONNECT_STATE_CONNECTED) {
            mConnectedThread.sendData(data);

            return true;
        }
        return false;
    }

    public String getState(int state) {
        switch (state) {
            case CONNECT_STATE_IDLE:
                return "CONNECT_STATE_IDLE";

            case CONNECT_STATE_CONNECTING:
                return "CONNECT_STATE_CONNECTING";


            case CONNECT_STATE_CONNECTED:
                return "CONNECT_STATE_CONNECTED";

            case LISTEN_STATE_IDLE:
                return "LISTEN_STATE_IDLE";

            case LISTEN_STATE_LISTENING:
                return "LISTEN_STATE_LISTENING";
        }

        return "UNKNOWN";
    }

    //==============================================================================================
    private synchronized void setConnectState(int state) {

        if(mConnectState == state) {
            return;
        }

        int oldState = mConnectState;
        mConnectState = state;

        if(mConnectionListener != null) {

            Log.d(TAG, "BT state change: "+getState(oldState)+" -> "+ getState(mConnectState));
            mConnectionListener.onConnectStateChange(oldState, mConnectState);
        }
    }

    private synchronized void setListenState(int state) {

        if(mListenState == state) {
            return;
        }

        int oldState = mListenState;
        mListenState = state;

        if(mConnectionListener != null) {

            Log.d(TAG, "BT state change: "+getState(oldState)+" -> "+ getState(mListenState));
            mConnectionListener.onListenStateChange(oldState, mListenState);
        }
    }

    private synchronized void connected(BluetoothSocket socket, boolean needConnect) {

        mConnectedThread = new ConnectedThread(socket, needConnect);
        mConnectedThread.start();
    }

    private class AcceptThread extends Thread {

        private final String TAG = "AcceptThread";

        private BluetoothServerSocket mServerSocket;
        private boolean mUserCancel;

        public AcceptThread() {
            Log.d(TAG, "create AcceptThread");
            BluetoothServerSocket tmp = null;
            mUserCancel = false;

            // Create a new listening server socket
            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        BT_NAME, BT_UUID);
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread create fail: ", e);
            }
            mServerSocket = tmp;
        }

        @Override
        public void run() {

            Log.d(TAG, "AcceptThread START");

            setName("AcceptThread");

            setListenState(LISTEN_STATE_LISTENING);

            BluetoothSocket socket = null;

            while(!mUserCancel) {
                try {
                    Log.d(TAG, "AcceptThread wait for accept a new socket");
                    socket = mServerSocket.accept();

                } catch (IOException e) {
                    Log.d(TAG, "AcceptThread exception: " + e);
                    mServerSocket = null;
                    break;
                }

                Log.d(TAG, "AcceptThread accepted a connection, ConnectState=: " + ConnectionManager.this.getState(mConnectState));
                if(mConnectState == CONNECT_STATE_CONNECTED || mConnectState == CONNECT_STATE_CONNECTING) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else if(mConnectState == CONNECT_STATE_IDLE) {
                    connected(socket, false);
                }
            }

            if(mServerSocket != null) {
                try {
                    mServerSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mServerSocket = null;
            }
            setListenState(LISTEN_STATE_IDLE);
            mAcceptThread = null;

            if(mUserCancel == true) {
                Log.d(TAG, "AcceptThread END since user cancel.");
            }
            else {
                Log.d(TAG, "AcceptThread END");
            }
        }

        public void cancel() {
            Log.d(TAG, "AcceptThread cancel");
            try {
                mUserCancel = true;
                if(mServerSocket != null) {
                    mServerSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread cancel fail, e: ");
            }
        }
    }

    private class ConnectedThread extends Thread {

        private final int MAX_BUFFER_SIZE = 1024;

        private BluetoothSocket mSocket;
        private InputStream mInStream;
        private OutputStream mOutStream;
        private boolean mUserCancel;
        private boolean mNeedConnect;

        public ConnectedThread(BluetoothSocket socket, boolean needConnect) {
            Log.d(TAG, "create ConnectedThread");

            setName("ConnectedThread");
            mNeedConnect = needConnect;
            mSocket = socket;
            mUserCancel = false;
        }

        @Override
        public void run() {

            Log.d(TAG, "ConnectedThread START");

            setConnectState(CONNECT_STATE_CONNECTING);

            if(mNeedConnect && !mUserCancel) {
                try {
                    mSocket.connect();
                } catch (IOException e) {

                    Log.d(TAG, "ConnectedThread END at connect(), " + e);
                    setConnectState(CONNECT_STATE_IDLE);
                    mSocket = null;
                    mConnectedThread = null;

                    return;
                }
            }

            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = mSocket.getInputStream();
                tmpOut = mSocket.getOutputStream();
            } catch (IOException e) {
                Log.d(TAG, "ConnectedThread END at getStream(), "+ e);
                setConnectState(CONNECT_STATE_IDLE);
                mSocket = null;
                mConnectedThread = null;

                return;
            }

            mInStream = tmpIn;
            mOutStream = tmpOut;


            setConnectState(CONNECT_STATE_CONNECTED);

            byte[] buffer = new byte[MAX_BUFFER_SIZE];
            int bytes;

            while (!mUserCancel) {
                try {
                    Log.d(TAG, "ConnectedThread wait for read data");
                    bytes = mInStream.read(buffer);

                    if(mConnectionListener != null && bytes > 0) {

                        byte [] data = new byte[bytes];
                        System.arraycopy(buffer, 0, data, 0, bytes);
                        mConnectionListener.onReadData(data);
                    }
                } catch (IOException e) {
                    Log.d(TAG, "ConnectedThread disconnected, ", e);
                    break;
                }
            }

            setConnectState(CONNECT_STATE_IDLE);
            mSocket = null;
            mConnectedThread = null;

            if(mUserCancel == true) {
                Log.d(TAG, "ConnectedThread END since user cancel.");
            }
            else {
                Log.d(TAG, "ConnectedThread END");
            }
        }

        public void cancel() {
            Log.d(TAG, "ConnectedThread cancel START");
            try {
                mUserCancel = true;
                if(mSocket != null) {
                    mSocket.close();
                }

            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread cancel failed", e);
            }

            Log.d(TAG, "ConnectedThread cancel END");
        }

        public void sendData(byte[] data) {
            try {
                mOutStream.write(data);

                if(mConnectionListener != null) {
                    mConnectionListener.onSendData(true, data);
                }
            } catch (IOException e) {
                Log.e(TAG, "send data fail", e);
                if(mConnectionListener != null) {
                    mConnectionListener.onSendData(true, data);
                }
            }
        }
    }
}


