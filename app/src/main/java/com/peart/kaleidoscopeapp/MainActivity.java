package com.peart.kaleidoscopeapp;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.health.SystemHealthManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import top.defaults.colorpicker.ColorPickerPopup;

public class MainActivity extends AppCompatActivity {

    // the maximum rate of posting to the api
    private static final long PUT_RATE = 250; //every n ms

    private static int speed = 0;
    private static int brightness = 0;
    private static int clockColor = 0;
    private static String mode = "";
    private static String clockFace = "";
    private static String drawStyle = "";

    boolean canFetchSettings = false;

    private long timeOfLastPut = 0;
    private View decorView; // for removing action/navigation bar
    private String previousMode = "Kaleidoscope";
    private boolean settingUp = true;

    SeekBar speedSeekBar, brightnessSeekBar;
    FloatingActionButton powerButton;
    Spinner clockFacesSpinner, modeNamesSpinner, drawStylesSpinner;
    List<String> clockFacesList, modeNamesList, drawStylesList;
    ArrayAdapter<String> clockAdapter, modeAdapter, drawStylesAdapter;
    ImageButton colorWheelButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speedSeekBar = findViewById(R.id.speed_seekbar);
        brightnessSeekBar = findViewById(R.id.brightness_seekbar);
        powerButton = findViewById(R.id.powerButton);
        clockFacesSpinner = findViewById(R.id.clockFacesSpinner);
        modeNamesSpinner = findViewById(R.id.modeNamesSpinner);
        drawStylesSpinner = findViewById(R.id.drawStylesSpinner);
        colorWheelButton = findViewById(R.id.colorWheelButton);

        clockFacesList = new ArrayList<>();
        modeNamesList = new ArrayList<>();
        drawStylesList = new ArrayList<>();

        clockAdapter = new ArrayAdapter<>(this, R.layout.spinner_row, clockFacesList);
        modeAdapter = new ArrayAdapter<>(this, R.layout.spinner_row, modeNamesList);
        drawStylesAdapter = new ArrayAdapter<>(this, R.layout.spinner_row, drawStylesList);

        clockFacesSpinner.setAdapter(clockAdapter);
        modeNamesSpinner.setAdapter(modeAdapter);
        drawStylesSpinner.setAdapter(drawStylesAdapter);

        modeNamesList.add("Select Mode");
        clockFacesList.add("Select Clock");
        drawStylesList.add("Select Draw Style");

        clockAdapter.notifyDataSetChanged();
        modeAdapter.notifyDataSetChanged();
        drawStylesAdapter.notifyDataSetChanged();

        fetchDrawStyles();
        fetchClockFaces();
        fetchModeNames();
        fetchSettings();

        // forces the app to stay in portrait mode
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        /**
        // used to get rid of bars at the top of the application
        decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if (visibility == 0)
                decorView.setSystemUiVisibility(hideSystemBars());
        });
        **/
        // turns the kaleidoscope on and off
        powerButton.setOnClickListener(v -> {
            if (!mode.equals("off")) { // if it's on turn it off
                modeNamesSpinner.setSelection(modeNamesList.indexOf("Select Mode"));
                previousMode = mode;
                MainActivity.mode = "off";
                powerButton.getBackground().mutate().setTint(ContextCompat.getColor(getApplicationContext(), R.color.powerButtonRed));
            } else { // if it's off turn it on
                modeNamesSpinner.setSelection(modeNamesList.indexOf(previousMode));
                mode = previousMode;
                powerButton.getBackground().mutate().setTint(ContextCompat.getColor(getApplicationContext(), R.color.powerButtonBlue));
            }
            makePost("on create");
        });

        colorWheelButton.setOnClickListener(v -> new ColorPickerPopup.Builder(MainActivity.this)
                .initialColor(clockColor) // Set initial color
                .enableBrightness(true) // Enable brightness slider or not
                .enableAlpha(false) // Enable alpha slider or not
                .okTitle("Choose")
                .cancelTitle("Cancel")
                .showIndicator(true)
                .showValue(false)
                .build()
                .show(v, new ColorPickerPopup.ColorPickerObserver() {
                    @Override
                    public void onColorPicked(int color) {
                        String temp = "#" + Integer.toHexString(color);
                        clockColor = getRgbFromHex(temp);
                        makePost("color wheel selection");
                    }
                }));


