package com.example.vcam;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.VideoView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.view.Window;
import android.widget.Toast;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView;
import android.graphics.Typeface;
import java.util.HashMap;
import java.util.ArrayList;

import com.topjohnwu.superuser.Shell;
import com.example.vcam.TerminalEmulator;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

    private Switch force_show_switch;
    private Switch disable_switch;
    private Switch play_sound_switch;
    private Switch force_private_dir;
    private Switch disable_toast_switch;
    private Button btnSelectVideo;
    private TextView tvSelectedVideo;
    private Button btnAppDescription;
    private Button btnSelectTargetApp;
    private TextView tvTargetApp;
    private RadioGroup rgRotation;
    private RadioButton rbRotation0;
    private RadioButton rbRotation90;
    private RadioButton rbRotation180;
    private RadioButton rbRotation270;
    private Button btnStartCopy;
    private TextView tvCopyStatus;
    private VideoView videoPreviewView;
    private TextView tvNoVideo;
    private LinearLayout videoControls;
    private Button btnPlayPause;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekbarProgress;
    private int currentRotation = 0;
    private boolean isVideoLoaded = false;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private Uri selectedVideoUri = null;
    private String selectedVideoPath = null;
    private Button btnBrowseMoreFiles;
    private ArrayList<VideoFileInfo> dcimVideoList = new ArrayList<>();
    private VideoFileAdapter videoAdapter;
    
    // 目标应用信息
    private String targetAppPackageName = "";
    private String targetAppName = "";
    
    // 调试日志相关
    private StringBuilder debugLogs = new StringBuilder();
    private static final int MAX_DEBUG_LOG_LINES = 1000;
    
    // 权限请求相关常量
    private static final int REQUEST_CODE_QUERY_ALL_PACKAGES = 1001;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show();
            }else {
                File camera_dir = new File (Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/Camera1/");
                if (!camera_dir.exists()){
                    camera_dir.mkdir();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sync_statue_with_files();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        force_show_switch = findViewById(R.id.switch1);
        disable_switch = findViewById(R.id.switch2);
        play_sound_switch = findViewById(R.id.switch3);
        force_private_dir = findViewById(R.id.switch4);
        disable_toast_switch = findViewById(R.id.switch5);
        btnSelectVideo = findViewById(R.id.btn_select_video);
        btnBrowseMoreFiles = findViewById(R.id.btn_browse_more_files);
        tvSelectedVideo = findViewById(R.id.tv_selected_video);
        btnAppDescription = findViewById(R.id.btn_app_description);
        btnSelectTargetApp = findViewById(R.id.btn_select_target_app);
        tvTargetApp = findViewById(R.id.tv_target_app);
        rgRotation = findViewById(R.id.rg_rotation);
        rbRotation0 = findViewById(R.id.rb_rotation_0);
        rbRotation90 = findViewById(R.id.rb_rotation_90);
        rbRotation180 = findViewById(R.id.rb_rotation_180);
        rbRotation270 = findViewById(R.id.rb_rotation_270);
        btnStartCopy = findViewById(R.id.btn_start_copy);
        tvCopyStatus = findViewById(R.id.tv_copy_status);

        sync_statue_with_files();
        initVideoSelection();
        initVideoPreview();
        initRotationSettings();
        initTargetAppSelection();
        initCopyButton();
        initAppDescriptionButton();

        // 自动请求ROOT权限
        RootManager rootManager = RootManager.getInstance();
        if (!rootManager.checkRootAccess()) {
            // 首次启动时请求ROOT权限
            new Thread(() -> {
                try {
                    // 尝试执行一个简单的ROOT命令来触发权限请求
                    com.topjohnwu.superuser.Shell.Result result = com.topjohnwu.superuser.Shell.su("echo 'VCAM ROOT Access Request'").exec();
                    // 再次检查ROOT权限
                    rootManager.checkRootAccess();
                } catch (Exception e) {
                    Log.e("MainActivity", "ROOT权限请求失败: " + e.getMessage());
                }
            }).start();
        } else {
            // 已经有ROOT权限
        }

        Button repo_button_chinamainland = findViewById(R.id.button2);
        repo_button_chinamainland.setOnClickListener(view -> {
            Uri uri = Uri.parse("https://gitee.com/w2016561536/android_virtual_cam");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });

        disable_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File disable_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable.jpg");
                    if (disable_file.exists() != b){
                        if (b){
                            try {
                                disable_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            disable_file.delete();
                        }
                    }
                }
            }
        });

        play_sound_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File play_sound_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/play_sound.jpg");
                    if (play_sound_file.exists() != b){
                        if (b){
                            try {
                                play_sound_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            play_sound_file.delete();
                        }
                    }
                }
            }
        });

        force_show_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File force_show_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_show.jpg");
                    if (force_show_file.exists() != b){
                        if (b){
                            try {
                                force_show_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            force_show_file.delete();
                        }
                    }
                }
            }
        });

        force_private_dir.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File force_private_dir_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_private_dir.jpg");
                    if (force_private_dir_file.exists() != b){
                        if (b){
                            try {
                                force_private_dir_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            force_private_dir_file.delete();
                        }
                    }
                }
            }
        });

        disable_toast_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File disable_toast_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable_toast.jpg");
                    if (disable_toast_file.exists() != b){
                        if (b){
                            try {
                                disable_toast_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            disable_toast_file.delete();
                        }
                    }
                }
            }
        });

        rgRotation.setOnCheckedChangeListener((group, checkedId) -> {
            if (!has_permission()) {
                request_permission();
                return;
            }

            int rotation = 0;
            if (checkedId == R.id.rb_rotation_90) {
                rotation = 90;
            } else if (checkedId == R.id.rb_rotation_180) {
                rotation = 180;
            } else if (checkedId == R.id.rb_rotation_270) {
                rotation = 270;
            }

            final int finalRotation = rotation;
            // 使用后台线程保存旋转设置，避免阻塞UI
            new Thread(() -> {
                boolean saveSuccess = saveRotationSetting(finalRotation);
                
                // 在UI线程显示反馈
                runOnUiThread(() -> {
                    if (saveSuccess) {
                        Toast.makeText(MainActivity.this, "✅ 旋转角度已保存：" + finalRotation + "°", Toast.LENGTH_SHORT).show();
                        // 更新视频预览的旋转角度
                        updateVideoPreviewRotation(finalRotation);
                    } else {
                        Toast.makeText(MainActivity.this, "❌ 旋转角度保存失败，请重试", Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });
    }







    /**
     * 保存旋转设置（带重试机制）
     * @param rotation 旋转角度
     * @return 是否保存成功
     */
    private boolean saveRotationSetting(int rotation) {
        TerminalEmulator terminal = TerminalEmulator.getInstance();
        String rotationContent = String.valueOf(rotation);
        int maxRetries = 3;
        boolean allSuccess = true;
        
        Log.d("MainActivity", "====================================");
        Log.d("MainActivity", "开始保存旋转设置：" + rotation + "°");
        Log.d("MainActivity", "ROOT权限状态：" + (terminal.isRootAvailable() ? "可用" : "不可用"));
        
        // 保存到公共目录
        String publicDirPath = "/data/media/0/DCIM/Camera1";
        File rotationFile = new File(publicDirPath + "/rotation.txt");
        
        Log.d("MainActivity", "公共目录路径：" + publicDirPath);
        Log.d("MainActivity", "旋转设置文件：" + rotationFile.getAbsolutePath());
        
        // 确保公共目录存在（带重试）
        boolean publicDirExists = false;
        for (int retry = 0; retry < maxRetries; retry++) {
            if (terminal.directoryExists(publicDirPath)) {
                publicDirExists = true;
                Log.d("MainActivity", "公共目录已存在");
                break;
            }
            Log.d("MainActivity", "创建公共目录，重试 " + (retry + 1) + "/" + maxRetries);
            boolean createSuccess = terminal.createDirectory(publicDirPath);
            Log.d("MainActivity", "创建公共目录结果：" + (createSuccess ? "成功" : "失败"));
            if (createSuccess) {
                publicDirExists = true;
                break;
            }
            if (retry == maxRetries - 1) {
                Log.e("MainActivity", "创建公共目录失败");
            }
        }
        
        // 写入公共目录（带重试）
        boolean publicSaveSuccess = false;
        if (publicDirExists) {
            for (int retry = 0; retry < maxRetries; retry++) {
                if (terminal.isRootAvailable()) {
                    String command = String.format("echo '%s' > '%s'", rotationContent, rotationFile.getAbsolutePath());
                    Log.d("MainActivity", "执行命令：" + command);
                    TerminalEmulator.TerminalResult result = terminal.executeCommand(command);
                    if (result.isSuccess()) {
                        publicSaveSuccess = true;
                        Log.d("MainActivity", "旋转设置已保存到公共目录: " + rotationFile.getAbsolutePath());
                        // 验证保存结果
                        try {
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(rotationFile));
                            String savedRotation = reader.readLine();
                            reader.close();
                            Log.d("MainActivity", "验证保存结果：" + savedRotation);
                            if (savedRotation != null && savedRotation.equals(rotationContent)) {
                                Log.d("MainActivity", "公共目录保存验证成功");
                            } else {
                                Log.e("MainActivity", "公共目录保存验证失败");
                                publicSaveSuccess = false;
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "验证公共目录保存结果失败: " + e.getMessage());
                        }
                        break;
                    } else {
                        Log.e("MainActivity", "公共目录保存失败，重试 " + (retry + 1) + "/" + maxRetries);
                        Log.e("MainActivity", "命令执行错误：" + result.getError());
                    }
                } else {
                    try {
                        Log.d("MainActivity", "使用标准API保存到公共目录");
                        java.io.FileWriter writer = new java.io.FileWriter(rotationFile);
                        writer.write(rotationContent);
                        writer.close();
                        publicSaveSuccess = true;
                        Log.d("MainActivity", "旋转设置已保存到公共目录（标准API）");
                        // 验证保存结果
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(rotationFile));
                        String savedRotation = reader.readLine();
                        reader.close();
                        Log.d("MainActivity", "验证保存结果：" + savedRotation);
                        if (savedRotation != null && savedRotation.equals(rotationContent)) {
                            Log.d("MainActivity", "公共目录保存验证成功");
                        } else {
                            Log.e("MainActivity", "公共目录保存验证失败");
                            publicSaveSuccess = false;
                        }
                        break;
                    } catch (Exception e) {
                        Log.e("MainActivity", "标准API保存到公共目录失败: " + e.getMessage());
                    }
                }
                
                if (retry == maxRetries - 1) {
                    Log.e("MainActivity", "保存到公共目录最终失败");
                    allSuccess = false;
                }
            }
        } else {
            Log.e("MainActivity", "公共目录不存在，无法保存旋转设置");
            allSuccess = false;
        }
        
        // 同时保存到目标应用私有目录
        if (!targetAppPackageName.isEmpty()) {
            String privateDirPath = "/data/media/0/Android/data/" + targetAppPackageName + "/files/Camera1";
            File privateRotationFile = new File(privateDirPath + "/rotation.txt");
            
            Log.d("MainActivity", "私有目录路径：" + privateDirPath);
            Log.d("MainActivity", "私有旋转设置文件：" + privateRotationFile.getAbsolutePath());
            
            // 确保私有目录存在（带重试）
            boolean privateDirExists = false;
            for (int retry = 0; retry < maxRetries; retry++) {
                if (terminal.directoryExists(privateDirPath)) {
                    privateDirExists = true;
                    Log.d("MainActivity", "私有目录已存在");
                    break;
                }
                Log.d("MainActivity", "创建私有目录，重试 " + (retry + 1) + "/" + maxRetries);
                boolean createSuccess = terminal.createDirectory(privateDirPath);
                Log.d("MainActivity", "创建私有目录结果：" + (createSuccess ? "成功" : "失败"));
                if (createSuccess) {
                    privateDirExists = true;
                    break;
                }
                if (retry == maxRetries - 1) {
                    Log.e("MainActivity", "创建私有目录失败");
                }
            }
            
            // 写入私有目录（带重试）
            boolean privateSaveSuccess = false;
            if (privateDirExists) {
                for (int retry = 0; retry < maxRetries; retry++) {
                    if (terminal.isRootAvailable()) {
                        String command = String.format("echo '%s' > '%s'", rotationContent, privateRotationFile.getAbsolutePath());
                        Log.d("MainActivity", "执行命令：" + command);
                        TerminalEmulator.TerminalResult result = terminal.executeCommand(command);
                        if (result.isSuccess()) {
                            privateSaveSuccess = true;
                            Log.d("MainActivity", "旋转设置已保存到私有目录: " + privateRotationFile.getAbsolutePath());
                            // 验证保存结果
                            try {
                                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(privateRotationFile));
                                String savedRotation = reader.readLine();
                                reader.close();
                                Log.d("MainActivity", "验证保存结果：" + savedRotation);
                                if (savedRotation != null && savedRotation.equals(rotationContent)) {
                                    Log.d("MainActivity", "私有目录保存验证成功");
                                } else {
                                    Log.e("MainActivity", "私有目录保存验证失败");
                                    privateSaveSuccess = false;
                                }
                            } catch (Exception e) {
                                Log.e("MainActivity", "验证私有目录保存结果失败: " + e.getMessage());
                            }
                            break;
                        } else {
                            Log.e("MainActivity", "私有目录保存失败，重试 " + (retry + 1) + "/" + maxRetries);
                            Log.e("MainActivity", "命令执行错误：" + result.getError());
                        }
                    } else {
                        try {
                            Log.d("MainActivity", "使用标准API保存到私有目录");
                            File privateDir = privateRotationFile.getParentFile();
                            if (!privateDir.exists()) {
                                privateDir.mkdirs();
                                Log.d("MainActivity", "创建私有目录（标准API）");
                            }
                            java.io.FileWriter privateWriter = new java.io.FileWriter(privateRotationFile);
                            privateWriter.write(rotationContent);
                            privateWriter.close();
                            privateSaveSuccess = true;
                            Log.d("MainActivity", "旋转设置已保存到私有目录（标准API）");
                            // 验证保存结果
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(privateRotationFile));
                            String savedRotation = reader.readLine();
                            reader.close();
                            Log.d("MainActivity", "验证保存结果：" + savedRotation);
                            if (savedRotation != null && savedRotation.equals(rotationContent)) {
                                Log.d("MainActivity", "私有目录保存验证成功");
                            } else {
                                Log.e("MainActivity", "私有目录保存验证失败");
                                privateSaveSuccess = false;
                            }
                            break;
                        } catch (Exception e) {
                            Log.e("MainActivity", "标准API保存到私有目录失败: " + e.getMessage());
                        }
                    }
                
                if (retry == maxRetries - 1) {
                    Log.e("MainActivity", "保存到私有目录最终失败");
                    allSuccess = false;
                }
                }
            } else {
                Log.e("MainActivity", "私有目录不存在，无法保存旋转设置");
                allSuccess = false;
            }
        }
        
        Log.d("MainActivity", "旋转设置保存完成，结果：" + (allSuccess ? "成功" : "失败"));
        Log.d("MainActivity", "====================================");
        
        return allSuccess;
    }

    private void initVideoSelection() {
        btnSelectVideo.setOnClickListener(v -> {
            showDcimVideoSelectionDialog();
        });

        btnBrowseMoreFiles.setOnClickListener(v -> {
            browseVideoFiles();
        });

        videoAdapter = new VideoFileAdapter(this, dcimVideoList);
        scanDcimVideos();
    }

    private void showDcimVideoSelectionDialog() {
        if (dcimVideoList.isEmpty()) {
            Toast.makeText(this, "正在扫描DCIM目录，请稍候...", Toast.LENGTH_SHORT).show();
            scanDcimVideos();
            return;
        }

        String[] videoNames = new String[dcimVideoList.size()];
        final boolean[] selected = new boolean[dcimVideoList.size()];

        for (int i = 0; i < dcimVideoList.size(); i++) {
            VideoFileInfo info = dcimVideoList.get(i);
            String sizeStr = formatFileSize(info.fileSize);
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(info.lastModified));
            videoNames[i] = info.fileName + "\n大小: " + sizeStr + " | 修改: " + dateStr;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择视频文件 (DCIM/Camera)");
        builder.setSingleChoiceItems(videoNames, -1, (dialog, which) -> {
            for (int i = 0; i < selected.length; i++) {
                selected[i] = false;
            }
            selected[which] = true;
        });
        builder.setPositiveButton("确定", (dialog, which) -> {
            int selectedIndex = -1;
            for (int i = 0; i < selected.length; i++) {
                if (selected[i]) {
                    selectedIndex = i;
                    break;
                }
            }
            if (selectedIndex >= 0 && selectedIndex < dcimVideoList.size()) {
                selectVideoFromList(dcimVideoList.get(selectedIndex));
            } else {
                Toast.makeText(this, "请选择一个视频文件", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void scanDcimVideos() {
        new Thread(() -> {
            dcimVideoList.clear();
            String dcimPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera";

            File dcimDir = new File(dcimPath);
            if (dcimDir.exists() && dcimDir.isDirectory()) {
                File[] videoFiles = dcimDir.listFiles((dir, name) -> {
                    String lowerName = name.toLowerCase();
                    return lowerName.endsWith(".mp4") || lowerName.endsWith(".3gp")
                           || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi")
                           || lowerName.endsWith(".mov") || lowerName.endsWith(".flv")
                           || lowerName.endsWith(".wmv");
                });

                if (videoFiles != null) {
                    java.util.Arrays.sort(videoFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

                    for (File file : videoFiles) {
                        VideoFileInfo info = new VideoFileInfo();
                        info.fileName = file.getName();
                        info.filePath = file.getAbsolutePath();
                        info.fileSize = file.length();
                        info.lastModified = file.lastModified();
                        dcimVideoList.add(info);
                    }
                }
            }

            runOnUiThread(() -> {
                videoAdapter.notifyDataSetChanged();
                if (dcimVideoList.isEmpty()) {
                    Toast.makeText(this, "DCIM/Camera 目录下未找到视频文件", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void selectVideoFromList(VideoFileInfo videoInfo) {
        selectedVideoPath = videoInfo.filePath;
        File videoFile = new File(selectedVideoPath);

        if (videoFile.exists()) {
            selectedVideoUri = Uri.fromFile(videoFile);
            saveSelectedVideoPath(selectedVideoPath);
            tvSelectedVideo.setText("已选择：" + videoInfo.fileName);
            Toast.makeText(this, "视频文件选择成功", Toast.LENGTH_SHORT).show();
            loadVideoPreview(selectedVideoPath);
        } else {
            Toast.makeText(this, "视频文件不存在或无法访问", Toast.LENGTH_SHORT).show();
        }
    }

    private void showVideoSelectionDialog() {
        browseVideoFiles();
    }

    private void browseVideoFiles() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*"});
        startActivityForResult(Intent.createChooser(intent, "选择视频文件"), 1);
    }

    private void initVideoPreview() {
        videoPreviewView = findViewById(R.id.video_preview_view);
        tvNoVideo = findViewById(R.id.tv_no_video);
        videoControls = findViewById(R.id.video_controls);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        seekbarProgress = findViewById(R.id.seekbar_progress);

        videoPreviewView.setOnPreparedListener(mp -> {
            isVideoLoaded = true;
            videoWidth = mp.getVideoWidth();
            videoHeight = mp.getVideoHeight();
            tvNoVideo.setVisibility(android.view.View.GONE);
            videoControls.setVisibility(android.view.View.VISIBLE);
            
            android.view.ViewGroup.LayoutParams params = videoPreviewView.getLayoutParams();
            
            if (currentRotation == 90 || currentRotation == 270) {
                params.width = videoHeight;
                params.height = videoWidth;
            } else {
                params.width = videoWidth;
                params.height = videoHeight;
            }
            videoPreviewView.setLayoutParams(params);
            
            mp.setLooping(true);
            int duration = mp.getDuration();
            tvTotalTime.setText(formatTime(duration));
            seekbarProgress.setMax(duration);
        });

        videoPreviewView.setOnCompletionListener(mp -> {
            btnPlayPause.setText("▶");
        });

        videoPreviewView.setOnErrorListener((mp, what, extra) -> {
            tvNoVideo.setText("视频播放错误");
            tvNoVideo.setVisibility(android.view.View.VISIBLE);
            videoControls.setVisibility(android.view.View.GONE);
            isVideoLoaded = false;
            return true;
        });

        btnPlayPause.setOnClickListener(v -> {
            if (videoPreviewView.isPlaying()) {
                videoPreviewView.pause();
                btnPlayPause.setText("▶");
            } else {
                if (videoPreviewView.getCurrentPosition() >= videoPreviewView.getDuration()) {
                    videoPreviewView.seekTo(0);
                }
                videoPreviewView.start();
                btnPlayPause.setText("⏸");
                startProgressUpdate();
            }
        });

        seekbarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    videoPreviewView.seekTo(progress);
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void startProgressUpdate() {
        new Thread(() -> {
            while (videoPreviewView != null && videoPreviewView.isPlaying()) {
                try {
                    int currentPos = videoPreviewView.getCurrentPosition();
                    seekbarProgress.setProgress(currentPos);
                    runOnUiThread(() -> tvCurrentTime.setText(formatTime(currentPos)));
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateVideoPreviewRotation(int rotation) {
        currentRotation = rotation;
        
        if (!isVideoLoaded) {
            return;
        }

        android.view.ViewGroup.LayoutParams params = videoPreviewView.getLayoutParams();

        if (rotation == 90 || rotation == 270) {
            params.width = videoHeight;
            params.height = videoWidth;
        } else {
            params.width = videoWidth;
            params.height = videoHeight;
        }
        videoPreviewView.setLayoutParams(params);
        videoPreviewView.setRotation(rotation);
    }

    private void loadVideoPreview(String videoPath) {
        if (videoPath == null || videoPath.isEmpty()) {
            tvNoVideo.setText("请先选择视频文件");
            tvNoVideo.setVisibility(android.view.View.VISIBLE);
            videoControls.setVisibility(android.view.View.GONE);
            isVideoLoaded = false;
            return;
        }

        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            tvNoVideo.setText("视频文件不存在");
            tvNoVideo.setVisibility(android.view.View.VISIBLE);
            videoControls.setVisibility(android.view.View.GONE);
            isVideoLoaded = false;
            return;
        }

        try {
            Uri videoUri = Uri.fromFile(videoFile);
            videoPreviewView.setVideoURI(videoUri);
            videoPreviewView.setRotation(currentRotation);
            tvNoVideo.setText("正在加载...");
            tvNoVideo.setVisibility(android.view.View.VISIBLE);
            videoControls.setVisibility(android.view.View.GONE);
            videoPreviewView.start();
        } catch (Exception e) {
            tvNoVideo.setText("无法加载视频");
            tvNoVideo.setVisibility(android.view.View.VISIBLE);
            videoControls.setVisibility(android.view.View.GONE);
            isVideoLoaded = false;
            Log.e("MainActivity", "加载视频预览失败: " + e.getMessage());
        }
    }

    private void loadVideoPreview(Uri videoUri) {
        if (videoUri == null) {
            tvNoVideo.setText("请先选择视频文件");
            tvNoVideo.setVisibility(android.view.View.VISIBLE);
            videoControls.setVisibility(android.view.View.GONE);
            isVideoLoaded = false;
            return;
        }

        try {
            videoPreviewView.setVideoURI(videoUri);
            videoPreviewView.setRotation(currentRotation);
            tvNoVideo.setText("正在加载...");
            tvNoVideo.setVisibility(android.view.View.VISIBLE);
            videoControls.setVisibility(android.view.View.GONE);
            videoPreviewView.start();
        } catch (Exception e) {
            tvNoVideo.setText("无法加载视频");
            tvNoVideo.setVisibility(android.view.View.VISIBLE);
            videoControls.setVisibility(android.view.View.GONE);
            isVideoLoaded = false;
            Log.e("MainActivity", "加载视频预览失败: " + e.getMessage());
        }
    }

    private void initRotationSettings() {
        // 初始化旋转设置
        try {
            File rotationFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/rotation.txt");
            if (rotationFile.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(rotationFile));
                String rotationStr = reader.readLine();
                reader.close();
                if (rotationStr != null) {
                    try {
                        int rotation = Integer.parseInt(rotationStr);
                        if (rotation == 90) {
                            rbRotation90.setChecked(true);
                        } else if (rotation == 180) {
                            rbRotation180.setChecked(true);
                        } else if (rotation == 270) {
                            rbRotation270.setChecked(true);
                        } else {
                            rbRotation0.setChecked(true);
                        }
                    } catch (NumberFormatException e) {
                        rbRotation0.setChecked(true);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "读取旋转设置失败", e);
        }
    }

    private void initTargetAppSelection() {
        btnSelectTargetApp.setOnClickListener(v -> {
            showAppSelectionDialog();
        });
    }

    private void initCopyButton() {
        btnStartCopy.setOnClickListener(v -> {
            // 更新状态为复制中
            updateCopyStatus("复制中...", "#FF9800", true);
            
            // 执行复制
            performCopyWithStatus();
        });
    }

    private void initAppDescriptionButton() {
        btnAppDescription.setOnClickListener(v -> {
            showAppDescriptionDialog();
        });
    }

    private void showAppDescriptionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("应用说明");
        
        // 创建滚动视图
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setPadding(20, 20, 20, 20);
        textView.setTextSize(14);
        textView.setText(
            "安装此模块，并在Xposed中启用此模块，Lsposed等包含作用域的框架需要选择目标app，无需选择系统框架。需要ROOT 权限。\n\n" +
            "在系统设置中，授予目标应用读取本地存储的权限，并强制结束目标应用程序。若应用程序未申请此权限，请见步骤3。\n\n" +
            "打开目标应用，若应用未能获得读取存储的权限，则会以气泡消息提示，Camera1目录被重定向至应用程序私有目录/[内部存储]/Android/data/[应用包名]/files/Camera1/。若未提示，则默认Camera1目录为/[内部存储]/DCIM/Camera1/。若目录不存在，请手动创建。\n\n" +
            "注意：私有目录下的Camera1仅对该应用单独生效。\n\n" +
            "在目标应用中打开相机预览，会以气泡消息提示\"宽：……高：……\"，需要根据此分辨率数据制作替换视频，放置于Camera1目录下，并命名为virtual.mp4，若打开相机并无提示消息，则无需调整视频分辨率。\n\n" +
            "若在目标应用中拍照却显示真实图片，且出现气泡消息发现拍照和分辨率，则需根据此分辨率数据准备一张照片，命名为1000.bmp，放入Camera1目录下（支持其它格式改后缀为bmp）。如果拍照时无气泡消息提示，则1000.bmp无效。\n\n" +
            "如果需要播放视频的声音，需在/[内部存储]/DCIM/Camera1/目录下创建no-silent.jpg文件。（全局实时生效）\n\n" +
            "如果需要临时停用视频替换，需在/[内部存储]/DCIM/Camera1/目录下创建disable.jpg文件。（全局实时生效）\n\n" +
            "如果觉得Toast消息烦，可以在/[内部存储]/DCIM/Camera1/目录下创建no_toast.jpg文件。（全局实时生效）\n\n" +
            "目录重定向消息默认只显示一次，如果错过了目录重定向的Toast消息，可以在/[内部存储]/DCIM/Camera1/目录下创建force_show.jpg文件来覆盖默认设定。（全局实时生效）\n\n" +
            "如果需要为每一个应用程序分配视频，可以在/[内部存储]/DCIM/Camera1/目录下创建private_dir.jpg强制使用应用程序私有目录。（全局实时生效）\n\n" +
            "注意：6~10的配置开关均在应用程序中，您可以快捷地在应用程序中配置，也可以手动创建文件。\n\n" +
            "常见问题\n" +
            "A1. 前置摄像头方向问题？\n" +
            "Q1. 大多数情况下,替换前置摄像头的视频需要水平翻转并右旋90度，并且视频处理后的分辨率应与气泡消息内分辨率相同。但有时这并不需要，具体请根据实际情况判断。\n\n" +
            "Q2. 画面黑屏，相机启动失败？\n" +
            "A2. 目前有些应用并不能成功替换（特别是系统相机）。或者是因为视频路径不对（是否创建了两级Camera1目录，如./DCIM/Camera1/Camera1/virtual.mp4，但只需要一级目录）。\n\n" +
            "Q3. 画面花屏？\n" +
            "A3. 视频分辨率不对。\n\n" +
            "Q4. 画面扭曲，变形？\n" +
            "A4. 请使用剪辑软件修改原视频来匹配屏幕。\n\n" +
            "Q5. 创建disable.jpg无效？\n" +
            "A5. 如果应用版本<=4.0，那么[内部存储]/DCIM/Camera1目录下的文件对具有访问存储权限的应用生效，其余无权限应用应在私有目录下创建\n" +
            "如果应用版本>=4.1，那么应在[内部存储]/DCIM/Camera1创建，无论目标应用是否具有权限。"
        );
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("确定", null);
        
        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        
        // 设置对话框大小
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9), 
                           (int) (getResources().getDisplayMetrics().heightPixels * 0.8));
        }
    }

    private void updateCopyStatus(String status, String color, boolean enable) {
        runOnUiThread(() -> {
            tvCopyStatus.setText("状态：" + status);
            tvCopyStatus.setTextColor(android.graphics.Color.parseColor(color));
            btnStartCopy.setEnabled(enable);

            if (enable) {
                btnStartCopy.setText("📋 复制视频到应用私有目录");
                btnStartCopy.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")));
            } else {
                btnStartCopy.setText("复制中，请稍候...");
                btnStartCopy.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#999999")));
            }
        });
    }

    private void performCopyWithStatus() {
        new Thread(() -> {
            try {
                if (selectedVideoUri == null && (selectedVideoPath == null || selectedVideoPath.isEmpty())) {
                    updateCopyStatus("请先选择视频文件", "#F44336", true);
                    return;
                }

                if (targetAppPackageName.isEmpty()) {
                    updateCopyStatus("请先选择目标应用", "#F44336", true);
                    return;
                }

                updateCopyStatus("正在复制视频...", "#FF9800", false);

                String targetDirPath = "/data/media/0/Android/data/" + targetAppPackageName + "/files/Camera1";
                File targetDir = new File(targetDirPath);
                File targetVideo = new File(targetDir, "virtual.mp4");

                StringBuilder directoryCheckLog = new StringBuilder();
                boolean success = performCopy(selectedVideoUri, selectedVideoPath, targetVideo, directoryCheckLog);

                if (success) {
                    updateCopyStatus("复制成功！", "#4CAF50", true);
                } else {
                    updateCopyStatus("复制失败：" + directoryCheckLog.toString(), "#F44336", true);
                }

            } catch (Exception e) {
                updateCopyStatus("复制异常：" + e.getMessage(), "#F44336", true);
            }
        }).start();
    }

    private boolean performCopy(Uri sourceUri, String sourcePath, File targetVideo, StringBuilder directoryCheckLog) {
        boolean copySuccess = false;

        try {
            TerminalEmulator terminal = TerminalEmulator.getInstance();

            File sourceFile = (sourcePath != null) ? new File(sourcePath) : null;
            String actualSourcePath = sourcePath;

            if (sourceFile == null || !sourceFile.exists()) {
                directoryCheckLog.append("源文件不存在于文件系统中，使用URI复制...\n");
                actualSourcePath = sourceUri.getPath();
            }

            if (terminal.isRootAvailable() && sourceFile != null && sourceFile.exists()) {
                directoryCheckLog.append("使用终端模拟器执行ROOT操作...\n");

                File targetDir = targetVideo.getParentFile();
                if (!terminal.directoryExists(targetDir.getAbsolutePath())) {
                    directoryCheckLog.append("创建目标目录：").append(targetDir.getAbsolutePath()).append("\n");
                    terminal.createDirectory(targetDir.getAbsolutePath());
                }

                directoryCheckLog.append("终端模拟器复制文件：\n");
                directoryCheckLog.append("  源文件：").append(sourceFile.getAbsolutePath()).append("\n");
                directoryCheckLog.append("  目标文件：").append(targetVideo.getAbsolutePath()).append("\n");

                copySuccess = terminal.copyFile(sourceFile.getAbsolutePath(), targetVideo.getAbsolutePath());
                if (copySuccess) {
                    directoryCheckLog.append("终端模拟器复制成功\n");
                    return true;
                }
                directoryCheckLog.append("终端模拟器复制失败，尝试其他方法...\n");
            } else {
                directoryCheckLog.append("ROOT权限不可用或源文件不存在，使用ContentResolver...\n");
            }

            directoryCheckLog.append("使用ContentResolver复制...\n");
            File targetDir = targetVideo.getParentFile();
            if (!targetDir.exists()) {
                directoryCheckLog.append("创建目标目录...\n");
                boolean created = targetDir.mkdirs();
                directoryCheckLog.append(created ? "目录创建成功\n" : "目录创建失败\n");
            }

            try {
                java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetVideo);
                java.io.InputStream inputStream = getContentResolver().openInputStream(sourceUri);

                if (inputStream == null) {
                    directoryCheckLog.append("无法打开源文件URI\n");
                    outputStream.close();
                    return false;
                }

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
                inputStream.close();
                outputStream.close();

                if (targetVideo.exists() && targetVideo.length() > 0) {
                    directoryCheckLog.append("ContentResolver复制完成并验证成功\n");
                    return true;
                } else {
                    directoryCheckLog.append("ContentResolver复制完成但验证失败\n");
                }
            } catch (Exception e) {
                directoryCheckLog.append("ContentResolver复制失败：").append(e.getMessage()).append("\n");
            }

        } catch (Exception e) {
            directoryCheckLog.append("复制异常：").append(e.getMessage()).append("\n");
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                final int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException e) {
                    Log.e("MainActivity", "无法获取持久化URI权限", e);
                }

                selectedVideoUri = uri;
                String path = getPathFromUri(uri);
                selectedVideoPath = path;

                if (path != null) {
                    saveSelectedVideoPath(path);
                    File file = new File(path);
                    String displayName = file.exists() ? file.getName() : getFileNameFromUri(uri);
                    tvSelectedVideo.setText("已选择：" + displayName);
                    Toast.makeText(MainActivity.this, "视频文件选择成功", Toast.LENGTH_SHORT).show();
                    loadVideoPreview(uri);
                } else {
                    selectedVideoPath = uri.getPath();
                    saveSelectedVideoPath(selectedVideoPath);
                    String displayName = getFileNameFromUri(uri);
                    tvSelectedVideo.setText("已选择：" + displayName);
                    Toast.makeText(MainActivity.this, "视频文件选择成功（临时权限）", Toast.LENGTH_SHORT).show();
                    loadVideoPreview(uri);
                }
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = "未知文件";
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    result = cursor.getString(nameIndex);
                }
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private String getPathFromUri(Uri uri) {
        String path = null;
        try {
            if (uri.getScheme() == null) {
                path = uri.getPath();
                return path;
            }

            if (uri.getScheme().equals("content")) {
                String[] projection = {android.provider.MediaStore.Video.Media.DATA};
                android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA);
                            path = cursor.getString(columnIndex);
                        }
                    } finally {
                        cursor.close();
                    }
                }
                if (path == null) {
                    path = uri.getPath();
                }
            } else if (uri.getScheme().equals("file")) {
                path = uri.getPath();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "从Uri获取路径失败: " + uri, e);
            path = uri.getPath();
        }
        return path;
    }

    private void saveSelectedVideoPath(String path) {
        try {
            File settingsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/settings.txt");
            java.io.FileWriter writer = new java.io.FileWriter(settingsFile);
            writer.write("selected_video=" + path);
            writer.close();
        } catch (Exception e) {
            Log.e("MainActivity", "保存视频路径失败", e);
        }
    }

    private void showAppSelectionDialog() {
        // 实现应用选择对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择目标应用");
        
        // 获取应用列表
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
        
        // 过滤并排序应用
        java.util.ArrayList<String> appNames = new java.util.ArrayList<>();
        java.util.ArrayList<String> packageNames = new java.util.ArrayList<>();
        
        for (ApplicationInfo app : apps) {
            // 过滤系统应用
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                appNames.add(app.loadLabel(getPackageManager()).toString());
                packageNames.add(app.packageName);
            }
        }
        
        // 添加系统应用
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                appNames.add(app.loadLabel(getPackageManager()).toString() + " (系统)");
                packageNames.add(app.packageName);
            }
        }
        
        final String[] appArray = appNames.toArray(new String[0]);
        
        builder.setItems(appArray, (dialog, which) -> {
            targetAppPackageName = packageNames.get(which);
            targetAppName = appArray[which];
            tvTargetApp.setText("已选择：" + targetAppName);
            Toast.makeText(MainActivity.this, "目标应用选择成功", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void sync_statue_with_files() {
        if (!has_permission()) {
            request_permission();
            return;
        }

        File camera_dir = new File (Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/Camera1/");
        if (!camera_dir.exists()){
            camera_dir.mkdir();
        }

        File disable_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable.jpg");
        disable_switch.setChecked(disable_file.exists());

        File play_sound_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/play_sound.jpg");
        play_sound_switch.setChecked(play_sound_file.exists());

        File force_show_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_show.jpg");
        force_show_switch.setChecked(force_show_file.exists());

        File force_private_dir_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_private_dir.jpg");
        force_private_dir.setChecked(force_private_dir_file.exists());

        File disable_toast_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable_toast.jpg");
        disable_toast_switch.setChecked(disable_toast_file.exists());

        // 读取旋转设置
        try {
            File rotationFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/rotation.txt");
            if (rotationFile.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(rotationFile));
                String rotationStr = reader.readLine();
                reader.close();
                if (rotationStr != null) {
                    try {
                        int rotation = Integer.parseInt(rotationStr);
                        if (rotation == 90) {
                            rbRotation90.setChecked(true);
                        } else if (rotation == 180) {
                            rbRotation180.setChecked(true);
                        } else if (rotation == 270) {
                            rbRotation270.setChecked(true);
                        } else {
                            rbRotation0.setChecked(true);
                        }
                    } catch (NumberFormatException e) {
                        rbRotation0.setChecked(true);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "读取旋转设置失败", e);
        }

        // 读取选定的视频文件
        try {
            File settingsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/settings.txt");
            if (settingsFile.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(settingsFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("selected_video=")) {
                        String videoPath = line.substring(15);
                        File videoFile = new File(videoPath);
                        if (videoFile.exists()) {
                            tvSelectedVideo.setText("已选择：" + videoFile.getName());
                        } else {
                            tvSelectedVideo.setText("视频文件不存在");
                        }
                        break;
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "读取视频路径失败", e);
        }

        // 读取目标应用
        if (!targetAppPackageName.isEmpty()) {
            tvTargetApp.setText("已选择：" + targetAppName);
        }
    }

    private boolean has_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void request_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 100);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 100);
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    /**
     * 获取当前选定的视频路径
     */
    private String getSelectedVideoPath() {
        String videoPath = "";
        try {
            // 从存储的设置中读取
            File settingsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/settings.txt");
            if (settingsFile.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(settingsFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("selected_video=")) {
                        videoPath = line.substring(15);
                        break;
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "读取视频路径失败: " + e.getMessage());
        }
        return videoPath;
    }

    /**
     * 自动复制视频到目标应用私有目录
     */
    private void autoCopyVideo() {
        new Thread(() -> {
            try {
                // 检查是否有选定的视频文件
                String selectedVideoPath = getSelectedVideoPath();
                if (selectedVideoPath.isEmpty()) {
                    Log.d("MainActivity", "未选择视频文件，跳过自动复制");
                    return;
                }
                
                // 检查是否有选定的目标应用
                if (targetAppPackageName.isEmpty()) {
                    Log.d("MainActivity", "未选择目标应用，跳过自动复制");
                    return;
                }
                
                Log.d("MainActivity", "开始自动复制视频");
                
                // 初始化终端模拟器
                TerminalEmulator terminal = TerminalEmulator.getInstance();
                
                // 步骤1：先复制到公共DCIM/Camera1目录（供HookMain使用）
                File sourceVideo = new File(selectedVideoPath);
                String publicDirPath = "/data/media/0/DCIM/Camera1";
                File publicVideo = new File(publicDirPath, "virtual.mp4");
                
                // 确保公共目录存在
                if (!terminal.directoryExists(publicDirPath)) {
                    boolean dirCreated = terminal.createDirectory(publicDirPath);
                    if (!dirCreated) {
                        Log.e("MainActivity", "创建公共目录失败");
                    }
                }
                
                // 复制到公共目录
                boolean publicCopySuccess = false;
                
                if (terminal.isRootAvailable()) {
                    publicCopySuccess = terminal.copyFile(sourceVideo.getAbsolutePath(), publicVideo.getAbsolutePath());
                } else {
                    // 使用标准API复制
                    try {
                        java.io.FileInputStream inputStream = new java.io.FileInputStream(sourceVideo);
                        java.io.FileOutputStream outputStream = new java.io.FileOutputStream(publicVideo);
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                        inputStream.close();
                        outputStream.close();
                        publicCopySuccess = true;
                    } catch (Exception e) {
                        Log.e("MainActivity", "复制到公共目录失败: " + e.getMessage());
                    }
                }
                
                if (publicCopySuccess) {
                    Log.d("MainActivity", "视频已复制到公共目录: " + publicVideo.getAbsolutePath());
                } else {
                    Log.e("MainActivity", "复制到公共目录失败");
                }
                
                // 步骤2：同时复制到目标应用私有目录（直接使用）
                String targetDirPath = "/data/media/0/Android/data/" + targetAppPackageName + "/files/Camera1";
                File targetVideo = new File(targetDirPath, "virtual.mp4");
                
                // 确保目标目录存在
                if (!terminal.directoryExists(targetDirPath)) {
                    boolean dirCreated = terminal.createDirectory(targetDirPath);
                    if (!dirCreated) {
                        Log.e("MainActivity", "创建目标目录失败");
                    }
                }
                
                // 复制到目标目录
                boolean targetCopySuccess = false;
                if (terminal.isRootAvailable()) {
                    targetCopySuccess = terminal.copyFile(sourceVideo.getAbsolutePath(), targetVideo.getAbsolutePath());
                } else {
                    // 使用标准API复制
                    try {
                        File targetDir = new File(targetDirPath);
                        if (!targetDir.exists()) {
                            targetDir.mkdirs();
                        }
                        java.io.FileInputStream inputStream = new java.io.FileInputStream(sourceVideo);
                        java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetVideo);
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                        inputStream.close();
                        outputStream.close();
                        targetCopySuccess = true;
                    } catch (Exception e) {
                        Log.e("MainActivity", "复制到目标目录失败: " + e.getMessage());
                    }
                }
                
                if (targetCopySuccess) {
                    Log.d("MainActivity", "视频已复制到目标目录: " + targetVideo.getAbsolutePath());
                } else {
                    Log.e("MainActivity", "复制到目标目录失败");
                }
                
                Log.d("MainActivity", "自动复制完成");
                
                // 更新UI状态
                runOnUiThread(() -> {
                    updateCopyStatus("复制完成", "#4CAF50", true);
                });
                
            } catch (Exception e) {
                Log.e("MainActivity", "自动复制视频失败: " + e.getMessage());
                runOnUiThread(() -> {
                    updateCopyStatus("复制失败: " + e.getMessage(), "#F44336", true);
                });
            }
        }).start();
    }

    /**
     * 执行实际的文件复制操作 - 使用终端模拟器执行cp命令
     */
    private void performFileCopy(File sourceVideo, File targetVideo, StringBuilder directoryCheckLog) {
        // 显示复制进度对话框
        AlertDialog.Builder progressBuilder = new AlertDialog.Builder(this);
        progressBuilder.setTitle("正在复制视频文件");
        progressBuilder.setMessage("请稍候，正在将视频文件复制到目标应用私有目录...");
        progressBuilder.setCancelable(false);
        AlertDialog progressDialog = progressBuilder.create();
        progressDialog.show();

        // 使用后台线程执行复制操作
        new Thread(() -> {
            try {
                boolean copySuccess = false;
                TerminalEmulator terminal = TerminalEmulator.getInstance();
                
                // ========== 步骤1：创建目标目录 ==========
                File targetDir = targetVideo.getParentFile();
                directoryCheckLog.append("\n===== 开始复制流程 =====\n");
                directoryCheckLog.append("目标目录：").append(targetDir.getAbsolutePath()).append("\n");
                
                // 检查目录是否存在
                boolean dirExists = terminal.directoryExists(targetDir.getAbsolutePath());
                directoryCheckLog.append("目录存在：").append(dirExists ? "是" : "否").append("\n");
                
                if (!dirExists) {
                    directoryCheckLog.append("创建目标目录...\n");
                    
                    // 使用ROOT创建目录
                    if (terminal.isRootAvailable()) {
                        directoryCheckLog.append("使用ROOT创建目录...\n");
                        boolean created = terminal.createDirectory(targetDir.getAbsolutePath());
                        if (created) {
                            directoryCheckLog.append("✅ ROOT目录创建成功\n");
                            dirExists = true;
                        } else {
                            directoryCheckLog.append("❌ ROOT目录创建失败\n");
                        }
                    } else {
                        directoryCheckLog.append("❌ ROOT权限不可用，无法创建目录\n");
                    }
                } else {
                    directoryCheckLog.append("目录已存在，跳过创建步骤\n");
                }
                
                // 验证目录最终状态
                if (!targetDir.exists()) {
                    directoryCheckLog.append("❌ 目录创建失败，无法继续复制\n");
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        showDetailedErrorDialog("创建目标目录失败，请检查ROOT权限", "错误");
                    });
                    return;
                }
                directoryCheckLog.append("✅ 目录准备就绪\n");
                
                // ========== 步骤2：执行文件复制（使用cp命令）==========
                directoryCheckLog.append("\n===== 开始复制文件 =====\n");
                directoryCheckLog.append("源文件：").append(sourceVideo.getAbsolutePath()).append("\n");
                directoryCheckLog.append("目标文件：").append(targetVideo.getAbsolutePath()).append("\n");
                directoryCheckLog.append("源文件大小：").append(formatFileSize(sourceVideo.length())).append("\n");

                // 检查存储空间
                long requiredSpace = sourceVideo.length();
                long freeSpace = targetDir.getFreeSpace();
                directoryCheckLog.append("可用空间：").append(formatFileSize(freeSpace)).append("\n");
                
                if (freeSpace < requiredSpace) {
                    directoryCheckLog.append("❌ 存储空间不足\n");
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        showDetailedErrorDialog("存储空间不足，无法复制文件", "错误");
                    });
                    return;
                }

                // 方法：使用cp命令复制文件
                if (terminal.isRootAvailable()) {
                    directoryCheckLog.append("\n使用ROOT权限执行cp命令...\n");
                    
                    // 构建cp命令
                    String sourcePath = sourceVideo.getAbsolutePath();
                    String targetPath = targetVideo.getAbsolutePath();
                    String command = String.format("cp -f '%s' '%s'", sourcePath, targetPath);
                    
                    // 执行命令
                    TerminalEmulator.TerminalResult result = terminal.executeCommand(command);
                    
                    if (result.isSuccess()) {
                        // 验证复制结果
                        if (terminal.fileExists(targetPath)) {
                            // 检查文件大小
                            String sizeCommand = String.format("stat -c %%s '%s'", targetPath);
                            TerminalEmulator.TerminalResult sizeResult = terminal.executeCommand(sizeCommand);
                            
                            if (sizeResult.isSuccess() && sizeResult.getOutput() != null && !sizeResult.getOutput().isEmpty()) {
                                try {
                                    long targetSize = Long.parseLong(sizeResult.getOutput().get(0).trim());
                                    if (targetSize == sourceVideo.length()) {
                                        copySuccess = true;
                                        directoryCheckLog.append("✅ cp命令复制并验证成功\n");
                                    } else {
                                        directoryCheckLog.append("❌ 文件大小不匹配\n");
                                    }
                                } catch (Exception e) {
                                    directoryCheckLog.append("❌ 解析文件大小失败\n");
                                }
                            } else {
                                directoryCheckLog.append("❌ 无法检查目标文件大小\n");
                            }
                        } else {
                            directoryCheckLog.append("❌ 目标文件不存在\n");
                        }
                    } else {
                        directoryCheckLog.append("❌ cp命令执行失败：").append(result.getError()).append("\n");
                    }
                } else {
                    directoryCheckLog.append("❌ ROOT权限不可用，无法执行cp命令\n");
                    // 尝试使用标准API作为备选方案
                    directoryCheckLog.append("\n尝试使用标准API复制...\n");
                    try {
                        java.io.FileInputStream inputStream = new java.io.FileInputStream(sourceVideo);
                        java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetVideo);

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalBytes = 0;
                        long fileSize = sourceVideo.length();

                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                        }

                        outputStream.flush();
                        inputStream.close();
                        outputStream.close();

                        if (targetVideo.exists() && targetVideo.length() == sourceVideo.length()) {
                            copySuccess = true;
                            directoryCheckLog.append("✅ 标准API复制成功\n");
                        } else {
                            directoryCheckLog.append("❌ 标准API复制失败\n");
                        }
                    } catch (Exception e) {
                        directoryCheckLog.append("❌ 标准API复制异常：").append(e.getMessage()).append("\n");
                    }
                }
                
                // ========== 步骤3：更新UI ==========
                directoryCheckLog.append("\n===== 复制").append(copySuccess ? "成功" : "失败").append(" =====\n");
                
                final String finalMessage = copySuccess ? 
                    "视频文件已成功复制到目标应用私有目录\n\n" +
                    "源文件：" + sourceVideo.getAbsolutePath() + "\n" +
                    "目标文件：" + targetVideo.getAbsolutePath() + "\n" +
                    "文件大小：" + formatFileSize(sourceVideo.length()) + "\n\n" +
                    "验证结果：✅ cp命令复制成功\n\n" +
                    directoryCheckLog.toString() :
                    "视频复制失败\n\n" +
                    "源文件：" + sourceVideo.getAbsolutePath() + "\n" +
                    "目标文件：" + targetVideo.getAbsolutePath() + "\n\n" +
                    "验证结果：❌ cp命令复制失败\n\n" +
                    directoryCheckLog.toString();
                
                final boolean finalCopySuccess = copySuccess;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showDetailedErrorDialog(finalMessage, finalCopySuccess ? "复制成功" : "复制失败");
                });

            } catch (Exception e) {
                Log.e("MainActivity", "复制操作异常", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showDetailedErrorDialog("复制操作异常：" + e.getMessage(), "错误");
                });
            }
        }).start();
    }

    /**
     * 显示详细错误对话框
     */
    private void showDetailedErrorDialog(String message, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(12);
        textView.setText(message);
        textView.setPadding(20, 20, 20, 20);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("确定", null);
        builder.setNegativeButton("复制", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("错误信息", message);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(MainActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public static class VideoFileInfo {
        public String fileName;
        public String filePath;
        public long fileSize;
        public long lastModified;
    }

    public class VideoFileAdapter extends android.widget.BaseAdapter {
        private Context context;
        private ArrayList<VideoFileInfo> videoList;

        public VideoFileAdapter(Context context, ArrayList<VideoFileInfo> videoList) {
            this.context = context;
            this.videoList = videoList;
        }

        @Override
        public int getCount() {
            return videoList.size();
        }

        @Override
        public Object getItem(int position) {
            return videoList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = android.view.View.inflate(context, android.R.layout.simple_list_item_2, null);
            }

            VideoFileInfo videoInfo = videoList.get(position);

            TextView tv1 = convertView.findViewById(android.R.id.text1);
            TextView tv2 = convertView.findViewById(android.R.id.text2);

            tv1.setText(videoInfo.fileName);
            tv1.setTextSize(14);

            String sizeStr = formatFileSize(videoInfo.fileSize);
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(videoInfo.lastModified));
            tv2.setText("大小: " + sizeStr + " | 修改: " + dateStr);
            tv2.setTextSize(12);
            tv2.setTextColor(0xFF666666);

            return convertView;
        }
    }
}
