package com.xuanyue.frp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private FrpcManager frpcManager;
    private TextView logView;
    private Button startButton;
    private Button stopButton;
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logView = findViewById(R.id.logView);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        
        frpcManager = new FrpcManager(this);
        frpcManager.setLogCallback(new FrpcManager.LogCallback() {
            @Override
            public void onLog(String log) {
                runOnUiThread(() -> {
                    logView.append(log + "\n");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    logView.append("错误: " + error + "\n");
                });
            }
        });
        
        // 初始化frpc
        if (frpcManager.initializeFrpc()) {
            logView.append("frpc初始化成功\n");
            updateButtons(false);
        } else {
            logView.append("frpc初始化失败\n");
            startButton.setEnabled(false);
            stopButton.setEnabled(false);
        }

        // 设置按钮点击事件
        startButton.setOnClickListener(v -> startFrpc());
        stopButton.setOnClickListener(v -> stopFrpc());
    }

    private void startFrpc() {
        logView.append("正在启动frpc...\n");
        String configPath = getFilesDir() + "/frpc.ini";
        if (frpcManager.startFrpc(configPath)) {
            logView.append("frpc启动成功\n");
            updateButtons(true);
        } else {
            logView.append("frpc启动失败\n");
            updateButtons(false);
        }
    }

    private void stopFrpc() {
        logView.append("正在停止frpc...\n");
        frpcManager.stopFrpc();
        logView.append("frpc已停止\n");
        updateButtons(false);
    }

    private void updateButtons(boolean isRunning) {
        this.isRunning = isRunning;
        startButton.setEnabled(!isRunning);
        stopButton.setEnabled(isRunning);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRunning) {
            frpcManager.stopFrpc();
        }
    }
} 