package edu.teco.maschm.tecoenvsensor;

/**
 * Created by maschm on 3/14/17.
 */

public class TECO_UUIDS {

    public static final String TECO_OG1 = "CB:0D:8D:AD:EF:3E";

    public static final String ENV_SERVICE =      "0000181a-0000-1000-8000-00805f9b34fb";


    // int16_t, Temperature in 0.01°C
    public static final String ENV_TEMPERATURE = "00002a6e-0000-1000-8000-00805f9b34fb";
    // int16_t, Humidity in 0.01%
    public static final String ENV_HUMIDITY =    "00002a6f-0000-1000-8000-00805f9b34fb";
    // uint32_t, Pressure in 0.1Pa
    public static final String ENV_PRESSURE =    "00002a6d-0000-1000-8000-00805f9b34fb";



    public static final String GAS_SERVICE =  "4b822f90-3941-4a4b-a3cc-b2602ffe0d00";

    // uint16_t, Raw A/D concentration value; see datasheet for conversion formula
    public static final String GAS_CO2_RAW =   "4b822fa1-3941-4a4b-a3cc-b2602ffe0d00";
    public static final String GAS_NO2_RAW = "4b822f91-3941-4a4b-a3cc-b2602ffe0d00";
    public static final String GAS_NH3_RAW = "4b822fb1-3941-4a4b-a3cc-b2602ffe0d00";

    // uint16_t, Device specific calibration value
    public static final String GAS_CO2_CALIBRATION =   "4b822fa2-3941-4a4b-a3cc-b2602ffe0d00";
    public static final String GAS_NO2_CALIBRATION = "4b822f92-3941-4a4b-a3cc-b2602ffe0d00";
    public static final String GAS_NH3_CALIBRATION = "4b822fb2-3941-4a4b-a3cc-b2602ffe0d00";


    public static final String DUST_SERVICE = "4b822fe0-3941-4a4b-a3cc-b2602ffe0d00";

    // uint16_t, Raw A/D values from dust sensor
    public static final String DUST_RAW =     "4b822fe1-3941-4a4b-a3cc-b2602ffe0d00";



    public static final String DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";


}
