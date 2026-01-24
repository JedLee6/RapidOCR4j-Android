package hzkitty.android.ocr.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.opencv.core.Point;

import java.util.List;

import hzkitty.android.ocr.R;
import io.github.hzkitty.entity.RecResult;

/**
 * 识别结果详细信息适配器
 */
public class RecResultAdapter extends RecyclerView.Adapter<RecResultAdapter.RecResultViewHolder> {

    private List<RecResult> recResultList;

    public RecResultAdapter(List<RecResult> recResultList) {
        this.recResultList = recResultList;
    }

    @NonNull
    @Override
    public RecResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rec_result, parent, false);
        return new RecResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecResultViewHolder holder, int position) {
        RecResult recResult = recResultList.get(position);
        
        // 设置识别文本
        holder.tvText.setText(recResult.getText());
        
        // 设置置信度
        String confidenceText = String.format(holder.itemView.getContext().getString(R.string.confidence_format), recResult.getConfidence() * 100);
        holder.tvConfidence.setText(confidenceText);
        
        // 设置坐标信息
        Point[] dtBoxes = recResult.getDtBoxes();
        StringBuilder coordinatesBuilder = new StringBuilder();
        coordinatesBuilder.append(holder.itemView.getContext().getString(R.string.coordinates));
        
        if (dtBoxes != null && dtBoxes.length > 0) {
            for (int i = 0; i < dtBoxes.length; i++) {
                Point point = dtBoxes[i];
                if (i > 0) {
                    coordinatesBuilder.append(", ");
                }
                coordinatesBuilder.append(String.format("(%.1f, %.1f)", point.x, point.y));
            }
        } else {
            coordinatesBuilder.append(holder.itemView.getContext().getString(R.string.none));
        }
        
        holder.tvCoordinates.setText(coordinatesBuilder.toString());
        
        // 设置边框信息
        if (recResult.getWordBoxResult() != null) {
            holder.tvBoxInfo.setVisibility(View.VISIBLE);
            holder.tvBoxInfo.setText(holder.itemView.getContext().getString(R.string.word_box) + recResult.getWordBoxResult().toString());
        } else {
            holder.tvBoxInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return recResultList.size();
    }

    /**
     * 更新数据
     */
    public void updateData(List<RecResult> newRecResultList) {
        this.recResultList = newRecResultList;
        notifyDataSetChanged();
    }

    static class RecResultViewHolder extends RecyclerView.ViewHolder {
        TextView tvText;
        TextView tvConfidence;
        TextView tvCoordinates;
        TextView tvBoxInfo;

        RecResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_text);
            tvConfidence = itemView.findViewById(R.id.tv_confidence);
            tvCoordinates = itemView.findViewById(R.id.tv_coordinates);
            tvBoxInfo = itemView.findViewById(R.id.tv_box_info);
        }
    }
}