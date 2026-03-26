package com.smartscanner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val folderId: Int?, // Nullable if not in a folder
    val title: String,
    val filePath: String,
    val fileType: String,
    val ocrText: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
