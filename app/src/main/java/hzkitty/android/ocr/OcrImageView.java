package hzkitty.android.ocr;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
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
import io.github.hzkitty.entity.WordBoxResult;
import org.opencv.core.Point;
import androidx.core.widget.TextViewCompat;

public class OcrImageView extends FrameLayout {
    private ImageView mImageView;
    private FrameLayout mTextContainer;
    private LensSelectView mLensSelectView;
    private Bitmap mBitmap;
    private List<RecResult> mOcrResults;
    private List<TextView> mTextViews;
    private List<OcrChar> mCharList;
    
    private Paint mDebugPaint; // 仅用于调试，显示文本框边界
    
    private boolean mTextVisible = true; // 文本及阴影背景的可见性
    private float mTextOpacity = 0.5f; // 文本及阴影背景的透明度 (0.0 - 1.0)
    
    // 坐标映射相关
    private float mScaleFactor;
    private float mOffsetX;
    private float mOffsetY;
    
    // 字符数据结构
    private static class OcrChar {
        String charText;
        RectF rect;
        int index;
        
        OcrChar(String charText, RectF rect, int index) {
            this.charText = charText;
            this.rect = rect;
            this.index = index;
        }
    }
    
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
        mLensSelectView = new LensSelectView(getContext());
        
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
        
