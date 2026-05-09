package com.smartscanner.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.smartscanner.data.Document;
import com.smartscanner.databinding.ItemDocumentBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DocumentAdapter extends ListAdapter<Document, DocumentAdapter.DocumentViewHolder> {
    public interface OnDocumentClickListener {
        void onDocumentClick(Document document);
    }

    private final OnDocumentClickListener onDocumentClick;
    private final OnDocumentClickListener onOptionsClick;

    public DocumentAdapter(OnDocumentClickListener onDocumentClick, OnDocumentClickListener onOptionsClick) {
        super(new DocumentDiffCallback());
        this.onDocumentClick = onDocumentClick;
        this.onOptionsClick = onOptionsClick;
    }

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDocumentBinding binding = ItemDocumentBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new DocumentViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class DocumentViewHolder extends RecyclerView.ViewHolder {
        private final ItemDocumentBinding binding;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        DocumentViewHolder(ItemDocumentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Document document) {
            binding.tvDocumentTitle.setText(document.title);
            binding.tvCreatedAt.setText(dateFormat.format(new Date(document.createdAt)));
            binding.getRoot().setOnClickListener(v -> onDocumentClick.onDocumentClick(document));
            binding.btnOptions.setOnClickListener(v -> onOptionsClick.onDocumentClick(document));
        }
    }

    static class DocumentDiffCallback extends DiffUtil.ItemCallback<Document> {
        @Override
        public boolean areItemsTheSame(@NonNull Document oldItem, @NonNull Document newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Document oldItem, @NonNull Document newItem) {
            return oldItem.equals(newItem);
        }
    }
}
