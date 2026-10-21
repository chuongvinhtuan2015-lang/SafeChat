package com.example.safechat;

import android.app.Application;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;

public class SafeChatApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        setupSecurityProvider();
    }

    private void setupSecurityProvider() {
        // Gỡ phiên bản Bouncy Castle mặc định bị thu gọn của Android nếu có
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        // Đưa Bouncy Castle mới nhất vào vị trí ưu tiên số 1
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }
}