        // 设置LensSelectView参数
        mLensSelectView.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));
        
        // 将子视图添加到容器中
        addView(mImageView);
        addView(mTextContainer);
        addView(mLensSelectView);
        
        // 初始化调试画笔（可选）
        mDebugPaint = new Paint();
        mDebugPaint.setColor(Color.argb(128, 0, 0, 0));
        mDebugPaint.setStyle(Paint.Style.FILL);
        
        // 初始化变量
        mOcrResults = new ArrayList<>();
        mTextViews = new ArrayList<>();
        mCharList = new ArrayList<>();
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
            
            // 保存坐标映射参数
            mScaleFactor = scale;
            mOffsetX = offsetX;
            mOffsetY = offsetY;
            
            // 转换OCR结果的坐标到视图坐标系
            int charIndex = 0;
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
                    mTextViews.add(textView);
                    mTextContainer.addView(textView);
                    
                    // 扁平化OCR结果为字符列表
                    WordBoxResult wordBoxResult = result.getWordBoxResult();
                    if (wordBoxResult != null) {
                        List<String> wordBoxContentList = wordBoxResult.getWordBoxContentList();
                        List<Point[]> sortedWordBoxList = wordBoxResult.getSortedWordBoxList();
                        
                        if (wordBoxContentList != null && sortedWordBoxList != null && 
                            wordBoxContentList.size() == sortedWordBoxList.size()) {
                            for (int i = 0; i < wordBoxContentList.size(); i++) {
                                String content = wordBoxContentList.get(i);
                                Point[] wordBox = sortedWordBoxList.get(i);
                                
                                if (wordBox != null && wordBox.length >= 4) {
                                    // 计算字符框的最小外接矩形
                                    float charLeft = Float.MAX_VALUE;
                                    float charTop = Float.MAX_VALUE;
                                    float charRight = Float.MIN_VALUE;
                                    float charBottom = Float.MIN_VALUE;
                                    
                                    for (Point point : wordBox) {
                                        // 应用缩放比例并加上偏移量
                                        float scaledX = (float) point.x * scale + offsetX;
                                        float scaledY = (float) point.y * scale + offsetY;
                                        
                                        charLeft = Math.min(charLeft, scaledX);
                                        charTop = Math.min(charTop, scaledY);
                                        charRight = Math.max(charRight, scaledX);
                                        charBottom = Math.max(charBottom, scaledY);
                                    }
                                    
                                    // 创建OcrChar对象
                                    RectF charRect = new RectF(charLeft, charTop, charRight, charBottom);
                                    OcrChar ocrChar = new OcrChar(content, charRect, charIndex);
                                    mCharList.add(ocrChar);
                                    charIndex++;
                                }
                            }
                        }
                    } else {
                        // 如果没有WordBoxResult，则使用文本框作为字符框
                        String text = result.getText();
                        if (text != null) {
                            float charWidth = (right - left) / (float) text.length();
                            for (int i = 0; i < text.length(); i++) {
                                char c = text.charAt(i);
                                float charLeft = left + i * charWidth;
                                float charRight = charLeft + charWidth;
                                RectF charRect = new RectF(charLeft, top, charRight, bottom);
                                OcrChar ocrChar = new OcrChar(String.valueOf(c), charRect, charIndex);
                                mCharList.add(ocrChar);
                                charIndex++;
                            }
                        }
                    }
                }
            }
            
            // 将字符列表传递给LensSelectView
            mLensSelectView.setCharList(mCharList);
        }
    }
    
    /**
     * 清除OCR结果
     */
    public void clearOcrResults() {
        mOcrResults.clear();
        mTextViews.clear();
        mCharList.clear();
        mTextContainer.removeAllViews();
        mLensSelectView.clearSelection();
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
    
    /**
     * 自定义文本选择视图，实现类似Google Lens的文本选择功能
     */
    private class LensSelectView extends View {
        // 数据源
        private List<OcrChar> mCharList = new ArrayList<>();
        
        // 选中状态
        private int mStartIndex = -1;
        private int mEndIndex = -1;
        
        // 拖拽状态
        private HandleType mDraggingHandle = HandleType.NONE;
        
        // 手柄类型枚举
        private enum HandleType {
            NONE, START, END
        }
        
        // 画笔与资源
        private final Paint mHighlightPaint;
        private final Paint mHandlePaint;
        private Drawable mStartHandleDrawable;
        private Drawable mEndHandleDrawable;
        private final int mHandleSize;
        
        // 菜单相关
        private ActionMode mActionMode;
        
        // 手势检测
        private GestureDetector mGestureDetector;
        
        public LensSelectView(Context context) {
            super(context);
            
            // 初始化画笔
            mHighlightPaint = new Paint();
            mHighlightPaint.setColor(Color.parseColor("#6633B5E5")); // 半透明蓝
            mHighlightPaint.setStyle(Paint.Style.FILL);
            
            mHandlePaint = new Paint();
            mHandlePaint.setColor(Color.WHITE);
            mHandlePaint.setStyle(Paint.Style.FILL);
            mHandlePaint.setAntiAlias(true);
            
            // 初始化手柄资源 - 使用默认绘制，不加载外部资源
            mStartHandleDrawable = null;
            mEndHandleDrawable = null;
            
            // 手柄大小
            mHandleSize = dpToPx(context, 20);
            
            // 初始化手势检测器
            mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent e) {
                    // 长按开始选择
                    float x = e.getX();
                    float y = e.getY();
                    int closestIndex = getClosestCharIndex(x, y);
                    if (closestIndex != -1) {
                        mStartIndex = closestIndex;
                        mEndIndex = closestIndex;
                        invalidate();
                        // 显示操作菜单
                        showActionMenu();
                    }
                }
            });
            
            // 设置可触摸
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
            setLongClickable(true);
        }
        
        /**
         * 设置字符列表
         */
        public void setCharList(List<OcrChar> charList) {
            mCharList = charList;
            invalidate();
        }
        
        /**
         * 清除选择
         */
        public void clearSelection() {
            mStartIndex = -1;
            mEndIndex = -1;
            mDraggingHandle = HandleType.NONE;
            if (mActionMode != null) {
                mActionMode.finish();
                mActionMode = null;
            }
            invalidate();
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            if (mStartIndex == -1 || mEndIndex == -1 || mCharList.isEmpty()) {
                return;
            }
            
            // 1. 绘制高亮背景
            int start = Math.min(mStartIndex, mEndIndex);
            int end = Math.max(mStartIndex, mEndIndex);
            
            for (int i = start; i <= end && i < mCharList.size(); i++) {
                OcrChar ocrChar = mCharList.get(i);
                canvas.drawRect(ocrChar.rect, mHighlightPaint);
            }
            
            // 2. 绘制开始手柄
            if (start < mCharList.size()) {
                OcrChar startChar = mCharList.get(start);
                drawHandle(canvas, mStartHandleDrawable, startChar.rect.left, startChar.rect.bottom);
            }
            
            // 3. 绘制结束手柄
            if (end < mCharList.size()) {
                OcrChar endChar = mCharList.get(end);
                drawHandle(canvas, mEndHandleDrawable, endChar.rect.right, endChar.rect.bottom);
            }
        }
        
        /**
         * 绘制手柄
         */
        private void drawHandle(Canvas canvas, Drawable drawable, float x, float y) {
            float centerX = x;
            float centerY = y;
            
            if (drawable != null) {
                // 使用资源图片绘制手柄
                int left = (int) (centerX - mHandleSize / 2);
                int top = (int) (centerY - mHandleSize / 2);
                int right = left + mHandleSize;
                int bottom = top + mHandleSize;
                
                drawable.setBounds(left, top, right, bottom);
                drawable.draw(canvas);
            } else {
                // 绘制默认圆形手柄
                canvas.drawCircle(centerX, centerY, mHandleSize / 2, mHandlePaint);
            }
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // 将事件传递给手势检测器
            boolean handledByGestureDetector = mGestureDetector.onTouchEvent(event);
            
            float x = event.getX();
            float y = event.getY();
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 1. 判断是否按到了手柄
                    if (isTouchingStartHandle(x, y)) {
                        mDraggingHandle = HandleType.START;
                        // 禁止父视图拦截触摸事件
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    } else if (isTouchingEndHandle(x, y)) {
                        mDraggingHandle = HandleType.END;
                        // 禁止父视图拦截触摸事件
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    break;
                    
                case MotionEvent.ACTION_MOVE:
                    // 2. 处理拖拽逻辑
                    if (mStartIndex != -1 && mEndIndex != -1) {
                        int targetIndex = getClosestCharIndex(x, y);
                        
                        if (targetIndex != -1) {
                            // 如果是拖拽状态，更新对应索引
                            if (mDraggingHandle == HandleType.START) {
                                mStartIndex = targetIndex;
                                // 禁止父视图拦截触摸事件
                                getParent().requestDisallowInterceptTouchEvent(true);
                                invalidate();
                                return true;
                            } else if (mDraggingHandle == HandleType.END) {
                                mEndIndex = targetIndex;
                                // 禁止父视图拦截触摸事件
                                getParent().requestDisallowInterceptTouchEvent(true);
                                invalidate();
                                return true;
                            } else {
                                // 如果不是拖拽状态，但有选择，判断是否要开始拖拽
                                if (isTouchingStartHandle(x, y)) {
                                    mDraggingHandle = HandleType.START;
                                    mStartIndex = targetIndex;
                                    // 禁止父视图拦截触摸事件
                                    getParent().requestDisallowInterceptTouchEvent(true);
                                    invalidate();
                                    return true;
                                } else if (isTouchingEndHandle(x, y)) {
                                    mDraggingHandle = HandleType.END;
                                    mEndIndex = targetIndex;
                                    // 禁止父视图拦截触摸事件
                                    getParent().requestDisallowInterceptTouchEvent(true);
                                    invalidate();
                                    return true;
                                } else {
                                    // 更新结束索引，实现自由滑动选择
                                    mEndIndex = targetIndex;
                                    // 禁止父视图拦截触摸事件
                                    getParent().requestDisallowInterceptTouchEvent(true);
                                    invalidate();
                                    return true;
                                }
                            }
                        }
                    }
                    break;
                    
                case MotionEvent.ACTION_UP:
                    // 3. 停止拖拽
                    mDraggingHandle = HandleType.NONE;
                    break;
            }
            
            // 如果手势检测器已经处理了事件，或者当前视图处理了事件，返回true
            return handledByGestureDetector || super.onTouchEvent(event);
        }
        
        /**
         * 判断是否触摸到开始手柄
         */
        private boolean isTouchingStartHandle(float x, float y) {
            if (mStartIndex == -1 || mStartIndex >= mCharList.size()) {
                return false;
            }
            
            OcrChar startChar = mCharList.get(mStartIndex);
            float handleX = startChar.rect.left;
            float handleY = startChar.rect.bottom;
            
            return isPointInCircle(x, y, handleX, handleY, mHandleSize);
        }
        
        /**
         * 判断是否触摸到结束手柄
         */
        private boolean isTouchingEndHandle(float x, float y) {
            if (mEndIndex == -1 || mEndIndex >= mCharList.size()) {
                return false;
            }
            
            OcrChar endChar = mCharList.get(mEndIndex);
            float handleX = endChar.rect.right;
            float handleY = endChar.rect.bottom;
            
            return isPointInCircle(x, y, handleX, handleY, mHandleSize);
        }
        
        /**
         * 判断点是否在圆形范围内
         */
        private boolean isPointInCircle(float pointX, float pointY, float circleX, float circleY, float radius) {
            float dx = pointX - circleX;
            float dy = pointY - circleY;
            return dx * dx + dy * dy <= radius * radius;
        }
        
        /**
         * 获取距离触摸点最近的字符索引
         */
        private int getClosestCharIndex(float x, float y) {
            if (mCharList.isEmpty()) {
                return -1;
            }
            
            int closestIndex = -1;
            float minDistance = Float.MAX_VALUE;
            
            for (int i = 0; i < mCharList.size(); i++) {
                OcrChar ocrChar = mCharList.get(i);
                float distance = getDistanceToRect(x, y, ocrChar.rect);
                
                if (distance < minDistance) {
                    minDistance = distance;
                    closestIndex = i;
                }
            }
            
            // 如果距离太远，不选择
            if (minDistance > mHandleSize * 3) {
                return -1;
            }
            
            return closestIndex;
        }
        
        /**
         * 计算点到矩形的距离
         */
        private float getDistanceToRect(float x, float y, RectF rect) {
            float dx = Math.max(rect.left - x, Math.max(0, x - rect.right));
            float dy = Math.max(rect.top - y, Math.max(0, y - rect.bottom));
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
        
        /**
         * 显示操作菜单
         */
        private void showActionMenu() {
            if (mStartIndex == -1 || mEndIndex == -1) {
                return;
            }
            
            if (mActionMode == null) {
                mActionMode = startActionMode(new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, "复制");
                        menu.add(Menu.NONE, Menu.FIRST + 1, Menu.NONE, "全选");
                        return true;
                    }
                    
                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }
                    
                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        switch (item.getItemId()) {
                            case Menu.FIRST:
                                // 复制
                                String selectedText = getSelectedString();
                                copyToClipboard(selectedText);
                                mode.finish();
                                return true;
                            case Menu.FIRST + 1:
                                // 全选
                                selectAll();
                                return true;
                            default:
                                return false;
                        }
                    }
                    
                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        mActionMode = null;
                    }
                });
            }
        }
        
        /**
         * 获取选中的文本
         */
        private String getSelectedString() {
            if (mStartIndex == -1 || mEndIndex == -1 || mCharList.isEmpty()) {
                return "";
            }
            
            int start = Math.min(mStartIndex, mEndIndex);
            int end = Math.max(mStartIndex, mEndIndex);
            
            StringBuilder sb = new StringBuilder();
            
            for (int i = start; i <= end && i < mCharList.size(); i++) {
                OcrChar ocrChar = mCharList.get(i);
                sb.append(ocrChar.charText);
            }
            
            return sb.toString();
        }
        
        /**
         * 复制文本到剪贴板
         */
        private void copyToClipboard(String text) {
            android.content.ClipboardManager clipboard = 
                    (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("OCR Text", text);
            clipboard.setPrimaryClip(clip);
            
            // 显示提示
            Toast.makeText(getContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
        
        /**
         * 全选文本
         */
        private void selectAll() {
            if (mCharList.isEmpty()) {
                return;
            }
            
            mStartIndex = 0;
            mEndIndex = mCharList.size() - 1;
            invalidate();
        }
        
        /**
         * dp转px
         */
        private int dpToPx(Context context, int dp) {
            return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}