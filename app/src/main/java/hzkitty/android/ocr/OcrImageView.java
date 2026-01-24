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
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.hzkitty.entity.RecResult;

public class OcrImageView extends androidx.appcompat.widget.AppCompatImageView {
    private Bitmap mBitmap;
    private List<RecResult> mOcrResults;
    private List<Rect> mTextRects;
    private List<String> mTextList;
    
    private Paint mRectPaint;
    private Paint mTextPaint;
    private Paint mSelectedPaint;
    
    private Rect mSelectedRect;
    private int mSelectedIndex;
    
    private float mDownX, mDownY;
    private boolean mIsSelecting;
    private int mTouchSlop;
    
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
        // 初始化画笔
        mRectPaint = new Paint();
        mRectPaint.setColor(Color.RED);
        mRectPaint.setStyle(Paint.Style.STROKE);
        mRectPaint.setStrokeWidth(2f);
        
        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(16f);
        mTextPaint.setShadowLayer(2f, 0f, 0f, Color.BLACK);
        
        mSelectedPaint = new Paint();
        mSelectedPaint.setColor(Color.argb(128, 0, 255, 255));
        mSelectedPaint.setStyle(Paint.Style.FILL);
        
        // 初始化变量
        mOcrResults = new ArrayList<>();
        mTextRects = new ArrayList<>();
        mTextList = new ArrayList<>();
        mSelectedRect = null;
        mSelectedIndex = -1;
        mIsSelecting = false;
        
        // 获取触摸阈值
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        
        // 设置触摸监听
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleTouchEvent(event);
            }
        });
    }
    
    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        mBitmap = bitmap;
        clearOcrResults();
    }
    
    /**
     * 设置OCR识别结果
     */
    public void setOcrResults(List<RecResult> results) {
        mOcrResults.clear();
        mTextRects.clear();
        mTextList.clear();
        
        if (results != null && !results.isEmpty() && mBitmap != null) {
            mOcrResults.addAll(results);
            
            // 获取ImageView的矩阵，该矩阵包含了图像的缩放和位移信息
            android.graphics.Matrix imageMatrix = getImageMatrix();
            
            // 创建一个用于转换坐标的数组
            float[] points = new float[2];
            
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
                        // 将原始坐标转换为视图坐标
                        points[0] = (float) point.x;
                        points[1] = (float) point.y;
                        imageMatrix.mapPoints(points);
                        
                        left = Math.min(left, (int) points[0]);
                        top = Math.min(top, (int) points[1]);
                        right = Math.max(right, (int) points[0]);
                        bottom = Math.max(bottom, (int) points[1]);
                    }
                    
                    Rect rect = new Rect(left, top, right, bottom);
                    mTextRects.add(rect);
                    mTextList.add(result.getText());
                }
            }
        }
        
        invalidate();
    }
    
    /**
     * 清除OCR结果
     */
    public void clearOcrResults() {
        mOcrResults.clear();
        mTextRects.clear();
        mTextList.clear();
        mSelectedRect = null;
        mSelectedIndex = -1;
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制OCR识别结果的文本框
        for (int i = 0; i < mTextRects.size(); i++) {
            Rect rect = mTextRects.get(i);
            
            // 如果是选中的文本框，绘制选中效果
            if (i == mSelectedIndex) {
                canvas.drawRect(rect, mSelectedPaint);
            }
            
            // 绘制文本框边框
            canvas.drawRect(rect, mRectPaint);
            
            // 绘制文本内容（可选）
            String text = mTextList.get(i);
            canvas.drawText(text, rect.left + 5, rect.top + 20, mTextPaint);
        }
    }
    
    /**
     * 处理触摸事件
     */
    private boolean handleTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = x;
                mDownY = y;
                mIsSelecting = true;
                mSelectedIndex = -1;
                mSelectedRect = null;
                break;
                
            case MotionEvent.ACTION_MOVE:
                // 如果移动距离超过触摸阈值，认为是滚动，不处理选择
                if (Math.abs(x - mDownX) > mTouchSlop || Math.abs(y - mDownY) > mTouchSlop) {
                    mIsSelecting = false;
                }
                break;
                
            case MotionEvent.ACTION_UP:
                if (mIsSelecting) {
                    // 检查是否点击了某个文本框
                    for (int i = 0; i < mTextRects.size(); i++) {
                        Rect rect = mTextRects.get(i);
                        if (rect.contains((int) x, (int) y)) {
                            mSelectedIndex = i;
                            mSelectedRect = rect;
                            
                            // 显示文本选择器
                            showTextSelection(i, x, y);
                            break;
                        }
                    }
                }
                invalidate();
                break;
        }
        
        return false; // 返回false，让ScrollView可以继续处理滚动事件
    }
    
    /**
     * 显示文本选择器
     */
    private void showTextSelection(int index, float x, float y) {
        if (index >= 0 && index < mTextList.size()) {
            String text = mTextList.get(index);
            
            // 这里我们使用Toast来显示选择的文本，实际应用中可以使用更复杂的UI组件
            // 在实际产品中，应该实现系统级的文本选择器
            Toast.makeText(getContext(), "已选择：" + text + "\n点击复制", Toast.LENGTH_SHORT).show();
            
            // 简单实现复制到剪贴板
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("OCR文本", text);
            clipboard.setPrimaryClip(clip);
            
            Toast.makeText(getContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }
}