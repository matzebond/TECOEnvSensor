package edu.teco.maschm.tecoenvsensor;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class BleDeviceListAdapter extends ArrayAdapter<BluetoothDevice> {

    public BleDeviceListAdapter(Context context) {
        super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        BluetoothDevice device = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.ble_device_item_list, parent, false);
        }
        // Lookup view for data population
        TextView name = (TextView) convertView.findViewById(R.id.tv_name);
        TextView addr = (TextView) convertView.findViewById(R.id.tv_addr);
        // Populate the data into the template view using the data object
        name.setText(device.getName());
        addr.setText(device.getAddress());
        // Return the completed view to render on screen
        return convertView;
    }

}
