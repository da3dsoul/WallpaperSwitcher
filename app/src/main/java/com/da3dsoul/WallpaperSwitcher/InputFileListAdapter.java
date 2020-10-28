package com.da3dsoul.WallpaperSwitcher;

import java.io.File;
import java.util.List;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class InputFileListAdapter extends RecyclerView.Adapter<InputFileListAdapter.ViewHolder> {
    private final List<InputFileListItem> mList;
    private final SelectDir parent;

    public InputFileListAdapter(SelectDir parent, List<InputFileListItem> data) {
        super();
        mList = data;
        this.parent = parent;
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.input_file_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        InputFileListItem item = mList.get(position);
        holder.mNameView.setText(item.getName());
        holder.mImageView.setImageResource(item.getIcon());
        holder.mPathView.setText(item.getDir());
        holder.mView.setOnClickListener(v -> {
            String selectedFileString = parent.directoryEntries.get(position).getName();

            if (selectedFileString.equals(parent.getString(R.string.up_one_level))) {
                parent.upOneLevel();
            } else {
                Log.i("InputFile", parent.directoryEntries.get(position).getDir());
                File clickedFile = new File(parent.directoryEntries.get(position).getDir());
                if (parent.directoryEntries.get(position).isFolder()) {
                    parent.browseTo(clickedFile);
                }
            }
        });
    }

    public long getItemId(int arg0) {
        return arg0;
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mNameView;
        public final TextView mPathView;
        public final ImageView mImageView;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mNameView = view.findViewById(R.id.multiple_name);
            mImageView = view.findViewById(R.id.tag);
            mPathView = view.findViewById(R.id.multiple_path);
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString() + " '" + mNameView.getText() + "'";
        }
    }
}