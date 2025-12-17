package com.example.proekt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SubscriptionAdapter extends RecyclerView.Adapter<SubscriptionAdapter.ViewHolder> {

    private List<FirebaseSubscription> subscriptionList;
    private OnSubscriptionLongClickListener longClickListener;

    public SubscriptionAdapter(List<FirebaseSubscription> subscriptionList, OnSubscriptionLongClickListener longClickListener) {
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
        FirebaseSubscription subscription = subscriptionList.get(position);

        holder.serviceName.setText(subscription.serviceName);
        holder.cost.setText(subscription.cost + " ₽");
        holder.nextPayment.setText("След. платёж: " + subscription.nextPaymentDate);

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(subscription, position);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return subscriptionList.size();
    }

    public void removeAt(int position) {
        subscriptionList.remove(position);
        notifyItemRemoved(position);
    }

    public void restoreAt(FirebaseSubscription subscription, int position) {
        subscriptionList.add(position, subscription);
        notifyItemInserted(position);
    }

    public List<FirebaseSubscription> getSubscriptions() {
        return subscriptionList;
    }

    public interface OnSubscriptionLongClickListener {
        void onLongClick(FirebaseSubscription subscription, int position);
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
