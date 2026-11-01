package com.areonedev.autotrack.business;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Notification;

import java.util.Date;

public class LeadInteractionReceiver extends BroadcastReceiver {
    private static final String TAG = "LeadReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!com.areonedev.autotrack.auth.AuthRuntime.hasAccount()) return;
        String contactInfo = null;
        String interactionType = "";

        // 1. Handle Incoming Call
        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                contactInfo = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                interactionType = "Call";
            }
        }

        // 2. Handle Incoming SMS
        else if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Object[] pdus = (Object[]) intent.getExtras().get("pdus");
            if (pdus != null) {
                for (Object pdu : pdus) {
                    SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                    contactInfo = sms.getOriginatingAddress();
                    interactionType = "SMS";
                }
            }
        }

        // 3. Handle Email Interaction (Custom Action)
        // Since Android doesn't broadcast "EMAIL_RECEIVED", we trigger this
        // when our app detects an email sent/received via a sync service or button
        else if ("com.areonedev.autotrack.EMAIL_INTERACTION".equals(intent.getAction())) {
            contactInfo = intent.getStringExtra("EXTRA_EMAIL_ADDRESS");
            interactionType = "Email";
        }

        // 4. Process the interaction if contact info was captured
        if (contactInfo != null) {
            processInteraction(contactInfo, interactionType);
        }
    }

    private void processInteraction(String contactInfo, String type) {
        AccessLeads accessLeads = new AccessLeads();
        // Use your existing method to find the lead
        Lead lead = accessLeads.getLeadByContactInfo(contactInfo);

        if (lead != null) {
            // Create a descriptive title: "Call from John Doe"
            String title = type + " from " + lead.getLeadFirstName() + " " + lead.getLeadLastName();
            Notification notification = new Notification(lead,title, new Date());

            // Save to database via the new AccessNotifications class
            AccessNotifications accessNotifications = new AccessNotifications();
            String result = accessNotifications.insertNotification(notification);

            if (result == null) {
                Log.d(TAG, "Successfully logged " + type + " for lead: " + lead.getLeadFirstName());
            } else {
                Log.e(TAG, "Failed to save notification: " + result);
            }
        } else {
            Log.d(TAG, "Interaction from unknown contact: " + contactInfo);
        }
    }
}
