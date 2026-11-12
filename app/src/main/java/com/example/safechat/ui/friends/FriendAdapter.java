package com.example.safechat.ui.friends;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safechat.R;
import com.example.safechat.data.models.Friend;
import com.example.safechat.utils.AvatarHelper;

import java.util.ArrayList;
import java.util.List;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendViewHolder> {

    private List<Friend> friends = new ArrayList<>();
    private final OnFriendActionListener listener;

    public interface OnFriendActionListener {
        void onFriendClick(Friend friend);
        void onBlockFriendClick(Friend friend);
        void onDeleteFriendClick(Friend friend);
        void onUnblockFriendClick(Friend friend);
    }

    public FriendAdapter(OnFriendActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Friend> list) {
        this.friends = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        holder.bind(friends.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFriendName;
        private final View layoutFriendActions;
        private final View btnBlockFriend;
        private final View btnDeleteFriend;
        private final View btnUnblock;
        private final ImageView imgFriendAvatar;
        private final TextView tvAvatarChar;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFriendName = itemView.findViewById(R.id.tvFriendName);
            layoutFriendActions = itemView.findViewById(R.id.layoutFriendActions);
            btnBlockFriend = itemView.findViewById(R.id.btnBlockFriend);
            btnDeleteFriend = itemView.findViewById(R.id.btnDeleteFriend);
            btnUnblock = itemView.findViewById(R.id.btnUnblock);
            imgFriendAvatar = itemView.findViewById(R.id.imgFriendAvatar);
            tvAvatarChar = itemView.findViewById(R.id.tvAvatarChar);
        }

        public void bind(Friend friend, OnFriendActionListener listener) {
            String displayName = friend.getFullName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Người dùng";
            }

            if (tvFriendName != null) {
                tvFriendName.setText(displayName);
            }

            AvatarHelper.loadAvatar(
                    itemView.getContext(),
                    friend.getAvatarUrl(),
                    displayName,
                    imgFriendAvatar,
                    tvAvatarChar
            );

            boolean isBlocked = "BLOCKED".equalsIgnoreCase(friend.getStatus());

            if (isBlocked) {
                // Trạng thái: ĐÃ CHẶN
                if (layoutFriendActions != null) layoutFriendActions.setVisibility(View.GONE);
                if (btnUnblock != null) {
                    btnUnblock.setVisibility(View.VISIBLE);
                    btnUnblock.setOnClickListener(v -> {
                        if (listener != null) listener.onUnblockFriendClick(friend);
                    });
                }
                // Vô hiệu hóa click chat & làm mờ nhẹ item
                itemView.setAlpha(0.55f);
                itemView.setOnClickListener(null);
            } else {
                // Trạng thái: BẠN BÈ BÌNH THƯỜNG
                if (layoutFriendActions != null) layoutFriendActions.setVisibility(View.VISIBLE);
                if (btnUnblock != null) btnUnblock.setVisibility(View.GONE);

                itemView.setAlpha(1.0f);
                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onFriendClick(friend);
                });

                if (btnBlockFriend != null) {
                    btnBlockFriend.setOnClickListener(v -> {
                        if (listener != null) listener.onBlockFriendClick(friend);
                    });
                }

                if (btnDeleteFriend != null) {
                    btnDeleteFriend.setOnClickListener(v -> {
                        if (listener != null) listener.onDeleteFriendClick(friend);
                    });
                }
            }
        }
    }
}