        // changes the speed of the kaleidoscope
        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean wasItUs = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (wasItUs) {
                    MainActivity.speed = getSpeedFromProgress(progress);
                    makePost("progress changed speed");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                wasItUs = true;
                canFetchSettings = false;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                makePost("stop tracking touch speed");
                canFetchSettings = true;
                wasItUs = false;
            }
        });

        // changes the brightness of the kaleidoscope
        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean wasItUs = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (wasItUs) {
                    MainActivity.brightness = getBrightnessFromProgress(progress);
                    makePost("progress changed brightness");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                wasItUs = true;
                canFetchSettings = false;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                makePost("stop tracking touch brightness");
                canFetchSettings = true;
                wasItUs = false;
            }
        });

        // changes the mode of the kaleidoscope
        modeNamesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!parent.getItemAtPosition(position).equals("Select Mode") && !settingUp) {
                    MainActivity.mode = modeNamesSpinner.getSelectedItem().toString();
                    makePost("mode names spinner selection"); // getting called on create
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // changes the clock of the kaleidoscope
        clockFacesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!parent.getItemAtPosition(position).equals("Select Clock") && !settingUp) {
                    MainActivity.clockFace = clockFacesSpinner.getSelectedItem().toString();
                    makePost("clock face spinner selection");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        drawStylesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!parent.getItemAtPosition(position).equals("Select Draw Style") && !settingUp) {
                    MainActivity.drawStyle = drawStylesSpinner.getSelectedItem().toString();
                    makePost("draw styles spinner selection"); // getting called on create
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        final Handler handler = new Handler();
        final int refreshRate = 2000; // 2.0 sec

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (canFetchSettings)
                    fetchSettings();
                handler.postDelayed(this, refreshRate);
            }
        }, refreshRate);

    }


    @Override
    protected void onStart() {
        super.onStart();
        fetchSettings();
    }

    //posts the brightness, speed, mode and clock to the api
    private void makePost(String s) {
        System.out.println(s);
        Thread thread = new Thread(() -> {
            if (System.currentTimeMillis() - timeOfLastPut > PUT_RATE) {
                timeOfLastPut = System.currentTimeMillis();
                try {
                    URL url = new URL("http://kaleidoscope/api/settings");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "*/*");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    JSONObject jsonParam = new JSONObject();
                    jsonParam.put("brightness", MainActivity.brightness);
                    jsonParam.put("speed", MainActivity.speed);
                    jsonParam.put("mode", MainActivity.mode);
                    jsonParam.put("clockFace", MainActivity.clockFace);
                    jsonParam.put("drawStyle", MainActivity.drawStyle);
                    jsonParam.put("handsColor", MainActivity.clockColor);

                    Log.i("JSON", jsonParam.toString());
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(jsonParam.toString());

                    os.flush();
                    os.close();

                    Log.i("STATUS", String.valueOf(conn.getResponseCode()));
                    Log.i("MSG", conn.getResponseMessage());

                    conn.disconnect();

                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    // gets the list of clock faces from the api and adds it to spinner
    private void fetchClockFaces() {

        String url = "http://kaleidoscope/api/faces";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                String mMessage = e.getMessage();
                Log.w("failure Response", mMessage);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {

                final String mMessage = response.body().string();

                MainActivity.this.runOnUiThread(() -> {
                    Log.e("Response", mMessage);
                    try {
                        JSONArray faces = new JSONArray(mMessage);

                        for (int i = 0; i < faces.length(); i++) {
                            String face = faces.getString(i);
                            Log.e("clockFace", face);
                            clockFacesList.add(face);
                        }
                        clockAdapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    // gets the list of mode names from the api and adds it to spinner
    private void fetchModeNames() {
        String url = "http://kaleidoscope/api/modes";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                String mMessage = e.getMessage();
                Log.w("failure Response", mMessage);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {

                final String mMessage = response.body().string();

                MainActivity.this.runOnUiThread(() -> {
                    Log.e("Response", mMessage);
                    try {
                        JSONArray modes = new JSONArray(mMessage);

                        for (int i = 0; i < modes.length(); i++) {
                            String mode = modes.getString(i);
                            Log.e("clockFace", mode);
                            modeNamesList.add(mode);
                        }
                        modeAdapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    // gets the list of draw types
    private void fetchDrawStyles() {
        String url = "http://kaleidoscope/api/drawstyles";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                String mMessage = e.getMessage();
                Log.w("failure Response", mMessage);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {

                final String mMessage = response.body().string();

                MainActivity.this.runOnUiThread(() -> {
                    Log.e("Response", mMessage);
                    try {
                        JSONArray modes = new JSONArray(mMessage);

                        for (int i = 0; i < modes.length(); i++) {
                            String style = modes.getString(i);
                            Log.e("drawStyle", style);
                            drawStylesList.add(style);
                        }
                        drawStylesAdapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    // gets the current list of settings and changes our values
    private void fetchSettings() {
        String url = "http://kaleidoscope/api/settings";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                String mMessage = e.getMessage();
                Log.w("failure Response", mMessage);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {

                final String mMessage = response.body().string();

                MainActivity.this.runOnUiThread(() -> {
                    Log.e("Response", mMessage);
                    try {
                        JSONObject settings = new JSONObject(mMessage);
                        brightness = (int) settings.get("brightness");
                        speed = (int) settings.get("speed");
                        mode = (String) settings.get("mode");
                        clockFace = (String) settings.get("clockFace");
                        drawStyle = (String) settings.get("drawStyle");
                        clockColor = (int) settings.get("handsColor");
                        setValuesFromSettings();
                        settingUp = false;
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    private void setValuesFromSettings() {
        brightnessSeekBar.setProgress(getProgressFromBrightness(brightness));
        speedSeekBar.setProgress(getProgressFromSpeed(speed));
        modeNamesSpinner.setSelection(modeNamesList.indexOf(mode));
        clockFacesSpinner.setSelection(clockFacesList.indexOf(clockFace));
        drawStylesSpinner.setSelection(drawStylesList.indexOf(drawStyle));
        if (mode.equals("off"))
            powerButton.getBackground().mutate().setTint(ContextCompat.getColor(getApplicationContext(), R.color.powerButtonRed));
        else
            powerButton.getBackground().mutate().setTint(ContextCompat.getColor(getApplicationContext(), R.color.powerButtonBlue));
    }

    private int getProgressFromSpeed(int speed) {
        //int p = 100 - (int) (100 * (Math.exp(( (double) speed / 1000) * Math.log(2)) - 1));
        //return Math.max(p, 0);
        return speed * 100 / 255;
    }

    private int getProgressFromBrightness(int brightness) {
        return (brightness + 305) / 44;
    }

    // returns the speed value to send to the api from the % of progress on seekbar
    private int getSpeedFromProgress(int progress) {
        //return 1000 - (int) (1000 * Math.log1p(((double) progress / 100)) / Math.log(2));
        return progress * 255 / 100;
    }

    // returns the brightness value to send to the api from the % of progress on seekbar
    private int getBrightnessFromProgress(int progress) {
        return (progress * 44) - 305;
    }
/**
    // used to get rid of bars at the top of the application
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            decorView.setSystemUiVisibility(hideSystemBars());
        }
    }

    // used to get rid of bars at the top of the application
    private int hideSystemBars() {
        return View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    }
**/
    private int getRgbFromHex(String hex) { //#ffffff red green blue 0-255  ->255, 0, 0 255 255 255
        int initColor = Color.parseColor(hex);
        int r = Color.red(initColor);
        int g = Color.green(initColor);
        int b = Color.blue(initColor);
        return r << 16 | g << 8 | b;
    }
}