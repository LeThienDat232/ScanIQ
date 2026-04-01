package com.smartscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val parentFolderId: Int? = null, // Null if it's a root folder
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
