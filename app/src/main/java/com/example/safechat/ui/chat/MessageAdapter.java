package com.example.safechat.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safechat.R;
import com.example.safechat.data.local.db.entities.MessageEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private List<MessageEntity> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void submitList(List<MessageEntity> list) {
        this.messages = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        MessageEntity message = messages.get(position);
        return message.isSentByMe() ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SENT) {
            View view = inflater.inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageEntity message = messages.get(position);
        String formattedTime = timeFormat.format(new Date(message.getTimestamp()));

        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message, formattedTime);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, formattedTime);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ViewHolder tin nhắn gửi đi (Bong bóng bên phải)
    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessageSent;
        private final TextView tvTimeSent;
        private final ImageView ivTick;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageSent = itemView.findViewById(R.id.tvMessageSent);
            tvTimeSent = itemView.findViewById(R.id.tvTimeSent);
            ivTick = itemView.findViewById(R.id.ivTick);
        }

        public void bind(MessageEntity message, String formattedTime) {
            if (tvMessageSent != null) {
                tvMessageSent.setText(message.getMessageText());
            }
            if (tvTimeSent != null) {
                tvTimeSent.setText(formattedTime);
            }
            if (ivTick != null) {
                // Điều chỉnh độ mờ hoặc trạng thái tick (PENDING, SENT, DELIVERED)
                if ("PENDING".equals(message.getStatus())) {
                    ivTick.setAlpha(0.4f);
                } else {
                    ivTick.setAlpha(1.0f);
                }
            }
        }
    }

    // ViewHolder tin nhắn nhận được (Bong bóng bên trái)
    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSenderMemberName;
        private final TextView tvMessageContent;
        private final TextView tvMessageTime;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderMemberName = itemView.findViewById(R.id.tvSenderMemberName);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            tvMessageTime = itemView.findViewById(R.id.tvMessageTime);
        }

        public void bind(MessageEntity message, String formattedTime) {
            // Trong chat 1-1 thì ẩn tên người gửi, chỉ hiển thị trong Chat Nhóm
            if (tvSenderMemberName != null) {
                tvSenderMemberName.setVisibility(View.GONE);
            }
            if (tvMessageContent != null) {
                tvMessageContent.setText(message.getMessageText());
            }
            if (tvMessageTime != null) {
                tvMessageTime.setText(formattedTime);
            }
        }
    }
}