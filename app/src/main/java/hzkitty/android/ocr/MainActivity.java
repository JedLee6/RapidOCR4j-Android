package hzkitty.android.ocr;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hzkitty.android.ocr.adapter.ModelListAdapter;
import hzkitty.android.ocr.adapter.RecResultAdapter;
import hzkitty.android.ocr.model.OcrModel;

import io.github.hzkitty.RapidOCR;
import io.github.hzkitty.entity.OcrResult;
import io.github.hzkitty.entity.OcrConfig;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1;
    private static final int REQUEST_IMAGE_CAPTURE = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    
    // 用于保存拍照后的图片路径
    private String currentPhotoPath;

    private Button btnSelectImage;
    private Button btnTakePhoto;
    private OcrImageView ivSelectedImage;
    private TextView tvOcrResult;
    private RecyclerView rvModelList;
    private RadioGroup rgDetModel;
    private Switch swTextVisible;
    private SeekBar sbTextOpacity;
    private Switch swMergeText;
    private RecyclerView rvRecResult;
    private RecResultAdapter recResultAdapter;
    private RapidOCR rapidOCR;
    
    // 保存上次的识别结果，用于开关切换时重新处理
    private Bitmap lastBitmap;
    private OcrResult lastOcrResult;
    
    private List<OcrModel> modelList;
    private ModelListAdapter modelListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI组件
        initUI();
        // 初始化模型列表
        initModelList();
        // 初始化模型列表RecyclerView
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

        // 设置拍照按钮点击事件
        btnTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });

        // 设置检测模型选择监听器
        rgDetModel.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // 当检测模型选择变化时，重新初始化RapidOCR
                initRapidOCR();
            }
        });
        
        // 设置文本显示开关的监听器
        swTextVisible.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ivSelectedImage.setTextVisible(isChecked);
            }
        });
        
        // 设置文本透明度滑块的监听器
        sbTextOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 将进度值（0-100）转换为透明度值（0.0-1.0）
                float opacity = progress / 100.0f;
                ivSelectedImage.setTextOpacity(opacity);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 开始拖动时不需要特殊处理
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 停止拖动时不需要特殊处理
            }
        });
        
        // 初始化识别结果详细信息RecyclerView
        rvRecResult = findViewById(R.id.rv_rec_result);
        rvRecResult.setLayoutManager(new LinearLayoutManager(this));
        recResultAdapter = new RecResultAdapter(Collections.emptyList());
        rvRecResult.setAdapter(recResultAdapter);
        
        // 智能合并文本开关的监听器
        swMergeText.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // 当开关状态改变时，重新处理当前的OCR结果
                if (lastBitmap != null && lastOcrResult != null) {
                    // 重新构建识别结果并显示
                    String ocrText = lastOcrResult.getStrRes();
                    if (isChecked) {
                        ocrText = mergeTextSmartly(ocrText);
                    }
                    
                    StringBuilder resultBuilder = new StringBuilder();
                    resultBuilder.append(getString(R.string.ocr_result)).append("\n")
                            .append(ocrText)
                            .append("\n\n")
                            .append(getString(R.string.time_statistics)).append("\n")
                            .append(getString(R.string.total_time)).append(String.format("%.2f", lastOcrResult.getElapseTime() * 1000)).append("ms\n")
                            .append(getString(R.string.detect_time)).append(String.format("%.2f", lastOcrResult.getDetTime() * 1000)).append("ms\n")
                            .append(getString(R.string.classify_time)).append(String.format("%.2f", lastOcrResult.getClsTime() * 1000)).append("ms\n")
                            .append(getString(R.string.recognize_time)).append(String.format("%.2f", lastOcrResult.getRecTime() * 1000)).append("ms");
                    
                    tvOcrResult.setText(resultBuilder.toString());
                }
            }
        });
    }

    /**
     * 初始化UI组件
     */
    private void initUI() {
        btnSelectImage = findViewById(R.id.btn_select_image);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        ivSelectedImage = findViewById(R.id.iv_selected_image);
        tvOcrResult = findViewById(R.id.tv_ocr_result);
        rvModelList = findViewById(R.id.rv_model_list);
        rgDetModel = findViewById(R.id.rg_det_model);
        swTextVisible = findViewById(R.id.sw_text_visible);
        sbTextOpacity = findViewById(R.id.sb_text_opacity);
        swMergeText = findViewById(R.id.sw_merge_text);
    }

    /**
     * 初始化模型列表
     */
    private void initModelList() {
        modelList = new ArrayList<>();
        modelList.add(new OcrModel(getString(R.string.model_chinese), "ch_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_latin), "latin_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_arabic), "arabic_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_slavic), "eslav_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_korean), "korean_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_cyrillic), "cyrillic_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_thai), "th_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_tamil), "ta_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_english), "en_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_greek), "el_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_devanagari), "devanagari_PP-OCRv5_rec_mobile_infer.onnx"));
        modelList.add(new OcrModel(getString(R.string.model_chinese_server), "ch_PP-OCRv5_rec_server_infer.onnx"));
        
        // 设置只有中文模型默认选中，其他模型默认不选中
        for (int i = 1; i < modelList.size(); i++) {
            modelList.get(i).setSelected(false);
        }
    }

    /**
     * 获取用户选择的检测模型
     * @return 检测模型路径
     */
    private String getSelectedDetModel() {
        int checkedId = rgDetModel.getCheckedRadioButtonId();
        return (String) findViewById(checkedId).getTag();
    }

    // 选择图片
    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    // 拍照
    private void takePhoto() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            dispatchTakePictureIntent();
        }
    }
    
    // 启动相机应用并保存原始图片
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 确保有相机应用可以处理这个意图
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // 创建一个文件来保存图片
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // 处理异常
                ex.printStackTrace();
                Toast.makeText(this, getString(R.string.msg_create_file_failed), Toast.LENGTH_SHORT).show();
            }
            // 如果文件创建成功，继续处理
            if (photoFile != null) {
                // 使用FileProvider创建URI，解决FileUriExposedException
                Uri photoURI = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                // 授予临时权限
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            Toast.makeText(this, getString(R.string.msg_no_camera_app), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 创建一个临时图片文件
    private File createImageFile() throws IOException {
        // 创建一个唯一的文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* 前缀 */
                ".jpg",         /* 后缀 */
                storageDir      /* 目录 */
        );
        
        // 保存文件路径
        currentPhotoPath = image.getAbsolutePath();
        return image;
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
                Toast.makeText(this, getString(R.string.msg_image_load_failed), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // 从文件中加载高分辨率图片
            try {
                // 设置BitmapFactory选项以加载原始分辨率图片
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888; // 使用高质量配置
                options.inScaled = false; // 不进行缩放
                options.inDither = true; // 启用抖动以提高质量
                
                Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, options);
                if (bitmap != null) {
                    ivSelectedImage.setImageBitmap(bitmap);
                    performOCR(bitmap);
                } else {
                    Toast.makeText(this, getString(R.string.msg_image_load_failed), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "处理图片失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
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
            detConfig.setModelPath(getSelectedDetModel()); // 使用用户选择的检测模型
            
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
    
    // 智能合并文本的方法
    private String mergeTextSmartly(String originalText) {
        if (originalText == null || originalText.isEmpty()) {
            return originalText;
        }
        
        // 按行分割文本
        String[] lines = originalText.split("\\n");
        if (lines.length <= 1) {
            return originalText;
        }
        
        StringBuilder mergedText = new StringBuilder();
        
        // 定义分隔符号集合
        String[] separators = {"。", "！", "？", "；", ".", "!", "?", ";", "：", ":"};
        
        for (int i = 0; i < lines.length; i++) {
            String currentLine = lines[i].trim();
            if (currentLine.isEmpty()) {
                continue;
            }
            
            mergedText.append(currentLine);
            
            // 检查当前行是否以分隔符号结尾
            boolean endsWithSeparator = false;
            for (String separator : separators) {
                if (currentLine.endsWith(separator)) {
                    endsWithSeparator = true;
                    break;
                }
            }
            
            // 如果不是最后一行且当前行不以分隔符号结尾，则合并下一行（用空格连接）
            if (i < lines.length - 1 && !endsWithSeparator) {
                mergedText.append(" ");
            } else {
                mergedText.append("\n");
            }
        }
        
        return mergedText.toString().trim();
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
                        String ocrText = finalOcrResult.getStrRes();
                        
                        // 根据开关状态决定是否智能合并文本
                        if (swMergeText.isChecked()) {
                            ocrText = mergeTextSmartly(ocrText);
                        }
                        
                        resultBuilder.append(getString(R.string.ocr_result)).append("\n")
                                .append(ocrText)
                                .append("\n\n")
                                .append(getString(R.string.time_statistics)).append("\n")
                                .append(getString(R.string.total_time)).append(String.format("%.2f", finalOcrResult.getElapseTime() * 1000)).append("ms\n")
                                .append(getString(R.string.detect_time)).append(String.format("%.2f", finalOcrResult.getDetTime() * 1000)).append("ms\n")
                                .append(getString(R.string.classify_time)).append(String.format("%.2f", finalOcrResult.getClsTime() * 1000)).append("ms\n")
                                .append(getString(R.string.recognize_time)).append(String.format("%.2f", finalOcrResult.getRecTime() * 1000)).append("ms");
                        
                        // 在文本区域显示识别结果和耗时信息
                        tvOcrResult.setText(resultBuilder.toString());
                        
                        // 更新识别结果详细信息
                        if (finalOcrResult.getRecRes() != null && !finalOcrResult.getRecRes().isEmpty()) {
                            recResultAdapter.updateData(finalOcrResult.getRecRes());
                            rvRecResult.setVisibility(View.VISIBLE);
                        } else {
                            rvRecResult.setVisibility(View.GONE);
                        }
                        
                        // 将OCR结果传递给OcrImageView，以便在图片上显示文本框和支持文本选择
                        ivSelectedImage.setOcrResults(finalOcrResult.getRecRes());
                        
                        // 保存当前的识别结果，用于开关切换时重新处理
                        lastBitmap = bitmap;
                        lastOcrResult = finalOcrResult;
                    }
                } finally {
                    // 恢复按钮状态
                    btnSelectImage.setEnabled(true);
                }
            });
        }).start();
    }
}