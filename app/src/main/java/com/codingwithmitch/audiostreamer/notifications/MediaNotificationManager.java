package com.codingwithmitch.audiostreamer.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.codingwithmitch.audiostreamer.R;
import com.codingwithmitch.audiostreamer.services.MediaService;
import com.codingwithmitch.audiostreamer.ui.MainActivity;

public class MediaNotificationManager {

    private static final String TAG = "MediaNotificationManage";

    private final MediaService mMediaService;
    private final NotificationManager mNotificationManager;
    private static final String CHANNEL_ID = "com.codingwithmitch.spotifyclone.musicplayer.channel";
    private static final int REQUEST_CODE = 101;
    public static final int NOTIFICATION_ID = 201;

    public MediaNotificationManager(MediaService mediaService) {
        mMediaService = mediaService;


        mNotificationManager = (NotificationManager) mMediaService.getSystemService(Context.NOTIFICATION_SERVICE);


        mNotificationManager.cancelAll();
    }

    public NotificationManager getNotificationManager() {
        return mNotificationManager;
    }

    // Does nothing on versions of Android earlier than O.
    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            CharSequence name = "MediaSession";
            String description = "MediaSession and MediaPlayer";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            mChannel.setLightColor(Color.RED);
            mChannel.enableVibration(true);
            mNotificationManager.createNotificationChannel(mChannel);
            Log.d(TAG, "createChannel: New channel created");
        } else {
            Log.d(TAG, "createChannel: Existing channel reused");
        }
    }

    private boolean isAndroidOOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }


    public Notification buildNotification(@NonNull PlaybackStateCompat state,
                                          MediaSessionCompat.Token token,
                                          final MediaDescriptionCompat description,
                                          Bitmap bitmap) {


        // Create the (mandatory) notification channel when running on Android Oreo.
        if (isAndroidOOrHigher()) {
            createChannel();
        }
//        RemoteViews remoteViews = new RemoteViews("com.codingwithmitch.audiostreamer", R.layout.notification_layout);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mMediaService, CHANNEL_ID);
        builder.setStyle(
                new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(token)
        )
                .setColor(ContextCompat.getColor(mMediaService, R.color.notification_bg))
                .setSmallIcon(R.drawable.ic_audiotrack_grey_24dp)
                .setContentIntent(createContentIntent())
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setLargeIcon(bitmap)
//                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
//                .setCustomContentView(remoteViews)
//                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
//                        mMediaService, PlaybackStateCompat.ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        return builder.build();
    }

    private PendingIntent createContentIntent() {
        Intent openUI = new Intent(mMediaService, MainActivity.class);
        openUI.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                mMediaService, REQUEST_CODE, openUI, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}





















