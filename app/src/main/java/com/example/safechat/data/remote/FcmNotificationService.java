package com.example.safechat.data.remote;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.safechat.R;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.ui.home.ChungActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
public class FcmNotificationService extends FirebaseMessagingService {
    private static final String TAG="FCM_NOTIFICATION_SERVICE";
    private static final String CHANNEL_ID = "safechat_notification_channel";
    public static String currentActivePartnerId=null;
    @Override
    public void onNewToken(@NonNull String token){
        super.onNewToken(token);
        Log.d(TAG,"FCM Token được cấp mới: "+token);
        String currentUid=LocalDataManager.getInstance(this).getUid();
        if(currentUid!=null&&!currentUid.trim().isEmpty()){
            FirebaseFirestore.getInstance().collection("users")
                    .document(currentUid)
                    .update("fcmToken",token)
                    .addOnFailureListener(e->Log.e(TAG,"Lỗi cập nhật token lên Firestore: " + e.getMessage()));

        }
    }
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Map<String,String> data=remoteMessage.getData();
        if(data.isEmpty()) return;
        String senderId=data.get("senderId");
        String senderName = data.get("senderName");

        if(senderId !=null &&senderId.equals(currentActivePartnerId)){
            return;
        }
        String title =(senderName != null && !senderName.trim().isEmpty()) ? senderName : "SafeChat";
        String content="Bạn có một tin nhắn mới";
        showNotification(title, content);
    }
    private void showNotification(String title, String content) {
        NotificationManager manager=(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager ==null) return;
        if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.O){
            NotificationChannel channel=new NotificationChannel(
                    CHANNEL_ID,
                    "Tin nhắn mới",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Kênh thông báo tin nhắn bảo mật");
            manager.createNotificationChannel(channel);
            Intent intent = new Intent(this, ChungActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_lock) // Icon nhỏ hiển thị trên status bar
                    .setContentTitle(title)
                    .setContentText(content)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent);

            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
