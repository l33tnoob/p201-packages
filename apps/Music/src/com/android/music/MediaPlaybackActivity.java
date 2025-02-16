/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.music;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.net.rtp.AudioStream;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.android.music.MusicUtils.ServiceToken;
import com.mediatek.common.voicecommand.IVoiceCommandListener;
import com.mediatek.common.voicecommand.IVoiceCommandManagerService;
import com.mediatek.common.voicecommand.VoiceCommandListener;
import com.mediatek.drm.OmaDrmStore;


import android.util.DisplayMetrics;//add by mokj
import android.view.WindowManager;

public class MediaPlaybackActivity extends Activity implements MusicUtils.Defs,
    View.OnTouchListener, View.OnLongClickListener, CreateBeamUrisCallback
{
    private static final String TAG = "MediaPlayback";

    private static final int USE_AS_RINGTONE = CHILD_MENU_BASE;

    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private IMediaPlaybackService mService = null;
    private RepeatingImageButton mPrevButton;
    private ImageButton mPauseButton;
    private RepeatingImageButton mNextButton;
    private ImageButton mRepeatButton;
    private ImageButton mShuffleButton;
    private ImageButton mQueueButton;
    private Worker mAlbumArtWorker;
    private AlbumArtHandler mAlbumArtHandler;
    private Toast mToast;
    private int mTouchSlop;
    private ServiceToken mToken;

    /// M: specific performace test case.
    private static final String PLAY_TEST = "play song";
    private static final String NEXT_TEST = "next song";
    private static final String PREV_TEST = "prev song";

    /// M: FM Tx package and activity information.
    private static final String FM_TX_PACKAGE = "com.mediatek.FMTransmitter";
    private static final String FM_TX_ACTIVITY = FM_TX_PACKAGE + ".FMTransmitterActivity";

    /// M: The command id of voice command ui.
    private static final int VOICE_COMMAND_PLAY = 13;
    private static final int VOICE_COMMAND_PAUSE = 12;
    private static final int VOICE_COMMAND_NEXT = 7;
    private static final int VOICE_COMMAND_PREV = 8;
    private static final int VOICE_COMMAND_SHFFLE = 9;
    private static final int VOICE_COMMAND_HIGHER = 11;
    private static final int VOICE_COMMAND_LOWER = 10;
    
    private static final int VOICE_COMMAND_INDICATOR = 110;
    /// M: show album art again when configuration change
    private boolean mIsShowAlbumArt = false;
    private Bitmap mArtBitmap = null;
    private long mArtSongId = -1;

    /// M: Add queue, repeat and shuffle to action bar when in landscape
    private boolean mIsLandscape;
    private MenuItem mQueueMenuItem;
    private MenuItem mRepeatMenuItem;
    private MenuItem mShuffleMenuItem;
    /// M: Add search button in actionbar when nowplaying not exist
    MenuItem mSearchItem;

    /// M: Add playlist sub menu to music
    private SubMenu mAddToPlaylistSubmenu;

    /// M: Music performance test string which is current runing
    private String mPerformanceTestString = null;

    /// M: use to make current playing time aways showing when seeking
    private int mRepeatCount = -1;

    /// M: Some music's durations can only be obtained when playing the media.
    // As a result we must know whether to update the durations.
    private boolean mNeedUpdateDuration = true;

    /// M: aviod Navigation button respond JE if Activity is background
    private boolean mIsInBackgroud = false;

    /// M: marked in onStop(), when get  phone call  from this activity,
    // if screen off to on, this activity will call onStart() to bind service,
    // the pause button may update in onResume() and onServiceConnected(), but
    // the service is not ready in onResume(), so need to discard the update.
    private boolean mIsCallOnStop = false;
    /// M: save the input of SearchView
    private CharSequence mQueryText;

    /// M:Voice Command UI
    private IVoiceCommandManagerService mVCmdMgrService;
    private NotificationManager mNotificationManager;
    private AudioManager mAudioManager;
    private boolean isRegistered = false;
    /// M: NFC feature
    NfcAdapter mNfcAdapter;
    /// M:identify whether the OptionMenu is opened
    private boolean mIsOptionMenuOpen = false;


    private boolean isSmallLCM = false;
	
    public MediaPlaybackActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

	 //add by mokj for fullscreen ,not statusbar
	 DisplayMetrics mDisplayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
         if((mDisplayMetrics.widthPixels == 320) && (mDisplayMetrics.heightPixels == 320))
              {  //getWindow().getDecorView().setSystemUiVisibility(View.STATUS_BAR_HIDDEN); 
                this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                		                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
				isSmallLCM = true;
         	}
		
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mAlbumArtWorker = new Worker("album art worker");
        mAlbumArtHandler = new AlbumArtHandler(mAlbumArtWorker.getLooper());

        /// M: Get the current orientation and enable action bar to add more function to it.
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        mIsLandscape = (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE);
        /// M: move UI component init and update to updateUI().
        updateUI();
        /// M: Register voice ui listener
        registerVoiceUiListener();
        /// M: Set the action bar on the right to be up navigation
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        /// M: Get Nfc adapter and set callback available. @{
        mNfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
        if (mNfcAdapter == null) {
            MusicLogUtils.e(TAG, "NFC not available!");
            return;
        }
        mNfcAdapter.setMtkBeamPushUrisCallback(this, this);
        /// @}
    }
    /**
     * M:execute the task which the voice command.
     * @param commandId
     */
    public void voiceUiCommand(int commandId) {
        try {
            switch (commandId) {
                case VOICE_COMMAND_PLAY :
                    if (!mService.isPlaying()) {
                        doPauseResume();
                    }
                    break;
                case VOICE_COMMAND_PAUSE :
                    if (mService.isPlaying()) {
                        doPauseResume();
                    }
                    break;
                case VOICE_COMMAND_NEXT :
                    Message msgnext = mHandler.obtainMessage(NEXT_BUTTON, null);
                    mHandler.removeMessages(NEXT_BUTTON);
                    mHandler.sendMessage(msgnext);
                    break;
                case VOICE_COMMAND_PREV :
                    Message msgprev = mHandler.obtainMessage(PREV_BUTTON, null);
                    mHandler.removeMessages(PREV_BUTTON);
                    mHandler.sendMessage(msgprev);
                    break;
                case VOICE_COMMAND_SHFFLE :
                    int shuffle = mService.getShuffleMode();
                    if (shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                        mService.next();
                    } else {
                        MusicUtils.togglePartyShuffle();
                        setShuffleButtonImage();
                    }
                    break;
                case VOICE_COMMAND_LOWER :
                    int lOldVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    lOldVolume = lOldVolume > 0 ? (lOldVolume - 1) : 0;
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, lOldVolume, AudioManager.FLAG_SHOW_UI);
                    break;
                case VOICE_COMMAND_HIGHER :
                    int hOldVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int maxMusicVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    hOldVolume = (hOldVolume < maxMusicVolume) ? (hOldVolume + 1) : maxMusicVolume;
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, hOldVolume, AudioManager.FLAG_SHOW_UI);
                default :
                    break;
            }
        } catch (RemoteException e) {
            MusicLogUtils.e(TAG, "RemoteException:" + e);
        }
    }
    
    int mInitialX = -1;
    int mLastX = -1;
    int mTextWidth = 0;
    int mViewWidth = 0;
    boolean mDraggingLabel = false;
    
    TextView textViewForContainer(View v) {
        View vv = v.findViewById(R.id.artistname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.albumname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.trackname);
        if (vv != null) return (TextView) vv;
        return null;
    }

    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        TextView tv = textViewForContainer(v);
        if (tv == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            /// M: For ICS style and support for theme manager
            v.setBackgroundColor(getBackgroundColor());
            mInitialX = mLastX = (int) event.getX();
            mDraggingLabel = false;
            /// M: Because only when the text has ellipzised we need scroll the text view to show ellipsis
            /// text to user, We should get the non-ellipsized text width to determine whether need to scroll
            /// the text view. {@
            mTextWidth = (int)tv.getPaint().measureText(tv.getText().toString());
            mViewWidth = tv.getWidth();
            /// @}

            /// M: when text width large than view width, we need turn off ellipsize to show total text.
            if (mTextWidth > mViewWidth) {
                tv.setEllipsize(null);
            }
        } else if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL) {
            v.setBackgroundColor(0);
            if (mDraggingLabel) {
                Message msg = mLabelScroller.obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(msg, 1000);
            }
            /// M: When touch finished, turn on ellipsize.
            tv.setEllipsize(TruncateAt.END);
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mDraggingLabel) {
                int scrollx = tv.getScrollX();
                int x = (int) event.getX();
                int delta = mLastX - x;
                if (delta != 0) {
                    mLastX = x;
                    scrollx += delta;
                    if (scrollx > mTextWidth) {
                        // scrolled the text completely off the view to the left
                        scrollx -= mTextWidth;
                        scrollx -= mViewWidth;
                    }
                    if (scrollx < -mViewWidth) {
                        // scrolled the text completely off the view to the right
                        scrollx += mViewWidth;
                        scrollx += mTextWidth;
                    }
                    tv.scrollTo(scrollx, 0);
                }
                return true;
            }
            int delta = mInitialX - (int) event.getX();
            if (Math.abs(delta) > mTouchSlop) {
                // start moving
                mLabelScroller.removeMessages(0, tv);
                /// M: Get the non-ellipsized text view width in event ACTION_DOWN to avoid persistently turn on/off
                /// ellipsize which will cause textview shake. @{
                /*
                // Only turn ellipsizing off when it's not already off, because it
                // causes the scroll position to be reset to 0.
                if (tv.getEllipsize() != null) {
                    tv.setEllipsize(null);
                }
                Layout ll = tv.getLayout();
                // layout might be null if the text just changed, or ellipsizing
                // was just turned off
                if (ll == null) {
                    return false;
                }
                // get the non-ellipsized line width, to determine whether scrolling
                // should even be allowed
                mTextWidth = (int) tv.getLayout().getLineWidth(0);*/
                /// @}
                
                if (mViewWidth > mTextWidth) {
                    // tv.setEllipsize(TruncateAt.END);
                    v.cancelLongPress();
                    return false;
                }
                
                mDraggingLabel = true;
                v.cancelLongPress();
                return true;
            }
        }
        return false; 
    }

    Handler mLabelScroller = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            TextView tv = (TextView) msg.obj;
            int x = tv.getScrollX();
            x = x * 3 / 4;
            tv.scrollTo(x, 0);
            if (x == 0) {
                tv.setEllipsize(TruncateAt.END);
            } else {
                Message newmsg = obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(newmsg, 15);
            }
        }
    };
    
    public boolean onLongClick(View view) {

        CharSequence title = null;
        String mime = null;
        String query = null;
        String artist;
        String album;
        String song;
        long audioid;
        
        try {
            artist = mService.getArtistName();
            album = mService.getAlbumName();
            song = mService.getTrackName();
            audioid = mService.getAudioId();
        } catch (RemoteException ex) {
            return true;
        } catch (NullPointerException ex) {
            // we might not actually have the service yet
            return true;
        }

        if (MediaStore.UNKNOWN_STRING.equals(album) &&
                MediaStore.UNKNOWN_STRING.equals(artist) &&
                song != null &&
                song.startsWith("recording")) {
            // not music
            return false;
        }

        if (audioid < 0) {
            return false;
        }

        Cursor c = MusicUtils.query(this,
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioid),
                new String[] {MediaStore.Audio.Media.IS_MUSIC}, null, null, null);
        boolean ismusic = true;
        if (c != null) {
            if (c.moveToFirst()) {
                ismusic = c.getInt(0) != 0;
            }
            c.close();
        }
        if (!ismusic) {
            return false;
        }

        boolean knownartist =
            (artist != null) && !MediaStore.UNKNOWN_STRING.equals(artist);

        boolean knownalbum =
            (album != null) && !MediaStore.UNKNOWN_STRING.equals(album);
        
        if (knownartist && view.equals(mArtistName.getParent())) {
            title = artist;
            query = artist;
            mime = MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE;
        } else if (knownalbum && view.equals(mAlbumName.getParent())) {
            title = album;
            if (knownartist) {
                query = artist + " " + album;
            } else {
                query = album;
            }
            mime = MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE;
        } else if (view.equals(mTrackName.getParent()) || !knownartist || !knownalbum) {
            if ((song == null) || MediaStore.UNKNOWN_STRING.equals(song)) {
                // A popup of the form "Search for null/'' using ..." is pretty
                // unhelpful, plus, we won't find any way to buy it anyway.
                return true;
            }

            title = song;
            if (knownartist) {
                query = artist + " " + song;
            } else {
                query = song;
            }
            mime = "audio/*"; // the specific type doesn't matter, so don't bother retrieving it
        } else {
            throw new RuntimeException("shouldn't be here");
        }
        title = getString(R.string.mediasearch, title);

        Intent i = new Intent();
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.putExtra(SearchManager.QUERY, query);
        if(knownartist) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
        }
        if(knownalbum) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
        }
        i.putExtra(MediaStore.EXTRA_MEDIA_TITLE, song);
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, mime);

        startActivity(Intent.createChooser(i, title));
        return true;
    }

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            /// M: only respond when progress bar don't change from touch
            //mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (mService == null)) return;
            /// M: only respond when progress bar don't change from touch  {@
            //long now = SystemClock.elapsedRealtime();
            //if ((now - mLastSeekEventTime) > 250) {
            //    mLastSeekEventTime = now;
            //    mPosOverride = mDuration * progress / 1000;
            //
            //    try {
            //        mService.seek(mPosOverride);
            //    } catch (RemoteException ex) {
            //    }
            //}

            // trackball event, allow progress updates
            if (!mFromTouch) {
                mPosOverride = mDuration * progress / 1000;
                try {
                    mService.seek(mPosOverride);
                } catch (RemoteException ex) {
                    MusicLogUtils.e(TAG, "Error:" + ex);
                }
            /// @}
                
                refreshNow();
                mPosOverride = -1;
            }
        }
        public void onStopTrackingTouch(SeekBar bar) {
           /// M: Save the seek position, seek and update UI. @{
           if (mService != null) {
                try {
                    mPosOverride = bar.getProgress() * mDuration / 1000;
                    mService.seek(mPosOverride);
                    refreshNow();
                } catch (RemoteException ex) {
                    MusicLogUtils.e(TAG, "Error:" + ex);
                }
           }
           /// @}
            mPosOverride = -1;
            mFromTouch = false;
        }
    };
    
    private View.OnClickListener mQueueListener = new View.OnClickListener() {
        public void onClick(View v) {
            startActivity(
                    new Intent(Intent.ACTION_EDIT)
                    .setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/track")
                    .putExtra("playlist", "nowplaying")
            );
        }
    };
    
    private View.OnClickListener mShuffleListener = new View.OnClickListener() {
        public void onClick(View v) {
            toggleShuffle();
        }
    };

    private View.OnClickListener mRepeatListener = new View.OnClickListener() {
        public void onClick(View v) {
            cycleRepeat();
        }
    };

    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
        }
    };

    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) {
            /// M: performance test, response time for Prev button
            MusicLogUtils.i("MusicPerformanceTest", "[Performance test][Music] prev song start ["
                                + System.currentTimeMillis() + "]");
            mPerformanceTestString = PREV_TEST;

            /// M: Handle click event in handler to avoid ANR for continuous
            // press @{
            MusicLogUtils.d(TAG, "Prev Button onClick,Send Msg");
            Message msg = mHandler.obtainMessage(PREV_BUTTON, null);
            mHandler.removeMessages(PREV_BUTTON);
            mHandler.sendMessage(msg);
            /// @}
        }
    };

    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            /// M: performance test, response time for Next button
            MusicLogUtils.i("MusicPerformanceTest", "[Performance test][Music] next song start ["
                                + System.currentTimeMillis() + "]");
            mPerformanceTestString = NEXT_TEST;

            /// M: Handle click event in handler to avoid ANR for continuous
            // press @{
            MusicLogUtils.d(TAG, "Next Button onClick,Send Msg");
            Message msg = mHandler.obtainMessage(NEXT_BUTTON, null);
            mHandler.removeMessages(NEXT_BUTTON);
            mHandler.sendMessage(msg);
            /// @}
        }
    };

    private RepeatingImageButton.RepeatListener mRewListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            MusicLogUtils.d(TAG, "music backward");
            /// M: use to make current playing time aways showing when seeking
            mRepeatCount = repcnt;
            scanBackward(repcnt, howlong);
        }
    };
    
    private RepeatingImageButton.RepeatListener mFfwdListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            MusicLogUtils.d(TAG, "music forward");
            /// M: use to make current playing time aways showing when seeking
            mRepeatCount = repcnt;
            scanForward(repcnt, howlong);
        }
    };
   
    @Override
    public void onStop() {
        paused = true;
        MusicLogUtils.d(TAG, "onStop()");
        /// M: so mark mIsCallOnStop is true
        mIsCallOnStop = true;
        mHandler.removeMessages(REFRESH);
        unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        paused = false;

        mToken = MusicUtils.bindToService(this, osc);
        if (mToken == null) {
            // something went wrong
            mHandler.sendEmptyMessage(QUIT);
        }
        
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        /// M: listen more status to update UI @{
        f.addAction(MediaPlaybackService.QUIT_PLAYBACK);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        /// @}
        registerReceiver(mStatusListener, new IntentFilter(f));
        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        /// M: when it launch from status bar, collapse status ba first. @{
        Intent intent = getIntent();
        boolean collapseStatusBar = intent.getBooleanExtra("collapse_statusbar", false);
        MusicLogUtils.d(TAG, "onResume: collapseStatusBar=" + collapseStatusBar);
        if (collapseStatusBar) {
            StatusBarManager statusBar = (StatusBarManager)getSystemService(Context.STATUS_BAR_SERVICE);
            statusBar.collapsePanels();
        }
        ///@}
        /// M: register voice command listener and send command. @{
        if (MusicFeatureOption.IS_SUPPORT_VOICE_COMMAND_UI) {
            if (mVCmdMgrService == null) {
                bindVoiceService();
            } else {
                String pkgName = getPackageName();
                registerVoiceCommand(pkgName);
                sendVoiceCommand(pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                        VoiceCommandListener.ACTION_VOICE_UI_START, null);
            }
        }
        /// @}
        updateTrackInfo();
        /// M: if it doesn't come from onStop(),we should update pause button. @{
        if (!mIsCallOnStop) {
            setPauseButtonImage();
        }
        mIsCallOnStop = false;
        /// @}

        /// M: When back to this activity, ask service for right position
        mPosOverride = -1;
        invalidateOptionsMenu();
        
        /// M: performance default test, response time for Play button
        mPerformanceTestString = PLAY_TEST;
        /// M: aviod Navigation button respond JE if Activity is background
        mIsInBackgroud = false;
    }

   private void sendVoiceCommand(String pkgName, int mainAction, int subAction, Bundle extraData) {
        if (isRegistered) {
            try {
                MusicLogUtils.d(TAG,"Send Command " + "pkgName" + pkgName + "mainAction=" + mainAction
                        + " subAction=" + subAction + " extraData=" + extraData);
                int errorid = mVCmdMgrService
                        .sendCommand(pkgName, mainAction, subAction, extraData);
                if (errorid != VoiceCommandListener.VOICE_NO_ERROR) {
                    MusicLogUtils.d(TAG,"Send Command failure");
                } else {
                    MusicLogUtils.d(TAG,"Send Command success");
                }
            } catch (RemoteException e) {
                isRegistered = false;
                mVCmdMgrService = null;
                MusicLogUtils.d(TAG,"sendCommand RemoteException");
            }
        } else {
            MusicLogUtils.d(TAG,"App has not register listener can not send command");
        }
    }

    private void registerVoiceCommand(String pkgName) {
        if (!isRegistered) {
            try {
                int errorid = mVCmdMgrService.registerListener(pkgName, mCallback);
                MusicLogUtils.d(TAG,"Register voice Listener pkgName = " + pkgName + ",errorid = " + errorid);
                if (errorid == VoiceCommandListener.VOICE_NO_ERROR) {
                    isRegistered = true;
                } else {
                    MusicLogUtils.d(TAG,"Register voice Listener failure ");
                }
            } catch (RemoteException e) {
                isRegistered = false;
                mVCmdMgrService = null;
                MusicLogUtils.d(TAG,"Register voice Listener RemoteException = " + e.getMessage());
            }
        } else {
            MusicLogUtils.d(TAG,"App has register voice listener success");
        }
        MusicLogUtils.d(TAG,"Register voice listener end!");
    }

    private void unRegisterVoiceCommand(String pkgName) {
        try {
            int errorid = mVCmdMgrService.unregisterListener(pkgName, mCallback);
            MusicLogUtils.d(TAG,"Unregister voice listener, errorid = " + errorid);
            if (errorid == VoiceCommandListener.VOICE_NO_ERROR) {
                isRegistered = false;
            }
        } catch (RemoteException e) {
            MusicLogUtils.d(TAG,"Unregister error in handler RemoteException = " + e.getMessage());
            isRegistered = false;
            mVCmdMgrService = null;
        }
        MusicLogUtils.d(TAG,"UnRegister voice listener end!");
    }

    @Override
    public void onDestroy() {
        mAlbumArtWorker.quit();
        super.onDestroy();
        // System.out.println("***************** playback activity onDestroy\n");
        if (MusicFeatureOption.IS_SUPPORT_VOICE_COMMAND_UI) {
            if (mVCmdMgrService != null) {
                MusicLogUtils.d(TAG, "Music unbind voice Service onDestroy");
                unbindService(mVoiceSerConnection);
                isRegistered = false;
                mVCmdMgrService = null;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Don't show the menu items if we got launched by path/filedescriptor, or
        // if we're in one shot mode. In most cases, these menu items are not
        // useful in those modes, so for consistency we never show them in these
        // modes, instead of tailoring them to the specific file being played.
        long currentAudioId = MusicUtils.getCurrentAudioId();
        if (currentAudioId >= 0) {
            /// M: adjust menu sequence
            // menu.add(0, GOTO_START, 0, R.string.goto_start)
            //            .setIcon(R.drawable.ic_menu_music_library);
            menu.add(0, PARTY_SHUFFLE, 0, R.string.party_shuffle);
            /// M: get the object for method onPrepareOptionsMenu to keep playlist menu up-to-date
            mAddToPlaylistSubmenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                    R.string.add_to_playlist).setIcon(android.R.drawable.ic_menu_add);
            // these next two are in a separate group, so they can be shown/hidden as needed
            // based on the keyguard state
			if (MusicUtils.isVoiceCapable(this)){
				menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu_short)
						.setIcon(R.drawable.ic_menu_set_as_ringtone);
			}

            menu.add(0, DELETE_ITEM, 0, R.string.delete_item)
                    .setIcon(R.drawable.ic_menu_delete);
            /// M: move to prepare option menu to disable menu when MusicFX is disable
            menu.add(0, EFFECTS_PANEL, 0, R.string.effects_list_title)
                    .setIcon(R.drawable.ic_menu_eq);

            /// M: Add FMTransmitter option menu, and remove goto library. {@
            if (MusicFeatureOption.IS_SUPPORT_FM_TX) {
                menu.add(0, FM_TRANSMITTER, 0, R.string.music_fm_transmiter)
                    .setIcon(R.drawable.ic_menu_fmtransmitter);
            } else {
                menu.add(0, GOTO_START, 0, R.string.goto_start)
                    .setIcon(R.drawable.ic_menu_music_library);
            }
            /// @}

            // Add action bar for no physical key(different in landscape and portrait). {@
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.music_playback_action_bar, menu);
            mQueueMenuItem = menu.findItem(R.id.current_playlist_menu_item);
            mShuffleMenuItem = menu.findItem(R.id.shuffle_menu_item);
            mRepeatMenuItem = menu.findItem(R.id.repeat_menu_item);
            /// @}

            /// M: Add search view
            mSearchItem = MusicUtils.addSearchView(this, menu, mQueryTextListener);

            /// M: collapseActionView when search view dismiss
            /*final SearchManager searchManager = (SearchManager) this.getSystemService(Context.SEARCH_SERVICE);
            searchManager.setOnDismissListener(new SearchManager.OnDismissListener() {
                @Override
                public void onDismiss() {
                    mSearchItem.collapseActionView();
                }
            });*/
            /// @}
            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mService == null) return false;
        MenuItem item = menu.findItem(PARTY_SHUFFLE);
        if (item != null) {
            int shuffle = MusicUtils.getCurrentShuffleMode();
            if (shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                item.setIcon(R.drawable.ic_menu_party_shuffle);
                item.setTitle(R.string.party_shuffle_off);
            } else {
                item.setIcon(R.drawable.ic_menu_party_shuffle);
                item.setTitle(R.string.party_shuffle);
            }
        }

        /// M: DRM feature, when track is drm and not FL type, it can not set as ringtone. {@
        if (MusicFeatureOption.IS_SUPPORT_DRM && MusicUtils.isVoiceCapable(this)) {
            try {
                menu.findItem(USE_AS_RINGTONE).setVisible(mService.canUseAsRingtone());
            } catch (RemoteException e) {
                MusicLogUtils.e(TAG, "onPrepareOptionsMenu with RemoteException " + e);
            }
        }

        /// M: Set effect menu visible depend the effect class whether disable or enable. {@
        MusicUtils.setEffectPanelMenu(getApplicationContext(), menu);
        /// @}

        /// M: Set FMTransmitter menu visible depend the FMTransmitter class whether
        /// disable or enable. {@
        if (MusicFeatureOption.IS_SUPPORT_FM_TX) {
            Intent intentFmTx = new Intent(FM_TX_ACTIVITY);
            intentFmTx.setClassName(FM_TX_PACKAGE, FM_TX_ACTIVITY);
            menu.findItem(FM_TRANSMITTER).setVisible(getPackageManager().resolveActivity(intentFmTx, 0) != null);
        }
        /// @}

        /// M: Keep the playlist menu up-to-date.
        MusicUtils.makePlaylistMenu(this, mAddToPlaylistSubmenu);
        mAddToPlaylistSubmenu.removeItem(MusicUtils.Defs.QUEUE);
        KeyguardManager km = (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        menu.setGroupVisible(1, !km.inKeyguardRestrictedInputMode());

        /// M: Switch to show action bar in landscape or button in portrait. {@
        mQueueMenuItem.setVisible(mIsLandscape);
        mShuffleMenuItem.setVisible(mIsLandscape);
        mRepeatMenuItem.setVisible(mIsLandscape);
        setRepeatButtonImage();
        setShuffleButtonImage();
        /// @}
        /// M: resore the input of SearchView when config change @{
        if(mQueryText != null && !mQueryText.toString().equals("")){
            MusicLogUtils.e(TAG, "setQueryText:" + mQueryText);
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            searchView.setQuery(mQueryText, false);
            mQueryText = null;
        }
        /// @}
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        try {
            switch (item.getItemId()) {
                case GOTO_START:
                    intent = new Intent();
                    intent.setClass(this, MusicBrowserActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    break;
                case USE_AS_RINGTONE: {
                    // Set the system setting to make this the current ringtone
                    if (mService != null) {
                        MusicUtils.setRingtone(this, mService.getAudioId());
                    }
                    return true;
                }
                case PARTY_SHUFFLE:
                    MusicUtils.togglePartyShuffle();
                    setShuffleButtonImage();
                    /// M: Update repeat button because will set repeat current to repeat all when open party shuffle.
                    setRepeatButtonImage();
                    break;
                    
                case NEW_PLAYLIST: {
                    intent = new Intent();
                    intent.setClass(this, CreatePlaylist.class);
                    /// M: Add to indicate the save_as_playlist and new_playlist
                    intent.putExtra(MusicUtils.SAVE_PLAYLIST_FLAG, MusicUtils.NEW_PLAYLIST);
                    startActivityForResult(intent, NEW_PLAYLIST);
                    return true;
                }

                case PLAYLIST_SELECTED: {
                    long [] list = new long[1];
                    list[0] = MusicUtils.getCurrentAudioId();
                    long playlist = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(this, list, playlist);
                    return true;
                }
                
                case DELETE_ITEM: {
                    if (mService != null) {
                        long [] list = new long[1];
                        list[0] = MusicUtils.getCurrentAudioId();
                        Bundle b = new Bundle();
                        String f;
                        /// M: Get string in DeleteItems Activity to get current language string. @{
                        //if (android.os.Environment.isExternalStorageRemovable()) {
                        //f = getString(R.string.delete_song_desc, mService.getTrackName());
                        //} else {
                        //    f = getString(R.string.delete_song_desc_nosdcard, mService.getTrackName());
                        //}
                        //b.putString("description", f);
                        b.putInt(MusicUtils.DELETE_DESC_STRING_ID, R.string.delete_song_desc);
                        b.putString(MusicUtils.DELETE_DESC_TRACK_INFO, mService.getTrackName());
                        /// @}
                        b.putLongArray("items", list);
                        intent = new Intent();
                        intent.setClass(this, DeleteItems.class);
                        intent.putExtras(b);
                        startActivityForResult(intent, -1);
                    }
                    return true;
                }

                /// M: Show effect panel and call the same method as other activities.
                case EFFECTS_PANEL:
                    return MusicUtils.startEffectPanel(this);

                /// M: Open FMTransmitter and Search view. {@
                case FM_TRANSMITTER:
                    Intent intentFMTx = new Intent(FM_TX_ACTIVITY);
                    intentFMTx.setClassName(FM_TX_PACKAGE, FM_TX_ACTIVITY);

                    try {
                        startActivity(intentFMTx);
                    } catch (ActivityNotFoundException anfe) {
                        MusicLogUtils.e(TAG, "FMTx activity isn't found!!");
                    }
                
                    return true;

                case R.id.search:
                    onSearchRequested();
                    return true;
                /// @}

                /// M: handle action bar and navigation up button. {@
                case android.R.id.home:
                    /// M: Navigation button press back,
                    /// aviod Navigation button respond JE if Activity is background
                    if (!mIsInBackgroud) {
                        Intent parentIntent = new Intent(this, MusicBrowserActivity.class);
                        parentIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        finish();
                        startActivity(parentIntent);
                    }
                    return true;

                case R.id.current_playlist_menu_item:
                    /// M: Current playlist(queue) button
                    mQueueListener.onClick(null);
                    break;

                case R.id.shuffle_menu_item:
                    /// M: Shuffle button
                    toggleShuffle();
                    break;

                case R.id.repeat_menu_item:
                    /// M: Repeat button
                    cycleRepeat();
                    break;

                default:
                    return true;
                /// @}
            }
        } catch (RemoteException ex) {
            MusicLogUtils.e(TAG, "onOptionsItemSelected with RemoteException " + ex);
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onOptionsMenuClosed(Menu menu) {
        // TODO Auto-generated method stub
        MusicLogUtils.d(TAG, "onOptionsMenuClosed");
        mIsOptionMenuOpen = false;
        super.onOptionsMenuClosed(menu);
    }
    
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        // TODO Auto-generated method stub
        MusicLogUtils.d(TAG, "onMenuOpened");
        mIsOptionMenuOpen = true;
        invalidateOptionsMenu();
        return super.onMenuOpened(featureId, menu);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case NEW_PLAYLIST:
                Uri uri = intent.getData();
                if (uri != null) {
                    long [] list = new long[1];
                    list[0] = MusicUtils.getCurrentAudioId();
                    int playlist = Integer.parseInt(uri.getLastPathSegment());
                    MusicUtils.addToPlaylist(this, list, playlist);
                }
                break;
        }
    }
    private final int keyboard[][] = {
        {
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
        },
        {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_DEL,
        },
        {
            KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_ENTER
        }

    };

    private int lastX;
    private int lastY;

    private boolean seekMethod1(int keyCode)
    {
        if (mService == null) return false;
        for(int x=0;x<10;x++) {
            for(int y=0;y<3;y++) {
                if(keyboard[y][x] == keyCode) {
                    int dir = 0;
                    // top row
                    if(x == lastX && y == lastY) dir = 0;
                    else if (y == 0 && lastY == 0 && x > lastX) dir = 1;
                    else if (y == 0 && lastY == 0 && x < lastX) dir = -1;
                    // bottom row
                    else if (y == 2 && lastY == 2 && x > lastX) dir = -1;
                    else if (y == 2 && lastY == 2 && x < lastX) dir = 1;
                    // moving up
                    else if (y < lastY && x <= 4) dir = 1; 
                    else if (y < lastY && x >= 5) dir = -1; 
                    // moving down
                    else if (y > lastY && x <= 4) dir = -1; 
                    else if (y > lastY && x >= 5) dir = 1; 
                    lastX = x;
                    lastY = y;
                    try {
                        mService.seek(mService.position() + dir * 5);
                    } catch (RemoteException ex) {
                    }
                    refreshNow();
                    return true;
                }
            }
        }
        lastX = -1;
        lastY = -1;
        return false;
    }

    private boolean seekMethod2(int keyCode)
    {
        if (mService == null) return false;
        for(int i=0;i<10;i++) {
            if(keyboard[0][i] == keyCode) {
                int seekpercentage = 100*i/10;
                try {
                    mService.seek(mService.duration() * seekpercentage / 100);
                } catch (RemoteException ex) {
                }
                refreshNow();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        try {
            switch(keyCode)
            {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            mPauseButton.requestFocus();
                            if (mStartSeekPos < 1000) {
                                mService.prev();
                            } else {
                                mService.seek(0);
                            }
                        } else {
                            scanBackward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            mPauseButton.requestFocus();
                            mService.next();
                        } else {
                            scanForward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
                    
                /// M: handle key code center. {@
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    View curSel = getCurrentFocus();
                    if ((curSel != null && R.id.pause == curSel.getId()) || 
                            (curSel == null)) {
                        doPauseResume();
                    }
                    return true;
                /// @}
            }
        } catch (RemoteException ex) {
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean useDpadMusicControl() {
        if (mDeviceHasDpad && (mPrevButton.isFocused() ||
                mNextButton.isFocused() ||
                mPauseButton.isFocused())) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        int direction = -1;
        int repcnt = event.getRepeatCount();

        if((seekmethod==0)?seekMethod1(keyCode):seekMethod2(keyCode))
            return true;

        switch(keyCode)
        {
/*
            // image scale
            case KeyEvent.KEYCODE_Q: av.adjustParams(-0.05, 0.0, 0.0, 0.0, 0.0,-1.0); break;
            case KeyEvent.KEYCODE_E: av.adjustParams( 0.05, 0.0, 0.0, 0.0, 0.0, 1.0); break;
            // image translate
            case KeyEvent.KEYCODE_W: av.adjustParams(    0.0, 0.0,-1.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_X: av.adjustParams(    0.0, 0.0, 1.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_A: av.adjustParams(    0.0,-1.0, 0.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_D: av.adjustParams(    0.0, 1.0, 0.0, 0.0, 0.0, 0.0); break;
            // camera rotation
            case KeyEvent.KEYCODE_R: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 0.0,-1.0); break;
            case KeyEvent.KEYCODE_U: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 0.0, 1.0); break;
            // camera translate
            case KeyEvent.KEYCODE_Y: av.adjustParams(    0.0, 0.0, 0.0, 0.0,-1.0, 0.0); break;
            case KeyEvent.KEYCODE_N: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 1.0, 0.0); break;
            case KeyEvent.KEYCODE_G: av.adjustParams(    0.0, 0.0, 0.0,-1.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_J: av.adjustParams(    0.0, 0.0, 0.0, 1.0, 0.0, 0.0); break;

*/

            case KeyEvent.KEYCODE_SLASH:
                seekmethod = 1 - seekmethod;
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mPrevButton.hasFocus()) {
                    mPrevButton.requestFocus();
                }
                scanBackward(repcnt, event.getEventTime() - event.getDownTime());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mNextButton.hasFocus()) {
                    mNextButton.requestFocus();
                }
                scanForward(repcnt, event.getEventTime() - event.getDownTime());
                return true;

            case KeyEvent.KEYCODE_S:
                toggleShuffle();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                /// M: handle key code center.
                 return true;
                 
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
                doPauseResume();
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (mSearchItem != null) {
                    if (mSearchItem.isActionViewExpanded()) {
                        return true;
                    }
                }
                return false;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private void scanBackward(int repcnt, long delta) {
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos - delta;
                if (newpos < 0) {
                    // move to previous track
                    mService.prev();
                    long duration = mService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private void scanForward(int repcnt, long delta) {
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = mService.duration();
                if (newpos >= duration) {
                    // move to next track
                    mService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void doPauseResume() {
        try {
            if(mService != null) {
                Boolean isPlaying = mService.isPlaying();
                MusicLogUtils.d(TAG, "doPauseResume: isPlaying=" + isPlaying);
                /// M: AVRCP and Android Music AP supports the FF/REWIND
                //   aways get position from service if user press pause button
                mPosOverride = -1;
                if (isPlaying) {
                    mService.pause();
                } else {
                    mService.play();
                }
                refreshNow();
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void toggleShuffle() {
        if (mService == null) {
            return;
        }
        try {
            int shuffle = mService.getShuffleMode();
            if (shuffle == MediaPlaybackService.SHUFFLE_NONE) {
                mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
                if (mService.getRepeatMode() == MediaPlaybackService.REPEAT_CURRENT) {
                    mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                }
                /// M: need to refresh repeat button when we modify rpeate mode.
                setRepeatButtonImage();
                showToast(R.string.shuffle_on_notif);
            } else if (shuffle == MediaPlaybackService.SHUFFLE_NORMAL ||
                    shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                /// M: After turn off party shuffle, we should to refresh option menu to avoid user click fast to show
                /// party shuffle off when has turned off.
                //invalidateOptionsMenu();
                showToast(R.string.shuffle_off_notif);
            } else {
                MusicLogUtils.w(TAG, "Invalid shuffle mode: " + shuffle);
            }
            setShuffleButtonImage();
        } catch (RemoteException ex) {
        }
    }
    
    private void cycleRepeat() {
        if (mService == null) {
            return;
        }
        try {
            int mode = mService.getRepeatMode();
            if (mode == MediaPlaybackService.REPEAT_NONE) {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                showToast(R.string.repeat_all_notif);
            } else if (mode == MediaPlaybackService.REPEAT_ALL) {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_CURRENT);
                if (mService.getShuffleMode() != MediaPlaybackService.SHUFFLE_NONE) {
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                    /// M: After turn off party shuffle, we should to refresh option menu to avoid user click fast to show
                    /// party shuffle off when has turned off.
                    //invalidateOptionsMenu();
                    setShuffleButtonImage();
                }
                showToast(R.string.repeat_current_notif);
            } else {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
                showToast(R.string.repeat_off_notif);
            }
            setRepeatButtonImage();
        } catch (RemoteException ex) {
        }
        
    }
    
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        mToast.setText(resid);
        mToast.show();
    }

    private void startPlayback() {

        if(mService == null)
            return;
        Intent intent = getIntent();
        String filename = "";
        Uri uri = intent.getData();
        if (uri != null && uri.toString().length() > 0) {
            // If this is a file:// URI, just use the path directly instead
            // of going through the open-from-filedescriptor codepath.
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                filename = uri.getPath();
            } else {
                filename = uri.toString();
            }
            try {
                mService.stop();
                mService.openFile(filename);
                mService.play();
                setIntent(new Intent());
            } catch (Exception ex) {
                MusicLogUtils.d(TAG, "couldn't start playback: " + ex);
            }
        }

        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }

    private ServiceConnection osc = new ServiceConnection() {
            public void onServiceConnected(ComponentName classname, IBinder obj) {
                mService = IMediaPlaybackService.Stub.asInterface(obj);
                /// M: Call this to invalidate option menu to install action bar
                invalidateOptionsMenu();
                startPlayback();
                try {
                    // Assume something is playing when the service says it is,
                    // but also if the audio ID is valid but the service is paused.
                    if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                            mService.getPath() != null) {
                        // something is playing now, we're done
                        /// M: Only in portrait we need to set them to be 
                        // visible {@
                        if (!mIsLandscape) {
                            mRepeatButton.setVisibility(View.VISIBLE);
                            mShuffleButton.setVisibility(View.VISIBLE);
                            mQueueButton.setVisibility(View.VISIBLE);
                        }
                        /// @}
                        setRepeatButtonImage();
                        setShuffleButtonImage();
                        setPauseButtonImage();
                        return;
                    }
                } catch (RemoteException ex) {
                }
                // Service is dead or not playing anything. If we got here as part
                // of a "play this file" Intent, exit. Otherwise go to the Music
                // app start screen.
                
                /// M: MTK Mark for PlayAll timing issue, if play many error file, it will back to
                /// last screen.if play one or two error file, it will go to start screen, So we
                /// unify the behavior. {@
                //if (getIntent().getData() == null) {
                //    Intent intent = new Intent(Intent.ACTION_MAIN);
                //    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //    intent.setClass(MediaPlaybackActivity.this, MusicBrowserActivity.class);
                //    startActivity(intent);
                //}
                /// @}
                
                finish();
            }
            public void onServiceDisconnected(ComponentName classname) {
                mService = null;
                /// M: Close the activity when service not exsit
                finish();
            }
    };

    private void setRepeatButtonImage() {
        if (mService == null) return;
        try {
            /// M: Set drawable to action bar in landscape and set it to button in 
            // portrait {@
            int drawable;
            switch (mService.getRepeatMode()) {
                case MediaPlaybackService.REPEAT_ALL:
                    drawable = R.drawable.ic_mp_repeat_all_btn;
                    break;
                    
                case MediaPlaybackService.REPEAT_CURRENT:
                    drawable = R.drawable.ic_mp_repeat_once_btn;
                    break;
                    
                default:
                    drawable = R.drawable.ic_mp_repeat_off_btn;
                    break;
                    
            }
            if (mIsLandscape) {
                if (mRepeatMenuItem != null) {   
                    mRepeatMenuItem.setIcon(drawable);
                }
            } else {
                mRepeatButton.setImageResource(drawable);
            }
            /// @}
        } catch (RemoteException ex) {
        }
    }
    
    private void setShuffleButtonImage() {
        if (mService == null) return;
        try {
            /// M: Set drawable to action bar in landscape and set it to button in 
            // portrait  {@
            int drawable;
            switch (mService.getShuffleMode()) {
                case MediaPlaybackService.SHUFFLE_NONE:
                    drawable = R.drawable.ic_mp_shuffle_off_btn;
                    break;
                    
                case MediaPlaybackService.SHUFFLE_AUTO:
                    drawable = R.drawable.ic_mp_partyshuffle_on_btn;
                    break;
                    
                default:
                    drawable = R.drawable.ic_mp_shuffle_on_btn;
                    break;
                    
            }
            if (mIsLandscape) {
                if (mShuffleMenuItem != null) {
                    mShuffleMenuItem.setIcon(drawable);
                }   
            } else {
                mShuffleButton.setImageResource(drawable);
            }
            /// @}
        } catch (RemoteException ex) {
        }
    }
    
    private void setPauseButtonImage() {
        try {
            if (mService != null && mService.isPlaying()) {
                mPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                /// M: When not seeking, aways get position from service to 
                // update current playing time.
                if (!mSeeking) {
                    mPosOverride = -1;
                }
            } else {
                mPauseButton.setImageResource(android.R.drawable.ic_media_play);
            }
        } catch (RemoteException ex) {
        }
    }
    
    private ImageView mAlbum;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mAlbumName;
    private TextView mTrackName;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private int seekmethod;
    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;
    private static final int GET_ALBUM_ART = 3;
    private static final int ALBUM_ART_DECODED = 4;

    /// M: Define next and prev button.
    private static final int NEXT_BUTTON = 6;
    private static final int PREV_BUTTON = 7;

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        /// M: duration for position correction for play complete
        final int positionCorrection = 300;
        if(mService == null)
            return 500;
        try {
            MusicLogUtils.d(TAG, "refreshNow()-mPosOverride = " + mPosOverride);
            long position = mService.position();
            MusicLogUtils.d(TAG, "refreshNow()-position = " + position);
            long pos = mPosOverride < 0 ? position : mPosOverride;
            /// M: position correction for play complete @{
            if (pos + positionCorrection > mDuration) {
                MusicLogUtils.d(TAG, "refreshNow()-do a workaround for position");
                pos = mDuration;
            }
            /// @}
            if ((pos >= 0) && (mDuration > 0)) {
                MusicLogUtils.d(TAG, "refreshNow()-pos = " + pos);
                String time = MusicUtils.makeTimeString(this, pos / 1000);
                MusicLogUtils.d(TAG, "refreshNow()-time = " + time);
                mCurrentTime.setText(time);
                /// M: Don't need to update from touch @{
                if (!mFromTouch) {
                    int progress = (int) (1000 * pos / mDuration);
                    mProgress.setProgress(progress);
                }
                /// @}
                /// M: use to make current playing time aways showing when seeking
                if (mService.isPlaying() || mRepeatCount > -1) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    return 500;
                }
            } else {
                /// M: adjust the UI for error file  @{
                mCurrentTime.setVisibility(View.VISIBLE);
                mCurrentTime.setText("0:00");
                mTotalTime.setText("--:--");
                if (!mFromTouch) {
                    mProgress.setProgress(0);
                }
                /// @}
            }
            /// M: update duration for specific formats
            updateDuration(pos);
            // calculate the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            long remaining = 1000 - (pos % 1000);

            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = mProgress.getWidth();
            if (width == 0) width = 320;
            long smoothrefreshtime = mDuration / width;

            if (smoothrefreshtime > remaining) return remaining;
            if (smoothrefreshtime < 20) return 20;
            return smoothrefreshtime;
        } catch (RemoteException ex) {
        }
        return 500;
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ALBUM_ART_DECODED:
                    mAlbum.setImageBitmap((Bitmap)msg.obj);
                    mAlbum.getDrawable().setDither(true);
                    break;

                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                    
                case QUIT:
                    // This can be moved back to onCreate once the bug that prevents
                    // Dialogs from being started from onCreate/onResume is fixed.
                    new AlertDialog.Builder(MediaPlaybackActivity.this)
                            .setTitle(R.string.service_start_error_title)
                            .setMessage(R.string.service_start_error_msg)
                            .setPositiveButton(R.string.service_start_error_button,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            finish();
                                        }
                                    })
                            .setCancelable(false)
                            .show();
                    break;

                /// M: Handle next and prev button. {@
                case NEXT_BUTTON:
                    MusicLogUtils.d(TAG, "Next Handle");
                    if (mService == null) {
                        return;
                    }
                    mNextButton.setEnabled(false);
                    mNextButton.setFocusable(false);
                    try {
                        mService.next();
                        mPosOverride = -1;
                    } catch (RemoteException ex) {
                        MusicLogUtils.e(TAG, "Error:" + ex);
                    }                
                    mNextButton.setEnabled(true);
                    mNextButton.setFocusable(true);
                    break;
                    
                case PREV_BUTTON:
                    MusicLogUtils.d(TAG, "Prev Handle");
                    if (mService == null) {
                        return;
                    }
                    mPrevButton.setEnabled(false);
                    mPrevButton.setFocusable(false);
                    try {
                        mPosOverride = -1;
                        mService.prev();
                    } catch (RemoteException ex) {
                        MusicLogUtils.e(TAG, "Error:" + ex);
                    }
                    mPrevButton.setEnabled(true);
                    mPrevButton.setFocusable(true);
                    break;
                /// @}

                default:
                    break;
            }
        }
    };

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            MusicLogUtils.d(TAG, "mStatusListener: " + action);
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                /// M: Refresh option menu when meta change
                invalidateOptionsMenu();

                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setPauseButtonImage();

                MusicLogUtils.v("MusicPerformanceTest", "[Performance test][Music] "
                        + mPerformanceTestString + " end [" + System.currentTimeMillis() 
                        + "]");
                MusicLogUtils.v("MusicPerformanceTest", "[CMCC Performance test][Music] "
                        + mPerformanceTestString + " end [" + System.currentTimeMillis() 
                        + "]");
                
                queueNextRefresh(1);
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            /// M: Handle more status. {@
            } else if (action.equals(MediaPlaybackService.QUIT_PLAYBACK)) {
                mHandler.removeMessages(REFRESH);
                finish();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                /// M: stop refreshing
                MusicLogUtils.d(TAG, "onReceive, stop refreshing ...");
                mHandler.removeMessages(REFRESH);
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                /// M: restore refreshing
                MusicLogUtils.d(TAG, "onReceive, restore refreshing ...");
                long next = refreshNow();
                queueNextRefresh(next);
            }
            /// @}
        }
    };

    private static class AlbumSongIdWrapper {
        public long albumid;
        public long songid;
        AlbumSongIdWrapper(long aid, long sid) {
            albumid = aid;
            songid = sid;
        }
    }
    
    private void updateTrackInfo() {
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();
            if (path == null) {
                finish();
                return;
            }
            
            long songid = mService.getAudioId(); 
            if (songid < 0 && path.toLowerCase().startsWith("http://")) {
                // Once we can get album art and meta data from MediaPlayer, we
                // can show that info again when streaming.
                ((View) mArtistName.getParent()).setVisibility(View.INVISIBLE);
                ((View) mAlbumName.getParent()).setVisibility(View.INVISIBLE);
                mAlbum.setVisibility(View.GONE);
                mTrackName.setText(path);
                mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
                mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(-1, -1)).sendToTarget();
            } else {
                ((View) mArtistName.getParent()).setVisibility(View.VISIBLE);
                ((View) mAlbumName.getParent()).setVisibility(View.VISIBLE);
                String artistName = mService.getArtistName();
                if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                    artistName = getString(R.string.unknown_artist_name);
                }
                mArtistName.setText(artistName);
                String albumName = mService.getAlbumName();
                long albumid = mService.getAlbumId();
                if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
                    albumName = getString(R.string.unknown_album_name);
                    albumid = -1;
                }
                mAlbumName.setText(albumName);
                mTrackName.setText(mService.getTrackName());
                mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
                mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(albumid, songid)).sendToTarget();
                mAlbum.setVisibility(View.VISIBLE);
            }
            mDuration = mService.duration();
            mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
            /// M: For specific file, its duration need to be updated when playing. 
            recordDurationUpdateStatus();
        } catch (RemoteException ex) {
            finish();
        }
    }

    public class AlbumArtHandler extends Handler {
        private long mAlbumId = -1;
        
        public AlbumArtHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg)
        {
            /// M: Keep album art in mArtBitmap to improve loading speed when config changed.
            long albumid = ((AlbumSongIdWrapper) msg.obj).albumid;
            long songid = ((AlbumSongIdWrapper) msg.obj).songid;
            if (msg.what == GET_ALBUM_ART && (mAlbumId != albumid || albumid < 0 || mIsShowAlbumArt)) {
                Message numsg = null;
                // while decoding the new image, show the default album art
                if (mArtBitmap == null || mArtSongId != songid) {
                    numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, null);
                    mHandler.removeMessages(ALBUM_ART_DECODED);
                    mHandler.sendMessageDelayed(numsg, 300);

                    // Don't allow default artwork here, because we want to fall back to song-specific
                    // album art if we can't find anything for the album.
                    /// M: if don't get album art from file,or the album art is not the same 
                    /// as the song ,we should get the album art again
                    mArtBitmap = MusicUtils.getArtwork(MediaPlaybackActivity.this,
                                                        songid, albumid, false);
                    MusicLogUtils.d(TAG, "get art. mArtSongId = " + mArtSongId 
                                            + " ,songid = " + songid + " ");
                    mArtSongId = songid;
                }
                
                if (mArtBitmap == null) {
                    mArtBitmap = MusicUtils.getDefaultArtwork(MediaPlaybackActivity.this);
                    albumid = -1;
                }
                if (mArtBitmap != null) {
                    numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, mArtBitmap);
                    mHandler.removeMessages(ALBUM_ART_DECODED);
                    mHandler.sendMessage(numsg);
                }
                mAlbumId = albumid;
                mIsShowAlbumArt = false;
            }
        }
    }
    
    private static class Worker implements Runnable {
        private final Object mLock = new Object();
        private Looper mLooper;
        
        /**
         * Creates a worker thread with the given name. The thread
         * then runs a {@link android.os.Looper}.
         * @param name A name for the new thread
         */
        Worker(String name) {
            Thread t = new Thread(null, this, name);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
            synchronized (mLock) {
                while (mLooper == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }
        
        public Looper getLooper() {
            return mLooper;
        }
        
        public void run() {
            synchronized (mLock) {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mLock.notifyAll();
            }
            Looper.loop();
        }
        
        public void quit() {
            mLooper.quit();
        }
    }

    /**
     * M: move from onCreat, Update media playback activity ui. call this method
     * when activity oncreate or on configuration changed.
     */
    private void updateUI() {

       if(isSmallLCM)
	     setContentView(R.layout.audio_player_smaller);
	else
            setContentView(R.layout.audio_player);
	
        mCurrentTime = (TextView) findViewById(R.id.currenttime);
        mTotalTime = (TextView) findViewById(R.id.totaltime);
        mProgress = (ProgressBar) findViewById(android.R.id.progress);

        mAlbum = (ImageView) findViewById(R.id.album);
        mArtistName = (TextView) findViewById(R.id.artistname);
        mAlbumName = (TextView) findViewById(R.id.albumname);
        mTrackName = (TextView) findViewById(R.id.trackname);

        View v = (View)mArtistName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        v = (View)mAlbumName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        v = (View)mTrackName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        mPrevButton = (RepeatingImageButton) findViewById(R.id.prev);
        mPrevButton.setOnClickListener(mPrevListener);
        mPrevButton.setRepeatListener(mRewListener, 260);
        mPauseButton = (ImageButton) findViewById(R.id.pause);
        mPauseButton.requestFocus();
        mPauseButton.setOnClickListener(mPauseListener);
        mNextButton = (RepeatingImageButton) findViewById(R.id.next);
        mNextButton.setOnClickListener(mNextListener);
        mNextButton.setRepeatListener(mFfwdListener, 260);
        seekmethod = 1;

        mDeviceHasDpad = (getResources().getConfiguration().navigation ==
            Configuration.NAVIGATION_DPAD);

        /// M: Only when in PORTRAIT we use button, otherwise we use action bar
        if (!mIsLandscape) {
            mQueueButton = (ImageButton) findViewById(R.id.curplaylist);
            mQueueButton.setOnClickListener(mQueueListener);
            mShuffleButton = ((ImageButton) findViewById(R.id.shuffle));
            mShuffleButton.setOnClickListener(mShuffleListener);
            mRepeatButton = ((ImageButton) findViewById(R.id.repeat));
            mRepeatButton.setOnClickListener(mRepeatListener);
        }

        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);

        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }

    /**
     *  M: save the activity is in background.
     */
    @Override
    protected void onPause() {
        /// M: aviod Navigation button respond JE if Activity is background
        mIsInBackgroud = true;
        MusicLogUtils.d(TAG, "Music voice command onPause ");

        /// M :unregister voice command listener and cancel voice command indicator.@{
        if (MusicFeatureOption.IS_SUPPORT_VOICE_COMMAND_UI) {
            MusicLogUtils.d(TAG,
                    "Music finish: unregister voice command listener and send stop command");
            if (mVCmdMgrService != null) {
                String pkgName = getPackageName();
                sendVoiceCommand(pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                        VoiceCommandListener.ACTION_VOICE_UI_STOP, null);
                unRegisterVoiceCommand(pkgName);
                mNotificationManager.cancel(VOICE_COMMAND_INDICATOR);
            }
        }
        ///@}
        /// M: Before invalidateOptionsMenu,save the input of SearchView @{
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            mQueryText = searchView.getQuery();
            MusicLogUtils.d(TAG, "searchText:" + mQueryText);
        }
        /// @}
        super.onPause();
    }

    /**
     *  M: handle config change.
     *
     * @param newConfig The new device configuration.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        /// M: When configuration change, get the current orientation
        mIsLandscape = (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE);
        /// M: when configuration changed ,set mIsShowAlbumArt = true to update album art
        mIsShowAlbumArt = true;
        updateUI();
        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
        setRepeatButtonImage();
        setPauseButtonImage();
        setShuffleButtonImage();
        /// M: When back to this activity, ask service for right position
        mPosOverride = -1;
        /// M: Before invalidateOptionsMenu,save the input of SearchView @{
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            mQueryText = searchView.getQuery();
            MusicLogUtils.d(TAG, "searchText:" + mQueryText);
        }
        /// @}
        /// M: Refresh action bar menu item
        invalidateOptionsMenu();
    }

    /**
     * M: Search view query text listener.
     */
    SearchView.OnQueryTextListener mQueryTextListener = new SearchView.OnQueryTextListener() {
        public boolean onQueryTextSubmit(String query) {
            Intent intent = new Intent();
            intent.setClass(MediaPlaybackActivity.this, QueryBrowserActivity.class);
            intent.putExtra(SearchManager.QUERY, query);
            startActivity(intent);
            mSearchItem.collapseActionView();
            return true;
        }

        public boolean onQueryTextChange(String newText) {
            return false;
        }
    };

    /**
     * M: get the background color when touched, it may get from thememager.
     * 
     * @return Return background color
     */
    private int getBackgroundColor() {
        /// M: default background color for ICS.
        final int defaultBackgroundColor = 0xcc0099cc;
        /// M: For ICS style and support for theme manager {@
        int ret = defaultBackgroundColor;
        /*if (MusicFeatureOption.IS_SUPPORT_THEMEMANAGER) {
            Resources res = getResources();
            ret = res.getThemeMainColor();
            if (ret == 0) {
                ret = defaultBackgroundColor;
            }
        }*/
        return ret;
    }

    /**
     * M: update duration for MP3/AMR/AWB/AAC/FLAC formats.
     *
     * @param position The current positon for error check.
     */
    private void updateDuration(long position) {
        final int soundToMs = 1000;
        try {
            if (mNeedUpdateDuration && mService.isPlaying()) {
                long newDuration = mService.duration();

                if (newDuration > 0L && newDuration != mDuration) {
                    mDuration = newDuration;
                    mNeedUpdateDuration = false;
                    /// M: Update UI with new duration.
                    mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / soundToMs));
                    MusicLogUtils.i(TAG, "new duration updated!!");
                }
            } else if (position < 0 || position >= mDuration) {
                mNeedUpdateDuration = false;
            }
        } catch (RemoteException ex) {
            MusicLogUtils.e(TAG, "Error:" + ex);
        }
    }

    /**
     * M: record duration update status when playing,
     * if play mp3/aac/amr/awb/flac file, set mNeedUpdateDuration to update
     * layter in updateDuration().
     */
    private void recordDurationUpdateStatus() {
        final String mimeTypeMpeg = "audio/mpeg";
        final String mimeTypeAmr = "audio/amr";
        final String mimeTypeAmrWb = "audio/amr-wb";
        final String mimeTypeAac = "audio/aac";
        final String mimeTypeFlac = "audio/flac";
        String mimeType;
        mNeedUpdateDuration = false;
        try {
            mimeType = mService.getMIMEType();
        } catch (RemoteException ex) {
            MusicLogUtils.e(TAG, "Error:" + ex);
            mimeType = null;
        }
        if (mimeType != null) {
            MusicLogUtils.i(TAG, "mimeType=" + mimeType);
            if (mimeType.equals(mimeTypeMpeg) 
                || mimeType.equals(mimeTypeAmr) 
                || mimeType.equals(mimeTypeAmrWb) 
                || mimeType.equals(mimeTypeAac)
                || mimeType.equals(mimeTypeFlac)) {
                mNeedUpdateDuration = true;
            }
        }
    }

    /**
     * M: Add NFC callback to provide the uri.
     */
    @Override
    public Uri[] createBeamUris(NfcEvent event) {
        Uri currentUri= ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MusicUtils.getCurrentAudioId());;
        MusicLogUtils.i(TAG, "NFC call for uri " + currentUri);
        return new Uri[] {currentUri};
    }

    /**
     * M: Register voice command listener.
     */
    private void registerVoiceUiListener() {
        if (!MusicFeatureOption.IS_SUPPORT_VOICE_COMMAND_UI) {
            MusicLogUtils.w(TAG, "registerVoiceUiListener when not support voice ui feature, return!");
            return;
        }
        /// M: Get voice command manager
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private void bindVoiceService() {
        MusicLogUtils.d(TAG, "Music bindservice.");
        Intent mVoiceServiceIntent = new Intent();
        mVoiceServiceIntent.setAction(VoiceCommandListener.VOICE_SERVICE_ACTION);
        mVoiceServiceIntent.addCategory(VoiceCommandListener.VOICE_SERVICE_CATEGORY);
        bindService(mVoiceServiceIntent, mVoiceSerConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mVoiceSerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mVCmdMgrService = IVoiceCommandManagerService.Stub.asInterface(service);
            MusicLogUtils.d(TAG, "Music ServiceConnection onServiceConnected.");
            String pkgName = getPackageName();
            registerVoiceCommand(pkgName);
            sendVoiceCommand(pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                    VoiceCommandListener.ACTION_VOICE_UI_START, null);
            MusicLogUtils.d(TAG, "Music register voice listener onServiceConnected.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            MusicLogUtils.d(TAG, "Music service disconnected");
            isRegistered = false;
            mVCmdMgrService = null;
        }
    };

    // Callback used to notify apps
    private IVoiceCommandListener mCallback = new IVoiceCommandListener.Stub() {

        @Override
        public void onVoiceCommandNotified(int mainAction, int subAction, Bundle extraData)
                throws RemoteException {
            MusicLogUtils.d(TAG,"Music onVoiceCommandNotified --> handleVoiceCommandNotified");
            Message.obtain(mVoiceCommandHandler, mainAction, subAction, 0, extraData).sendToTarget();
        }
    };

    private Handler mVoiceCommandHandler = new Handler() {
        public void handleMessage(Message msg) {
            handleVoiceCommandNotified(msg.what, msg.arg1, (Bundle) msg.obj);
        };
    };

    public void handleVoiceCommandNotified(int mainAction, int subAction, Bundle extraData) {
        int actionExtraResult = extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT);
        int actionExtraResultInfo = extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO);
        String actionExtraResultInfoString = extraData
                .getString(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO1);
        MusicLogUtils.d(TAG, "Music mainAction = " + mainAction + ",subAction = " + subAction
                + " ,result = " + actionExtraResult + " ,info = " + actionExtraResultInfo
                + " ,infoString = " + actionExtraResultInfoString);
        if (actionExtraResult != VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
            MusicLogUtils.w(TAG, "onVoiceCommandNotified with failed result, just return");
            return;
        }
        if (mainAction == VoiceCommandListener.ACTION_MAIN_VOICE_UI) {
            switch (subAction) {
            // /M : voice command start,send command about show the indicator.
            case VoiceCommandListener.ACTION_VOICE_UI_START:
                String pkgName = getPackageName();
                MusicLogUtils.d(TAG, "Music RegisterCallback " + pkgName);
                sendVoiceCommand(pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                        VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD, null);
                break;
            // / M: the voice command action.Such as "play","pause","next" and so on.
            case VoiceCommandListener.ACTION_VOICE_UI_NOTIFY:
                voiceUiCommand(actionExtraResultInfo);
                break;
            // / M: TODO voice command stop May use if need.
            case VoiceCommandListener.ACTION_VOICE_UI_STOP:
                break;
            default:
                MusicLogUtils.e(TAG, "Undefined voice ui sub action!");
                break;
            }
        } else if (mainAction == VoiceCommandListener.ACTION_MAIN_VOICE_COMMON) {
            // / M: show the indicator in notification bar.
            if (subAction == VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD) {
                String[] stringCommonInfo = extraData
                        .getStringArray(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO);
                MusicLogUtils.i(TAG, "onVoiceCommandNotified with " + stringCommonInfo);
                if (stringCommonInfo != null) {
                    showVoiceCommandIndicator(stringCommonInfo);
                }
            }
        }
    }

    /**
     * M: Show voice command indicator to notify user.
     * 
     * @param stringCommonInfo The voice command stirng info
     */
    private void showVoiceCommandIndicator(String[] stringCommonInfo) {
        int commandLength = stringCommonInfo.length;
        StringBuffer keywords = new StringBuffer(commandLength);
        String lastWord = "\"" + stringCommonInfo[commandLength - 1] + "\"";

        for (int i = 0; i < commandLength - 1; i++) {
            keywords.append("\"").append(stringCommonInfo[i]).append("\"");
            if (i != commandLength - 2) {
                keywords.append(",");
            }
        }
        String indicatorTicker = getString(R.string.voice_command_indicator_ticker, keywords.toString(), lastWord);
        Notification indicatorNotify = new Notification.Builder(getApplicationContext())
                        .setTicker(indicatorTicker)
                        .setContentTitle(getString(R.string.voice_command_indicator_content_title))
                        .setContentText(getString(R.string.voice_command_indicator_content_text))
                        .setSmallIcon(com.mediatek.internal.R.drawable.stat_voice).build();
        mNotificationManager.notify(VOICE_COMMAND_INDICATOR, indicatorNotify);
        MusicLogUtils.i(TAG, "showVoiceCommandIndicator with " + indicatorTicker);
    }
    /**
     * M: Call when search request and expand search action view.
     */
    @Override
    public boolean onSearchRequested() {
        if (mSearchItem != null) {
            mSearchItem.expandActionView();
        }
        return true;
    }
}
