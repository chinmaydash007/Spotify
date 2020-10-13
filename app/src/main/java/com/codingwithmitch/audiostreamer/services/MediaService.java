package com.codingwithmitch.audiostreamer.services;

import android.app.Notification;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import androidx.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;


import com.codingwithmitch.audiostreamer.MyApplication;
import com.codingwithmitch.audiostreamer.R;
import com.codingwithmitch.audiostreamer.notifications.MediaNotificationManager;
import com.codingwithmitch.audiostreamer.players.MediaPlayerAdapter;
import com.codingwithmitch.audiostreamer.players.PlaybackInfoListener;
import com.codingwithmitch.audiostreamer.util.MyPreferenceManager;

import java.util.ArrayList;
import java.util.List;

import static com.codingwithmitch.audiostreamer.util.Constants.MEDIA_QUEUE_POSITION;
import static com.codingwithmitch.audiostreamer.util.Constants.QUEUE_NEW_PLAYLIST;
import static com.codingwithmitch.audiostreamer.util.Constants.SEEK_BAR_MAX;
import static com.codingwithmitch.audiostreamer.util.Constants.SEEK_BAR_PROGRESS;

public class MediaService extends MediaBrowserServiceCompat {

    private static final String TAG = "MediaService";

    private MediaSessionCompat mSession;
    private MediaPlayerAdapter mPlayback;
    private MyApplication mMyApplication;
    private MyPreferenceManager mMyPrefManager;
    private MediaNotificationManager mMediaNotificationManager;
    private boolean mIsServiceStarted;


    @Override
    public void onCreate() {
        super.onCreate();
        mMyApplication = MyApplication.getInstance();
        mMyPrefManager = new MyPreferenceManager(this);

        //Build the MediaSession
        mSession = new MediaSessionCompat(this, TAG);

        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                // https://developer.android.com/guide/topics/media-apps/mediabuttons#mediabuttons-and-active-mediasessions
                // Media buttons on the device
                // (handles the PendingIntents for MediaButtonReceiver.buildMediaButtonPendingIntent)

                MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS); // Control the items in the queue (aka playlist)
        // See https://developer.android.com/guide/topics/media-apps/mediabuttons for more info on flags

        mSession.setCallback(new MediaSessionCallback());

        // A token that can be used to create a MediaController for this session
        setSessionToken(mSession.getSessionToken());


