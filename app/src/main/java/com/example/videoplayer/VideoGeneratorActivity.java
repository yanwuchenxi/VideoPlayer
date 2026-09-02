package com.example.videoplayer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 视频生成器测试活动
 * 用于生成测试用的视频帧序列
 */
public class VideoGeneratorActivity extends AppCompatActivity {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int FRAMES = 300; // 10秒 @ 30fps

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_generator);

        Button btnGenerate = findViewById(R.id.btnGenerate);
        TextView tvStatus = findViewById(R.id.tvStatus);

        btnGenerate.setOnClickListener(v -> {
            tvStatus.setText("正在生成测试视频...");
            new Thread(() -> {
                generateTestFrames(tvStatus);
            }).start();
        });
    }

    private void generateTestFrames(TextView tvStatus) {
        try {
            File outputDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM), "VideoPlayerTest");
            outputDir.mkdirs();

            Paint paint = new Paint();
            paint.setTextSize(100);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);

            for (int i = 0; i < FRAMES; i++) {
                Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                
                // 绘制渐变背景
                int color = Color.rgb(
                    (int)(100 + 155 * Math.sin(i * 0.1)),
                    (int)(100 + 155 * Math.cos(i * 0.1)),
                    50
                );
                canvas.drawColor(color);
                
                // 绘制帧数
                canvas.drawText(String.format("Frame %d / %d", i + 1, FRAMES),
                    WIDTH / 2, HEIGHT / 2, paint);
                
                // 保存为 PNG
                File frameFile = new File(outputDir, String.format("frame_%04d.png", i));
                FileOutputStream fos = new FileOutputStream(frameFile);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                bitmap.recycle();
                
                // 更新进度
                final int progress = i;
                runOnUiThread(() -> 
                    tvStatus.setText(String.format("生成中... %d%%", (progress * 100) / FRAMES))
                );
            }

            runOnUiThread(() -> {
                tvStatus.setText("测试帧已生成! 使用 FFmpeg 转换为视频:\n" +
                    "ffmpeg -framerate 30 -i frame_%04d.png -c:v libx264 -r 30 -pix_fmt yuv420p output.mp4");
                Toast.makeText(this, "帧生成完成!", Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            runOnUiThread(() -> {
                tvStatus.setText("生成失败: " + e.getMessage());
                Toast.makeText(this, "生成失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }
}