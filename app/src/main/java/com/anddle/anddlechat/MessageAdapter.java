package com.anddle.anddlechat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * Created by Gym on 16/6/13.
 */
public class MessageAdapter extends ArrayAdapter<ChatMessage> {

    private final LayoutInflater mInflater;
    private int mResourceMe;
    private int mResourceOthers;

    public MessageAdapter(Context context, int resourceMe, int resourceOthers) {
        super(context, 0);
        mInflater = LayoutInflater.from(context);
        mResourceMe = resourceMe;
        mResourceOthers = resourceOthers;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ChatMessage message = getItem(position);
        convertView = mInflater.inflate(message.messageSender == ChatMessage.MSG_SENDER_ME ? mResourceMe:mResourceOthers, parent, false);

        TextView name = (TextView) convertView.findViewById(R.id.message_content);
        name.setText(message.messageContent);

        return convertView;
    }
}