        mPlayback = new MediaPlayerAdapter(this, new MediaPlayerListener());
        mMediaNotificationManager = new MediaNotificationManager(this);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved: stopped");
        super.onTaskRemoved(rootIntent);
        mPlayback.stop();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        mSession.release();
        Log.d(TAG, "onDestroy: MediaPlayerAdapter stopped, and MediaSession released");
    }


    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String s, int i, @Nullable Bundle bundle) {

        Log.d(TAG, "onGetRoot: called. ");
        if(s.equals(getApplicationContext().getPackageName())){

            // Allowed to browse media
            return new BrowserRoot("some_real_playlist", null); // return no media
        }
        return new BrowserRoot("empty_media", null); // return no media
    }

    @Override
    public void onLoadChildren(@NonNull String s, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        Log.d(TAG, "onLoadChildren: called: " + s + ", " + result);

        //  Browsing not allowed
        if (TextUtils.equals("empty_media", s)) {
            result.sendResult(null);
            return;
        }
        result.sendResult(mMyApplication.getMediaItems()); // return all available media
    }


    public class MediaSessionCallback extends MediaSessionCompat.Callback {

        private final List<MediaSessionCompat.QueueItem> mPlaylist = new ArrayList<>();
        private int mQueueIndex = -1;
        private MediaMetadataCompat mPreparedMedia;

        private void resetPlaylist(){
            mPlaylist.clear();
            mQueueIndex = -1;
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d(TAG, "onPlayFromMediaId: CALLED.");
            if(extras.getBoolean(QUEUE_NEW_PLAYLIST, false)){
                resetPlaylist();
            }

            mPreparedMedia = mMyApplication.getTreeMap().get(mediaId);
            mSession.setMetadata(mPreparedMedia);
            if (!mSession.isActive()) {
                mSession.setActive(true);
            }
            mPlayback.playFromMedia(mPreparedMedia);

            int newQueuePosition = extras.getInt(MEDIA_QUEUE_POSITION, -1);
            if(newQueuePosition == -1){
                mQueueIndex++;
            }
            else{
                mQueueIndex = extras.getInt(MEDIA_QUEUE_POSITION);
            }
            mMyPrefManager.saveQueuePosition(mQueueIndex);
            mMyPrefManager.saveLastPlayedMedia(mPreparedMedia.getDescription().getMediaId());
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            Log.d(TAG, "onAddQueueItem: CALLED: position in list: " + mPlaylist.size());
            mPlaylist.add(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mQueueIndex == -1) ? 0 : mQueueIndex;
            mSession.setQueue(mPlaylist);
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            mPlaylist.remove(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mPlaylist.isEmpty()) ? -1 : mQueueIndex;
            mSession.setQueue(mPlaylist);
        }

        @Override
        public void onPrepare() {
            if (mQueueIndex < 0 && mPlaylist.isEmpty()) {
                // Nothing to play.
                return;
            }

            String mediaId = mPlaylist.get(mQueueIndex).getDescription().getMediaId();
            mPreparedMedia = mMyApplication.getTreeMap().get(mediaId);
            mSession.setMetadata(mPreparedMedia);

            if (!mSession.isActive()) {
                mSession.setActive(true);
            }
        }

        @Override
        public void onPlay() {

            if (!isReadyToPlay()) {
                // Nothing to play.
                return;
            }

            if (mPreparedMedia == null) {
                onPrepare();
            }

            mPlayback.playFromMedia(mPreparedMedia);
            mMyPrefManager.saveQueuePosition(mQueueIndex);
            mMyPrefManager.saveLastPlayedMedia(mPreparedMedia.getDescription().getMediaId());
        }

        @Override
        public void onPause() {
            mPlayback.pause();
        }

        @Override
        public void onStop() {
            mPlayback.stop();
            mSession.setActive(false);
        }

        @Override
        public void onSkipToNext() {
            Log.d(TAG, "onSkipToNext: SKIP TO NEXT");
            // increment and then check using modulus
            mQueueIndex = (++mQueueIndex % mPlaylist.size());
            mPreparedMedia = null;
            onPlay();
        }

        @Override
        public void onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious: SKIP TO PREVIOUS");
            mQueueIndex = mQueueIndex > 0 ? mQueueIndex - 1 : mPlaylist.size() - 1;
            mPreparedMedia = null;
            onPlay();
        }

        @Override
        public void onSeekTo(long pos) {
            mPlayback.seekTo(pos);
        }

        private boolean isReadyToPlay() {
            return (!mPlaylist.isEmpty());
        }
    }


    public class MediaPlayerListener implements PlaybackInfoListener {

        private final ServiceManager mServiceManager;

        MediaPlayerListener() {
            mServiceManager = new ServiceManager();
        }

        @Override
        public void updateUI(String newMediaId) {
            Log.d(TAG, "updateUI: CALLED: " + newMediaId);
            Intent intent = new Intent();
            intent.setAction(getString(R.string.broadcast_update_ui));
            intent.putExtra(getString(R.string.broadcast_new_media_id), newMediaId);
            sendBroadcast(intent);
        }

        @Override
        public void onPlaybackStateChange(PlaybackStateCompat state) {
            // Report the state to the MediaSession.
            mSession.setPlaybackState(state);


            // Manage the started state of this service.
            switch (state.getState()) {
                case PlaybackStateCompat.STATE_PLAYING:
                    mServiceManager.displayNotification(state);
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    mServiceManager.displayNotification(state);
                    break;
                case PlaybackStateCompat.STATE_STOPPED:
                    Log.d(TAG, "onPlaybackStateChange: STOPPED.");
                    mServiceManager.moveServiceOutOfStartedState();
                    break;
            }
        }

        @Override
        public void onSeekTo(long progress, long max) {
            Intent intent = new Intent();
            intent.setAction(getString(R.string.broadcast_seekbar_update));
            intent.putExtra(SEEK_BAR_PROGRESS, progress);
            intent.putExtra(SEEK_BAR_MAX, max);
            sendBroadcast(intent);
        }

        @Override
        public void onPlaybackComplete() {
            Log.d(TAG, "onPlaybackComplete: SKIPPING TO NEXT.");
            mSession.getController().getTransportControls().skipToNext();
        }


        class ServiceManager {


            public ServiceManager() {
            }


            public void displayNotification(PlaybackStateCompat state){

                // Manage the started state of this service.
                Notification notification = null;
                switch (state.getState()) {

                    case PlaybackStateCompat.STATE_PLAYING:
                        notification =
                                mMediaNotificationManager.buildNotification(
                                        state, getSessionToken(), mPlayback.getCurrentMedia().getDescription(), BitmapFactory.decodeResource(getBaseContext().getResources(),R.drawable.hello));

                        if (!mIsServiceStarted) {
                            ContextCompat.startForegroundService(
                                    MediaService.this,
                                    new Intent(MediaService.this, MediaService.class));
                            mIsServiceStarted = true;
                        }

                        startForeground(MediaNotificationManager.NOTIFICATION_ID, notification);
                        break;

                    case PlaybackStateCompat.STATE_PAUSED:
                        stopForeground(false);
                        notification =
                                mMediaNotificationManager.buildNotification(
                                        state, getSessionToken(), mPlayback.getCurrentMedia().getDescription(), null);
                        mMediaNotificationManager.getNotificationManager()
                                .notify(MediaNotificationManager.NOTIFICATION_ID, notification);
                        break;
                }
            }

            private void moveServiceOutOfStartedState() {
                stopForeground(true);
                stopSelf();
                mIsServiceStarted = false;
            }
        }
    }
}































