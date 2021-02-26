package com.example.gpc1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;


import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

public class IntentServicePerekamanData extends IntentService implements SensorEventListener {
    NotificationManager notificationManager;
    NotificationGPC notificationGPC;
    private final int NOTIFICATION_ID = 0;
    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-M-dd HH:mm:ss");

    private SensorManager sensorManager;
    private Sensor mSuhuUdara;
    private Sensor mKelembabanUdara;
    private Sensor mTekananUdara;
    private Sensor mSuhuCPU;


    Calendar calendar;
    String timeStamp; //Sudah
    private double longitude; //Sudah
    private double latitude; //Sudah
    private double altitude; //Sudah
    private double altitude1; //Sudah
    private float suhuBaterai; //sudah
    private float suhuUdara = 0; //sudah
    private float tekananUdara = 0; //sudah
    private float kelembabanUdara = 0; //sudah
    private float suhuCPU; //sudah
    private boolean dikirim; //sudah
    private boolean statusLayar; //sudah, true jika nyala, false jika mati
    private boolean statusBaterai; //Sudah, Jika false Tidak di dcharge, jika true dicharge

    private final String NOTIFICATION_CHANNEL_ID = "gpcNotification";

    DataModel dataRekaman = new DataModel();
    DatabaseHelper databaseHelper = new DatabaseHelper(this);

