package com.example.safechat.data.local.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.safechat.data.local.db.dao.ConversationDao;
import com.example.safechat.data.local.db.dao.MessageDao;
import com.example.safechat.data.local.db.dao.RatchetSessionDao;
import com.example.safechat.data.local.db.entities.ConversationEntity;
import com.example.safechat.data.local.db.entities.MessageEntity;
import com.example.safechat.data.local.db.entities.RatchetSessionEntity;

@Database(
        entities = {
                ConversationEntity.class,
                MessageEntity.class,
                RatchetSessionEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "safechat_local.db";
    private static volatile AppDatabase instance;

    public abstract ConversationDao conversationDao();
    public abstract MessageDao messageDao();
    public abstract RatchetSessionDao ratchetSessionDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}