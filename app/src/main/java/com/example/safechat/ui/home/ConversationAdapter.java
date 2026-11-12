package com.example.safechat.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safechat.R;
import com.example.safechat.data.local.db.entities.ConversationEntity;
import com.example.safechat.utils.AvatarHelper;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(ConversationEntity conversation);
    }

    private final List<ConversationEntity> conversationList = new ArrayList<>();
    private final OnConversationClickListener listener;

    public ConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật danh sách cuộc trò chuyện và vẽ lại UI
     */
    public void setList(List<ConversationEntity> newList) {
        this.conversationList.clear();
        if (newList != null) {
            this.conversationList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chung, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        holder.bind(conversationList.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return conversationList.size();
    }

    public static class ConversationViewHolder extends RecyclerView.ViewHolder {

        private final FrameLayout layoutAvatar;
        private final TextView tvAvatarText;
        private final TextView tvSenderName;
        private final TextView tvLastMessage;
        private final TextView tvTime;
        private final MaterialCardView tvUnreadBadgeCard;
        private final TextView tvUnreadBadge;
        private final ImageView imgAvatar;

        public ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutAvatar = itemView.findViewById(R.id.layoutAvatar);
            tvAvatarText = itemView.findViewById(R.id.tvAvatarText);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvUnreadBadgeCard = itemView.findViewById(R.id.tvUnreadBadgeCard);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
            imgAvatar=itemView.findViewById(R.id.imgAvatar);
        }

        public void bind(ConversationEntity conversation, OnConversationClickListener listener) {
            // 1. Tên người nhận
            String name = conversation.getPartnerName();
            if (name != null && !name.trim().isEmpty()) {
                tvSenderName.setText(name);
                tvAvatarText.setText(String.valueOf(name.trim().charAt(0)).toUpperCase(Locale.getDefault()));
            } else {
                tvSenderName.setText("Người dùng");
                tvAvatarText.setText("U");
            }
            AvatarHelper.loadAvatar(
                    itemView.getContext(),
                    conversation.getPartnerAvatar(),
                    conversation.getPartnerName(),
                    imgAvatar,
                    tvAvatarText


            );
            // 2. Nội dung tin nhắn cuối cùng (đã giải mã)
            String lastMsg = conversation.getLastMessage();
            tvLastMessage.setText(lastMsg != null ? lastMsg : "");

            // 3. Thời gian tin nhắn cuối
            tvTime.setText(formatTime(conversation.getLastTimestamp()));

            // 4. Badge tin nhắn chưa đọc (tạm thời ẩn)
            if (tvUnreadBadgeCard != null) {
                tvUnreadBadgeCard.setVisibility(View.GONE);
            }

            // 5. Click mở cuộc trò chuyện
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationClick(conversation);
                }
            });
        }

        private String formatTime(long timestamp) {
            if (timestamp <= 0) return "";

            Calendar msgCal = Calendar.getInstance();
            msgCal.setTimeInMillis(timestamp);

            Calendar nowCal = Calendar.getInstance();

            // Nếu cùng ngày -> Hiện HH:mm (vd: 14:30)
            if (nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                    nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)) {
                return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
            }

            // Nếu là hôm qua -> Hiện "Hôm qua"
            nowCal.add(Calendar.DAY_OF_YEAR, -1);
            if (nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                    nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)) {
                return "Hôm qua";
            }

            // Các ngày trước nữa -> Hiện ngày/tháng
            return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(timestamp));
        }
    }
}