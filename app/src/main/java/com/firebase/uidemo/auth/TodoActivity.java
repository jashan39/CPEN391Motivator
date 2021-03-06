package com.firebase.uidemo.auth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.uidemo.db.TaskDBHelper;
import com.firebase.uidemo.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class TodoActivity extends AppCompatActivity {

    private static final String TAG = "TodoActivity";
    public TaskDBHelper mHelper;
    private ListView mTaskListView;
    private ArrayAdapter<String> mAdapter;
    ArrayList<String> taskListBluetooth = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo);
        mHelper = new TaskDBHelper(this);
        mTaskListView = (ListView) findViewById(R.id.list_todo);

        SQLiteDatabase db = mHelper.getReadableDatabase();
        Cursor cursor = db.query(TaskContract.TaskEntry.TABLE,
                                 new String[]{TaskContract.TaskEntry._ID, TaskContract.TaskEntry.COL_TASK_TITLE},
                                 null, null, null, null, null);
        while(cursor.moveToNext()){
            int idx = cursor.getColumnIndex(TaskContract.TaskEntry.COL_TASK_TITLE);
            Log.d(TAG, "Task : cursor.getString(idx)");
        }

        // get the context for the application
        context = getApplicationContext();

        // This call returns a handle to the one bluetooth device within your Android device
        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // check to see if your android device even has a bluetooth device !!!!,
        if (myBluetoothAdapter == null) {
            Toast toast = Toast.makeText(context, "starting !!", Toast.LENGTH_LONG);
            toast = Toast.makeText(context, "No Bluetooth !!", Toast.LENGTH_LONG);
            toast.show();
            finish();

            // if no bluetooth device on this tablet don’t go any further.
            return;
        }

        if (!myBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult (enableBtIntent, REQUEST_ENABLE_BT);
        }

        IntentFilter filterFound = new IntentFilter (BluetoothDevice.ACTION_FOUND);
        IntentFilter filterStart = new IntentFilter (BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        IntentFilter filterStop = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver (mReceiver, filterFound);
        registerReceiver (mReceiver, filterStart);
        registerReceiver (mReceiver, filterStop);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver (mReceiver, filter);

        myBluetoothAdapter.startDiscovery();

        cursor.close();
        db.close();
        updateUI();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_task:
                Log.d(TAG, "Add a new task");
                final EditText taskEditText = new EditText(this);
                int maxLength = 27;
                taskEditText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("Add a new task")
                        .setMessage("What do you want to do next?")
                        .setView(taskEditText)
                        .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String task = String.valueOf(taskEditText.getText());
                                SQLiteDatabase db = mHelper.getWritableDatabase();
                                ContentValues values = new ContentValues();
                                values.put(TaskContract.TaskEntry.COL_TASK_TITLE, task);
                                db.insertWithOnConflict(TaskContract.TaskEntry.TABLE,
                                                        null,
                                                        values,
                                                        SQLiteDatabase.CONFLICT_REPLACE);
                                db.close();
                                updateUI();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .create();
                dialog.show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void updateUI(){
        ArrayList<String> taskList = new ArrayList<>();
        SQLiteDatabase db = mHelper.getReadableDatabase();
        Cursor cursor = db.query(TaskContract.TaskEntry.TABLE,
                                 new String[]{TaskContract.TaskEntry._ID, TaskContract.TaskEntry.COL_TASK_TITLE},
                                 null, null, null, null, null);

        while (cursor.moveToNext()) {
            int idx = cursor.getColumnIndex(TaskContract.TaskEntry.COL_TASK_TITLE);
            taskList.add(cursor.getString(idx));
        }

        if (mAdapter == null){
            mAdapter = new ArrayAdapter<>(this,
                                          R.layout.item_todo,
                                          R.id.task_title,
                                          taskList);
            mTaskListView.setAdapter(mAdapter);
        }

        else{
            mAdapter.clear();
            mAdapter.addAll(taskList);
            mAdapter.notifyDataSetChanged();
        }

        cursor.close();
        db.close();
        taskListBluetooth = taskList;
    }

    public void deleteTask(View view) {
        View parent = (View) view.getParent();
        TextView taskTextView = (TextView) parent.findViewById(R.id.task_title);
        String task = String.valueOf(taskTextView.getText());
        SQLiteDatabase db = mHelper.getWritableDatabase();
        db.delete(TaskContract.TaskEntry.TABLE,
                  TaskContract.TaskEntry.COL_TASK_TITLE + " = ?",
                  new String[]{task});
        db.close();
        updateUI();
    }


    //SAM COMES IN

    // we want to display all paired devices to the user in a ListView so they can choose a device
    private ArrayList <BluetoothDevice> Paireddevices = new ArrayList <BluetoothDevice>();

    // an Array/List to hold string details of the Paired Bluetooth devices, name + MAC address etc.
    // this is displayed in the listview for the user to choose
    private ArrayList <String> myPairedDevicesStringArray = new ArrayList <String>();

    private BluetoothSocket mmSocket = null;
    public static InputStream mmInStream = null;
    public static OutputStream mmOutStream = null;

    private boolean Connected = false;

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice newDevice;
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Intent will contain discovered Bluetooth Device so go and get it
                newDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                // Add the name and address to the custom array adapter to show in a ListView
                String theDevice = new String(deviceName + "\nMAC Address = " + deviceHardwareAddress);

                //Toast.makeText(context, theDevice, Toast.LENGTH_LONG).show();
                if(deviceName.equals("2017group16")) {
                    CreateSerialBluetoothDeviceSocket(newDevice);
                    ConnectToSerialBlueToothDevice();


                }
                // notify array adaptor that the contents of String Array have changed
                // TODO: This needs private MyCustomArrayAdaptor myDiscoveredArrayAdapter
                //myDiscoveredArrayAdapter.notifyDataSetChanged();
            }
            // more visual feedback for user (not essential but useful)
            else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                //Toast.makeText(context, "Discovery Started", Toast.LENGTH_LONG).show();
            }
            else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
            {
                //Toast.makeText(context, "Discovery Finished", Toast.LENGTH_LONG).show();
            }
        }
    };


    // an Array/List to hold discovered Bluetooth devices
