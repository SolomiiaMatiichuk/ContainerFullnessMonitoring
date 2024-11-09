package com.example.myapplication.ui.bluetooth;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
    private List<User> userList;
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(User user);
    }

    public UserAdapter(List<User> userList, OnItemClickListener onItemClickListener) {
        this.userList = userList;
        this.onItemClickListener = onItemClickListener;
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        public TextView idView, emailView, usernameView;

        public UserViewHolder(View itemView) {
            super(itemView);
            idView = itemView.findViewById(R.id.user_id_text);
            emailView = itemView.findViewById(R.id.user_email_text);
            usernameView = itemView.findViewById(R.id.user_username_text);
        }
    }

    @Override
    public UserViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.user_item, parent, false);
        return new UserViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(UserViewHolder holder, int position) {
        User user = userList.get(position);
        holder.idView.setText(String.valueOf(user.getId()));
        holder.emailView.setText(user.getEmail());
        holder.usernameView.setText(user.getUsername());

        // Handle item click
        holder.itemView.setOnClickListener(v -> onItemClickListener.onItemClick(user));
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }
}

