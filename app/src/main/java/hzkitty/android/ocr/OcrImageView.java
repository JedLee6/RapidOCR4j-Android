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
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Magnifier;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import androidx.core.widget.TextViewCompat;
import android.content.res.ColorStateList;

import android.view.ScaleGestureDetector;

public class OcrImageView extends FrameLayout {
    private FrameLayout mContainer; // 内容容器，用于统一缩放和平移
    private ImageView mImageView;
    private FrameLayout mTextContainer;
    private LensSelectView mLensSelectView;
    private Bitmap mBitmap;
    private List<RecResult> mOcrResults;
    private List<TextView> mTextViews;
    private List<OcrChar> mCharList;
    
    // 手势检测相关
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;
    
    private float mScale = 1.0f;
    private float mTransX = 0f;
    private float mTransY = 0f;
    
    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 5.0f;
    
    private Paint mDebugPaint; // 仅用于调试，显示文本框边界
    
    private boolean mTextVisible = true; // 文本及阴影背景的可见性
    private float mTextOpacity = 1.0f; // 文本及阴影背景的透明度 (-1.0 - 1.0)
    
    // 坐标映射相关
    private float mScaleFactor;
    private float mOffsetX;
    private float mOffsetY;
    
    // 字符数据结构
    private static class OcrChar {
        String charText;
        RectF rect;
        int index;
        int blockIndex;
        
        OcrChar(String charText, RectF rect, int index, int blockIndex) {
            this.charText = charText;
            this.rect = rect;
            this.index = index;
            this.blockIndex = blockIndex;
        }
    }

    public interface OnTextProcessedListener {
        void onTextProcessed(String text);
    }

    private OnTextProcessedListener mTextProcessedListener;

    public void setOnTextProcessedListener(OnTextProcessedListener listener) {
        this.mTextProcessedListener = listener;
    }
    
    public OcrImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public OcrImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OcrImageView(Context context) {
        super(context);
        init();
    }
    
