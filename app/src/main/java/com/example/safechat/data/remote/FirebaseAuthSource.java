package com.example.safechat.data.remote;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.EmailAuthProvider;

public class FirebaseAuthSource {
    private final FirebaseAuth auth;

    public FirebaseAuthSource() {
        this.auth = FirebaseAuth.getInstance();
    }

    public Task<AuthResult> createUser(String email, String password) {
        return auth.createUserWithEmailAndPassword(email, password);
    }

    public Task<AuthResult> signIn(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password);
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public void signOut() {
        auth.signOut();
    }

    public Task<Void> reauthenticate(String password) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null || user.getEmail() == null) {
            throw new IllegalStateException(
                    "Không tìm thấy tài khoản đang đăng nhập."
            );
        }

        return user.reauthenticate(
                EmailAuthProvider.getCredential(user.getEmail(), password)
        );
    }

    public Task<Void> updatePassword(String newPassword) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            throw new IllegalStateException(
                    "Không tìm thấy tài khoản đang đăng nhập."
            );
        }

        return user.updatePassword(newPassword);
    }
}