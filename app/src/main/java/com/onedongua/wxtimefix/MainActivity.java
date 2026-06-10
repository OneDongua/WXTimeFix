package com.onedongua.wxtimefix;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SELECT_DIR = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 1003;

    private ConstraintLayout banner;
    private EditText etPath;
    private EditText etStartPosition;
    private EditText etEndPosition;
    private TextView tvFileCount;
    private Button btnSelectPath;
    private Button btnStartFix;
    private Button btnRefreshMedia;
    private ImageButton btnMore;

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "WXTimeFixPrefs";
    private static final String KEY_PATH = "path";
    private static final String KEY_START_POSITION = "startPosition";
    private static final String KEY_END_POSITION = "endPosition";
    private static final String DEFAULT_PATH = "/sdcard/Pictures/Weixin";

    // 匹配 mmexport{毫秒时间戳}.{后缀名} 和 wx_camera_{毫秒时间戳}.{后缀名} 格式
    private static final Pattern FILE_PATTERN_MMEXPORT = Pattern.compile("^mmexport(\\d{13})\\.(.+)$");
    private static final Pattern FILE_PATTERN_WXCAMERA = Pattern.compile("^wx_camera_(\\d{13})\\.(.+)$");

    // 创建线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        adaptInsets();
        initSharedPreferences();
        loadSavedData();
        setupListeners();
        checkAndRequestManageStoragePermission();
    }


    private void initViews() {
        banner = findViewById(R.id.banner);
        etPath = findViewById(R.id.etPath);
        etStartPosition = findViewById(R.id.etStartPosition);
        etEndPosition = findViewById(R.id.etEndPosition);
        tvFileCount = findViewById(R.id.tvFileCount);
        btnSelectPath = findViewById(R.id.btnSelectPath);
        btnStartFix = findViewById(R.id.btnStartFix);
        btnRefreshMedia = findViewById(R.id.btnRefreshMedia);
        btnMore = findViewById(R.id.btnMore);
    }

    private void adaptInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(banner, (v, insets) -> {
            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutOutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBarsInsets.left + displayCutOutInsets.left,
                    systemBarsInsets.top,
                    systemBarsInsets.right + displayCutOutInsets.right,
                    systemBarsInsets.bottom);
            return insets;
        });
    }


    private void initSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void loadSavedData() {
        String savedPath = sharedPreferences.getString(KEY_PATH, DEFAULT_PATH);
        String savedStartPos = sharedPreferences.getString(KEY_START_POSITION, "");
        String savedEndPos = sharedPreferences.getString(KEY_END_POSITION, "");

        if (!savedPath.isEmpty()) {
            etPath.setText(savedPath);
            updateFileCount(savedPath);
        }
        if (!savedStartPos.isEmpty()) {
            etStartPosition.setText(savedStartPos);
        }
        if (!savedEndPos.isEmpty()) {
            etEndPosition.setText(savedEndPos);
        }
    }

    private void saveData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_PATH, etPath.getText().toString().trim());
        editor.putString(KEY_START_POSITION, etStartPosition.getText().toString().trim());
        editor.putString(KEY_END_POSITION, etEndPosition.getText().toString().trim());
        editor.apply();
    }

    private void setupListeners() {
        // 选择路径按钮
        btnSelectPath.setOnClickListener(v -> selectDirectory());

        // 开始修复按钮
        btnStartFix.setOnClickListener(v -> startFix());

        // 刷新媒体库按钮
        btnRefreshMedia.setOnClickListener(v -> refreshMediaStore());

        // 更多按钮
        btnMore.setOnClickListener(v -> showAboutDialog());

        // 输入框变化时自动保存
        etPath.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveData();
                updateFileCount(etPath.getText().toString().trim());
            }
        });

        etStartPosition.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveData();
            }
        });

        etEndPosition.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveData();
            }
        });
    }

    /**
     * 检查并请求所有文件访问权限
     */
    private void checkAndRequestManageStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
            if (!Environment.isExternalStorageManager()) {
                // 跳转到设置页面让用户授予权限
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
            }
        } else {
            // Android 10 及以下使用传统存储权限
            requestLegacyStoragePermissions();
        }
    }

    /**
     * 请求传统存储权限（Android 10 及以下）
     */
    private void requestLegacyStoragePermissions() {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "存储权限被拒绝，应用可能无法正常工作", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "权限获取成功", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void selectDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_SELECT_DIR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 处理所有文件访问权限请求结果
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "已获得所有文件访问权限", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "未获得所有文件访问权限，应用可能无法正常工作", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }

        if (requestCode == REQUEST_CODE_SELECT_DIR && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // 获取真实路径
                String path = FileUtils.getPathFromUri(this, uri);
                if (path != null) {
                    etPath.setText(path);
                    saveData();
                    updateFileCount(path);
                } else {
                    Toast.makeText(this, "无法获取路径", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void updateFileCount(String directoryPath) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            tvFileCount.setText("匹配的文件数量: 0");
            return;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            tvFileCount.setText("匹配的文件数量: 0");
            return;
        }

        int count = countMatchingFiles(directory);
        tvFileCount.setText("匹配的文件数量: " + count);
    }

    private int countMatchingFiles(File directory) {
        int count = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    // 尝试匹配 mmexport 格式
                    Matcher matcher = FILE_PATTERN_MMEXPORT.matcher(fileName);
                    if (!matcher.matches()) {
                        // 尝试匹配 wx_camera 格式
                        matcher = FILE_PATTERN_WXCAMERA.matcher(fileName);
                    }
                    if (matcher.matches()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private void startFix() {
        String path = etPath.getText().toString().trim();
        String startPosStr = etStartPosition.getText().toString().trim();
        String endPosStr = etEndPosition.getText().toString().trim();

        if (path.isEmpty()) {
            Toast.makeText(this, "请先选择目录路径", Toast.LENGTH_SHORT).show();
            return;
        }

        File directory = new File(path);
        if (!directory.exists() || !directory.isDirectory()) {
            Toast.makeText(this, "目录不存在或无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取所有匹配的文件，用于确定起始和终止位置
        File[] allFiles = directory.listFiles();
        if (allFiles == null || allFiles.length == 0) {
            Toast.makeText(this, "目录中没有文件", Toast.LENGTH_SHORT).show();
            return;
        }

        // 收集所有匹配文件的文件名和时间戳
        java.util.List<File> matchingFiles = new java.util.ArrayList<>();
        java.util.List<Long> timestamps = new java.util.ArrayList<>();

        for (File file : allFiles) {
            if (!file.isFile()) {
                continue;
            }

            String fileName = file.getName();
            Matcher matcher = FILE_PATTERN_MMEXPORT.matcher(fileName);
            if (!matcher.matches()) {
                matcher = FILE_PATTERN_WXCAMERA.matcher(fileName);
            }

            if (matcher.matches()) {
                try {
                    long timestamp = Long.parseLong(matcher.group(1));
                    matchingFiles.add(file);
                    timestamps.add(timestamp);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }

        if (matchingFiles.isEmpty()) {
            Toast.makeText(this, "没有找到匹配的文件", Toast.LENGTH_SHORT).show();
            return;
        }

        // 对时间戳排序，确定最小和最大值
        java.util.Collections.sort(timestamps);
        long minTimestamp = timestamps.get(0);
        long maxTimestamp = timestamps.get(timestamps.size() - 1);

        // 解析起始位置，如果为空则使用最小值
        long startPos;
        if (startPosStr.isEmpty()) {
            startPos = minTimestamp;
        } else {
            try {
                startPos = Long.parseLong(startPosStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "起始位置格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 解析终止位置，如果为空则使用最大值
        long endPos;
        if (endPosStr.isEmpty()) {
            endPos = maxTimestamp;
        } else {
            try {
                endPos = Long.parseLong(endPosStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "终止位置格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (startPos >= endPos) {
            Toast.makeText(this, "起始位置必须小于终止位置", Toast.LENGTH_SHORT).show();
            return;
        }

        // 在线程池中执行批量修复
        executorService.execute(() -> {
            int processedCount = fixFilesInDirectory(directory, startPos, endPos);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "已处理 " + processedCount + " 个文件", Toast.LENGTH_SHORT).show();
                updateFileCount(path);
            });
        });
    }

    private int fixFilesInDirectory(File directory, long startPos, long endPos) {
        int processedCount = 0;
        File[] files = directory.listFiles();

        if (files == null) {
            return 0;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String fileName = file.getName();
            Matcher matcher = FILE_PATTERN_MMEXPORT.matcher(fileName);
            if (!matcher.matches()) {
                matcher = FILE_PATTERN_WXCAMERA.matcher(fileName);
            }

            if (!matcher.matches()) {
                continue;
            }

            try {
                long timestamp = Long.parseLong(matcher.group(1));

                // 检查时间戳是否在范围内
                if (timestamp < startPos || timestamp > endPos) {
                    continue;
                }

                // 设置文件的修改时间和创建时间
                boolean modified = file.setLastModified(timestamp);

                if (modified) {
                    processedCount++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return processedCount;
    }

    private void refreshMediaStore() {
        String path = etPath.getText().toString().trim();

        if (path.isEmpty()) {
            Toast.makeText(this, "请先选择目录路径", Toast.LENGTH_SHORT).show();
            return;
        }

        File directory = new File(path);
        if (!directory.exists() || !directory.isDirectory()) {
            Toast.makeText(this, "目录不存在或无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 发送广播刷新媒体库
        executorService.execute(() -> {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        // 发送媒体扫描广播
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(Uri.fromFile(file));
                        sendBroadcast(mediaScanIntent);
                    }
                }
            }

            runOnUiThread(() -> Toast.makeText(MainActivity.this, "媒体库刷新完成", Toast.LENGTH_SHORT).show());
        });
    }

    /**
     * 显示关于对话框
     */
    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        //  Inflate 自定义布局
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_about, null);

        // 设置应用图标
        ImageView ivAppIcon = dialogView.findViewById(R.id.ivAppIcon);
        ivAppIcon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));

        // 设置应用名称
        TextView tvAppName = dialogView.findViewById(R.id.tvAppName);
        tvAppName.setText(getString(R.string.app_name));

        // 设置应用版本
        TextView tvAppVersion = dialogView.findViewById(R.id.tvAppVersion);
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            int versionCode = packageInfo.versionCode;
            tvAppVersion.setText("版本: " + versionName + " (" + versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            tvAppVersion.setText("版本: 未知");
        }

        // 设置作者信息
        TextView tvAppAuthor = dialogView.findViewById(R.id.tvAppAuthor);
        tvAppAuthor.setText("作者: 一個冬瓜 & Qwen");

        builder.setView(dialogView);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭线程池
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