    private void init() {
        // 初始化容器
        mContainer = new FrameLayout(getContext());
        mContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // 设置Pivot为(0,0)，方便通过Translation和Scale控制位置
        mContainer.setPivotX(0);
        mContainer.setPivotY(0);
        addView(mContainer);
        
        // 初始化子视图
        mImageView = new ImageView(getContext());
        mTextContainer = new FrameLayout(getContext());
        mLensSelectView = new LensSelectView(getContext());
        
        // 设置ImageView参数
        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mImageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)); // 修改为MATCH_PARENT以填充容器
        mImageView.setAdjustViewBounds(true);
        
        // 设置文本容器参数
        mTextContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        mTextContainer.setClipChildren(false);
        mTextContainer.setClipToPadding(false);
        
        // 设置LensSelectView参数
        mLensSelectView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        
        // 将子视图添加到容器中
        mContainer.addView(mImageView);
        mContainer.addView(mTextContainer);
        mContainer.addView(mLensSelectView);
        
        // 初始化手势检测器
        mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        mGestureDetector = new GestureDetector(getContext(), new GestureListener());
        
        // 初始化调试画笔（可选）
        mDebugPaint = new Paint();
        mDebugPaint.setColor(Color.argb(128, 0, 0, 0));
        mDebugPaint.setStyle(Paint.Style.FILL);
        
        // 初始化变量
        mOcrResults = new ArrayList<>();
        mTextViews = new ArrayList<>();
        mCharList = new ArrayList<>();
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 处理手势缩放和平移
        // 优先让ScaleDetector处理
        mScaleDetector.onTouchEvent(ev);
        
        // 如果正在缩放，禁止父视图拦截事件（解决与ScrollView的冲突）
        if (mScaleDetector.isInProgress()) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        
        // 如果不是在缩放中，尝试处理平移
        if (!mScaleDetector.isInProgress()) {
            mGestureDetector.onTouchEvent(ev);
        }
        
        // 继续分发事件给子视图（如LensSelectView）
        // 注意：如果LensSelectView消费了事件，这里依然会返回true
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 缩放手势监听器
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            // 开始缩放时，禁止父视图拦截
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = mScale * scaleFactor;
            
            // 限制缩放范围
            newScale = Math.max(MIN_SCALE, Math.min(newScale, MAX_SCALE));
            
            // 计算焦点在内容坐标系中的位置 (相对于容器未缩放时的坐标)
            // 当前屏幕焦点 = mTrans + focusInContent * mScale
            // focusInContent = (当前屏幕焦点 - mTrans) / mScale
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            
            float contentFocusX = (focusX - mTransX) / mScale;
            float contentFocusY = (focusY - mTransY) / mScale;
            
            // 更新缩放比例
            mScale = newScale;
            
            // 更新平移量，保持焦点位置不变
            // 新屏幕焦点(不变) = mNewTrans + focusInContent * mNewScale
            // mNewTrans = 屏幕焦点 - focusInContent * mNewScale
            mTransX = focusX - contentFocusX * mScale;
            mTransY = focusY - contentFocusY * mScale;
            
            // 边界检查
            checkBounds();
            
            // 应用变换
            applyTransform();
            
            return true;
        }
    }
    
    /**
     * 普通手势监听器（用于平移）
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // 如果LensSelectView正在拖拽手柄，不进行平移
            if (mLensSelectView.isDraggingHandle()) {
                return false;
            }
            
            // 如果缩放比例大于1，说明在查看细节，禁止父视图拦截以允许自由平移
            if (mScale > MIN_SCALE) {
                getParent().requestDisallowInterceptTouchEvent(true);
            } else {
                return false; // 如果未缩放，不处理平移，交给ScrollView
            }
            
            mTransX -= distanceX;
            mTransY -= distanceY;
            
            checkBounds();
            applyTransform();
            
            return true;
        }
        
        // 可以添加双击复位功能
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mScale > MIN_SCALE) {
                // 复位
                mScale = MIN_SCALE;
                mTransX = 0;
                mTransY = 0;
            } else {
                // 放大到2倍
                float targetScale = 2.0f;
                float focusX = e.getX();
                float focusY = e.getY();
                
                // 简单的放大逻辑，围绕点击点
                mTransX = focusX - (focusX - mTransX) * (targetScale / mScale);
                mTransY = focusY - (focusY - mTransY) * (targetScale / mScale);
                mScale = targetScale;
            }
            checkBounds();
            applyTransform();
            return true;
        }
    }
    
    private void checkBounds() {
        // 计算内容实际占用的尺寸
        float contentWidth = getWidth() * mScale;
        float contentHeight = getHeight() * mScale;
        
        // 限制水平平移
        if (contentWidth <= getWidth()) {
            // 如果内容小于视图宽度，居中
            mTransX = (getWidth() - contentWidth) / 2;
        } else {
            // 限制左右边界
            // maxTransX = 0 (左边贴左边)
            // minTransX = viewWidth - contentWidth (右边贴右边)
            mTransX = Math.min(0, Math.max(getWidth() - contentWidth, mTransX));
        }
        
        // 限制垂直平移
        if (contentHeight <= getHeight()) {
            mTransY = (getHeight() - contentHeight) / 2;
        } else {
            mTransY = Math.min(0, Math.max(getHeight() - contentHeight, mTransY));
        }
    }
    
    private void applyTransform() {
        mContainer.setScaleX(mScale);
        mContainer.setScaleY(mScale);
        mContainer.setTranslationX(mTransX);
        mContainer.setTranslationY(mTransY);
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
                    // 计算文本框的最小外接矩形 (视图坐标系)
                    int left = Integer.MAX_VALUE;
                    int top = Integer.MAX_VALUE;
                    int right = Integer.MIN_VALUE;
                    int bottom = Integer.MIN_VALUE;
                    
                    // 计算原始图片上的坐标用于提取颜色
                    int origLeft = Integer.MAX_VALUE;
                    int origTop = Integer.MAX_VALUE;
                    int origRight = Integer.MIN_VALUE;
                    int origBottom = Integer.MIN_VALUE;
                    
                    for (Point point : box) {
                        // 记录原始坐标
                        origLeft = Math.min(origLeft, (int) point.x);
                        origTop = Math.min(origTop, (int) point.y);
                        origRight = Math.max(origRight, (int) point.x);
                        origBottom = Math.max(origBottom, (int) point.y);
                        
                        // 应用缩放比例并加上偏移量，转换为视图坐标
                        float scaledX = (float) point.x * scale + offsetX;
                        float scaledY = (float) point.y * scale + offsetY;
                        
                        left = Math.min(left, (int) scaledX);
                        top = Math.min(top, (int) scaledY);
                        right = Math.max(right, (int) scaledX);
                        bottom = Math.max(bottom, (int) scaledY);
                    }
                    
                    // 提取颜色 (使用原始图片坐标)
                    int[] colors = extractColors(mBitmap, new Rect(origLeft, origTop, origRight, origBottom));
                    int bgColor = colors[0];
                    int textColor = colors[1];
                    
                    // 创建TextView显示识别的文本
                    TextView textView = createTextView(result.getText(), new Rect(left, top, right, bottom), textColor, bgColor);
                    mTextViews.add(textView);
                    mTextContainer.addView(textView);
                    
                    // 扁平化OCR结果为字符列表的逻辑已移除，改为在布局完成后从TextView获取
                }
            }
            
            // 等待布局完成后，从TextView获取字符位置信息，确保选择区域与显示文本完全对应
            mTextContainer.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mTextContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    populateCharListFromViews();
                }
            });

        }
    }
    
    /**
     * 从TextView中提取字符位置信息，填充mCharList
     */
    private void populateCharListFromViews() {
        mCharList.clear();
        int globalIndex = 0;
        int blockIndex = 0;
        
        for (TextView textView : mTextViews) {
            android.text.Layout layout = textView.getLayout();
            if (layout == null) {
                continue;
            }
            
            String text = textView.getText().toString();
            float tvLeft = textView.getLeft();
            float tvTop = textView.getTop();
            Paint paint = textView.getPaint();
            
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                String charStr = String.valueOf(c);
                
                int line = layout.getLineForOffset(i);
                float lineTop = layout.getLineTop(line);
                float lineBottom = layout.getLineBottom(line);
                
                RectF charRect;
                if (c == '\n') {
                    // 换行符，位置在行末，宽度为0
                    float lineRight = layout.getLineRight(line);
                    charRect = new RectF(tvLeft + lineRight, tvTop + lineTop, tvLeft + lineRight, tvTop + lineBottom);
                } else {
                    float left = layout.getPrimaryHorizontal(i);
                    float right;
                    
                    // 尝试获取下一个字符的位置作为当前字符的右边界
                    if (i + 1 < text.length() && layout.getLineForOffset(i + 1) == line) {
                        right = layout.getPrimaryHorizontal(i + 1);
                    } else {
                        // 行末或最后一个字符，通过测量获取宽度
                        float charWidth = paint.measureText(text, i, i + 1);
                        right = left + charWidth;
                    }
                    
                    // 修正：Layout.getPrimaryHorizontal返回的是相对于TextView内容的坐标
                    // 需要加上TextView的左上角坐标
                    charRect = new RectF(tvLeft + left, tvTop + lineTop, tvLeft + right, tvTop + lineBottom);
                }
                
                mCharList.add(new OcrChar(charStr, charRect, globalIndex++, blockIndex));
            }
            blockIndex++;
        }
        
        // 更新LensSelectView的数据
        mLensSelectView.setCharList(mCharList);

        // 通知外部文本处理完成
        if (mTextProcessedListener != null) {
            String fullText = getAllSmartText();
            mTextProcessedListener.onTextProcessed(fullText);
        }
    }

    /**
     * 获取所有智能合并后的文本
     */
    public String getAllSmartText() {
        return getSmartText(mCharList);
    }

    /**
     * 核心智能合并逻辑
     */
    private String getSmartText(List<OcrChar> chars) {
        if (chars == null || chars.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < chars.size(); i++) {
            OcrChar curr = chars.get(i);
            
            // 1. 处理当前字符如果是换行符的情况
            if (curr.charText.equals("\n") || curr.charText.equals("\r") || curr.charText.equals("\r\n")) {
                if (i + 1 < chars.size()) {
                    smartJoin(sb, chars.get(i + 1));
                }
                continue;
            }
            
            sb.append(curr.charText);
            
            // 2. 处理跨越文本块（Block）的连接
            if (i + 1 < chars.size()) {
                OcrChar next = chars.get(i + 1);
                
                // 如果跨越了文本块
                if (curr.blockIndex != next.blockIndex) {
                    // 计算行高和垂直间距
                    float currHeight = curr.rect.height();
                    float nextHeight = next.rect.height();
                    float avgHeight = (currHeight + nextHeight) / 2;
                    
                    // 垂直间距：下一行顶部 - 当前行底部
                    float verticalGap = next.rect.top - curr.rect.bottom;
                    
                    // 判断是否为同一段落的软换行
                    // 条件：间距不过大 (Gap < avgHeight * 1.5)
                    boolean isSoftWrap = verticalGap < avgHeight * 1.5; 
                    
                    if (isSoftWrap) {
                        smartJoin(sb, next);
                    } else {
                        // 硬换行，保留换行符
                        sb.append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 智能连接逻辑：处理连字符、CJK字符和空格
     */
    private void smartJoin(StringBuilder sb, OcrChar next) {
        if (sb.length() == 0) return;
        
        char prevChar = sb.charAt(sb.length() - 1);
        String nextStr = next.charText;
        if (nextStr == null || nextStr.isEmpty()) return;
        
        // 1. 处理连字符 (Hyphenation)
        if (prevChar == '-') {
            boolean precedeBySpace = false;
            if (sb.length() > 1) {
                char prevPrev = sb.charAt(sb.length() - 2);
                if (Character.isWhitespace(prevPrev)) {
                    precedeBySpace = true;
                }
            }
            
            if (!precedeBySpace) {
                // 认为是单词截断，移除连字符
                sb.deleteCharAt(sb.length() - 1);
                // 移除后直接连接，不加空格
                return; 
            } else {
                // 独立连字符，保留并加空格
                sb.append(" ");
                return;
            }
        }
        
        // 2. 处理CJK字符（中文/日文/韩文）
        boolean prevIsCJK = isCJK(String.valueOf(prevChar));
        boolean nextIsCJK = isCJK(nextStr);
        
        if (prevIsCJK && nextIsCJK) {
            // 中文/CJK之间不加空格
            return;
        }
        
        // 3. 其他情况（英文/数字等）加空格
        if (prevChar != ' ') {
            sb.append(" ");
        }
    }
    
    private boolean isCJK(String s) {
        if (s == null || s.isEmpty()) return false;
        int cp = s.codePointAt(0);
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block)
                || Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS.equals(block)
                || Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A.equals(block)
                || Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B.equals(block)
                || Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION.equals(block)
                || Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS.equals(block)
                || Character.UnicodeBlock.HIRAGANA.equals(block)
                || Character.UnicodeBlock.KATAKANA.equals(block);
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
    private TextView createTextView(String text, Rect rect, int textColor, int bgColor) {
        // 使用AppCompatTextView以确保在所有版本上都支持自动调整字体大小
        androidx.appcompat.widget.AppCompatTextView textView = new androidx.appcompat.widget.AppCompatTextView(getContext());
        
        // 保存提取的颜色到Tag
        textView.setTag(new int[]{bgColor, textColor});
        
        // 设置文本内容
        textView.setText(text);
        
        // 设置文本样式
        textView.setTextColor(textColor);
        // 阴影设置移至updateTextViewAppearance中处理
        
        // 移除默认padding，设置为0
        textView.setPadding(0, 0, 0, 0);
        // 移除字体上下留白
        textView.setIncludeFontPadding(false);
        // 设置行间距为0
        textView.setLineSpacing(0, 1f);
        
        // 设置背景透明度及其他外观
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
        
        // 获取原始背景颜色和文字颜色
        int extractedBgColor = Color.BLACK; // 默认黑色
        int extractedTextColor = Color.WHITE; // 默认白色
        Object tag = textView.getTag();
        if (tag instanceof int[]) {
            int[] colors = (int[]) tag;
            if (colors.length >= 2) {
                extractedBgColor = colors[0];
                extractedTextColor = colors[1];
            }
        }
        
        // 根据透明度模式设置背景颜色和文字颜色
        // mTextOpacity > 0: 使用提取的背景色，完全不透明
        // mTextOpacity <= 0: 使用黑色背景，根据值计算透明度
        if (mTextOpacity > 0) {
            // 模式1：取色背景，完全不透明
            textView.setBackgroundColor(Color.rgb(Color.red(extractedBgColor), Color.green(extractedBgColor), Color.blue(extractedBgColor)));
            // 恢复提取的文字颜色
            textView.setTextColor(extractedTextColor);
            
            // 如果使用了提取的颜色，稍微减弱阴影
            if (mTextVisible && extractedBgColor != Color.BLACK) {
                textView.setShadowLayer(1f, 0.5f, 0.5f, isDark(extractedBgColor) ? Color.BLACK : Color.GRAY);
            } else if (mTextVisible) {
                textView.setShadowLayer(2f, 1f, 1f, Color.BLACK);
            } else {
                textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            }
        } else {
            // 模式2：黑色阴影，可变透明度
            // -1.0 (完全透明) -> 0.0 (完全不透明黑色)
            // 映射公式：alpha = (val + 1) * 255
            int alpha = (int) ((mTextOpacity + 1) * 255);
            // 限制alpha在0-255之间
            alpha = Math.max(0, Math.min(255, alpha));
            
            textView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
            // 阴影模式下，强制文字颜色为白色，确保在深色阴影上清晰可见
            textView.setTextColor(Color.WHITE);
            
            // 黑色背景模式下，始终保持标准阴影
            if (mTextVisible) {
                textView.setShadowLayer(2f, 1f, 1f, Color.BLACK);
            } else {
                textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            }
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
        // 确保透明度在-1.0到1.0之间
        mTextOpacity = Math.max(-1.0f, Math.min(1.0f, opacity));
        for (TextView textView : mTextViews) {
            updateTextViewAppearance(textView);
        }
    }
    
    /**
     * 获取文本及阴影背景的透明度
     */
    public float getTextOpacity() {
        return mTextOpacity;
    }
    
    /**
     * Extracts background and text colors from the bitmap within the specified rectangle.
     * @return int[] {backgroundColor, textColor}
     */
    private int[] extractColors(Bitmap bitmap, Rect rect) {
        if (bitmap == null || rect == null || bitmap.isRecycled()) {
            return new int[]{Color.BLACK, Color.WHITE};
        }

        try {
            // Ensure rect is within bitmap bounds
            int left = Math.max(0, rect.left);
            int top = Math.max(0, rect.top);
            int right = Math.min(bitmap.getWidth(), rect.right);
            int bottom = Math.min(bitmap.getHeight(), rect.bottom);

            if (left >= right || top >= bottom) {
                return new int[]{Color.BLACK, Color.WHITE};
            }

            // 1. Estimate Background Color (sample corners and edges)
            // Sampling more points for better stability
            int[] bgSamples = new int[] {
                bitmap.getPixel(left, top),
                bitmap.getPixel(right - 1, top),
                bitmap.getPixel(left, bottom - 1),
                bitmap.getPixel(right - 1, bottom - 1),
                bitmap.getPixel(left + (right - left) / 2, top), // top-mid
                bitmap.getPixel(left + (right - left) / 2, bottom - 1) // bottom-mid
            };

            // Simple average of samples for background
            long r = 0, g = 0, b = 0;
            for (int color : bgSamples) {
                r += Color.red(color);
                g += Color.green(color);
                b += Color.blue(color);
            }
            int bgColor = Color.rgb((int)(r / bgSamples.length), (int)(g / bgSamples.length), (int)(b / bgSamples.length));

            // 2. Estimate Text Color
            // Scan a few lines to find the color with maximum contrast to bgColor
            int bestTextColor = Color.WHITE; // Fallback
            double maxContrast = -1;

            // Sample stride to improve performance
            int stepX = Math.max(1, (right - left) / 20); // Check 20 points horizontally
            int stepY = Math.max(1, (bottom - top) / 5);  // Check 5 lines vertically

            for (int y = top + stepY; y < bottom; y += stepY) {
                for (int x = left; x < right; x += stepX) {
                    int pixel = bitmap.getPixel(x, y);
                    double contrast = calculateColorDifference(pixel, bgColor);
                    if (contrast > maxContrast) {
                        maxContrast = contrast;
                        bestTextColor = pixel;
                    }
                }
            }
            
            // If contrast is too low, fallback to black or white based on bg brightness
            if (maxContrast < 30) { 
                 bestTextColor = isDark(bgColor) ? Color.WHITE : Color.BLACK;
            }

            return new int[]{bgColor, bestTextColor};
        } catch (Exception e) {
            e.printStackTrace();
            return new int[]{Color.BLACK, Color.WHITE};
        }
    }

    private double calculateColorDifference(int c1, int c2) {
        int r = Color.red(c1) - Color.red(c2);
        int g = Color.green(c1) - Color.green(c2);
        int b = Color.blue(c1) - Color.blue(c2);
        return Math.sqrt(r * r + g * g + b * b);
    }
    
    private boolean isDark(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) < 128;
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
        private PopupWindow mActionPopup;
        private boolean mIsMenuDismissedByUser = false;
        private final Runnable mShowMenuRunnable = new Runnable() {
            @Override
            public void run() {
                if (mStartIndex != -1 && mEndIndex != -1 && !mIsMenuDismissedByUser) {
                    showActionMenu();
                }
            }
        };
        private final android.view.ViewTreeObserver.OnScrollChangedListener mScrollChangedListener = new android.view.ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (mActionPopup != null && mActionPopup.isShowing()) {
                    mActionPopup.dismiss();
                }
                removeCallbacks(mShowMenuRunnable);
                postDelayed(mShowMenuRunnable, 200);
            }
        };

        // 手势检测
        private GestureDetector mGestureDetector;
        
        // 放大镜
        private Magnifier mMagnifier;

        /**
         * 判断是否正在拖拽手柄
         */
        public boolean isDraggingHandle() {
            return mDraggingHandle != HandleType.NONE;
        }

        public LensSelectView(Context context) {
            super(context);
            
            // 初始化放大镜 (API 28+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // API 29+ 使用Builder进行更多自定义
                Magnifier.Builder builder = new Magnifier.Builder(this);
                // 放大倍数增加 (默认约为1.25，用户要求放大1倍，即约2.5，这里设置为2.0f作为较好的体验)
                builder.setInitialZoom(2.0f); 
                // 设置垂直偏移量，向上移动以避免手指遮挡
                // 默认偏移量通常在手指上方，但用户希望能再往上一点
                // 设定一个较大的负值，例如 -100dp
                builder.setDefaultSourceToMagnifierOffset(0, -dpToPx(context, 100));
                mMagnifier = builder.build();
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                mMagnifier = new Magnifier(this);
            }
            
            // 初始化画笔
            mHighlightPaint = new Paint();
            mHighlightPaint.setColor(Color.parseColor("#6633B5E5")); // 半透明蓝
            mHighlightPaint.setStyle(Paint.Style.FILL);
            
            mHandlePaint = new Paint();
            // 深蓝色，80%不透明度 (#CC = 204/255 ≈ 80%)
            mHandlePaint.setColor(Color.parseColor("#CC00008B"));
            mHandlePaint.setStyle(Paint.Style.FILL);
            mHandlePaint.setAntiAlias(true);
            
            // 初始化手柄资源 - 使用默认绘制，不加载外部资源
            mStartHandleDrawable = null;
            mEndHandleDrawable = null;
            
            // 手柄大小 (原20dp -> 40dp)
            mHandleSize = dpToPx(context, 40);
            
            // 初始化手势检测器
            mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true; // 必须返回true，表明接收该事件序列，否则onLongPress可能无法触发
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    float x = e.getX();
                    float y = e.getY();

                    // 如果长按的是手柄，进入拖拽模式，不重置选择
                    if (isTouchingStartHandle(x, y)) {
                        mDraggingHandle = HandleType.START;
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        if (mMagnifier != null) mMagnifier.show(x, y);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return;
                    }
                    if (isTouchingEndHandle(x, y)) {
                        mDraggingHandle = HandleType.END;
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        if (mMagnifier != null) mMagnifier.show(x, y);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return;
                    }

                    // 长按开始新的选择
                    int closestIndex = getClosestCharIndex(x, y);
                    if (closestIndex != -1) {
                        // 智能扩展选择：向左右扩展直到遇到空白符
                        expandSelectionToWord(closestIndex);
                        
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        invalidate();
                        // 显示操作菜单
                        mIsMenuDismissedByUser = false;
                        showActionMenu();
                    }
                }

                /**
                 * 从指定位置向左右扩展选择，直到遇到空白符或标点，且不跨行
                 */
                private void expandSelectionToWord(int index) {
                    if (index < 0 || index >= mCharList.size()) return;

                    int start = index;
                    int end = index;
                    
                    OcrChar centerChar = mCharList.get(index);
                    // 检查点击的是否本身就是边界符
                    if (isBoundary(centerChar.charText)) {
                        mStartIndex = index;
                        mEndIndex = index;
                        return;
                    }
                    
                    float centerY = centerChar.rect.centerY();
                    // 使用高度的一半作为行判定阈值，防止跨行选择
                    float threshold = centerChar.rect.height() / 2f;
                    
                    // 向左扩展
                    for (int i = index - 1; i >= 0; i--) {
                        OcrChar current = mCharList.get(i);
                        
                        // 1. 检查是否跨行 (Y轴差异过大)
                        if (Math.abs(current.rect.centerY() - centerY) > threshold) {
                            break;
                        }
                        
                        // 2. 检查是否遇到边界符
                        if (isBoundary(current.charText)) {
                            break;
                        }
                        
                        start = i;
                    }
                    
                    // 向右扩展
                    for (int i = index + 1; i < mCharList.size(); i++) {
                        OcrChar current = mCharList.get(i);
                        
                        // 1. 检查是否跨行
                        if (Math.abs(current.rect.centerY() - centerY) > threshold) {
                            break;
                        }
                        
                        // 2. 检查是否遇到边界符
                        if (isBoundary(current.charText)) {
                            break;
                        }
                        
                        end = i;
                    }
                    
                    mStartIndex = start;
                    mEndIndex = end;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    // 单击事件，用于取消选择
                    if (mStartIndex != -1 && mEndIndex != -1) {
                        float x = e.getX();
                        float y = e.getY();
                        
                        // 检查是否点击了手柄
                        if (isTouchingStartHandle(x, y) || isTouchingEndHandle(x, y)) {
                            return false;
                        }
                        
                        // 检查是否点击了已选择的区域
                        if (isPointInSelection(x, y)) {
                            // 切换菜单可见性
                            if (mActionPopup != null && mActionPopup.isShowing()) {
                                mActionPopup.dismiss();
                                mIsMenuDismissedByUser = true;
                            } else {
                                mIsMenuDismissedByUser = false;
                                showActionMenu();
                            }
                            return true;
                        }
                        
                        // 点击了非选择区域，重置选择
                        clearSelection();
                        return true;
                    }
                    return false;
                }
            });
            
            // 设置可触摸
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
            setLongClickable(true);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnScrollChangedListener(mScrollChangedListener);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            getViewTreeObserver().removeOnScrollChangedListener(mScrollChangedListener);
            removeCallbacks(mShowMenuRunnable);
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
            mIsMenuDismissedByUser = false;
            if (mActionPopup != null) {
                mActionPopup.dismiss();
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
                // 传入 true 表示开始手柄
                drawHandle(canvas, true, startChar.rect.left, startChar.rect.bottom);
            }
            
            // 3. 绘制结束手柄
            if (end < mCharList.size()) {
                OcrChar endChar = mCharList.get(end);
                // 传入 false 表示结束手柄
                drawHandle(canvas, false, endChar.rect.right, endChar.rect.bottom);
            }
        }
        
        /**
         * 绘制手柄
         * @param isStartHandle true为开始手柄，false为结束手柄
         * @param x 锚点X坐标（文本边界）
         * @param y 锚点Y坐标（文本底部）
         */
        private void drawHandle(Canvas canvas, boolean isStartHandle, float x, float y) {
            float radius = mHandleSize / 2f;
            float centerX, centerY;
            
            Path path = new Path();
            
            if (isStartHandle) {
                // 开始手柄：位于锚点左下方
                // 圆心向左下偏移半径距离
                centerX = x - radius;
                centerY = y + radius;
                
                // 1. 绘制圆形主体
                path.addCircle(centerX, centerY, radius, Path.Direction.CW);
                // 2. 填充右上角（使其变成直角）
                // 矩形范围：圆心到(x, y-radius)之间其实是空的，我们需要填补圆心到(x,y)这个象限的缺口
                // 实际上是填充圆心右上方的区域
                // 矩形区域：left=centerX, top=centerY-radius, right=centerX+radius, bottom=centerY
                path.addRect(centerX, centerY - radius, centerX + radius, centerY, Path.Direction.CW);
            } else {
                // 结束手柄：位于锚点右下方
                // 圆心向右下偏移半径距离
                centerX = x + radius;
                centerY = y + radius;
                
                // 1. 绘制圆形主体
                path.addCircle(centerX, centerY, radius, Path.Direction.CW);
                // 2. 填充左上角（使其变成直角）
                // 矩形区域：left=centerX-radius, top=centerY-radius, right=centerX, bottom=centerY
                path.addRect(centerX - radius, centerY - radius, centerX, centerY, Path.Direction.CW);
            }
            
            canvas.drawPath(path, mHandlePaint);
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
                        if (mActionPopup != null) mActionPopup.dismiss();
                        // 显示放大镜
                        if (mMagnifier != null) mMagnifier.show(x, y);
                        // 禁止父视图拦截触摸事件
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    } else if (isTouchingEndHandle(x, y)) {
                        mDraggingHandle = HandleType.END;
                        if (mActionPopup != null) mActionPopup.dismiss();
                        // 显示放大镜
                        if (mMagnifier != null) mMagnifier.show(x, y);
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
                                // 显示放大镜
                                if (mMagnifier != null) mMagnifier.show(x, y);
                                // 禁止父视图拦截触摸事件
                                getParent().requestDisallowInterceptTouchEvent(true);
                                invalidate();
                                return true;
                            } else if (mDraggingHandle == HandleType.END) {
                                mEndIndex = targetIndex;
                                // 显示放大镜
                                if (mMagnifier != null) mMagnifier.show(x, y);
                                // 禁止父视图拦截触摸事件
                                getParent().requestDisallowInterceptTouchEvent(true);
                                invalidate();
                                return true;
                            } else {
                                // 如果不是拖拽状态，但有选择，判断是否要开始拖拽
                                if (isTouchingStartHandle(x, y)) {
                                    mDraggingHandle = HandleType.START;
                                    mStartIndex = targetIndex;
                                    // 显示放大镜
                                    if (mMagnifier != null) mMagnifier.show(x, y);
                                    // 禁止父视图拦截触摸事件
                                    getParent().requestDisallowInterceptTouchEvent(true);
                                    invalidate();
                                    return true;
                                } else if (isTouchingEndHandle(x, y)) {
                                    mDraggingHandle = HandleType.END;
                                    mEndIndex = targetIndex;
                                    // 显示放大镜
                                    if (mMagnifier != null) mMagnifier.show(x, y);
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
                case MotionEvent.ACTION_CANCEL:
                    // 隐藏放大镜
                    if (mMagnifier != null) mMagnifier.dismiss();
                    
                        // 3. 停止拖拽
                    if (mDraggingHandle != HandleType.NONE) {
                        mDraggingHandle = HandleType.NONE;
                        mIsMenuDismissedByUser = false;
                        showActionMenu();
                    }
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
            float radius = mHandleSize / 2f;
            // 计算新的圆心位置：锚点左下偏移
            float centerX = startChar.rect.left - radius;
            float centerY = startChar.rect.bottom + radius;
            
            return isPointInCircle(x, y, centerX, centerY, mHandleSize);
        }
        
        /**
         * 判断是否触摸到结束手柄
         */
        private boolean isTouchingEndHandle(float x, float y) {
            if (mEndIndex == -1 || mEndIndex >= mCharList.size()) {
                return false;
            }
            
            OcrChar endChar = mCharList.get(mEndIndex);
            float radius = mHandleSize / 2f;
            // 计算新的圆心位置：锚点右下偏移
            float centerX = endChar.rect.right + radius;
            float centerY = endChar.rect.bottom + radius;
            
            return isPointInCircle(x, y, centerX, centerY, mHandleSize);
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
         * 判断点是否在当前选择区域内
         */
        private boolean isPointInSelection(float x, float y) {
            if (mStartIndex == -1 || mEndIndex == -1 || mCharList.isEmpty()) {
                return false;
            }
            
            int start = Math.min(mStartIndex, mEndIndex);
            int end = Math.max(mStartIndex, mEndIndex);
            
            for (int i = start; i <= end && i < mCharList.size(); i++) {
                OcrChar ocrChar = mCharList.get(i);
                if (ocrChar.rect.contains(x, y)) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * 判断字符是否为边界符（空白符或标点符号）
         */
        private boolean isBoundary(String text) {
            if (text == null || text.isEmpty()) {
                return false;
            }
            char c = text.charAt(0);
            
            // 空白符
            if (Character.isWhitespace(c)) {
                return true;
            }
            
            // 常见的标点分隔符（包括英文和中文）
            // 英文：, . ! ? ; :
            // 中文：， 。 ！ ？ ； ： 、
            String separators = ",.!?;:，。！？；：、";
            return separators.indexOf(c) != -1;
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
            
            // 如果已显示，先隐藏
            if (mActionPopup != null && mActionPopup.isShowing()) {
                mActionPopup.dismiss();
            }

            // 创建菜单布局
            LinearLayout menuLayout = new LinearLayout(getContext());
            menuLayout.setOrientation(LinearLayout.HORIZONTAL);
            menuLayout.setGravity(Gravity.CENTER_VERTICAL);
            
            // 设置背景
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.WHITE);
            background.setCornerRadius(dpToPx(getContext(), 8));
            // 添加阴影效果需要elevation，但GradientDrawable本身不支持，依赖View的elevation
            menuLayout.setBackground(background);
            menuLayout.setElevation(dpToPx(getContext(), 4));
            
            int padding = dpToPx(getContext(), 12);
            menuLayout.setPadding(padding, padding / 2, padding, padding / 2);

            // 创建"复制"按钮
            TextView copyBtn = createMenuButton("Copy");
            copyBtn.setOnClickListener(v -> {
                String selectedText = getSelectedString();
                copyToClipboard(selectedText);
                clearSelection(); // 复制后清除选择
                if (mActionPopup != null) mActionPopup.dismiss();
            });
            menuLayout.addView(copyBtn);

            // 分割线
            View divider = new View(getContext());
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dpToPx(getContext(), 1), dpToPx(getContext(), 16));
            dividerParams.setMargins(dpToPx(getContext(), 12), 0, dpToPx(getContext(), 12), 0);
            divider.setLayoutParams(dividerParams);
            divider.setBackgroundColor(Color.LTGRAY);
            menuLayout.addView(divider);

            // 创建"全选"按钮
            TextView selectAllBtn = createMenuButton("Select All");
            selectAllBtn.setOnClickListener(v -> {
                selectAll();
                if (mActionPopup != null) mActionPopup.dismiss();
                // 全选后重新显示菜单
                post(this::showActionMenu); 
            });
            menuLayout.addView(selectAllBtn);

            // 创建PopupWindow
            mActionPopup = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
            mActionPopup.setElevation(dpToPx(getContext(), 8));
            mActionPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); 
            mActionPopup.setOutsideTouchable(false);

            // 计算显示位置
            RectF selectionRect = getSelectionRect();
            if (selectionRect != null) {
                // 将视图坐标转换为屏幕坐标
                int[] screenLocation = new int[2];
                getLocationOnScreen(screenLocation);
                
                // 菜单显示在选择区域上方
                int x = (int) (screenLocation[0] + selectionRect.centerX());
                int y = (int) (screenLocation[1] + selectionRect.top - dpToPx(getContext(), 70));
                
                // 确保不超出屏幕顶部
                if (y < dpToPx(getContext(), 50)) {
                    y = (int) (screenLocation[1] + selectionRect.bottom + dpToPx(getContext(), 10));
                }
                
                // 居中显示
                menuLayout.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                int popupWidth = menuLayout.getMeasuredWidth();
                x -= popupWidth / 2;
                
                // 确保不超出屏幕左右边界
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                if (x < 10) x = 10;
                if (x + popupWidth > screenWidth - 10) x = screenWidth - popupWidth - 10;

                mActionPopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
            }
        }
        
        private TextView createMenuButton(String text) {
            TextView btn = new TextView(getContext());
            btn.setText(text);
            btn.setTextColor(Color.BLACK);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btn.setPadding(dpToPx(getContext(), 12), dpToPx(getContext(), 8), dpToPx(getContext(), 12), dpToPx(getContext(), 8));
            return btn;
        }

        private RectF getSelectionRect() {
            if (mStartIndex == -1 || mEndIndex == -1 || mCharList.isEmpty()) {
                return null;
            }
            int start = Math.min(mStartIndex, mEndIndex);
            int end = Math.max(mStartIndex, mEndIndex);
            
            float left = Float.MAX_VALUE;
            float top = Float.MAX_VALUE;
            float right = Float.MIN_VALUE;
            float bottom = Float.MIN_VALUE;
            
            for (int i = start; i <= end && i < mCharList.size(); i++) {
                RectF r = mCharList.get(i).rect;
                left = Math.min(left, r.left);
                top = Math.min(top, r.top);
                right = Math.max(right, r.right);
                bottom = Math.max(bottom, r.bottom);
            }
            return new RectF(left, top, right, bottom);
        }

        private String getSelectedString() {
            if (mStartIndex == -1 || mEndIndex == -1 || mCharList.isEmpty()) {
                return "";
            }
            
            int start = Math.min(mStartIndex, mEndIndex);
            int end = Math.max(mStartIndex, mEndIndex);
            
            List<OcrChar> selectedChars = new ArrayList<>();
            for (int i = start; i <= end && i < mCharList.size(); i++) {
                selectedChars.add(mCharList.get(i));
            }
            
            return OcrImageView.this.getSmartText(selectedChars);
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