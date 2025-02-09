package com.xuanyue.frp;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private FrpcManager frpcManager;
    private TextView logView;
    private Button startButton;
    private Button stopButton;
    private boolean isRunning = false;
    private EditText config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logView = findViewById(R.id.tv_log);
        startButton = findViewById(R.id.btn_start_frpc);
        stopButton = findViewById(R.id.btn_stop_frpc);
        config = findViewById(R.id.edit_config);
        
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
        //读取配置
        String configContent = readConfig();
        config.setText(configContent);

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
        startButton.setOnClickListener(this::startFrpc);
        stopButton.setOnClickListener(this::stopFrpc);
    }

    private void startFrpc(View view) {
        saveConfig(config.getText().toString());
        logView.append("正在启动frpc...\n");

        if (frpcManager.startFrpc()) {
            logView.append("frpc启动成功\n");
            updateButtons(true);
        } else {
            logView.append("frpc启动失败\n");
            updateButtons(false);
        }
    }

    private void stopFrpc(View view) {
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

    private void saveConfig(String configStr){
        FileOutputStream outputStream;

        try {
            // 打开文件输出流
            outputStream = this.openFileOutput("frpc.toml", Context.MODE_PRIVATE);
            // 写入数据
            outputStream.write(configStr.getBytes());
            // 关闭流
            outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "写入配置文件失败");
        }
    }
    public String readConfig() {
        String configName = "frpc.toml";
        String configContent = "";
        File file = new File(this.getFilesDir(), configName);
        if (file.exists()){
            try {
                FileInputStream fis = openFileInput(configName);
                InputStreamReader isr = new InputStreamReader(fis);
                char[] inputBuffer = new char[fis.available()];
                isr.read(inputBuffer);
                configContent = new String(inputBuffer);
                isr.close();
            }catch (IOException e){
                Log.e(TAG, String.valueOf(e));
            }
        }
        return configContent;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRunning) {
            frpcManager.stopFrpc();
        }
    }
} 