package hzkitty.android.ocr;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import hzkitty.android.ocr.adapter.ModelListAdapter;
import hzkitty.android.ocr.model.OcrModel;

import io.github.hzkitty.RapidOCR;
import io.github.hzkitty.entity.OcrResult;
import io.github.hzkitty.entity.OcrConfig;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private Button btnSelectImage;
    private OcrImageView ivSelectedImage;
    private TextView tvOcrResult;
    private RecyclerView rvModelList;
    private RapidOCR rapidOCR;
    
    private List<OcrModel> modelList;
    private ModelListAdapter modelListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI组件
        btnSelectImage = findViewById(R.id.btn_select_image);
        ivSelectedImage = findViewById(R.id.iv_selected_image);
        tvOcrResult = findViewById(R.id.tv_ocr_result);
        rvModelList = findViewById(R.id.rv_model_list);
        
        // 初始化模型列表
        initModelList();
        
        // 初始化模型选择RecyclerView
        initModelRecyclerView();

        // 初始化RapidOCR
        initRapidOCR();

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

    // 初始化模型列表
    private void initModelList() {
        modelList = new ArrayList<>();
        modelList.add(new OcrModel("中文识别模型", "ch_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel("拉丁文字识别模型", "latin_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel("阿拉伯文字识别模型", "arabic_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel("斯拉夫文字识别模型", "eslav_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel("韩文字识别模型", "korean_PP-OCRv5_rec_mobile_infer.onnx"));
        
        // 设置只有中文模型默认选中，其他模型默认不选中
        for (int i = 1; i < modelList.size(); i++) {
            modelList.get(i).setSelected(false);
        }
    }
    
    // 初始化模型选择RecyclerView
    private void initModelRecyclerView() {
        modelListAdapter = new ModelListAdapter(modelList);
        rvModelList.setLayoutManager(new LinearLayoutManager(this));
        rvModelList.setAdapter(modelListAdapter);
        
        // 设置模型选择监听器
        modelListAdapter.setOnModelSelectListener(new ModelListAdapter.OnModelSelectListener() {
            @Override
            public void onModelSelect(OcrModel model, boolean isSelected) {
                // 当模型选择状态变化时，重新初始化RapidOCR
                initRapidOCR();
            }
        });
    }
    
    // 初始化或重新配置RapidOCR
    private void initRapidOCR() {
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
            
            // 设置检测模块配置
            OcrConfig.DetConfig detConfig = config.getDet();
            detConfig.setModelPath("ch_PP-OCRv5_mobile_det.onnx"); // 使用v5检测模型
            
            // 查找第一个选中的模型作为默认模型
            boolean hasSelectedModel = false;
            for (OcrModel model : modelList) {
                if (model.isSelected()) {
                    if (!hasSelectedModel) {
                        // 设置第一个选中的模型为默认模型
                        recConfig.setModelPath(model.getModelPath());
                        hasSelectedModel = true;
                    } else {
                        // 添加其他选中的模型
                        OcrConfig.RecConfig additionalRecConfig = new OcrConfig.RecConfig();
                        additionalRecConfig.setRecBatchNum(4);
                        additionalRecConfig.setRecImgShape(new int[]{3, 48, 240});
                        additionalRecConfig.setIntraOpNumThreads(4);
                        additionalRecConfig.setInterOpNumThreads(2);
                        additionalRecConfig.setModelPath(model.getModelPath());
                        config.addRecConfig(additionalRecConfig);
                    }
                }
            }
            
            if (!hasSelectedModel) {
                // 如果没有选中任何模型，默认使用中文模型
                recConfig.setModelPath("ch_PP-OCRv5_rec_mobile_infer.onnx");
            }
            
            // 重新创建RapidOCR实例
            rapidOCR = RapidOCR.create(this, config);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "OCR初始化失败", Toast.LENGTH_SHORT).show();
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
                        
                        // 将OCR结果传递给OcrImageView，以便在图片上显示文本框和支持文本选择
                        ivSelectedImage.setOcrResults(finalOcrResult.getRecRes());
                    }
                } finally {
                    // 恢复按钮状态
                    btnSelectImage.setEnabled(true);
                }
            });
        }).start();
    }


}