package hzkitty.android.ocr;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import org.opencv.core.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
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
import java.util.Collections;
import java.util.Comparator;
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
    
    // 长按文本选择相关变量
    private GestureDetector mGestureDetector;
    private List<OcrWordModel> mOcrWordModels = new ArrayList<>();
    private List<OcrWordModel> mSelectedWordModels = new ArrayList<>();
    private boolean mLongPressMode = false;
    private PointF mStartCursorPoint = new PointF();
    private PointF mEndCursorPoint = new PointF();
    private boolean mTextSelectionInProgress = false;
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private static final float MARKER_RADIUS = 20; // marker的半径，用于检测触摸
    
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
                RelativeLayout.LayoutParams.WRAP_CONTENT));
        mImageView.setAdjustViewBounds(true);
        
        // 设置文本容器参数
        mTextContainer.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));
        mTextContainer.setClipChildren(false);
        mTextContainer.setClipToPadding(false);
        mTextContainer.setClickable(false); // 确保文本容器不拦截触摸事件
        mTextContainer.setLongClickable(false);
        
        // 将子视图添加到容器中
        addView(mImageView);
        addView(mTextContainer);
        
        // 设置OcrImageView为可点击和可长按，确保能接收触摸事件
        setClickable(true);
        setLongClickable(true);
        
        // 初始化调试画笔（可选）
        mDebugPaint = new Paint();
        mDebugPaint.setColor(Color.argb(128, 0, 0, 0));
        mDebugPaint.setStyle(Paint.Style.FILL);
        
        // 初始化变量
        mOcrResults = new ArrayList<>();
        mTextViews = new ArrayList<>();
        mOcrWordModels = new ArrayList<>();
        mSelectedWordModels = new ArrayList<>();
        
        // 初始化手势检测器
        initGestureDetector();
    }
    
    /**
     * 初始化手势检测器，用于检测长按事件
     */
    private void initGestureDetector() {
        mGestureDetector = new GestureDetector(getContext(), new LongPressGestureListener());
        
        // 设置触摸监听器
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    // 当触摸开始时，请求父视图不要拦截触摸事件
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                
                // 将触摸事件传递给手势检测器
                if (mGestureDetector.onTouchEvent(event)) {
                    return true;
                }
                
                int touchX = (int) event.getX();
                int touchY = (int) event.getY();
                
                // 处理选择器的拖拽
                if (mTextSelectionInProgress) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_MOVE:
                            // 请求父视图不要拦截触摸事件
                            getParent().requestDisallowInterceptTouchEvent(true);
                            
                            // 检查是否拖拽的是开始marker（+号）
                            if (isMarkerTouched(mStartCursorPoint, touchX, touchY)) {
                                // 允许拖拽开始marker（+号）
                                mStartCursorPoint.x = touchX;
                                mStartCursorPoint.y = touchY;
                                updateSelectionOnMove();
                                return true;
                            }
                            // 检查是否拖拽的是结束marker（-号）
                            else if (isMarkerTouched(mEndCursorPoint, touchX, touchY)) {
                                // 允许拖拽结束marker（-号）
                                mEndCursorPoint.x = touchX;
                                mEndCursorPoint.y = touchY;
                                updateSelectionOnMove();
                                return true;
                            }
                            // 如果是新的选择过程（+号固定，只移动-号）
                            else {
                                // 只移动结束marker（-号）
                                mEndCursorPoint.x = touchX;
                                mEndCursorPoint.y = touchY;
                                updateSelectionOnMove();
                                lastTouchX = touchX;
                                lastTouchY = touchY;
                                return true;
                            }
                        case MotionEvent.ACTION_UP:
                            // 触摸结束时，确定结束marker（-号）的最终位置
                            mEndCursorPoint.x = touchX;
                            mEndCursorPoint.y = touchY;
                            updateSelectionOnMove();
                            // 允许父视图重新拦截触摸事件
                            getParent().requestDisallowInterceptTouchEvent(false);
                            mTextSelectionInProgress = false;
                            break;
                        case MotionEvent.ACTION_CANCEL:
                            // 触摸取消时，确定结束marker（-号）的最终位置
                            mEndCursorPoint.x = touchX;
                            mEndCursorPoint.y = touchY;
                            updateSelectionOnMove();
                            // 允许父视图重新拦截触摸事件
                            getParent().requestDisallowInterceptTouchEvent(false);
                            mTextSelectionInProgress = false;
                            break;
                    }
                }
                
                return false;
            }
        });
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
            
            // 计算图片的缩放比例（FIT_CENTER模式）
            float scale = Math.min((float) viewWidth / imageWidth, (float) viewHeight / imageHeight);
            // 计算图片在ImageView中的实际尺寸
            int scaledImageWidth = Math.round(imageWidth * scale);
            int scaledImageHeight = Math.round(imageHeight * scale);
            // 计算图片在ImageView中的偏移量
            int offsetX = (viewWidth - scaledImageWidth) / 2;
            int offsetY = (viewHeight - scaledImageHeight) / 2;
            
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
                        // 应用缩放比例并加上偏移量
                        float scaledX = (float) point.x * scale + offsetX;
                        float scaledY = (float) point.y * scale + offsetY;
                        
                        left = Math.min(left, (int) scaledX);
                        top = Math.min(top, (int) scaledY);
                        right = Math.max(right, (int) scaledX);
                        bottom = Math.max(bottom, (int) scaledY);
                    }
                    
                    // 创建TextView显示识别的文本
                    TextView textView = createTextView(result.getText(), new Rect(left, top, right, bottom));
                    // 设置TextView为不可点击和不可长按，确保触摸事件能传递给OcrImageView
                    textView.setClickable(false);
                    textView.setLongClickable(false);
                    mTextViews.add(textView);
                    mTextContainer.addView(textView);
                    
                    // 创建OcrWordModel对象，用于文本选择
                    OcrWordModel wordModel = new OcrWordModel(result.getText(), new Rect(left, top, right, bottom));
                    mOcrWordModels.add(wordModel);
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
        mOcrWordModels.clear();
        mSelectedWordModels.clear();
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
     * 重写onInterceptTouchEvent方法，在文本选择进行中时拦截触摸事件
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 如果文本选择正在进行中，拦截触摸事件
        if (mTextSelectionInProgress) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
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
     * 在触摸位置选择单词
     */
    private void selectWordOnTouch(int touchX, int touchY, boolean longPressMode) {
        mLongPressMode = longPressMode;
        
        boolean foundWord = false;
        
        // 遍历所有单词模型，查找包含触摸点的单词
        for (OcrWordModel wordModel : mOcrWordModels) {
            Rect rect = wordModel.getRect();
            
            // 检查触摸点是否在单词矩形内
            if (rect.contains(touchX, touchY)) {
                foundWord = true;
                
                // 清除之前的选择
                mSelectedWordModels.clear();
                
                // 设置选择器的起始位置（+号marker），并固定
                mStartCursorPoint.x = rect.centerX();
                mStartCursorPoint.y = rect.centerY();
                // 结束位置（-号marker）初始化为与开始位置相同
                mEndCursorPoint.x = mStartCursorPoint.x;
                mEndCursorPoint.y = mStartCursorPoint.y;
                mTextSelectionInProgress = true;
                
                // 更新UI显示
                updateSelectionUI();
                break;
            }
        }
        
        if (!foundWord && longPressMode) {
            // 如果长按位置没有单词，设置选择器的起始位置（+号marker），并固定
            mStartCursorPoint.x = touchX;
            mStartCursorPoint.y = touchY;
            // 结束位置（-号marker）初始化为与开始位置相同
            mEndCursorPoint.x = touchX;
            mEndCursorPoint.y = touchY;
            mTextSelectionInProgress = true;
            
            // 更新UI显示
            updateSelectionUI();
        }
    }
    
    /**
     * 检查是否触摸到了marker
     */
    private boolean isMarkerTouched(PointF markerPoint, int touchX, int touchY) {
        float dx = markerPoint.x - touchX;
        float dy = markerPoint.y - touchY;
        return Math.sqrt(dx * dx + dy * dy) <= MARKER_RADIUS;
    }
    
    /**
     * 长按手势监听器
     */
    private class LongPressGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);
            
            int touchX = (int) e.getX();
            int touchY = (int) e.getY();
            
            // 检查是否长按到了marker
            boolean touchedStartMarker = isMarkerTouched(mStartCursorPoint, touchX, touchY);
            boolean touchedEndMarker = isMarkerTouched(mEndCursorPoint, touchX, touchY);
            
            if (touchedStartMarker || touchedEndMarker) {
                // 如果长按到了marker，进入编辑模式
                mTextSelectionInProgress = true;
                // 不需要清除之前的选择，因为只是拖拽marker
                updateSelectionUI();
            } else {
                // 否则开始新的选择
                selectWordOnTouch(touchX, touchY, true);
            }
        }
    }
    
    /**
     * 在移动时更新选择
     */
    private void updateSelectionOnMove() {
        // 清除之前的选择
        mSelectedWordModels.clear();
        
        // 计算选择区域
        Rect selectionRect = new Rect(
                Math.min((int) mStartCursorPoint.x, (int) mEndCursorPoint.x),
                Math.min((int) mStartCursorPoint.y, (int) mEndCursorPoint.y),
                Math.max((int) mStartCursorPoint.x, (int) mEndCursorPoint.x),
                Math.max((int) mStartCursorPoint.y, (int) mEndCursorPoint.y)
        );
        
        // 选择所有与选择区域相交的单词
        for (OcrWordModel wordModel : mOcrWordModels) {
            Rect wordRect = wordModel.getRect();
            
            if (Rect.intersects(selectionRect, wordRect)) {
                mSelectedWordModels.add(wordModel);
            }
        }
        
        // 更新UI显示
        updateSelectionUI();
    }
    
    /**
     * 更新选择的UI显示
     */
    private void updateSelectionUI() {
        // 重新绘制视图
        invalidate();
        
        // 获取选中的文本
        List<String> selectedText = new ArrayList<>();
        for (OcrWordModel wordModel : mSelectedWordModels) {
            selectedText.add(wordModel.getText());
        }
        
        // 可以在这里将选中的文本传递给外部，例如通过回调接口
        if (selectedText.size() > 0) {
            String combinedText = String.join(" ", selectedText);
            Toast.makeText(getContext(), "选中的文本: " + combinedText, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        
        // 绘制选中文本的边框
        if (mSelectedWordModels.size() > 0) {
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setPathEffect(new DashPathEffect(new float[]{2, 2}, 0));
            paint.setColor(Color.RED);
            paint.setAntiAlias(true);
            
            for (OcrWordModel wordModel : mSelectedWordModels) {
                canvas.drawRect(wordModel.getRect(), paint);
            }
        }
        
        // 绘制选择器
        if (mTextSelectionInProgress) {
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.BLUE);
            paint.setAntiAlias(true);
            
            // 绘制选择区域
            Rect selectionRect = new Rect(
                    Math.min((int) mStartCursorPoint.x, (int) mEndCursorPoint.x),
                    Math.min((int) mStartCursorPoint.y, (int) mEndCursorPoint.y),
                    Math.max((int) mStartCursorPoint.x, (int) mEndCursorPoint.x),
                    Math.max((int) mStartCursorPoint.y, (int) mEndCursorPoint.y)
            );
            canvas.drawRect(selectionRect, paint);
            
            // 绘制选择器的起点和终点marker
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLUE);
            canvas.drawCircle(mStartCursorPoint.x, mStartCursorPoint.y, MARKER_RADIUS, paint);
            canvas.drawCircle(mEndCursorPoint.x, mEndCursorPoint.y, MARKER_RADIUS, paint);
            
            // 在marker上绘制白色的加号和减号，区分开始和结束
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(3);
            // 开始marker绘制加号
            canvas.drawLine(mStartCursorPoint.x - 10, mStartCursorPoint.y, mStartCursorPoint.x + 10, mStartCursorPoint.y, paint);
            canvas.drawLine(mStartCursorPoint.x, mStartCursorPoint.y - 10, mStartCursorPoint.x, mStartCursorPoint.y + 10, paint);
            // 结束marker绘制减号
            canvas.drawLine(mEndCursorPoint.x - 10, mEndCursorPoint.y, mEndCursorPoint.x + 10, mEndCursorPoint.y, paint);
        }
    }
    
    /**
     * OcrWordModel类，用于表示识别出的单词及其位置
     */
    private class OcrWordModel {
        private String mText;
        private Rect mRect;
        
        public OcrWordModel(String text, Rect rect) {
            mText = text;
            mRect = rect;
        }
        
        public String getText() {
            return mText;
        }
        
        public Rect getRect() {
            return mRect;
        }
        
        public int getLeft() {
            return mRect.left;
        }
        
        public int getTop() {
            return mRect.top;
        }
        
        public int getRight() {
            return mRect.right;
        }
        
        public int getBottom() {
            return mRect.bottom;
        }
        
        public int getWidth() {
            return mRect.width();
        }
        
        public int getHeight() {
            return mRect.height();
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