package com.da3dsoul.WallpaperSwitcher;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.da3dsoul.WallpaperSwitcher.Activity.AddDirectoryActivity;

import java.util.List;

public class AddDirectoryRecyclerViewAdapter extends RecyclerView.Adapter<AddDirectoryRecyclerViewAdapter.ViewHolder> {

    private final List<DirectoryModel> mValues;
    private final ActivityResultLauncher<Intent> resultLauncher;

    public AddDirectoryRecyclerViewAdapter(List<DirectoryModel> items, ActivityResultLauncher<Intent> resultLauncher) {
        mValues = items;
        this.resultLauncher = resultLauncher;
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.directory_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        DirectoryModel item = mValues.get(position);
        holder.mDirectory.setText(item.Directory);
        holder.mMinAspect.setText(String.format("%.1f", item.MinAspect));
        holder.mMaxAspect.setText(String.format("%.1f", item.MaxAspect));
        holder.mView.setOnClickListener(v -> {
            Activity a = getActivity(v.getContext());
            Intent it = new Intent(a, AddDirectoryActivity.class);
            it.putExtra("index", position);
            it.putExtra("model", item);
            resultLauncher.launch(it);
        });
    }

    private static Activity getActivity(Context cont) {
        if (cont == null)
            return null;
        else if (cont instanceof Activity)
            return (Activity)cont;
        else if (cont instanceof ContextWrapper)
            return getActivity(((ContextWrapper)cont).getBaseContext());

        return null;
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mMinAspect;
        public final TextView mMaxAspect;
        public final TextView mDirectory;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mMinAspect = view.findViewById(R.id.aspect_min);
            mMaxAspect = view.findViewById(R.id.aspect_max);
            mDirectory = view.findViewById(R.id.directory);
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString() + " '" + mDirectory.getText() + "' " + mMinAspect.getText() + " " + mMaxAspect.getText();
        }
    }
}