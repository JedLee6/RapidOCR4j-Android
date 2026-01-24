package io.github.hzkitty.example;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import hzkitty.android.ocr.R;

import java.io.InputStream;
import java.util.List;

import io.github.hzkitty.RapidOCR;
import io.github.hzkitty.entity.OcrConfig;
import io.github.hzkitty.entity.OcrResult;
import io.github.hzkitty.entity.RecResult;

public class MultiLanguageOcrExample extends AppCompatActivity {

    private static final String TAG = "MultiLanguageOcrExample";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // 加载测试图片
            InputStream is = this.getAssets().open("multilang_text.png");
            Bitmap imgContent = BitmapFactory.decodeStream(is);

            // 初始化多语言OCR配置
            OcrConfig config = new OcrConfig();

            // 配置中文字识别模型（默认）
            config.getRec().setModelPath("ch_PP-OCRv5_rec_server_infer.onnx");

            // 添加拉丁文字识别模型
            OcrConfig.RecConfig latinRecConfig = new OcrConfig.RecConfig();
            latinRecConfig.setModelPath("latin_PP-OCRv5_rec_mobile_infer.onnx");
            latinRecConfig.setRecImgShape(new int[]{3, 48, 320});
            config.addRecConfig(latinRecConfig);

            // 添加阿拉伯文字识别模型
            OcrConfig.RecConfig arabicRecConfig = new OcrConfig.RecConfig();
            arabicRecConfig.setModelPath("arabic_PP-OCRv5_rec_mobile_infer.onnx");
            arabicRecConfig.setRecImgShape(new int[]{3, 48, 320});
            config.addRecConfig(arabicRecConfig);

            // 添加斯拉夫文字识别模型
            OcrConfig.RecConfig eslavRecConfig = new OcrConfig.RecConfig();
            eslavRecConfig.setModelPath("eslav_PP-OCRv5_rec_mobile_infer.onnx");
            eslavRecConfig.setRecImgShape(new int[]{3, 48, 320});
            config.addRecConfig(eslavRecConfig);

            // 添加韩文字识别模型
            OcrConfig.RecConfig koreanRecConfig = new OcrConfig.RecConfig();
            koreanRecConfig.setModelPath("korean_PP-OCRv5_rec_mobile_infer.onnx");
            koreanRecConfig.setRecImgShape(new int[]{3, 48, 320});
            config.addRecConfig(koreanRecConfig);

            // 创建RapidOCR实例
            RapidOCR rapidOCR = RapidOCR.create(this, config);

            // 执行OCR识别
            long startTime = System.currentTimeMillis();
            OcrResult ocrResult = rapidOCR.run(imgContent);
            long endTime = System.currentTimeMillis();

            // 输出识别结果
            Log.d(TAG, "OCR识别耗时: " + (endTime - startTime) + "ms");
            Log.d(TAG, "总识别文本: " + ocrResult.getStrRes());

            // 输出每个文本框的识别结果
            List<RecResult> recResults = ocrResult.getRecRes();
            for (int i = 0; i < recResults.size(); i++) {
                RecResult result = recResults.get(i);
                Log.d(TAG, "文本框 " + (i + 1) + ": 文本='" + result.getText() + "' 置信度=" + result.getConfidence());
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "OCR识别出错: " + e.getMessage());
        }
    }
}