package com.xuanyue.frp;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class FrpcManager {
    private static final String TAG = "FrpcManager";
    private Context context;
    private String frpcPath;
    private Process process;
    private LogCallback logCallback;
    private Thread outputThread;
    private Thread errorThread;

    public interface LogCallback {
        void onLog(String log);
        void onError(String error);
    }

    public FrpcManager(Context context) {
        this.context = context;
        // 使用应用私有目录
        this.frpcPath = context.getFilesDir() + "/frpc";
        Log.i(TAG, "应用目录: " + context.getApplicationInfo().dataDir);
        Log.i(TAG, "frpc路径: " + frpcPath);
    }

    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }

    public boolean initializeFrpc() {
        Log.i(TAG, "开始初始化frpc...");
        try {
            File frpcFile = new File(frpcPath);
            
            // 检查文件是否已存在
            if (frpcFile.exists()) {
                Log.i(TAG, "frpc文件已存在，检查执行权限");
                if (!frpcFile.canExecute()) {
                    Log.w(TAG, "frpc文件没有执行权限，尝试添加执行权限");
                    frpcFile.setExecutable(true, true);
                }
                return true;
            }

            Log.i(TAG, "从assets复制frpc文件到: " + frpcPath);
            
            // 从assets复制frpc文件
            InputStream inputStream = context.getAssets().open("frpc");
            FileOutputStream outputStream = new FileOutputStream(frpcPath);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();

            // 设置可执行权限
            boolean executableSet = frpcFile.setExecutable(true, true);
            Log.i(TAG, "设置可执行权限: " + (executableSet ? "成功" : "失败"));
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "初始化frpc失败: " + e.getMessage(), e);
            if (logCallback != null) {
                logCallback.onError("初始化frpc失败: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean startFrpc(String configPath) {
        Log.i(TAG, "开始启动frpc，配置文件路径: " + configPath);
        try {
            // 检查frpc文件是否存在
            File frpcFile = new File(frpcPath);
            if (!frpcFile.exists()) {
                String error = "frpc文件不存在: " + frpcPath;
                Log.e(TAG, error);
                if (logCallback != null) {
                    logCallback.onError(error);
                }
                return false;
            }
            
            // 检查配置文件
            File configFile = new File(configPath);
            if (!configFile.exists()) {
                String error = "配置文件不存在: " + configPath;
                Log.e(TAG, error);
                if (logCallback != null) {
                    logCallback.onError(error);
                }
                return false;
            }

            // 设置执行权限
            try {
                Process chmodProcess = Runtime.getRuntime().exec("chmod 700 " + frpcPath);
                int exitCode = chmodProcess.waitFor();
                Log.i(TAG, "chmod执行结果: " + exitCode);
            } catch (Exception e) {
                Log.e(TAG, "设置执行权限失败: " + e.getMessage(), e);
            }

            // 构建命令数组
            String[] cmd = {
                frpcPath,
                "-c",
                configPath
            };
            
            Log.i(TAG, "执行命令: " + String.join(" ", cmd));
            
            // 使用Runtime.exec启动进程
            process = Runtime.getRuntime().exec(cmd);
            
            // 先读取一些初始输出
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()));
            BufferedReader outputReader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            
            // 检查是否有立即的错误输出
            if (errorReader.ready()) {
                String initialError = errorReader.readLine();
                Log.e(TAG, "frpc初始错误输出: " + initialError);
            }
            
            // 检查是否有立即的标准输出
            if (outputReader.ready()) {
                String initialOutput = outputReader.readLine();
                Log.i(TAG, "frpc初始输出: " + initialOutput);
            }
            
            // 检查进程是否立即退出
            try {
                Thread.sleep(100); // 等待100ms
                int exitCode = process.exitValue(); // 如果进程还在运行，这里会抛出异常
                // 如果能执行到这里，说明进程已经退出
                String error = "frpc进程立即退出，退出码: " + exitCode;
                Log.e(TAG, error);
                if (logCallback != null) {
                    logCallback.onError(error);
                }
                return false;
            } catch (IllegalThreadStateException e) {
                // 进程还在运行，这是我们想要的
                Log.i(TAG, "frpc进程正在运行");
            }
            
            // 开启标准输出流读取线程
            outputThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        final String log = line;
                        Log.i(TAG, log);
                        if (logCallback != null) {
                            logCallback.onLog(log);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            outputThread.start();

            // 开启错误输出流读取线程
            errorThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        final String error = line;
                        Log.e(TAG, error);
                        if (logCallback != null) {
                            logCallback.onError(error);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            errorThread.start();
            
            return true;
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "启动frpc失败: " + e.getMessage(), e);
            if (logCallback != null) {
                logCallback.onError("启动frpc失败: " + e.getMessage());
            }
            return false;
        }
    }

    public void stopFrpc() {
        if (process != null) {
            process.destroy();
            process = null;
        }
        
        // 停止日志读取线程
        if (outputThread != null) {
            outputThread.interrupt();
            outputThread = null;
        }
        if (errorThread != null) {
            errorThread.interrupt();
            errorThread = null;
        }
    }
} 