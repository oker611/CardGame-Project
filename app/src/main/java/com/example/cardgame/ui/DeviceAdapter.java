package com.example.cardgame.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cardgame.R;
import com.example.cardgame.model.DeviceInfo;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private List<DeviceInfo> deviceList;
    private OnConnectClickListener listener;

    public interface OnConnectClickListener {
        void onConnect(DeviceInfo device);
    }

    public DeviceAdapter(List<DeviceInfo> deviceList, OnConnectClickListener listener) {
        this.deviceList = deviceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceInfo device = deviceList.get(position);
        holder.tvDeviceName.setText(device.getDeviceName());

        // 状态标签
        if (device.isPaired()) {
            holder.tvStatus.setText("已配对");
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.green));
        } else {
            holder.tvStatus.setText("未配对");
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.red));
        }

        // 设备图标
        if (device.getDeviceType() == 1) { // 主机
            holder.ivDeviceIcon.setImageResource(R.drawable.ic_device_host);
        } else {
            holder.ivDeviceIcon.setImageResource(R.drawable.ic_device_phone);
        }

        // 信号强度
        switch (device.getSignalStrength()) {
            case 2:
                holder.ivSignal.setImageResource(R.drawable.signal_strong);
                break;
            case 1:
                holder.ivSignal.setImageResource(R.drawable.signal_medium);
                break;
            default:
                holder.ivSignal.setImageResource(R.drawable.signal_weak);
                break;
        }

        holder.btnConnect.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConnect(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList == null ? 0 : deviceList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivDeviceIcon;
        TextView tvDeviceName;
        TextView tvStatus;
        ImageView ivSignal;
        Button btnConnect;

        ViewHolder(View itemView) {
            super(itemView);
            ivDeviceIcon = itemView.findViewById(R.id.iv_device_icon);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvStatus = itemView.findViewById(R.id.tv_status);
            ivSignal = itemView.findViewById(R.id.iv_signal);
            btnConnect = itemView.findViewById(R.id.btn_connect);
        }
    }
}