// A bluetooth device contains device Name and Mac Address information which
// we want to display to the user in a List View so they can choose a device
// to connect to. We also need that info to actually connect to the device
    private ArrayList <BluetoothDevice> Discovereddevices = new ArrayList<BluetoothDevice>();
    // an Array/List to hold string details of the Bluetooth devices, name + MAC address etc.
// this is displayed in the listview for the user to choose
    private ArrayList <String> myDiscoveredDevicesStringArray = new ArrayList <String>();

    BluetoothAdapter myBluetoothAdapter;


    // A constant that we use to determine if our request to turn on bluetooth worked
    private final static int REQUEST_ENABLE_BT = 1;

    // get the context for the application. We use this with things like "toast" popups
    private Context context;
// two customized array adaptors for use with list view (see example page 7)
// each adaptor and view will hold details of either paired or discovered devices
// the array adaptors were developed in the lecture some slides previous
//private mBluetoothAdapter myPairedArrayAdapter;
//private MyCustomArrayAdaptor myDiscoveredArrayAdapter;


    // this call back function is run when an activity that returns a result ends.
// Check the requestCode (given when we start the activity) to identify which
// activity is returning a result, and then resultCode is the value returned
// by the activity. In most cases this is RESULT_OK. If not end the activity
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if ( requestCode == REQUEST_ENABLE_BT)
            // was it the “enable bluetooth ” activity?
            if ( resultCode != RESULT_OK)  {
                // if so did it work OK?
                Toast toast = Toast.makeText(context, "BlueTooth Failed to Start", Toast.LENGTH_LONG);
                toast.show();
                finish();
                return ;
            } else {
                Toast toast = Toast.makeText(context, "BlueTooth Started!", Toast.LENGTH_LONG);
                toast.show();
                finish();
                return ;
            }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    void closeConnection() {
        try {
            mmInStream.close();
            mmInStream = null;
        }
        catch (IOException e) {}
        try {
            mmOutStream.close();
            mmOutStream = null;
        } catch (IOException e) {}
        try {
            mmSocket.close();
            mmSocket = null;
        } catch (IOException e) {}
        Connected = false;
    }

    public void CreateSerialBluetoothDeviceSocket(BluetoothDevice device) {
        mmSocket = null;
// universal UUID for a serial profile RFCOMM blue tooth device
// this is just one of those “things” that you have to do and just works
        UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
// Get a Bluetooth Socket to connect with the given BluetoothDevice
        try {
// MY_UUID is the app's UUID string, also used by the server code
            mmSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            Toast.makeText(context, "Socket Creation Failed", Toast.LENGTH_LONG).show();
        }
    }

    public void ConnectToSerialBlueToothDevice() {
// Cancel discovery because it will slow down the connection
        myBluetoothAdapter.cancelDiscovery();
        try {
// Attempt connection to the device through the socket.
            mmSocket.connect();
            Toast.makeText(context, "Connection Made", Toast.LENGTH_LONG).show();
        }
        catch (IOException connectException) {
            Toast.makeText(context, "Connection Failed", Toast.LENGTH_LONG).show();
            return;
        }
// create the input/output stream and record fact we have made a connection
        GetInputOutputStreamsForSocket();
// see page 26
        Connected = true;
    }

    // gets the input/output stream associated with the current socket
    public void GetInputOutputStreamsForSocket() {
        try {
            mmInStream = mmSocket.getInputStream();
            mmOutStream = mmSocket.getOutputStream();
        }
        catch (IOException e) { }
    }

    public void WriteToBTDevice(String message) {
        String s = new String("\r\n");
        byte [] msgBuffer = message.getBytes();
        byte [] newline = s.getBytes();
        try {
            mmOutStream.write(msgBuffer) ;
            mmOutStream.write(newline) ;
        }
        catch (IOException e) { }
    }
    public void bluetoothStuff(View view){
        String data = "";

        for(String values : taskListBluetooth){
            data = data + values + "\t" + "n/A" + "\t";
        }

        data = data + "\0";
        Log.i("asdas", data);
        if(data == "")
            WriteToBTDevice("\0");
        else
            WriteToBTDevice(data);
    }
}
