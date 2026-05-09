package com.smartscanner.data;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertFolder(Folder folder);

    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    LiveData<List<Folder>> getAllFolders();

    @Delete
    int deleteFolder(Folder folder);

    @Update
    int updateFolder(Folder folder);

    @Query("UPDATE folders SET parentFolderId = :newParentFolderId WHERE parentFolderId = :oldParentFolderId")
    int moveChildFolders(int oldParentFolderId, @Nullable Integer newParentFolderId);
}
