package com.tssw.newmupdf;


import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tssw.newmupdf.R;

import java.util.List;

public class GenericAndroidAppAdapter extends RecyclerView.Adapter<GenericAndroidAppAdapter.ViewHolder> {
    private List<GenericAndroidApp> apps;
    private OnItemClickListener listener;

    public GenericAndroidAppAdapter(List<GenericAndroidApp> apps, OnItemClickListener listener) {
        this.apps = apps;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.dialog_app_chooser_item, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        GenericAndroidApp app = apps.get(viewHolder.getAdapterPosition());
        viewHolder.bind(app);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView appIcon;
        private TextView appName;
        private GenericAndroidApp app;

        ViewHolder(View itemView, final OnItemClickListener listener) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.image_view_app_icon);
            appName = itemView.findViewById(R.id.text_view_app_name);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(app);
                }
            });
        }

        void bind(GenericAndroidApp app) {
            this.app = app;
            PackageManager packageManager = appName.getContext().getPackageManager();
            appIcon.setImageDrawable(app.resolveInfo.loadIcon(packageManager));
            appName.setText(app.resolveInfo.loadLabel(packageManager));
        }
    }

    public interface OnItemClickListener {
        void onItemClick(GenericAndroidApp app);
    }
}