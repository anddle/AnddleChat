package com.anddle.anddlechat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.util.Set;

public class DeviceListActivity extends AppCompatActivity {

    private final static String TAG = "BT discover";
    private final int BT_SEARCH_STATE_IDLE = 0;
    private final int BT_SEARCH_STATE_SEARCHING = 1;

    private ListView mBTDeviceListView;
    private BluetoothAdapter mBluetoothAdapter;
    private int mBTSearchingState;
    private MenuItem mSearchMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_list_activity);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        DeviceItemAdapter adapter = new DeviceItemAdapter(this, R.layout.device_list_item);
        mBTDeviceListView = (ListView) findViewById(R.id.device_list);
        mBTDeviceListView.setAdapter(adapter);
        mBTDeviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.cancelDiscovery();
                }

                ArrayAdapter adapter = (ArrayAdapter) mBTDeviceListView.getAdapter();
                BluetoothDevice device = (BluetoothDevice) adapter.getItem(position);

                Intent i = new Intent();
                i.putExtra("DEVICE_ADDR", device.getAddress());

                setResult(RESULT_OK, i);
                finish();

            }
        });

        mBTSearchingState = BT_SEARCH_STATE_IDLE;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter);

        updateDeviceList();
    }

    private void updateDeviceList() {

        DeviceItemAdapter adapter = (DeviceItemAdapter) mBTDeviceListView.getAdapter();
        adapter.clear();

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.d(TAG, "BT device bounded:" + device.getName());
                adapter.add(device);
            }
        }

        adapter.notifyDataSetChanged();

        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        int hasPermission = ActivityCompat.checkSelfPermission(DeviceListActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (hasPermission != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(DeviceListActivity.this,
                    new String[]{
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    0);
        } else {
            boolean ret = mBluetoothAdapter.startDiscovery();
            Log.d(TAG, "BT device discover about to start: ret=" + ret);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.device_menu, menu);
        mSearchMenuItem = menu.findItem(R.id.search_menu);;
        updateUI();

        return true;
    }

    private void updateUI() {

        switch (mBTSearchingState)
        {
            case BT_SEARCH_STATE_IDLE:
            {
                if(mSearchMenuItem != null)
                {
                    mSearchMenuItem.setTitle(R.string.search);
                }
            }
            break;

            case BT_SEARCH_STATE_SEARCHING:
            {

                if(mSearchMenuItem != null)
                {
                    mSearchMenuItem.setTitle(R.string.cancel);
                }
            }
            break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch(item.getItemId())
        {
            case R.id.search_menu:
            {
                if(mBTSearchingState == BT_SEARCH_STATE_IDLE) {
                    if (mBluetoothAdapter.isDiscovering()) {
                        mBluetoothAdapter.cancelDiscovery();
                    }
                    updateDeviceList();
                }
                else if(mBTSearchingState == BT_SEARCH_STATE_SEARCHING) {
                    if (mBluetoothAdapter.isDiscovering()) {
                        mBluetoothAdapter.cancelDiscovery();
                    }
                }
            }
            break;

            case android.R.id.home:
                this.finish();

            break;

        }
        return true;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                Log.d(TAG, "BT device found:" + device.getName());

                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    DeviceItemAdapter adapter = (DeviceItemAdapter) mBTDeviceListView.getAdapter();
                    adapter.add(device);
                    adapter.notifyDataSetChanged();
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "BT device discover started");
                mBTSearchingState = BT_SEARCH_STATE_SEARCHING;
                updateUI();

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "BT device discover finished");
                mBTSearchingState = BT_SEARCH_STATE_IDLE;
                updateUI();

            }
            else {
                Log.d(TAG, "BT device got action:"+action);
            }

        }
    };
}
