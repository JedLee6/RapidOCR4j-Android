package hzkitty.android.ocr.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import hzkitty.android.ocr.R;
import hzkitty.android.ocr.model.OcrModel;

/**
 * 模型选择列表适配器
 */
public class ModelListAdapter extends RecyclerView.Adapter<ModelListAdapter.ModelViewHolder> {

    private List<OcrModel> modelList;
    private OnModelSelectListener selectListener;

    public ModelListAdapter(List<OcrModel> modelList) {
        this.modelList = modelList;
    }

    @NonNull
    @Override
    public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model_selection, parent, false);
        return new ModelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
        OcrModel model = modelList.get(position);
        holder.tvModelName.setText(model.getName());
        holder.cbSelect.setChecked(model.isSelected());

        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            model.setSelected(isChecked);
            if (selectListener != null) {
                selectListener.onModelSelect(model, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return modelList.size();
    }

    public void setOnModelSelectListener(OnModelSelectListener listener) {
        this.selectListener = listener;
    }

    public interface OnModelSelectListener {
        void onModelSelect(OcrModel model, boolean isSelected);
    }

    static class ModelViewHolder extends RecyclerView.ViewHolder {
        TextView tvModelName;
        CheckBox cbSelect;

        ModelViewHolder(@NonNull View itemView) {
            super(itemView);
            tvModelName = itemView.findViewById(R.id.tv_model_name);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }
    }
}