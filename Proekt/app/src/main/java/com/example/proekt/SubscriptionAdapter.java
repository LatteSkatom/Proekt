package com.example.proekt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proekt.network.Subscription;
import java.util.List;

public class SubscriptionAdapter extends RecyclerView.Adapter<SubscriptionAdapter.ViewHolder> {

    private List<Subscription> subscriptionList;
    private OnSubscriptionLongClickListener longClickListener;

    // Конструктор с обработчиком долгого нажатия
    public SubscriptionAdapter(List<Subscription> subscriptionList, OnSubscriptionLongClickListener longClickListener) {
        this.subscriptionList = subscriptionList;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subscription, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Subscription subscription = subscriptionList.get(position);

        holder.serviceName.setText(subscription.getServis());
        holder.cost.setText(subscription.getCost() + " ₽");
        holder.nextPayment.setText("След. платёж: " + subscription.getNextPaymentDate());

        // 🔹 Обработка долгого нажатия
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onSubscriptionLongClick(subscription, position);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return subscriptionList.size();
    }

    // 🔹 Метод для удаления элемента
    public void removeAt(int position) {
        subscriptionList.remove(position);
        notifyItemRemoved(position);
    }

    // 🔹 Метод для восстановления элемента
    public void restoreAt(Subscription subscription, int position) {
        subscriptionList.add(position, subscription);
        notifyItemInserted(position);
    }

    // 🔹 Метод для получения списка (например, чтобы сохранить в память)
    public List<Subscription> getSubscriptions() {
        return subscriptionList;
    }

    // 🔹 Интерфейс для долгого нажатия
    public interface OnSubscriptionLongClickListener {
        void onSubscriptionLongClick(Subscription subscription, int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView serviceName, cost, nextPayment;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            serviceName = itemView.findViewById(R.id.serviceName);
            cost = itemView.findViewById(R.id.cost);
            nextPayment = itemView.findViewById(R.id.nextPayment);
        }
    }
}
