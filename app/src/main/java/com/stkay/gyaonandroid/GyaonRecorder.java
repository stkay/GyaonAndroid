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
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

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
class GyaonRecorder {
    private final String TAG = "Recorder";

    private MediaRecorder mediaRecorder = new MediaRecorder();

    private String path = Environment.getExternalStorageDirectory() + "/gyaon.mp4";

    private String gyaonId;

    private Context context;

    private Handler handler;

    private LocationManager locationManager;

    private Boolean isRecording = false;

    Boolean getIsRecording() {
        return isRecording;
    }

    @Nullable
    private MainActivity.GyaonListener gyaonListener;

    GyaonRecorder(Context c, @Nullable MainActivity.GyaonListener _gyaonListener) {
        context = c;
        handler = new Handler();
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        gyaonListener = _gyaonListener;
    }

    void setGyaonId(String id) {
        this.gyaonId = id;
    }

    void start() {
        isRecording = true;
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
            isRecording = false;
            e.printStackTrace();
        }
        Toast.makeText(context, "Start Recording", Toast.LENGTH_SHORT).show();
    }

    void stop() {
        isRecording = false;
        Log.d(TAG, "rec stop");
        handler.postDelayed(stop, 200);
    }

    private void upload(@Nullable Location location) {
        Log.d(TAG, "Start uploading");
        OkHttpClient client = new OkHttpClient();
        Double lat = 0d;
        Double lon = 0d;

        if (location != null) {
            lat = location.getLatitude();
            lon = location.getLongitude();
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "gyaon.mp4", RequestBody.create(MediaType.parse("audio/aac"), new File(path)))
                .addFormDataPart("lat", lat + "")
                .addFormDataPart("lon", lon + "")
                .build();

        Request request = new Request.Builder()
                .url("https://gyaon.herokuapp.com/upload/" + gyaonId)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "Failed to upload to Gyaon");
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                JSONObject res;
                String key = null;
                try {
                    res = new JSONObject(response.body().string());
                    key = res.getJSONObject("object").get("key").toString();
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (key != null) {
                    Log.d(TAG, "gyaon : " + key);
                    gyaonListener.onUpload(key);
                }
            }
        });
    }

    private Runnable stop = new Runnable() {
        @Override
        public void run() throws SecurityException {
            mediaRecorder.stop();
            mediaRecorder.reset();
            Toast.makeText(context, "Stop Recording", Toast.LENGTH_SHORT).show();

            //既存のHandlerを使うという選択肢はないのか？
            //録音停止毎にhandlerを生成するのはいかがなものか？

            final LocationListener listener = new LocationListener(){
                @Override
                public void onLocationChanged(Location location) {
                    Log.d(TAG, "onLocationChanged");
                    upload(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                    Log.d(TAG, "onLocationProviderDisabled");
                    upload(null);
                }
            };

            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            locationManager.requestSingleUpdate(criteria, listener, null);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "onLocationTimeout");
                    locationManager.removeUpdates(listener);
                    upload(null);
                }
            }, 3000);

        }
    };
}
