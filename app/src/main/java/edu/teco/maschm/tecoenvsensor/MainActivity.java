package edu.teco.maschm.tecoenvsensor;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String APP = "tecoenvsensor";

    private static final int REQUEST_ENABLE_BT = 356;
    private static final int REQUEST_ALLOW_LOCATION = 1094;
    private static final int REQUEST_ALLOW_LOCATION2 = 9234;
    private static final int REQUEST_ENABLE_LOCATION = 2131;

    private static final int SCAN_PERIOD = 10000; //in ms => 10s
    private static final int READ_PERIOD = 1000; //in ms => 1s

    private TextView tbBtStatus;
    private TextView tbGATTStatus;
    private Button btnBleScan;
    private Button btnBleDisconnect;
    private ListView lvDevices;
    private BleDeviceListAdapter adapter;


    private BluetoothAdapter mBluetoothAdapter;
    private IntentFilter btDeviceFoundFilter;
    private BluetoothLeScanner mBluetoothLEAdapter;
    private Handler mHandler = new Handler();
    private boolean mScanning = false;
    private BluetoothGatt mGatt = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tbBtStatus = (TextView) findViewById(R.id.tv_bt_status);
        tbGATTStatus = (TextView) findViewById(R.id.tv_gatt_status);


        btnBleScan = (Button) findViewById(R.id.btn_ble_scan);
        btnBleScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(!mScanning);
            }
        });

        btnBleDisconnect = (Button) findViewById(R.id.btn_ble_disconnect);
        btnBleDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGatt != null) {
                    mGatt.disconnect();
                    mGatt = null;
                }
            }
        });

        adapter = new BleDeviceListAdapter(this);
        lvDevices = (ListView) findViewById(R.id.lv_device_list);
        lvDevices.setAdapter(adapter);
        lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mGatt == null) {
                    BluetoothDevice device = adapter.getItem(position);
                    mGatt = device.connectGatt(MainActivity.this, false, mGattCallback);
                    tbGATTStatus.setText("connection to " + device.getName());
                }
            }
        });

        // check for Bluetooth
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            tbBtStatus.setText(R.string.bl_not_supported);
            Log.w(APP, "BL getPackageManager().hasSystemFeature is false");
            Toast.makeText(this, R.string.bl_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // check for BLE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(APP, "BLE getPackageManager().hasSystemFeature is false");
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // get BluetoothAdapter
        // mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            tbBtStatus.setText(R.string.bl_not_supported);
            Log.v(APP, "BluetoothAdapter.getDefaultAdapter() is null");
            Toast.makeText(this, R.string.bl_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        mBluetoothLEAdapter = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothAdapter == null) {
            Log.v(APP, "can't get getBluetoothLeScanner");
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // enable Bluetooth
        if (!mBluetoothAdapter.isEnabled()) {
            tbBtStatus.setText(R.string.bl_off);
            Log.v(APP, "enabling Bluetooth");
            Toast.makeText(this, R.string.bl_turn_on, Toast.LENGTH_SHORT).show();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else {
            tbBtStatus.setText(R.string.bl_on);
        }


        // Register for broadcasts when a device is discovered.
        btDeviceFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, btDeviceFoundFilter);
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));
        Log.v(APP, "registered BluetoothDevice.ACTION_FOUND receiver");


        // request location permission (needed for scanning on ble)
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ALLOW_LOCATION);
        // requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ALLOW_LOCATION2);



        // String locMod = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_MODE);

        LocationManager lm = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        Log.v(APP, "GPS_PROVIDER: " + lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        Log.v(APP, "NETWORK_PROVIDER: " + lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        Log.v(APP, "PASSIVE_PROVIDER: " + lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER));

        if (! (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        || lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)))
        {
            Toast.makeText(this, R.string.location_turn_on, Toast.LENGTH_SHORT).show();
            Intent enableLocationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(enableLocationIntent, REQUEST_ENABLE_LOCATION);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }

        // unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults){
        if(requestCode == REQUEST_ALLOW_LOCATION || requestCode == REQUEST_ALLOW_LOCATION2) {
            for (int i = 0; i < permissions.length; i++){
                if (permissions[i].equals("android.permission.ACCESS_COARSE_LOCATION")) {
                    if (grantResults[i] != -1) {
                        Log.v(APP, "granted android.permission.ACCESS_COARSE_LOCATION");
                    }else {
                        Log.v(APP, "denied android.permission.ACCESS_COARSE_LOCATION");
                    }
                }
                if (permissions[i].equals("android.permission.ACCESS_FINE_LOCATION")) {
                    if (grantResults[i] != -1) {
                        Log.v(APP, "granted android.permission.ACCESS_FINE_LOCATION");
                    }else {
                        Log.v(APP, "denied android.permission.ACCESS_FINE_LOCATION");
                    }
                }
            }
        }
    }

    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.v(APP, "enabled BLUETOOTH by intent");
                this.tbBtStatus.setText(R.string.bl_on);
            }
            else {
                Log.w(APP, "couldn't enabled BLUETOOTH by intent");
            }
        }
        else if(requestCode == REQUEST_ENABLE_LOCATION) {
            if (resultCode == RESULT_OK) {
                Log.v(APP, "enabled LOCATION by intent");
                this.tbBtStatus.setText(R.string.bl_on);
            }
            else {
                Log.w(APP, "couldn't LOCATION by intent");
            }

        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter f = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("4b822f90-3941-4a4b-a3cc-b2602ffe0d00")).build();
            filters.add(f);

            adapter.clear();

            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLEAdapter.stopScan(mLeScanCallback);
                    Log.v(APP, "stop scanning");
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothLEAdapter.startScan(mLeScanCallback);
            Log.v(APP, "start scanning");
        } else {
            mScanning = false;
            mBluetoothLEAdapter.stopScan(mLeScanCallback);
            Log.v(APP, "stop scanning");
        }
    }


    private final ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.v(APP, "ble scan onScanResult");

            BluetoothDevice device = result.getDevice();

            adapter.add(device);

            String deviceName = device.getName();
            String deviceHardwareAddress = device.getAddress(); // MAC address

            Log.v(APP, "scanned deviceName: " + deviceName + " MAC: " + deviceHardwareAddress);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(APP, "ble scan onScanFailed");
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            List<BluetoothDevice> list = new ArrayList<>(results.size());
            for (ScanResult res : results) {
                list.add(res.getDevice());
            }
            adapter.addAll(list);

            Log.v(APP, "ble scan onBatchScanResults");
        }
    };

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(APP, "received intent: " + intent.toString());

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                Log.v(APP, "BluetoothDevice.ACTION_FOUND");
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.v(APP, "found deviceName: " + deviceName + " MAC: " + deviceHardwareAddress);
            }
            else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                Log.v(APP, "bt state changed");
            }

        }
    };


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tbGATTStatus.setText("connected to " + mGatt.getDevice().getName());
                    }
                });



                Log.i(APP, "Connected to GATT server.");
                Log.i(APP, "Attempting to start service discovery:" + gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tbGATTStatus.setText("disconnected");
                    }
                });

                Log.i(APP, "Disconnected from GATT server.");
            }
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(APP, "GATT services discovered");
                for ( BluetoothGattService service : gatt.getServices()) {
                    Log.v(APP, "service: " + service.getUuid());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.v(APP, "characteristic: " + characteristic.getUuid());
                    }
                }
            } else {
                Log.w(APP, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(APP, characteristic.toString());
            }
            else {
                Log.w(APP, "couldn't read characteristic");
            }
        }

    };
}
