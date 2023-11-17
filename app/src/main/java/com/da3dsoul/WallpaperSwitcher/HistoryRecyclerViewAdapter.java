package com.da3dsoul.WallpaperSwitcher;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class HistoryRecyclerViewAdapter extends RecyclerView.Adapter<HistoryRecyclerViewAdapter.ViewHolder> {

    private final List<String> mValues;

    public HistoryRecyclerViewAdapter(List<String> items) {
        mValues = items;
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.history_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        String item = mValues.get(position);
        holder.uri = item;
        holder.mNameView.setText(item.substring(item.lastIndexOf("/")+1));
        Glide.with(holder.mImageView.getContext())
                .load(holder.uri)
                .fitCenter()
                .into(holder.mImageView);

        holder.mView.setOnClickListener(v -> {
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(Uri.parse(holder.uri), "image/*");
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            v.getContext().startActivity(openIntent);
            Activity a = getActivity(v.getContext());
            if (a != null) a.finish();
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
        public final TextView mNameView;
        public final ImageView mImageView;
        public String uri;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mNameView = view.findViewById(R.id.item_name);
            mImageView = view.findViewById(R.id.item_image);
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString() + " '" + mNameView.getText() + "'";
        }
    }
}