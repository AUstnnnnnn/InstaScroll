package com.instascroll;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        Button btn = findViewById(R.id.btn_enable);

        btn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ScrollService.isRunning) {
            status.setText("Service is ACTIVE");
            status.setTextColor(0xFF55FF55);
        } else {
            status.setText("Service not enabled");
            status.setTextColor(0xFFFF5555);
        }
    }
}
