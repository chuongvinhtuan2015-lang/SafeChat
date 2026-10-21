
package com.example.safechat.data.remote;

import android.net.Uri;

import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class FirebaseStorageSource {

    private final FirebaseStorage storage;

    public FirebaseStorageSource() {
        storage = FirebaseStorage.getInstance();
    }

    public Task<Uri> uploadAvatar(String uid, byte[] imageBytes) {
        StorageReference avatarRef = storage.getReference()
                .child("avatars")
                .child(uid + ".jpg");

        UploadTask uploadTask = avatarRef.putBytes(imageBytes);

        return uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();

                if (error != null) {
                    throw error;
                }

                throw new Exception("Không thể tải ảnh đại diện lên Firebase Storage.");
            }

            return avatarRef.getDownloadUrl();
        });
    }
}
