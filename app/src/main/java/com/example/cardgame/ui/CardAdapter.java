package com.example.cardgame.ui;

import com.example.cardgame.R;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.ViewHolder> {

    private Context context;
    private List<String> cardList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public CardAdapter(Context context, List<String> cardList, OnItemClickListener listener) {
        this.context = context;
        this.cardList = cardList;
        this.listener = listener;
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
            // 图片未找到时显示默认背景（可选）
            holder.ivCard.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        holder.itemView.setOnClickListener(v -> listener.onItemClick(position));

        // 动态设置左边距：第一张牌没有负边距，其余牌向左重叠8dp
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (position == 0) {
            params.leftMargin = 0;
        } else {
            float density = context.getResources().getDisplayMetrics().density;
            params.leftMargin = (int) (-8 * density); // -8dp 转换为像素
        }
        holder.itemView.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCard;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCard = itemView.findViewById(R.id.iv_card);
        }
    }

    /**
     * 根据牌的文本（如 "♥A"）获取 drawable 中对应图片的资源 ID。
     * 命名规则：花色_点数（全部小写）
     * 花色：heart, spade, diamond, club
     * 点数：ace, 2-10, jack, queen, king
     */
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
            default: rankPart = rank; break; // 数字 2-10
        }

        String fileName = suitPart + "_" + rankPart;
        return context.getResources().getIdentifier(fileName, "drawable", context.getPackageName());
    }
    public void updateData(List<String> newCardList) {
        this.cardList = newCardList;
        notifyDataSetChanged();
    }
}