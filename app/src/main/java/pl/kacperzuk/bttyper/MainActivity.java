package pl.kacperzuk.bttyper;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import pl.kacperzuk.libs.seconn.SeConn;
import pl.kacperzuk.libs.seconn.SeConnHandler;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity implements SeConnHandler {
    private static final String mDevName = "Inzynierka";
    private static final int MESSAGE_READ = 1;
    private static final int MESSAGE_CONNECTED = 2;
    private static final int MESSAGE_CONNECTION_ERROR = 3;
    private static final int MESSAGE_WRITTEN = 4;
    private static final int MESSAGE_DISCONNECTED = 5;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final UUID uuid1 = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final UUID uuid2 = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb");

    private SeConn mSeConn;
    private TextView mStatusTv;
    private BluetoothAdapter mBluetoothAdapter;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_CONNECTED) {
                addState("BT connected, authenticating...");
                mSeConn.connect();
            } else if (msg.what == MESSAGE_WRITTEN) {
                return;
            } else if (msg.what == MESSAGE_READ) {
                byte[] data = (byte[])msg.obj;
                mSeConn.newData(data);
            } else if (msg.what == MESSAGE_CONNECTION_ERROR) {
                addState("Connection failed");
            } else if (msg.what == MESSAGE_DISCONNECTED) {
                addState("DISCONNECTED");
            }
        }
    };

    @Override
    public void writeData(byte[] data) {
        rawSend(data);
    }

    @Override
    public void onDataReceived(byte[] data) {
        addState(new String(data));
    }

    @Override
    public void onStateChange(SeConn.State prev_state, SeConn.State cur_state) {
        addState("New seconn state:"+cur_state);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                Class class1 = device.getClass();
                Class aclass[] = new Class[1];
                aclass[0] = Integer.TYPE;
                Method method = class1.getMethod("createRfcommSocket", aclass);
                Object aobj[] = new Object[1];
                aobj[0] = Integer.valueOf(1);

                tmp = (BluetoothSocket) method.invoke(device, aobj);
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException ignored) {
                }
                connectException.printStackTrace();
                mHandler.obtainMessage(MESSAGE_CONNECTION_ERROR).sendToTarget();
                return;
            }

            synchronized (MainActivity.this) {
                mConnectThread = null;
            }

            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket(mmSocket);
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, Arrays.copyOfRange(buffer, 0, bytes))
                                .sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    mHandler.obtainMessage(MESSAGE_DISCONNECTED)
                            .sendToTarget();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                mHandler.obtainMessage(MESSAGE_WRITTEN, bytes.length, -1)
                        .sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        /* Call this from the main activity to shutdown the connection */
        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void manageConnectedSocket(BluetoothSocket mmSocket) {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();

        mHandler.obtainMessage(MESSAGE_CONNECTED).sendToTarget();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSeConn = new SeConn(this);
        setContentView(R.layout.activity_main);


        mStatusTv = (TextView) findViewById(R.id.status_tv);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final EditText msgText = (EditText) findViewById(R.id.msg_to_send);
        findViewById(R.id.send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSeConn.writeData(msgText.getText().toString().getBytes());
            }
        });


        if (mBluetoothAdapter == null) {
            addState("Bluetooth not supported on this device");
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                findDevices();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                findDevices();
            } else {
                addState("BT not enabled");
            }
        }
    }

    private void findDevices() {
        addState("Looking for devices");
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals(mDevName)) {
                    connectAsClient(device);
                    return;
                } else {
                    addState("No paired device.");
                }
            }
        }
    }

    private void addState(String st) {
        mStatusTv.setText(st);
    }

    private synchronized void connectAsClient(BluetoothDevice device) {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        addState("Connecting in thread...");
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    private synchronized void rawSend(byte[] data) {
        if (mConnectedThread != null) {
            mConnectedThread.write(data);
        }
    }
    private void send(String text) {
        rawSend((text.trim() + "\n").getBytes());
    }
}
