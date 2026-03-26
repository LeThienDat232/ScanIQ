package com.smartscanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartscanner.data.Document
import com.smartscanner.databinding.ItemDocumentBinding
import java.text.SimpleDateFormat
import java.util.*

class DocumentAdapter(
    private val onDocumentClick: (Document) -> Unit,
    private val onOptionsClick: (Document) -> Unit
) : ListAdapter<Document, DocumentAdapter.DocumentViewHolder>(DocumentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DocumentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val document = getItem(position)
        holder.bind(document)
    }

    inner class DocumentViewHolder(private val binding: ItemDocumentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        fun bind(document: Document) {
            binding.tvDocumentTitle.text = document.title
            binding.tvCreatedAt.text = dateFormat.format(Date(document.createdAt))
            
            binding.root.setOnClickListener { onDocumentClick(document) }
            binding.btnOptions.setOnClickListener { onOptionsClick(document) }
        }
    }

    class DocumentDiffCallback : DiffUtil.ItemCallback<Document>() {
        override fun areItemsTheSame(oldItem: Document, newItem: Document): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Document, newItem: Document): Boolean {
            return oldItem == newItem
        }
    }
}
