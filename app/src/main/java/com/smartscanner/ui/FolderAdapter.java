package com.smartscanner.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.smartscanner.data.Folder;
import com.smartscanner.databinding.ItemFolderBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FolderAdapter extends ListAdapter<Folder, FolderAdapter.FolderViewHolder> {
    public interface OnFolderClickListener {
        void onFolderClick(Folder folder);
    }

    private final OnFolderClickListener onFolderClick;

    public FolderAdapter(OnFolderClickListener onFolderClick) {
        super(new FolderDiffCallback());
        this.onFolderClick = onFolderClick;
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFolderBinding binding = ItemFolderBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new FolderViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class FolderViewHolder extends RecyclerView.ViewHolder {
        private final ItemFolderBinding binding;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        FolderViewHolder(ItemFolderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Folder folder) {
            binding.tvFolderName.setText(folder.name);
            binding.tvCreatedAt.setText(dateFormat.format(new Date(folder.createdAt)));
            binding.getRoot().setOnClickListener(v -> onFolderClick.onFolderClick(folder));
        }
    }

    static class FolderDiffCallback extends DiffUtil.ItemCallback<Folder> {
        @Override
        public boolean areItemsTheSame(@NonNull Folder oldItem, @NonNull Folder newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Folder oldItem, @NonNull Folder newItem) {
            return oldItem.equals(newItem);
        }
    }
}
