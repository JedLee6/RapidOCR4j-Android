package io.github.hzkitty.entity;

import org.opencv.core.Point;

import java.util.Arrays;

public class RecResult {
    private Point[] dtBoxes;
    private final String text;
    private final float confidence;
    private WordBoxResult wordBoxResult;
    private boolean isFinalBox = false; // 标记是否为最终框，不再参与后续合并遍历

    public RecResult(Point[] dtBoxes, String text, float confidence, WordBoxResult wordBoxResult) {
        this.dtBoxes = dtBoxes;
        this.text = text;
        this.confidence = confidence;
        this.wordBoxResult = wordBoxResult;
    }

    public Point[] getDtBoxes() {
        return dtBoxes;
    }

    public void setDtBoxes(Point[] dtBoxes) {
        this.dtBoxes = dtBoxes;
    }

    public String getText() {
        return text;
    }

    public float getConfidence() {
        return confidence;
    }

    public WordBoxResult getWordBoxResult() {
        return wordBoxResult;
    }

    public void setWordBoxResult(WordBoxResult wordBoxResult) {
        this.wordBoxResult = wordBoxResult;
    }

    public boolean isFinalBox() {
        return isFinalBox;
    }

    public void setFinalBox(boolean isFinalBox) {
        this.isFinalBox = isFinalBox;
    }

    @Override
    public String toString() {
        return "RecResult{" +
                "dtBoxes=" + Arrays.toString(dtBoxes) +
                ", text='" + text + '\'' +
                ", confidence=" + confidence +
                ", wordBoxResult=" + wordBoxResult +
                ", isFinalBox=" + isFinalBox +
                '}';
    }
}