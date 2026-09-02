package com.example.videoplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private VideoView videoView;
    private SeekBar seekBar;
    private ImageButton btnPlayPause, btnPlayPauseMain, btnRewind, btnForward;
    private ProgressBar progressBar;
    private TextView tvCurrentTime, tvDuration, tvFilePath;
    private Spinner spinnerSpeed;
    private Button btnSelectFile;

    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final int REQUEST_PICK_VIDEO = 101;

    private boolean isPlaying = false;
    private int currentSpeedIndex = 0;
    private float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private String currentVideoPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        setupSpeedSpinner();
        checkPermissions();
        
        // 尝试加载内置测试视频
        loadTestVideo();
    }

    private void initViews() {
        videoView = findViewById(R.id.videoView);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPlayPauseMain = findViewById(R.id.btnPlayPauseMain);
        btnRewind = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        progressBar = findViewById(R.id.progressBar);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvDuration = findViewById(R.id.tvDuration);
        tvFilePath = findViewById(R.id.tvFilePath);
        spinnerSpeed = findViewById(R.id.spinnerSpeed);
        btnSelectFile = findViewById(R.id.btnSelectFile);
    }

    private void setupListeners() {
        // 播放/暂停按钮
        View.OnClickListener playPauseListener = v -> togglePlayPause();
        btnPlayPause.setOnClickListener(playPauseListener);
        btnPlayPauseMain.setOnClickListener(playPauseListener);

        // 进度条
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && videoView != null) {
                    videoView.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 快退
        btnRewind.setOnClickListener(v -> {
            if (videoView != null) {
                int newPosition = Math.max(0, videoView.getCurrentPosition() - 10000);
                videoView.seekTo(newPosition);
            }
        });

        // 快进
        btnForward.setOnClickListener(v -> {
            if (videoView != null) {
                int newPosition = Math.min(videoView.getDuration(), videoView.getCurrentPosition() + 10000);
                videoView.seekTo(newPosition);
            }
        });

        // 选择文件
        btnSelectFile.setOnClickListener(v -> {
            if (checkPermissions()) {
                openVideoPicker();
            } else {
                requestPermissions();
            }
        });

        // 视频完成监听
        videoView.setOnCompletionListener(mp -> {
            isPlaying = false;
            updatePlayPauseIcon();
            seekBar.setProgress(0);
            tvCurrentTime.setText("00:00");
        });

        // 视频加载完成
        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            tvDuration.setText(formatTime(mp.getDuration()));
            seekBar.setMax(mp.getDuration());
        });

        // 视频错误
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "视频播放出错", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return true;
        });
    }

    private void setupSpeedSpinner() {
        List<String> speedLabels = new ArrayList<>();
        for (float speed : speeds) {
            speedLabels.add(speed + "x");
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, speedLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSpeed.setAdapter(adapter);
        
        spinnerSpeed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentSpeedIndex = position;
                if (videoView != null && isPlaying) {
                    videoView.setSpeed(speeds[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void togglePlayPause() {
        if (videoView == null || currentVideoPath.isEmpty()) {
            Toast.makeText(this, "请先选择视频文件", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPlaying) {
            videoView.pause();
            isPlaying = false;
        } else {
            videoView.start();
            isPlaying = true;
            videoView.setSpeed(speeds[currentSpeedIndex]);
        }
        updatePlayPauseIcon();
    }

    private void updatePlayPauseIcon() {
        int resId = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        btnPlayPause.setImageResource(resId);
        btnPlayPauseMain.setImageResource(resId);
    }

    private void openVideoPicker() {
        android.content.Intent intent = new Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String path = getRealPathFromURI(uri);
                if (path != null) {
                    loadVideo(path);
                }
            }
        }
    }

    private String getRealPathFromURI(Uri uri) {
        String[] projection = {android.provider.MediaStore.Video.Media.DATA};
        android.content.CursorLoader loader = new android.content.CursorLoader(this, uri, projection, null, null, null);
        android.database.Cursor cursor = loader.loadInBackground();
        
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(columnIndex);
            cursor.close();
            return path;
        }
        return null;
    }

    private void loadVideo(String path) {
        currentVideoPath = path;
        tvFilePath.setText(path);
        progressBar.setVisibility(View.VISIBLE);
        videoView.setVideoPath(path);
        videoView.start();
        isPlaying = true;
        updatePlayPauseIcon();
        
        // 启动进度更新
        startProgressUpdater();
    }

    private void startProgressUpdater() {
        new Thread(() -> {
            while (isPlaying && videoView != null) {
                try {
                    Thread.sleep(500);
                    runOnUiThread(() -> {
                        if (videoView != null) {
                            int currentPosition = videoView.getCurrentPosition();
                            seekBar.setProgress(currentPosition);
                            tvCurrentTime.setText(formatTime(currentPosition));
                        }
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = milliseconds / 1000;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private boolean checkPermissions() {
        int readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return readPermission == PackageManager.PERMISSION_GRANTED && 
               writePermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, 
            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
            REQUEST_STORAGE_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadTestVideo();
            } else {
                Toast.makeText(this, "需要存储权限才能访问视频文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadTestVideo() {
        File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File testVideo = new File(externalDir, "test.mp4");
        
        if (testVideo.exists()) {
            loadVideo(testVideo.getAbsolutePath());
        } else {
            tvFilePath.setText("请从存储中选择视频文件或放置 test.mp4 到 DCIM 目录");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoView.suspend();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && isPlaying) {
            videoView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.release();
        }
    }
}