package com.example.safechat.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

public class AvatarHelper {

    private static final int[] AVATAR_COLORS = {
            Color.parseColor("#1E88E5"), // Blue
            Color.parseColor("#43A047"), // Green
            Color.parseColor("#E53935"), // Red
            Color.parseColor("#FB8C00"), // Orange
            Color.parseColor("#8E24AA"), // Purple
            Color.parseColor("#00ACC1"), // Cyan
            Color.parseColor("#3949AB"), // Indigo
            Color.parseColor("#D81B60")  // Pink
    };
    public static void loadAvatar(Context context, String base64Avatar, String displayName,
                                  ImageView imgAvatar, TextView tvAvatarText) {
        if (imgAvatar == null) return;

        if (base64Avatar != null && !base64Avatar.trim().isEmpty()) {
            try {
                String cleanData = base64Avatar.trim();
                // Bỏ header metadata nếu có (ví dụ: data:image/jpeg;base64,...)
                if (cleanData.contains(",")) {
                    cleanData = cleanData.substring(cleanData.indexOf(",") + 1);
                }

                byte[] imageBytes = Base64.decode(cleanData, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                if (bitmap != null && context != null) {
                    // Bo tròn ảnh bằng API Android gốc
                    RoundedBitmapDrawable roundedDrawable =
                            RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
                    roundedDrawable.setCircular(true);

                    imgAvatar.setImageDrawable(roundedDrawable);
                    imgAvatar.setVisibility(View.VISIBLE);
                    if (tvAvatarText != null) {
                        tvAvatarText.setVisibility(View.GONE);
                    }
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        showDefaultInitial(displayName, imgAvatar, tvAvatarText);
    }

    /**
     * Nạp trực tiếp từ mảng byte[] (nếu lấy thẳng byte[] từ local DB)
     */
    public static void loadAvatar(Context context, byte[] imageBytes, String displayName,
                                  ImageView imgAvatar, TextView tvAvatarText) {
        if (imgAvatar == null) return;

        if (imageBytes != null && imageBytes.length > 0 && context != null) {
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (bitmap != null) {
                    RoundedBitmapDrawable roundedDrawable =
                            RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
                    roundedDrawable.setCircular(true);

                    imgAvatar.setImageDrawable(roundedDrawable);
                    imgAvatar.setVisibility(View.VISIBLE);
                    if (tvAvatarText != null) {
                        tvAvatarText.setVisibility(View.GONE);
                    }
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        showDefaultInitial(displayName, imgAvatar, tvAvatarText);
    }
    //Khởi tạo mặc định
    private static void showDefaultInitial(String displayName, ImageView imgAvatar, TextView tvAvatarText) {
        if (imgAvatar != null) {
            imgAvatar.setImageDrawable(null);
            imgAvatar.setVisibility(View.GONE);
        }
        if (tvAvatarText != null) {
            tvAvatarText.setVisibility(View.VISIBLE);
            tvAvatarText.setText(getInitial(displayName));
            tvAvatarText.setTextColor(Color.WHITE);
            setCircleBackgroundColor(tvAvatarText, getColorForUser(displayName));
        }
    }


    public static String getInitial(String name) {
        if (name == null || name.trim().isEmpty()) return "U";
        String[] parts = name.trim().split("\\s+");
        String lastWord = parts[parts.length - 1];
        if (lastWord.isEmpty()) return "U";
        return String.valueOf(lastWord.charAt(0)).toUpperCase();
    }

    private static int getColorForUser(String name) {
        if (name == null || name.isEmpty()) return AVATAR_COLORS[0];
        int hash = Math.abs(name.hashCode());
        return AVATAR_COLORS[hash % AVATAR_COLORS.length];
    }

    private static void setCircleBackgroundColor(TextView textView, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        textView.setBackground(shape);
    }
}