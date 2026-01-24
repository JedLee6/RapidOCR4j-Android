package hzkitty.android.ocr;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.github.hzkitty.RapidOCR;
import io.github.hzkitty.entity.OcrResult;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private Button btnSelectImage;
    private ImageView ivSelectedImage;
    private TextView tvOcrResult;
    private RapidOCR rapidOCR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI组件
        btnSelectImage = findViewById(R.id.btn_select_image);
        ivSelectedImage = findViewById(R.id.iv_selected_image);
        tvOcrResult = findViewById(R.id.tv_ocr_result);

        // 初始化RapidOCR
        try {
            rapidOCR = RapidOCR.create(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "OCR初始化失败", Toast.LENGTH_SHORT).show();
        }

        // 设置按钮点击事件
        btnSelectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectImage();
            }
        });
    }

    // 选择图片
    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    // 处理图片选择结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                // 获取图片并显示
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                ivSelectedImage.setImageBitmap(bitmap);

                // 执行OCR识别
                performOCR(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 执行OCR识别
    private void performOCR(Bitmap bitmap) {
        // 显示加载状态
        tvOcrResult.setText("正在识别...");
        btnSelectImage.setEnabled(false); // 禁用按钮避免重复点击
        
        // 创建子线程执行OCR识别
        new Thread(() -> {
            OcrResult ocrResult = null;
            Exception exception = null;
            
            try {
                // 执行OCR识别
                ocrResult = rapidOCR.run(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
                exception = e;
            }
            
            // 获取最终结果和异常信息
            final OcrResult finalOcrResult = ocrResult;
            final Exception finalException = exception;
            
            // 在主线程更新UI
            runOnUiThread(() -> {
                try {
                    if (finalException != null) {
                        // 识别失败
                        tvOcrResult.setText("识别失败: " + finalException.getMessage());
                    } else {
                        // 识别成功

                        // 同时在文本区域显示识别结果
                        tvOcrResult.setText(finalOcrResult.getStrRes());
                    }
                } finally {
                    // 恢复按钮状态
                    btnSelectImage.setEnabled(true);
                }
            });
        }).start();
    }


}