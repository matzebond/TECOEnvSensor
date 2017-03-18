package edu.teco.maschm.tecoenvsensor;

import android.nfc.Tag;
import android.os.AsyncTask;
import android.util.Log;

import java.io.Console;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MoCIoTInfluxRestClient {
    public static class Entry {
        public float temp = Float.NaN;
        public float humi = Float.NaN;
        public float pres = Float.NaN;
        public float co2 = Float.NaN;
        public float no2 = Float.NaN;
        public float nh3 = Float.NaN;

        public boolean isComplete() {
            return ! ( Float.isNaN(temp) || Float.isNaN(humi) || Float.isNaN(pres) || Float.isNaN(co2) || Float.isNaN(no2) || Float.isNaN(nh3) );
        }
    }

    private static final String TAG = "RestClient";

    private static final String BASE_URL = "http://mociotdb2.teco.edu/db.php";

    private static final String MY_DB = "maschmdb";
    private static final String MY_PW = "pwoert321";
    private static final String MY_TS = "mytimeseries";

    public static Entry currentEntry = new Entry();

    public static OkHttpClient client = new OkHttpClient();

    public static MediaType defaultType = MediaType.parse("text/plain; charset=utf-8");

    public static void createDB() {
        String url = getAbsoluteUrl("/createDB?dbName="+MY_DB+"&dbKey="+ MY_PW);

        final Request request = new Request.Builder().url(url).build();

        Log.v(TAG, "try create DB");

        AsyncTask<String, Void, String> task = new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                try {
                    Log.v(TAG, "execute create DB");

                    Response response = client.newCall(request).execute();
                    Log.v(TAG, "createDB response: " + response.body().string());
                }
                catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                return "";
            }
        };
        task.execute();

    }

    public static boolean tryWrite() {
        /*


        if (!currentEntry.isComplete()) {
            return false;
        }

         */

        boolean first = true;

        StringBuilder builder = new StringBuilder(MY_TS).append(' ');

        if (!Float.isNaN(currentEntry.temp)) {
            if (!first) {
                builder.append(',');
            } else {
                first = false;
            }
            builder.append("temp=").append(currentEntry.temp);
        }
        if (!Float.isNaN(currentEntry.humi)) {
            if (!first) {
                builder.append(',');
            } else {
                first = false;
            }
            builder.append("humi=").append(currentEntry.humi);
        }
        if (!Float.isNaN(currentEntry.pres)) {
            if (!first) {
                builder.append(',');
            } else {
                first = false;
            }
            builder.append("pres=").append(currentEntry.pres);
        }
        if (!Float.isNaN(currentEntry.co2)) {
            if (!first) {
                builder.append(',');
            } else {
                first = false;
            }
            builder.append("co2=").append(currentEntry.co2);
        }
        if (!Float.isNaN(currentEntry.no2)) {
            if (!first) {
                builder.append(',');
            } else {
                first = false;
            }
            builder.append("no2=").append(currentEntry.no2);
        }
        if (!Float.isNaN(currentEntry.nh3)) {
            if (!first) {
                builder.append(',');
            } else {
                first = false;
            }
            builder.append("nh3=").append(currentEntry.nh3);
        }

        Log.v(TAG, builder.toString());


        Log.v(TAG, "building post request");

        currentEntry = new Entry();

        RequestBody body = RequestBody.create(defaultType, builder.toString());

        String url = getAbsoluteUrl("/write/" + MY_DB + "?dbKey=" + MY_PW);

        final Request request = new Request.Builder().url(url).post(body).build();

        AsyncTask<String, Void, String> task = new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                try {
                    Log.v(TAG, "execute write DB");

                    Response response = client.newCall(request).execute();
                    Log.v(TAG, "write DB response: " + response.body().string());
                }
                catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                return "";
            }
        };
        task.execute();
        return true;
    }

    private static String getAbsoluteUrl(String relativeUrl) {
        return BASE_URL + relativeUrl;
    }
}
