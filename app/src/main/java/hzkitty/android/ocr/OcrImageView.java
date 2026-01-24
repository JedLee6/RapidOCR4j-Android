package hzkitty.android.ocr;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import org.opencv.core.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.hzkitty.entity.RecResult;
import androidx.core.widget.TextViewCompat;

public class OcrImageView extends RelativeLayout {
    private ImageView mImageView;
    private FrameLayout mTextContainer;
    private Bitmap mBitmap;
    private List<RecResult> mOcrResults;
    private List<TextView> mTextViews;
    
    private Paint mDebugPaint; // 仅用于调试，显示文本框边界
    
    private boolean mTextVisible = true; // 文本及阴影背景的可见性
    private float mTextOpacity = 0.5f; // 文本及阴影背景的透明度 (0.0 - 1.0)
    
    public OcrImageView(Context context) {
        super(context);
        init();
    }
    
    public OcrImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public OcrImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }
    
    private void init() {
        // 初始化子视图
        mImageView = new ImageView(getContext());
        mTextContainer = new FrameLayout(getContext());
        
        // 设置ImageView参数
        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mImageView.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));
        
        // 设置文本容器参数
        mTextContainer.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));
        mTextContainer.setClipChildren(false);
        mTextContainer.setClipToPadding(false);
        
        // 将子视图添加到容器中
        addView(mImageView);
        addView(mTextContainer);
        
        // 初始化调试画笔（可选）
        mDebugPaint = new Paint();
        mDebugPaint.setColor(Color.argb(128, 0, 0, 0));
        mDebugPaint.setStyle(Paint.Style.FILL);
        
        // 初始化变量
        mOcrResults = new ArrayList<>();
        mTextViews = new ArrayList<>();
    }
    
    public void setImageBitmap(Bitmap bitmap) {
        mImageView.setImageBitmap(bitmap);
        mBitmap = bitmap;
        clearOcrResults();
    }
    
    /**
     * 设置OCR识别结果
     */
    public void setOcrResults(List<RecResult> results) {
        clearOcrResults();
        
        if (results != null && !results.isEmpty() && mBitmap != null) {
            mOcrResults.addAll(results);
            
            // 获取ImageView的尺寸
            int viewWidth = getWidth();
            int viewHeight = getHeight();
            
            // 获取图片的原始尺寸
            int imageWidth = mBitmap.getWidth();
            int imageHeight = mBitmap.getHeight();
            
            // 计算图片在ImageView中的实际显示区域
            float scale = Math.min((float) viewWidth / imageWidth, (float) viewHeight / imageHeight);
            float scaledWidth = imageWidth * scale;
            float scaledHeight = imageHeight * scale;
            float offsetX = (viewWidth - scaledWidth) / 2;
            float offsetY = (viewHeight - scaledHeight) / 2;
            
            // 转换OCR结果的坐标到视图坐标系
            for (RecResult result : results) {
                Point[] box = result.getDtBoxes();
                if (box != null && box.length >= 4) {
                    // 计算文本框的最小外接矩形
                    int left = Integer.MAX_VALUE;
                    int top = Integer.MAX_VALUE;
                    int right = Integer.MIN_VALUE;
                    int bottom = Integer.MIN_VALUE;
                    
                    for (Point point : box) {
                        // 先将原始坐标缩放，然后添加偏移量
                        float scaledX = (float) point.x * scale + offsetX;
                        float scaledY = (float) point.y * scale + offsetY;
                        
                        left = Math.min(left, (int) scaledX);
                        top = Math.min(top, (int) scaledY);
                        right = Math.max(right, (int) scaledX);
                        bottom = Math.max(bottom, (int) scaledY);
                    }
                    
                    // 创建TextView显示识别的文本
                    TextView textView = createTextView(result.getText(), new Rect(left, top, right, bottom));
                    mTextViews.add(textView);
                    mTextContainer.addView(textView);
                }
            }
        }
    }
    
    /**
     * 清除OCR结果
     */
    public void clearOcrResults() {
        mOcrResults.clear();
        mTextViews.clear();
        mTextContainer.removeAllViews();
    }
    
    /**
     * 创建用于显示识别文本的TextView
     */
    private TextView createTextView(String text, Rect rect) {
        // 使用AppCompatTextView以确保在所有版本上都支持自动调整字体大小
        androidx.appcompat.widget.AppCompatTextView textView = new androidx.appcompat.widget.AppCompatTextView(getContext());
        
        // 设置文本内容
        textView.setText(text);
        
        // 设置文本样式
        textView.setTextColor(Color.WHITE);
        textView.setShadowLayer(2f, 1f, 1f, Color.BLACK);
        // 移除默认padding，设置为0
        textView.setPadding(0, 0, 0, 0);
        // 移除字体上下留白
        textView.setIncludeFontPadding(false);
        // 设置行间距为0
        textView.setLineSpacing(0, 1f);
        
        // 设置背景透明度
        updateTextViewAppearance(textView);
        
        // 设置文本选择功能
        textView.setTextIsSelectable(true);
        textView.setFocusable(true);
        textView.setFocusableInTouchMode(true);
        
        // 设置自动调整字体大小（使用兼容方式）
        TextViewCompat.setAutoSizeTextTypeWithDefaults(textView, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        // 调整步长为1SP以获得更精细的字体大小调整
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                textView, 
                5, // 最小字体大小
                250, // 最大字体大小
                1, // 步长值（1SP）
                //unit参数的枚举值：
                //TypedValue.COMPLEX_UNIT_PX = 0（像素）
                //TypedValue.COMPLEX_UNIT_DIP = 1（设备独立像素dp）
                //TypedValue.COMPLEX_UNIT_SP = 2（缩放像素sp，推荐用于字体）
                //TypedValue.COMPLEX_UNIT_PT = 3（点）
                //TypedValue.COMPLEX_UNIT_IN = 4（英寸）
                //TypedValue.COMPLEX_UNIT_MM = 5（毫米）
                0); // 步长单位：SP (TypedValue.COMPLEX_UNIT_SP = 2)
        
        // 设置TextView的位置和大小
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                rect.width(), rect.height());
        params.leftMargin = rect.left;
        params.topMargin = rect.top;
        textView.setLayoutParams(params);
        
        return textView;
    }
    
    /**
     * 计算文本在指定矩形内的最佳字体大小
     * @param text 要绘制的文本
     * @param rect 文本框矩形
     * @param paint 绘制文本的画笔
     * @return 最佳字体大小（像素）
     */
    private float calculateOptimalFontSize(String text, Rect rect, Paint paint) {
        // 计算文本框的可用宽度和高度
        float availableWidth = rect.width() - 10; // 左右各留5像素边距
        float availableHeight = rect.height() - 10; // 上下各留5像素边距
        
        // 初始字体大小设置为文本框高度
        float fontSize = availableHeight;
        paint.setTextSize(fontSize);
        
        // 测量文本宽度
        float textWidth = paint.measureText(text);
        
        // 如果文本宽度超过可用宽度，逐渐减小字体大小
        while (textWidth > availableWidth && fontSize > 5) {
            fontSize -= 1;
            paint.setTextSize(fontSize);
            textWidth = paint.measureText(text);
        }
        
        // 确保字体大小不小于最小值
        return Math.max(fontSize, 5);
    }
    
    /**
     * 将像素值转换为sp单位
     */
    private float pxToSp(Context context, float px) {
        return px / context.getResources().getDisplayMetrics().scaledDensity;
    }
    
    /**
     * 暴露ImageView的getImageMatrix方法
     */
    public android.graphics.Matrix getImageMatrix() {
        return mImageView.getImageMatrix();
    }
    
    /**
     * 设置ImageView的ScaleType
     */
    public void setScaleType(ImageView.ScaleType scaleType) {
        mImageView.setScaleType(scaleType);
    }
    
    /**
     * 获取ImageView
     */
    public ImageView getImageView() {
        return mImageView;
    }
    
    /**
     * 更新TextView的外观（可见性和透明度）
     */
    private void updateTextViewAppearance(TextView textView) {
        // 设置可见性
        textView.setVisibility(mTextVisible ? View.VISIBLE : View.GONE);
        
        // 设置背景透明度
        int alpha = (int) (mTextOpacity * 255);
        textView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
        
        // 更新阴影效果的可见性
        if (mTextVisible) {
            textView.setShadowLayer(2f, 1f, 1f, Color.BLACK);
        } else {
            textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        }
    }
    
    /**
     * 设置文本及阴影背景的可见性
     */
    public void setTextVisible(boolean visible) {
        mTextVisible = visible;
        for (TextView textView : mTextViews) {
            updateTextViewAppearance(textView);
        }
    }
    
    /**
     * 获取文本及阴影背景的可见性
     */
    public boolean isTextVisible() {
        return mTextVisible;
    }
    
    /**
     * 设置文本及阴影背景的透明度
     * @param opacity 透明度值（0.0 - 1.0）
     */
    public void setTextOpacity(float opacity) {
        // 确保透明度在0.0到1.0之间
        mTextOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        for (TextView textView : mTextViews) {
            int alpha = (int) (mTextOpacity * 255);
            textView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
        }
    }
    
    /**
     * 获取文本及阴影背景的透明度
     */
    public float getTextOpacity() {
        return mTextOpacity;
    }
}