package hzkitty.android.ocr.model;

/**
 * OCR识别模型类，用于表示一个文本识别模型
 */
public class OcrModel {
    private String name;          // 模型名称
    private String modelPath;     // 模型文件路径
    private boolean isSelected;   // 是否被选中

    public OcrModel(String name, String modelPath) {
        this.name = name;
        this.modelPath = modelPath;
        this.isSelected = true;   // 默认选中
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}