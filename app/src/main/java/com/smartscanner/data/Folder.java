package com.smartscanner.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(tableName = "folders")
public class Folder {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @Nullable
    public Integer parentFolderId;

    @NonNull
    public String name;

    public long createdAt;

    public Folder() {
        this.name = "";
        this.createdAt = System.currentTimeMillis();
    }

    @Ignore
    public Folder(@NonNull String name, @Nullable Integer parentFolderId) {
        this(0, parentFolderId, name, System.currentTimeMillis());
    }

    @Ignore
    public Folder(int id, @Nullable Integer parentFolderId, @NonNull String name, long createdAt) {
        this.id = id;
        this.parentFolderId = parentFolderId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Folder copyWithParent(@Nullable Integer newParentFolderId) {
        return new Folder(id, newParentFolderId, name, createdAt);
    }

    public Folder copyWithName(@NonNull String newName) {
        return new Folder(id, parentFolderId, newName, createdAt);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Folder)) {
            return false;
        }
        Folder other = (Folder) obj;
        return id == other.id
                && createdAt == other.createdAt
                && Objects.equals(parentFolderId, other.parentFolderId)
                && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, parentFolderId, name, createdAt);
    }
}
