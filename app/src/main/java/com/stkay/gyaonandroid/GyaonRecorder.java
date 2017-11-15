package com.stkay.gyaonandroid;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by satake on 16/06/16.
 */
public class GyaonRecorder {
    private final String TAG = "Recorder";

    private MediaRecorder mediaRecorder = new MediaRecorder();

    private String path = Environment.getExternalStorageDirectory() + "/gyaon.mp4";

    private String gyaonId;

    private Context context;

    private Handler handler;

    private LocationManager locationManager;

    GyaonRecorder(Context c) {
        context = c;
        handler = new Handler();
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    void setGyaonId(String id) {
        this.gyaonId = id;
    }

    void start() {
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioEncodingBitRate(128000);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(path);
        try {
            Log.d(TAG, "rec start");
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            Log.d(TAG, "rec failed");
            e.printStackTrace();
        }
        Toast.makeText(context, "Start Recording", Toast.LENGTH_SHORT).show();
    }

    void stop() {
        Log.d(TAG, "rec stop");
        handler.postDelayed(stop, 200);
    }

    private void upload(Location location) {
        Log.d(TAG, "upload start");
        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "gyaon.mp4", RequestBody.create(MediaType.parse("audio/aac"), new File(path)))
                .addFormDataPart("lat", location.getLatitude() + "")
                .addFormDataPart("lon", location.getLongitude() + "")
                .build();

        Request request = new Request.Builder()
                .url("https://gyaon.herokuapp.com/upload/" + gyaonId)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "upload failed");
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, response.body().string());
            }
        });
    }

    private Runnable stop = new Runnable() {
        @Override
        public void run() throws SecurityException{
            mediaRecorder.stop();
            mediaRecorder.reset();
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            locationManager.requestSingleUpdate(criteria, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    upload(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {}
            }, null);
            Toast.makeText(context, "Stop Recording", Toast.LENGTH_SHORT).show();
        }
    };
}
