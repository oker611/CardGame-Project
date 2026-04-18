package com.example.cardgame.ui;

import com.example.cardgame.R;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.ViewHolder> {

    private Context context;
    private List<String> cardList;
    private OnItemClickListener listener;
    private boolean[] selectedStates;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public CardAdapter(Context context, List<String> cardList, OnItemClickListener listener) {
        this.context = context;
        this.cardList = cardList;
        this.listener = listener;
        this.selectedStates = new boolean[cardList.size()];
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String card = cardList.get(position);
        int resId = getCardDrawableResource(card);
        if (resId != 0) {
            holder.ivCard.setImageResource(resId);
        } else {
            holder.ivCard.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // 只做上移，不改变背景和阴影
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (selectedStates[position]) {
            params.topMargin = (int) (-8 * context.getResources().getDisplayMetrics().density);
        } else {
            params.topMargin = 0;
        }
        holder.itemView.setLayoutParams(params);

        holder.itemView.setOnClickListener(v -> {
            selectedStates[position] = !selectedStates[position];
            notifyItemChanged(position);
            listener.onItemClick(position);
        });

        // 左边距设置（不变）
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (position == 0) {
            marginParams.leftMargin = 0;
        } else {
            float density = context.getResources().getDisplayMetrics().density;
            marginParams.leftMargin = (int) (-8 * density);
        }
        holder.itemView.setLayoutParams(marginParams);
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }

    public void updateData(List<String> newCardList) {
        this.cardList = newCardList;
        this.selectedStates = new boolean[newCardList.size()];
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCard;
        CardView cardView;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCard = itemView.findViewById(R.id.iv_card);
            cardView = (CardView) itemView;
        }
    }

    private int getCardDrawableResource(String cardId) {
        if (cardId == null || cardId.length() < 2) return 0;

        String suitPart = "";
        String rankPart = "";

        char suitChar = cardId.charAt(0);
        switch (suitChar) {
            case '♥': suitPart = "heart"; break;
            case '♠': suitPart = "spade"; break;
            case '♦': suitPart = "diamond"; break;
            case '♣': suitPart = "club"; break;
            default: return 0;
        }

        String rank = cardId.substring(1);
        switch (rank) {
            case "A": rankPart = "ace"; break;
            case "J": rankPart = "jack"; break;
            case "Q": rankPart = "queen"; break;
            case "K": rankPart = "king"; break;
            default: rankPart = rank; break;
        }

        String fileName = suitPart + "_" + rankPart;
        return context.getResources().getIdentifier(fileName, "drawable", context.getPackageName());
    }
}