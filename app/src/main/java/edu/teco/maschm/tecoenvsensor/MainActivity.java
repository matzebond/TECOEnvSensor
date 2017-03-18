package edu.teco.maschm.tecoenvsensor;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
import android.os.Message;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String TAG = "tecoenvsensor";

    private static final int REQUEST_ENABLE_BT = 356;
    private static final int REQUEST_ALLOW_LOCATION = 1094;
    private static final int REQUEST_ALLOW_LOCATION2 = 9234;
    private static final int REQUEST_ENABLE_LOCATION = 2131;

    private static final int SCAN_PERIOD = 60000; //in ms => 10s
    private static final int READ_PERIOD = 1000; //in ms => 1s
    private static Timer mTimer = new Timer();

    private TextView tvBtStatus;
    private TextView tvGATTStatus;
    private TextView tvTemp;
    private TextView tvHumi;
    private TextView tvPres;
    private TextView tvCO2;
    private TextView tvNO2;
    private TextView tvNH3;
    private CheckBox cbREST;
    private ProgressBar pbScan;
    private Button btnBleScan;
    private Button btnBleDisconnect;
    private ListView lvDevices;
    private BleDeviceListAdapter adapter;

    private int co2Calibration = 0;
    private int no2Calibration = 0;
    private int nh3Calibration = 0;


    private BluetoothAdapter mBluetoothAdapter;
    private IntentFilter btDeviceFoundFilter;
    private BluetoothLeScanner mBluetoothLEAdapter;
    private boolean mScanning = false;
    private BluetoothGatt mGatt = null;
    private BluetoothDevice mTecoOG1;
    private List<BluetoothDevice> mTecoDeviceList = new ArrayList<>();

    private Runnable mScanRunnable = new Runnable() {
        @Override
        public void run() {
            if (mScanning) {
                toggleBLEScan();
            }
        }
    };

    // handler used vor stopping scan AND requesting ble gatt values
    private Handler mHandler = new Handler();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvBtStatus = (TextView) findViewById(R.id.tv_bt_status);
        tvGATTStatus = (TextView) findViewById(R.id.tv_gatt_status);
        tvTemp = (TextView) findViewById(R.id.tv_temp);
        tvHumi = (TextView) findViewById(R.id.tv_humi);
        tvPres = (TextView) findViewById(R.id.tv_pres);
        tvCO2 = (TextView) findViewById(R.id.tv_CO2);
        tvNO2 = (TextView) findViewById(R.id.tv_NO2);
        tvNH3 = (TextView) findViewById(R.id.tv_NH3);
        cbREST = (CheckBox) findViewById(R.id.cb_REST);

        pbScan = (ProgressBar) findViewById(R.id.scanProgress);
        pbScan.setVisibility(View.INVISIBLE);

        btnBleScan = (Button) findViewById(R.id.btn_ble_scan);
        btnBleScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBLEScan();
            }
        });

        btnBleDisconnect = (Button) findViewById(R.id.btn_ble_disconnect);
        btnBleDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGatt != null) {
                    mGatt.disconnect();
                    btnBleDisconnect.setEnabled(false);
                }
            }
        });

        adapter = new BleDeviceListAdapter(this);
        lvDevices = (ListView) findViewById(R.id.lv_device_list);
        lvDevices.setAdapter(adapter);
        lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mGatt != null) {
                    mGatt.close();
                }
                BluetoothDevice device = adapter.getItem(position);
                mGatt = device.connectGatt(MainActivity.this, false, mGattCallback);
                tvGATTStatus.setText(String.format(getString(R.string.gatt_connecting), device.getName()));
            }
        });

        checkBluetooth();

        requestLocation();

        MoCIoTInfluxRestClient.createDB();
    }

    public void checkBluetooth() {
        // check for Bluetooth
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            tvBtStatus.setText(R.string.bl_not_supported);
            Log.w(TAG, "BL getPackageManager().hasSystemFeature is false");
            Toast.makeText(this, R.string.bl_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // check for BLE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "BLE getPackageManager().hasSystemFeature is false");
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // get BluetoothAdapter
        // mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            tvBtStatus.setText(R.string.bl_not_supported);
            Log.v(TAG, "BluetoothAdapter.getDefaultAdapter() is null");
            Toast.makeText(this, R.string.bl_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        mBluetoothLEAdapter = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothAdapter == null) {
            Log.v(TAG, "can't get getBluetoothLeScanner");
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // enable Bluetooth
        if (!mBluetoothAdapter.isEnabled()) {
            tvBtStatus.setText(R.string.bl_off);
            Log.v(TAG, "enabling Bluetooth");
            Toast.makeText(this, R.string.bl_turn_on, Toast.LENGTH_SHORT).show();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else {
            tvBtStatus.setText(R.string.bl_on);
        }


        // Register for broadcasts when a device is discovered.
        btDeviceFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, btDeviceFoundFilter);
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));
        Log.v(TAG, "registered BluetoothDevice.ACTION_FOUND receiver");

        //mTecoOG1 = mBluetoothAdapter.getRemoteDevice(TECO_UUIDS.TECO_OG1);

        //mTecoDeviceList.add(mTecoOG1);
    }

    public void requestLocation() {
        // request location permission (needed for scanning on ble)
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ALLOW_LOCATION);
        // requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ALLOW_LOCATION2);

        // String locMod = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_MODE);

        LocationManager lm = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        Log.v(TAG, "GPS_PROVIDER: " + lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        Log.v(TAG, "NETWORK_PROVIDER: " + lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        Log.v(TAG, "PASSIVE_PROVIDER: " + lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER));

        if (! (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                || lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)))
        {
            Toast.makeText(this, R.string.location_turn_on, Toast.LENGTH_SHORT).show();
            Intent enableLocationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(enableLocationIntent, REQUEST_ENABLE_LOCATION);
        }
    }

    private void toggleTimer(boolean start) {
        if(true) return;

        if(start) {
            Log.v(TAG, "start timer");
            mTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    mHandler.obtainMessage().sendToTarget();
                }
            }, 0, READ_PERIOD);
        }
        else {
            Log.v(TAG, "stop timer");
            mTimer.purge();
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
                        Log.v(TAG, "granted android.permission.ACCESS_COARSE_LOCATION");
                    }else {
                        Log.v(TAG, "denied android.permission.ACCESS_COARSE_LOCATION");
                    }
                }
                if (permissions[i].equals("android.permission.ACCESS_FINE_LOCATION")) {
                    if (grantResults[i] != -1) {
                        Log.v(TAG, "granted android.permission.ACCESS_FINE_LOCATION");
                    }else {
                        Log.v(TAG, "denied android.permission.ACCESS_FINE_LOCATION");
                    }
                }
            }
        }
    }

    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.v(TAG, "enabled BLUETOOTH by intent");
                this.tvBtStatus.setText(R.string.bl_on);
            }
            else {
                Log.w(TAG, "couldn't enabled BLUETOOTH by intent");
            }
        }
        else if(requestCode == REQUEST_ENABLE_LOCATION) {
            if (resultCode == RESULT_OK) {
                Log.v(TAG, "enabled LOCATION by intent");
                this.tvBtStatus.setText(R.string.bl_on);
            }
            else {
                Log.w(TAG, "couldn't LOCATION by intent");
            }

        }
    }

    private void toggleBLEScan() {
        if (!mScanning) {
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter f = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(TECO_UUIDS.ENV_SERVICE)).build();
            filters.add(f);

            adapter.clear();
            adapter.addAll(mTecoDeviceList);
            adapter.notifyDataSetChanged();

            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(mScanRunnable, SCAN_PERIOD);

            mScanning = true;
            mBluetoothLEAdapter.startScan(mLeScanCallback);

            btnBleScan.setText(R.string.ble_scan_stop);
            pbScan.setVisibility(View.VISIBLE);
            pbScan.setEnabled(true);

            Log.v(TAG, "start scanning");
        } else {
            mHandler.removeCallbacks(mScanRunnable);

            mScanning = false;
            mBluetoothLEAdapter.stopScan(mLeScanCallback);

            btnBleScan.setText(R.string.ble_scan_start);
            pbScan.setVisibility(View.INVISIBLE);
            pbScan.setEnabled(false);

            Log.v(TAG, "stop scanning");
        }
    }

    private final ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            adapter.add(device);

            String deviceName = device.getName();
            String deviceHardwareAddress = device.getAddress(); // MAC address

            Log.v(TAG, "ble scan onScanResult deviceName: " + deviceName + " MAC: " + deviceHardwareAddress);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "ble scan onScanFailed");
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.v(TAG, "ble scan onBatchScanResults");

            List<BluetoothDevice> list = new ArrayList<>(results.size());
            for (ScanResult res : results) {
                list.add(res.getDevice());
            }
            adapter.addAll(list);
        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "state " + status + " newState " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Connected to GATT server.");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvGATTStatus.setText(String.format(getString(R.string.gatt_connected), mGatt.getDevice().getName()));
                        btnBleDisconnect.setEnabled(true);
                        toggleTimer(true);
                    }
                });

                Log.i(TAG, "Attempting to start service discovery:" + gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvGATTStatus.setText(R.string.gatt_disconnected);
                        btnBleDisconnect.setEnabled(false);
                        toggleTimer(false);
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG, "GATT services discovered");
                for ( BluetoothGattService service : gatt.getServices()) {
                    Log.v(TAG, "service: " + service.getUuid());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.v(TAG, "characteristic: " + characteristic.getUuid() + " v " + characteristic.getValue() + " per " + characteristic.getPermissions() + " pro " + characteristic.getProperties() + " w " + characteristic.getWriteType());
                    }
                }

                notifyCharacteristics();
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            Log.v(TAG, "received characteristic read");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCharacteristic(characteristic);
                    }
                });

                Log.v(TAG, characteristic.toString());
            }
            else {
                Log.w(TAG, "couldn't read characteristic");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // Log.v(TAG, "onCharacteristicChanged: " + characteristic.getUuid() + " value " + characteristic.getValue());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateCharacteristic(characteristic);
                }
            });
        }
    };

    private void notifyCharacteristics() {
        // android doesn't want to write to several descriptors at once


        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.ENV_SERVICE), UUID.fromString(TECO_UUIDS.ENV_TEMPERATURE));
            }
        }, 0);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.ENV_SERVICE), UUID.fromString(TECO_UUIDS.ENV_HUMIDITY));
            }
        }, 1000);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.ENV_SERVICE), UUID.fromString(TECO_UUIDS.ENV_PRESSURE));
            }
        }, 2000);



        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Log.v(TAG, "readCharacteristic: " + mGatt.readCharacteristic(mGatt.getService(UUID.fromString(TECO_UUIDS.GAS_SERVICE)).getCharacteristic(UUID.fromString(TECO_UUIDS.GAS_CO2_CALIBRATION))));
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.GAS_SERVICE), UUID.fromString(TECO_UUIDS.GAS_CO2_CALIBRATION));
            }
        }, 3000);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Log.v(TAG, "readCharacteristic: " + mGatt.readCharacteristic(mGatt.getService(UUID.fromString(TECO_UUIDS.GAS_SERVICE)).getCharacteristic(UUID.fromString(TECO_UUIDS.GAS_NO2_CALIBRATION))));
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.GAS_SERVICE), UUID.fromString(TECO_UUIDS.GAS_NO2_CALIBRATION));
            }
        }, 4000);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Log.v(TAG, "readCharacteristic: " + mGatt.readCharacteristic(mGatt.getService(UUID.fromString(TECO_UUIDS.GAS_SERVICE)).getCharacteristic(UUID.fromString(TECO_UUIDS.GAS_NH3_CALIBRATION))));
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.GAS_SERVICE), UUID.fromString(TECO_UUIDS.GAS_NH3_CALIBRATION));
            }
        }, 5000);



        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.GAS_SERVICE), UUID.fromString(TECO_UUIDS.GAS_CO2_RAW));
            }
        }, 6000);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.GAS_SERVICE), UUID.fromString(TECO_UUIDS.GAS_NO2_RAW));
            }
        }, 7000);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notifyCharacteristic(UUID.fromString(TECO_UUIDS.GAS_SERVICE), UUID.fromString(TECO_UUIDS.GAS_NH3_RAW));
            }
        }, 8000);
    }

    private void notifyCharacteristic(UUID service, UUID charac) {
        if (mGatt == null || mBluetoothAdapter == null) {
            return;
        }

        BluetoothGattCharacteristic characteristic = mGatt.getService(service).getCharacteristic(charac);

        mGatt.setCharacteristicNotification(characteristic, true);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(TECO_UUIDS.DESCRIPTOR));
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            Log.v(TAG, "writeSdtDescriptor: " + mGatt.writeDescriptor(descriptor));
        }
    }


    private void updateCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (TECO_UUIDS.ENV_TEMPERATURE.equals(characteristic.getUuid().toString())) {
            int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            float temp = value / 100f;
            tvTemp.setText(String.format(getString(R.string.temp), temp));
            MoCIoTInfluxRestClient.currentEntry.temp = temp;
            if (cbREST.isChecked()) {
                MoCIoTInfluxRestClient.tryWrite();
            }
        }
        else if (TECO_UUIDS.ENV_HUMIDITY.equals(characteristic.getUuid().toString())) {
            int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            float humi = value / 100f;
            tvHumi.setText(String.format(getString(R.string.humi), humi));
            MoCIoTInfluxRestClient.currentEntry.humi = humi;
            if (cbREST.isChecked()) {
                MoCIoTInfluxRestClient.tryWrite();
            }
        }
        else if (TECO_UUIDS.ENV_PRESSURE.equals(characteristic.getUuid().toString())) {
            int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            float pres = value / 10f;
            tvPres.setText(String.format(getString(R.string.pres), pres));
            MoCIoTInfluxRestClient.currentEntry.pres = pres;
            if (cbREST.isChecked()) {
                MoCIoTInfluxRestClient.tryWrite();
            }
        }
        else if (TECO_UUIDS.GAS_CO2_CALIBRATION.equals(characteristic.getUuid().toString())) {
            co2Calibration = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        }
        else if (TECO_UUIDS.GAS_NO2_CALIBRATION.equals(characteristic.getUuid().toString())) {
            no2Calibration = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        }
        else if (TECO_UUIDS.GAS_NH3_CALIBRATION.equals(characteristic.getUuid().toString())) {
            nh3Calibration = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        }
        else if (TECO_UUIDS.GAS_CO2_RAW.equals(characteristic.getUuid().toString()) && co2Calibration != 0) {
            int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            double ratio = value / (double) co2Calibration;
            double co2 = Math.pow(ratio, -1.179) * 4.385;
            tvCO2.setText(String.format(getString(R.string.co2), co2));
            MoCIoTInfluxRestClient.currentEntry.co2 = (float)co2;
            if (cbREST.isChecked()) {
                MoCIoTInfluxRestClient.tryWrite();
            }
        }
        else if (TECO_UUIDS.GAS_NO2_RAW.equals(characteristic.getUuid().toString()) && no2Calibration != 0) {
            int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            double ratio = value / (double) no2Calibration;
            double no2 = Math.pow(ratio, 1.007) / 6.855;
            tvNO2.setText(String.format(getString(R.string.no2), no2));
            MoCIoTInfluxRestClient.currentEntry.no2 = (float)no2;
            if (cbREST.isChecked()) {
                MoCIoTInfluxRestClient.tryWrite();
            }
        }
        else if (TECO_UUIDS.GAS_NH3_RAW.equals(characteristic.getUuid().toString()) && nh3Calibration != 0) {
            int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            double ratio = value / (double) nh3Calibration;
            double nh3 = Math.pow(ratio, -1.67) / 1.47;
            tvNH3.setText(String.format(getString(R.string.nh3), nh3));
            MoCIoTInfluxRestClient.currentEntry.nh3 = (float)nh3;
            if (cbREST.isChecked()) {
                MoCIoTInfluxRestClient.tryWrite();
            }
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "received intent: " + intent.toString());

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                Log.v(TAG, "BluetoothDevice.ACTION_FOUND");
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.v(TAG, "found deviceName: " + deviceName + " MAC: " + deviceHardwareAddress);
            }
            else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                Log.v(TAG, "bt state changed");
            }

        }
    };
}
