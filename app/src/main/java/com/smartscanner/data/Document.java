package com.smartscanner.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(
        tableName = "documents",
        foreignKeys = @ForeignKey(
                entity = Folder.class,
                parentColumns = "id",
                childColumns = "folderId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("folderId")
)
public class Document {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @Nullable
    public Integer folderId;

    @NonNull
    public String title;

    @NonNull
    public String filePath;

    @NonNull
    public String fileType;

    @Nullable
    public String ocrText;

    public long createdAt;

    public Document() {
        this.title = "";
        this.filePath = "";
        this.fileType = "application/octet-stream";
        this.createdAt = System.currentTimeMillis();
    }

    @Ignore
    public Document(@Nullable Integer folderId,
                    @NonNull String title,
                    @NonNull String filePath,
                    @NonNull String fileType,
                    long createdAt) {
        this(0, folderId, title, filePath, fileType, null, createdAt);
    }

    @Ignore
    public Document(int id,
                    @Nullable Integer folderId,
                    @NonNull String title,
                    @NonNull String filePath,
                    @NonNull String fileType,
                    @Nullable String ocrText,
                    long createdAt) {
        this.id = id;
        this.folderId = folderId;
        this.title = title;
        this.filePath = filePath;
        this.fileType = fileType;
        this.ocrText = ocrText;
        this.createdAt = createdAt;
    }

    public Document copyWithFolderId(@Nullable Integer newFolderId) {
        return new Document(id, newFolderId, title, filePath, fileType, ocrText, createdAt);
    }

    public Document copyWithTitleAndPath(@NonNull String newTitle, @NonNull String newPath) {
        return new Document(id, folderId, newTitle, newPath, fileType, ocrText, createdAt);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Document)) {
            return false;
        }
        Document other = (Document) obj;
        return id == other.id
                && createdAt == other.createdAt
                && Objects.equals(folderId, other.folderId)
                && Objects.equals(title, other.title)
                && Objects.equals(filePath, other.filePath)
                && Objects.equals(fileType, other.fileType)
                && Objects.equals(ocrText, other.ocrText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, folderId, title, filePath, fileType, ocrText, createdAt);
    }
}
