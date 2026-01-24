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
import io.github.hzkitty.entity.OcrConfig;

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

        // 初始化RapidOCR，配置参数以提高识别速度
        try {
            OcrConfig config = new OcrConfig();
            
            // 全局配置优化
            OcrConfig.GlobalConfig globalConfig = config.getGlobal();
            globalConfig.setUseCls(false); // 关闭分类模块（如果图片方向已知）
            globalConfig.setMaxSideLen(800); // 限制图片最大边长（减小图片尺寸）
            globalConfig.setReturnWordBox(false); // 关闭返回单词级别的框
            
            // 识别模块配置优化
            OcrConfig.RecConfig recConfig = config.getRec();
            recConfig.setRecBatchNum(4); // 增加批处理大小
            recConfig.setRecImgShape(new int[]{3, 48, 240}); // 减小识别模型输入宽度
            recConfig.setIntraOpNumThreads(4); // 设置推理线程数
            recConfig.setInterOpNumThreads(2); // 设置操作间线程数
            // 使用latin v5识别模型(支持拉丁文字识别)
            // 注意：请先手动下载latin_PP-OCRv5_rec_mobile_infer.onnx模型文件并放入OcrLibrary/src/main/assets目录
            recConfig.setModelPath("latin_PP-OCRv5_rec_mobile_infer.onnx"); // 使用latin v5识别模型(支持英文、数字、符号等拉丁文字)
            
            // 检测模块配置优化
            OcrConfig.DetConfig detConfig = config.getDet();
            detConfig.setModelPath("ch_PP-OCRv5_mobile_det.onnx"); // 使用v5检测模型
            detConfig.setBoxThresh(0.5f); // 提高检测阈值，减少检测框数量
            detConfig.setUnclipRatio(1.2f); // 调整文本框膨胀系数
            

            
            rapidOCR = RapidOCR.create(this, config);
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

                        // 构建包含耗时信息的识别结果
                        StringBuilder resultBuilder = new StringBuilder();
                        resultBuilder.append("识别结果：\n")
                                .append(finalOcrResult.getStrRes())
                                .append("\n\n")
                                .append("耗时统计：\n")
                                .append("总耗时：").append(String.format("%.2f", finalOcrResult.getElapseTime() * 1000)).append("ms\n")
                                .append("检测耗时：").append(String.format("%.2f", finalOcrResult.getDetTime() * 1000)).append("ms\n")
                                .append("分类耗时：").append(String.format("%.2f", finalOcrResult.getClsTime() * 1000)).append("ms\n")
                                .append("识别耗时：").append(String.format("%.2f", finalOcrResult.getRecTime() * 1000)).append("ms");
                        
                        // 在文本区域显示识别结果和耗时信息
                        tvOcrResult.setText(resultBuilder.toString());
                    }
                } finally {
                    // 恢复按钮状态
                    btnSelectImage.setEnabled(true);
                }
            });
        }).start();
    }


}