    public IntentServicePerekamanData (){
        super("IntenServicePerekamanData");
    }

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        super.onStart(intent, startId);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationGPC = new NotificationGPC(this, notificationManager);
        startForeground(1, notificationGPC.notification(this, "Memulai Perekaman Data"));
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String contextNotification;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSuhuUdara = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        mSuhuCPU = sensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE);
        mKelembabanUdara  = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        mTekananUdara = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if(mTekananUdara != null){
            sensorManager.registerListener(this, mTekananUdara, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(mSuhuUdara != null){
            sensorManager.registerListener(this, mSuhuUdara, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(mKelembabanUdara != null){
            sensorManager.registerListener(this, mKelembabanUdara, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(mSuhuCPU != null){
            sensorManager.registerListener(this, mSuhuCPU, SensorManager.SENSOR_DELAY_NORMAL );
        }
        Calendar calendar = Calendar.getInstance();
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        timeStamp = simpleDateFormat.format(calendar.getTime());
        System.out.println("Calendar = " + timeStamp);
        dataRekaman.setTimeStamp(timeStamp);
        suhuBaterai = readBatteryTemp(this);

        FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String bestProvider = String.valueOf(locationManager.getBestProvider(criteria, true)).toString();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            notificationGPC.deliverNotification("Akses Lokasi Diperlukan Pada Setting Aplikasi");
        }

        else{
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @SuppressLint("MissingPermission")
                @Override
                public void onSuccess(Location location) {
                    if(location == null){
                        longitude = location.getLongitude();
                        latitude = location.getLatitude();
                        altitude = location.getAltitude();
                        System.out.println("Perekaman Data = " + longitude + " And "  + latitude);
                        System.out.println("Ketinggian Altitude = "+ altitude);
                    }
                    else {
                        locationManager.requestLocationUpdates(bestProvider, 1000, 0, new LocationListener() {
                            @Override
                            public void onLocationChanged(@NonNull Location location1) {
                                locationManager.removeUpdates(this);
                                latitude = location1.getLatitude();
                                longitude = location1.getLongitude();
                                altitude = location1.getAltitude();
                            }
                        });
                    }
                }
            });
        }
        try {
            System.out.println("Tidur 7 Detik");
            Thread.sleep(7 * 1000);
            System.out.println("LocationManager = " + latitude + ", " + longitude + ", "+ altitude);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        RequestQueue requestQueue = Volley.newRequestQueue(IntentServicePerekamanData.this);
        String url = "https://api.opentopodata.org/v1/srtm30m?locations=" + latitude + "," + longitude + "&interpolation=cubic";
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url,null
                , new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    JSONArray results = response.getJSONArray("results");
                    JSONObject jsonObject = results.getJSONObject(0);
                    altitude1 = jsonObject.getDouble("elevation");
                    System.out.println("altitudeAPI = "+ altitude1);
                    dataRekaman.setAltitude1(altitude1);
                    boolean b = databaseHelper.addData(dataRekaman);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
                requestQueue.stop();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                dataRekaman.setAltitude1(0);
                System.out.println("Error API");
                boolean b = databaseHelper.addData(dataRekaman);
                requestQueue.stop();
            }
        });
        requestQueue.add(request);
        dataRekaman.setLongitude(longitude);
        dataRekaman.setLatitude(latitude);
        dataRekaman.setAltitude(altitude);
        dataRekaman.setSuhuBaterai(suhuBaterai);
        dikirim = false;
        dataRekaman.setDikirim(dikirim);
        dataRekaman.setStatusLayar(statusLayar());
        dataRekaman.setStatusBaterai(statusBaterai(intent));
        if(mSuhuCPU == null){
            dataRekaman.setCpuTemperatur((float)getCurrentCPUTemperature());
        }
        dataRekaman.setLongitude(longitude);
        dataRekaman.setLatitude(latitude);
        dataRekaman.setAltitude(altitude);
        dataRekaman.setSuhuBaterai(suhuBaterai);
        dikirim = false;
        dataRekaman.setDikirim(dikirim);
        dataRekaman.setStatusLayar(statusLayar());
        dataRekaman.setStatusBaterai(statusBaterai(intent));
        if(mSuhuCPU == null){
            dataRekaman.setCpuTemperatur((float)getCurrentCPUTemperature());
        }
        notificationGPC.deliverNotification("Perekaman Data Berhasil. Terima Kasih :D ");
        System.out.println(dataRekaman.toString());
    }

    private boolean statusBaterai(Intent intent) {
        int baterai = intent.getIntExtra("statusBaterai", -1);
        if (baterai == 0){
            statusBaterai = false;
        }
        else{
            statusBaterai = true;
        }
        return statusBaterai;
    }

    @SuppressLint("ObsoleteSdkInt")
    private boolean statusLayar() {
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH){
            statusLayar = pm.isInteractive();
        }
        else{
            statusLayar = pm.isScreenOn();
        }
        return statusLayar;
    }

    private float readBatteryTemp (Context context){
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        float temp = ((float) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0))/10;
        return  temp;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int sensorType = event.sensor.getType();
        float currentValue = event.values[0];
        switch (sensorType){
            case Sensor.TYPE_AMBIENT_TEMPERATURE :
                suhuUdara = currentValue;
                dataRekaman.setSuhuUdara(suhuUdara);
                System.out.println("Sensor Suhu Udara = " + suhuUdara);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                kelembabanUdara = currentValue;
                dataRekaman.setKelembabanUdara(kelembabanUdara);
                System.out.println("Sensor Kelembaban = " + suhuUdara);
                break;
            case Sensor.TYPE_PRESSURE:
                tekananUdara = currentValue;
                dataRekaman.setTekananUdara(tekananUdara);
                System.out.println("Sensor Tekanan udara = " + suhuUdara);
                break;
            case Sensor.TYPE_TEMPERATURE:
                suhuCPU = currentValue;
                dataRekaman.setCpuTemperatur(suhuCPU);
                System.out.println("Sensor SuhuCPU = " + suhuUdara);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    private double getCurrentCPUTemperature() {
        String [] dirs = {"sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
                "sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
                "sys/class/thermal/thermal_zone1/temp",
                "sys/class/thermal/thermal_zone0/temp",
                "sys/class/thermal/thermal_zone2/temp",
                "sys/class/thermal/thermal_zone3/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp",
                "sys/class/i2c-adapter/i2c-4/4-004c/temperature",
                "sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature",
                "sys/devices/platform/omap/omap_temp_sensor.0/temperature",
                "sys/devices/platform/tegra_tmon/temp1_input",
                "sys/kernel/debug/tegra_thermal/temp_tj",
                "sys/devices/platform/s5p-tmu/temperature",
                "sys/devices/virtual/thermal/thermal_zone0/temp",
                "sys/devices/virtual/thermal/thermal_zone1/temp",
                "sys/devices/virtual/thermal/thermal_zone2/temp",
                "sys/devices/virtual/thermal/thermal_zone3/temp",
                "sys/class/hwmon/hwmon0/device/temp1_input",
                "sys/class/hwmon/hwmonX/temp1_input",
                "sys/devices/platform/s5p-tmu/curr_temp",
                "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
        };
        ArrayList<Double> suhu = new ArrayList<>();
        Process process;
        String line;
        BufferedReader reader;
        RandomAccessFile reader1;
        for (String dir : dirs) {
            try {
                Double val = OneLineReader.getValue(dir);
                suhu.add(val);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        double temp = 0.0;
        for (int i = 0; i < suhu.size() ; i++){
            double suhuAnggota = suhu.get(i);
            if (suhuAnggota > 10000){
                suhuAnggota = suhuAnggota/1000;
            }
            if (suhuAnggota > 100){
                suhuAnggota = suhuAnggota/100;
            }
            temp = temp + suhuAnggota;
        }
        temp = temp / suhu.size();
        System.out.println("Suhu = " + suhu);
        return temp;
    }
}
