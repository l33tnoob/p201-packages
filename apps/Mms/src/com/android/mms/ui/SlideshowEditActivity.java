/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.ui;

import com.google.android.mms.MmsException;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.draft.DraftManager;
import com.android.mms.model.IModelChangedObserver;
import com.android.mms.model.Model;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;

import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPersister;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
/// M: Code analyze 001, fix bug ALPS00342944, Using idleHandler to
/// finish activity for avoiding NPE @{
import android.os.Looper;
/// @}
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/// M: Code analyze 002, new feature, import some useful classes @{
import android.content.res.Resources;
import android.util.Config;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;

import com.android.mms.ExceedMessageSizeException;
import com.android.mms.model.LayoutModel;
import com.mediatek.encapsulation.MmsLog;
/// @}

/// M: Feature for add slide to top
import com.android.mms.MmsPluginManager;
import com.mediatek.mms.ext.IMmsSlideshowEditor;
import com.mediatek.mms.ext.MmsSlideshowEditorImpl;

/**
 * A list of slides which allows user to edit each item in it.
 */
public class SlideshowEditActivity extends ListActivity {
    private final static String TAG = "SlideshowEditActivity";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    public static SlideshowEditActivity sCurrentActivity;
    // Menu ids.
    private final static int MENU_MOVE_UP           = 0;
    private final static int MENU_MOVE_DOWN         = 1;
    private final static int MENU_REMOVE_SLIDE      = 2;
    private final static int MENU_ADD_SLIDE         = 3;
    private final static int MENU_DISCARD_SLIDESHOW = 4;

    private final static int REQUEST_CODE_EDIT_SLIDE         = 6;

    /// M: Feature for add slide to top
    private final static int MENU_ADD_SLIDE_TO_TOP = 7;
    private final static int MENU_ADD_SLIDE_TO_BOTTOM = 8;

    // State.
    private final static String STATE = "state";
    private final static String SLIDE_INDEX = "slide_index";
    private final static String MESSAGE_URI = "message_uri";

    private ListView mList;
    private SlideListAdapter mSlideListAdapter;

    private SlideshowModel mSlideshowModel = null;
    private SlideshowEditor mSlideshowEditor = null;

    private Bundle mState;
    private Uri mUri;
    private Intent mResultIntent;
    private boolean mDirty;
    private View mAddSlideItem;
    /// M: Code analyze 003, fix bug ALPS00229067, insert Mms Slide page in the top or middle
    /// through SlideItemView context Menu @{
    private int mSelectedItemPosition =  -1;
    /// @}

    private Menu mOptionsMenu;

    private long mThreadId;

    /// M: only open one SlideEditorActivity
    private boolean mIsSlideOpened = false;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mList = getListView();
        mAddSlideItem = createAddSlideItem();
        mList.addFooterView(mAddSlideItem);
        mAddSlideItem.setVisibility(View.GONE);

        if (icicle != null) {
            // Retrieve previously saved state of this activity.
            mState = icicle.getBundle(STATE);
        }

        if (mState != null) {
            mUri = Uri.parse(mState.getString(MESSAGE_URI));
        } else {
            mUri = getIntent().getData();
        }

        if (mUri == null) {
            Log.e(TAG, "Cannot startup activity, null Uri.");
            finish();
            return;
        }

        /// get thread id from intent @{
        this.mThreadId = this.getIntent().getLongExtra("thread_id", -1L);
        /// @}

        // Return the Uri of the message to whoever invoked us.
        mResultIntent = new Intent();
        mResultIntent.setData(mUri);

        try {
            initSlideList();
            adjustAddSlideVisibility();
        } catch (MmsException e) {
            Log.e(TAG, "Failed to initialize the slide-list.", e);
            /// M: Code analyze 001, fix bug ALPS00342944, Using idleHandler to
            /// finish activity for avoiding NPE @{
            Looper.myQueue().addIdleHandler(new SlideshowEditorIdler());
            return;
            /// @}
        }
        sCurrentActivity = this;
    }

    private View createAddSlideItem() {
        View v = ((LayoutInflater) getSystemService(
                Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.slideshow_edit_item, null);

        //  Add slide.
        TextView text = (TextView) v.findViewById(R.id.slide_number_text);
        text.setText(R.string.add_slide);

        text = (TextView) v.findViewById(R.id.text_preview);
        /// M: Code analyze 004, fix bug ALPS00275979, increase Text Width and use Marquee
        /// to avoiding font cut @{
        text.setWidth(300);
        /// @}
        text.setText(R.string.add_slide_hint);
        text.setVisibility(View.VISIBLE);

        ImageView image = (ImageView) v.findViewById(R.id.image_preview);
        image.setImageResource(R.drawable.slideshow_holo_light);

        return v;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (position == (l.getCount() - 1) && MmsConfig.isSmsEnabled(this)) {
            addNewSlide();
        } else {
            /// M: only open slide once. @{
            if (!mIsSlideOpened) {
                openSlide(position);
                mIsSlideOpened = true;
                MmsLog.d(TAG, "openSlide once");
            }
            /// @}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsSlideOpened = false;
        invalidateOptionsMenu();
        /// M: Code analyze 005, new feature, set AddSlideItem visibility
        /// according to MAX_SLIDE_NUM when resume @{
        adjustAddSlideVisibility();
        /// @}
        if (mState != null) {
            mList.setSelection(mState.getInt(SLIDE_INDEX, 0));
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mState = new Bundle();
        if (mList.getSelectedItemPosition() >= 0) {
            mState.putInt(SLIDE_INDEX, mList.getSelectedItemPosition());
        }

        if (mUri != null) {
            mState.putString(MESSAGE_URI, mUri.toString());
        }

        if (LOCAL_LOGV) {
            Log.v(TAG, "Saving state: " + mState);
        }
        outState.putBundle(STATE, mState);
    }

    @Override
    protected void onPause()  {
        super.onPause();

        synchronized (this) {
            if (mDirty) {
                    /// M: Code analyze 006, fix bug ALPS00352374, Check for NPE @{
                    if (mSlideshowModel != null) {
                        DraftManager.getInstance().update(DraftManager.SYNC_UPDATE_ACTION,
                                mThreadId, this, mUri, mSlideshowModel, null);

//                        PduBody pb = mSlideshowModel.toPduBody();
//                        PduPersister.getPduPersister(this).updateParts(mUri, pb, null);
//                        if (pb != null) {
//                            mSlideshowModel.sync(pb);
//                        }
                        MmsLog.v(TAG, "onPause() Slideshow num = " + mSlideshowModel.size());
                    }
                    /// @}
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupSlideshowModel();
        sCurrentActivity = null;
        if (SlideEditorActivity.sCurrentActivity != null) {
            SlideEditorActivity.sCurrentActivity.finish();
        }
    }

    private void cleanupSlideshowModel() {
        if (mSlideshowModel != null) {
            mSlideshowModel.unregisterModelChangedObserver(
                    mModelChangedObserver);
            mSlideshowModel = null;
        }
    }

    private void initSlideList() throws MmsException {
        cleanupSlideshowModel();
        mSlideshowModel = SlideshowModel.createFromMessageUri(this, mUri);
        mSlideshowModel.registerModelChangedObserver(mModelChangedObserver);
        mSlideshowEditor = new SlideshowEditor(this, mSlideshowModel);
        /// M: Code analyze 007, new feature, use SharedPreferences to init Text_Layout @{
        if((mSlideshowModel.size() == 0 )) {
            boolean b = getSharedPreferences("SetDefaultLayout", 0).getBoolean("SetDefaultLayout", true);
            if(b) {
                mSlideshowEditor.changeLayout(LayoutModel.LAYOUT_BOTTOM_TEXT);
            } else {
                getSharedPreferences("SetDefaultLayout", 0).edit().putBoolean("SetDefaultLayout", true).commit();
            }
        }
        /// @}
        mSlideListAdapter = new SlideListAdapter(
                this, R.layout.slideshow_edit_item, mSlideshowModel);
        /// M: Code analyze 003, fix bug ALPS00229067, insert Mms Slide page in the top or middle
        /// through SlideItemView context Menu
        mList.setOnCreateContextMenuListener(mSlideListMenuCreateListener);
        /// @}
        mList.setAdapter(mSlideListAdapter);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mOptionsMenu = menu ;
        menu.clear();
        /// M: disable when non-default sms
        if (!MmsConfig.isSmsEnabled(this)) {
            return false;
        }

        /// M: Code analyze 001, fix bug ALPS00342944, check for NPE @{
        if (mList == null || mSlideshowModel == null) {
            return true;
        }
        /// @}
        int position = mList.getSelectedItemPosition();
        if ((position >= 0) && (position != (mList.getCount() - 1))) {
            // Selected one slide.
            if (position > 0) {
                menu.add(0, MENU_MOVE_UP, 0, R.string.move_up).setIcon(R.drawable.ic_menu_move_up);
            }

            if (position < (mSlideListAdapter.getCount() - 1)) {
                menu.add(0, MENU_MOVE_DOWN, 0, R.string.move_down).setIcon(
                        R.drawable.ic_menu_move_down);
            }
            /// M: Code analyze 005, new feature, AddSlide according to MAX_SLIDE_NUM @{
            if (mSlideshowModel.size() < SlideshowEditor.MAX_SLIDE_NUM){
                menu.add(0, MENU_ADD_SLIDE, 0, R.string.add_slide).setIcon(R.drawable.ic_menu_add_slide);
            }
            /// @}

            menu.add(0, MENU_REMOVE_SLIDE, 0, R.string.remove_slide).setIcon(
                    android.R.drawable.ic_menu_delete);
        } else {
            /// M: Feature for add slide to top
            IMmsSlideshowEditor MmsSlideshowEditorPlugin = 
                  (IMmsSlideshowEditor)MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MMS_SLIDESHOW_EDITOR);

            if (MmsSlideshowEditorPlugin != null && 
                       MmsSlideshowEditorPlugin.isSupportAddTopSlide()) {
                //add slide to top
                if (mSlideshowModel.size() >= 1 && mSlideshowModel.size() < SlideshowEditor.MAX_SLIDE_NUM) {
                    menu.add(0,MENU_ADD_SLIDE_TO_TOP,0,R.string.add_top_slide);
                }
                //add slide to bottom
                if (mSlideshowModel.size() < SlideshowEditor.MAX_SLIDE_NUM){
                    menu.add(0, MENU_ADD_SLIDE_TO_BOTTOM, 0, R.string.add_bottom_slide).setIcon(R.drawable.ic_menu_add_slide);
                }
            } else {
                //common
                /// M: Code analyze 005, new feature, AddSlide according to MAX_SLIDE_NUM @{
                if (mSlideshowModel.size() < SlideshowEditor.MAX_SLIDE_NUM){
                    menu.add(0, MENU_ADD_SLIDE, 0, R.string.add_slide).setIcon(R.drawable.ic_menu_add_slide);
                }
            }
        }

        menu.add(0, MENU_DISCARD_SLIDESHOW, 0,
                R.string.discard_slideshow).setIcon(R.drawable.ic_menu_delete_played);

        return true;
    }
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int position = mList.getSelectedItemPosition();

        switch (item.getItemId()) {
            case MENU_MOVE_UP:
                if ((position > 0) && (position < mSlideshowModel.size())) {
                    mSlideshowEditor.moveSlideUp(position);
                    mSlideListAdapter.notifyDataSetChanged();
                    mList.setSelection(position - 1);
                }
                break;
            case MENU_MOVE_DOWN:
                if ((position >= 0) && (position < mSlideshowModel.size() - 1)) {
                    mSlideshowEditor.moveSlideDown(position);
                    mSlideListAdapter.notifyDataSetChanged();
                    mList.setSelection(position + 1);
                }
                break;
            case MENU_REMOVE_SLIDE:
                if ((position >= 0) && (position < mSlideshowModel.size())) {
                    mSlideshowEditor.removeSlide(position);
                    mSlideListAdapter.notifyDataSetChanged();
                }
                break;
            case MENU_ADD_SLIDE:
                addNewSlide();
                break;
            case MENU_DISCARD_SLIDESHOW:
                // delete all slides from slideshow.
                mSlideshowEditor.removeAllSlides();
                mSlideListAdapter.notifyDataSetChanged();
                finish();
                break;

            /// M: Feature for add slide to top
            case MENU_ADD_SLIDE_TO_TOP:
                addNewSlideToTop();
                break;
            case MENU_ADD_SLIDE_TO_BOTTOM:
                addNewSlide();
                break;

        }

        return true;
    }
    private void openSlide(int index) {
        Intent intent = new Intent(this, SlideEditorActivity.class);
        intent.setData(mUri);
        intent.putExtra("thread_id", mThreadId);
        intent.putExtra(SlideEditorActivity.SLIDE_INDEX, index);
        startActivityForResult(intent, REQUEST_CODE_EDIT_SLIDE);
    }

    private void adjustAddSlideVisibility() {
        /// M: Code analyze 001, fix bug ALPS00342944, check for NPE @{
        if (mSlideshowModel == null) {
            return;
        }
        /// @}
        MenuItem item = null;
        MenuItem itemAddTop = null;
        MenuItem itemAddBottom = null;

        Log.i(TAG,"mOptionsMenu = "+ mOptionsMenu);

        IMmsSlideshowEditor MmsSlideshowEditorPlugin =
                  (IMmsSlideshowEditor)MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MMS_SLIDESHOW_EDITOR);
        boolean isSupportAddTopBottomSlide = false;
        if (MmsSlideshowEditorPlugin != null && MmsSlideshowEditorPlugin.isSupportAddTopSlide() == true) {
            isSupportAddTopBottomSlide = true;
        }

        if (mOptionsMenu != null) {
            if (isSupportAddTopBottomSlide) {
                itemAddTop = mOptionsMenu.findItem(MENU_ADD_SLIDE_TO_TOP);
                itemAddBottom = mOptionsMenu.findItem(MENU_ADD_SLIDE_TO_BOTTOM);
            } else {
            item = mOptionsMenu.findItem(MENU_ADD_SLIDE);
        }
        }
        if (mSlideshowModel.size() >= SlideshowEditor.MAX_SLIDE_NUM) {
            mAddSlideItem.setVisibility(View.GONE);
            if (isSupportAddTopBottomSlide) {
                if (itemAddTop != null && itemAddBottom != null) {
                    itemAddTop.setVisible(false);
                    itemAddBottom.setVisible(false);
                }
            } else {
            if (item != null) {
                item.setVisible(false);
            }
            }
        } else {
            mAddSlideItem.setVisibility(View.VISIBLE);
            if (isSupportAddTopBottomSlide) {
                if (itemAddTop != null && itemAddBottom != null) {
                    itemAddTop.setVisible(true);
                    itemAddBottom.setVisible(true);
                } else if(mOptionsMenu != null) {
                    itemAddTop = mOptionsMenu.add(0, MENU_ADD_SLIDE_TO_TOP, 0, R.string.add_top_slide).setIcon(R.drawable.ic_menu_add_slide);
                    itemAddBottom = mOptionsMenu.add(0, MENU_ADD_SLIDE_TO_BOTTOM, 0, R.string.add_bottom_slide).setIcon(R.drawable.ic_menu_add_slide);
                }
            } else {
            if (item != null) {
                item.setVisible(true);
            } else if (mOptionsMenu != null) {
                item = mOptionsMenu.add(0, MENU_ADD_SLIDE, 0, R.string.add_slide).setIcon(R.drawable.ic_menu_add_slide);
            }
        }
        }
        if (item != null) {
            Log.i(TAG,"item isVisible = "+item.isVisible());
        }
        if (itemAddTop != null && itemAddBottom != null) {
            Log.i(TAG,"itemAddTop isVisible = "+itemAddTop.isVisible()+ "itemAddBottom isVisible = " + itemAddBottom.isVisible());
        }
        /// M: disable when non-default sms
        if (MmsConfig.isSmsEnabled(this)) {
            if (mList.getFooterViewsCount() == 0) {
                mList.addFooterView(mAddSlideItem);
            }
        } else {
            mList.removeFooterView(mAddSlideItem);
        }
    }

    /// M: Feature for add slide to top
    private void addNewSlideToTop() {
        if (mSlideshowEditor.addNewSlide(0)) {
            // add successfully
            mSlideListAdapter.notifyDataSetChanged();

            // Select the new slide.
            mList.requestFocus();
            mList.setSelection(mSlideshowModel.size() - 1);
        } else {
            Toast.makeText(this, R.string.cannot_add_slide_anymore,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void addNewSlide() {
        if ( mSlideshowEditor.addNewSlide() ) {
            // add successfully
            mSlideListAdapter.notifyDataSetChanged();

            // Select the new slide.
            mList.requestFocus();
            mList.setSelection(mSlideshowModel.size() - 1);
        } else {
            Toast.makeText(this, R.string.cannot_add_slide_anymore,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {

        MmsLog.v(TAG,"onActivityResult: requestCode=" + requestCode
                + ", resultCode=" + resultCode + ", data=" + data);
            
        if (resultCode != RESULT_OK &&
            resultCode != RESULT_CANCELED) {
            return;
        }

        switch(requestCode) {
            case REQUEST_CODE_EDIT_SLIDE:
                synchronized (this) {
                    mDirty = true;
                }
                setResult(RESULT_OK, mResultIntent);

                if ((data != null) && data.getBooleanExtra("done", false)) {
                    finish();
                    return;
                }

                try {
                    initSlideList();
                } catch (MmsException e) {
                    Log.e(TAG, "Failed to initialize the slide-list.", e);
                    /// M: Code analyze 001, fix bug ALPS00342944, Using idleHandler to
                    /// finish activity for avoiding NPE @{
                    Looper.myQueue().addIdleHandler(new SlideshowEditorIdler());
                    /// @}
                    return;
                }
                break;
        }
    }

    private static class SlideListAdapter extends ArrayAdapter<SlideModel> {
        private final Context mContext;
        private final int mResource;
        private final LayoutInflater mInflater;
        private final SlideshowModel mSlideshow;

        public SlideListAdapter(Context context, int resource,
                SlideshowModel slideshow) {
            super(context, resource, slideshow);

            mContext = context;
            mResource = resource;
            mInflater = LayoutInflater.from(context);
            mSlideshow = slideshow;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return createViewFromResource(position, convertView, mResource);
        }

        private View createViewFromResource(int position, View convertView, int resource) {
            SlideListItemView slideListItemView;
            slideListItemView = (SlideListItemView) mInflater.inflate(
                    resource, null);

            // Show slide number.
            TextView text;
            text = (TextView) slideListItemView.findViewById(R.id.slide_number_text);
            text.setText(mContext.getString(R.string.slide_number, position + 1));

            SlideModel slide = getItem(position);
            int dur = slide.getDuration() / 1000;
            text = (TextView) slideListItemView.findViewById(R.id.duration_text);
            text.setText(mContext.getResources().
                         getQuantityString(R.plurals.slide_duration, dur, dur));

            Presenter presenter = PresenterFactory.getPresenter(
                    "SlideshowPresenter", mContext, slideListItemView, mSlideshow);
            ((SlideshowPresenter) presenter).setLocation(position);
            presenter.present(null);

            return slideListItemView;
        }
    }

    private final IModelChangedObserver mModelChangedObserver =
        new IModelChangedObserver() {
            public void onModelChanged(Model model, boolean dataChanged) {
                synchronized (SlideshowEditActivity.this) {
                    mDirty = true;
                }
                setResult(RESULT_OK, mResultIntent);
                adjustAddSlideVisibility();
            }
        };

    /// M: Code analyze 008, unuseless @{
    private String getResourcesString(int id) {
        Resources r = getResources();
        return r.getString(id);
    }
    /// @}

    /// M: Code analyze 003, fix bug ALPS00229067, insert Mms Slide page in the top or middle
    /// through SlideItemView context Menu @{
    private final OnCreateContextMenuListener mSlideListMenuCreateListener =
        new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            menu.clear();
            mSelectedItemPosition =  -1;

            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            mSelectedItemPosition =  info.position;

            /// M: disable when non-default sms
            if (!MmsConfig.isSmsEnabled(SlideshowEditActivity.this)) {
                return;
            }

            menu.setHeaderTitle(R.string.edit_slide_activity);

            SlideListMenuClickListener slc = new SlideListMenuClickListener();
            if ((mSelectedItemPosition >= 0) && (mSelectedItemPosition != (mList.getCount() - 1))) {
                // Selected one slide.
                if (mSelectedItemPosition > 0) {
                    menu.add(0, MENU_MOVE_UP, 0, R.string.move_up).setIcon(R.drawable.ic_menu_move_up).setOnMenuItemClickListener(slc);
                }

                if (mSelectedItemPosition < (mSlideListAdapter.getCount() - 1)) {
                    menu.add(0, MENU_MOVE_DOWN, 0, R.string.move_down).setIcon(
                            R.drawable.ic_menu_move_down).setOnMenuItemClickListener(slc);
                }
                if (mSlideshowModel.size() < SlideshowEditor.MAX_SLIDE_NUM){
                    menu.add(0, MENU_ADD_SLIDE, 0, R.string.add_slide).setIcon(R.drawable.ic_menu_add_slide).setOnMenuItemClickListener(slc);
                }

                menu.add(0, MENU_REMOVE_SLIDE, 0, R.string.remove_slide).setIcon(
                        android.R.drawable.ic_menu_delete).setOnMenuItemClickListener(slc);
            } else {
                if (mSlideshowModel.size() < SlideshowEditor.MAX_SLIDE_NUM) {
                    menu.add(0, MENU_ADD_SLIDE, 0, R.string.add_slide).setIcon(R.drawable.ic_menu_add_slide)
                            .setOnMenuItemClickListener(slc);
                }
            }
        }
    };

    private final class SlideListMenuClickListener implements MenuItem.OnMenuItemClickListener {
        public boolean onMenuItemClick(MenuItem item) {
            int position = mSelectedItemPosition;
            mSelectedItemPosition = -1;
            switch (item.getItemId()) {
                case MENU_MOVE_DOWN:
                    if ((position >= 0) && (position < mSlideshowModel.size() - 1)) {
                        mSlideshowEditor.moveSlideDown(position);
                        mSlideListAdapter.notifyDataSetChanged();
                        mList.setSelection(position + 1);
                    }
                    break;
                case MENU_MOVE_UP:
                    if ((position > 0) && (position < mSlideshowModel.size())) {
                        mSlideshowEditor.moveSlideUp(position);
                        mSlideListAdapter.notifyDataSetChanged();
                        mList.setSelection(position - 1);
                    }
                    break;
                case MENU_REMOVE_SLIDE:
                    if ((position >= 0) && (position < mSlideshowModel.size())) {
                        mSlideshowEditor.removeSlide(position);
                        mSlideListAdapter.notifyDataSetChanged();
                    }
                    break;
                case MENU_ADD_SLIDE:
                    if (position != mSlideshowModel.size()) {
                        position++;
                    }
                    if (mSlideshowEditor.addNewSlide(position)) {
                        /// M: add successfully
                        mSlideListAdapter.notifyDataSetChanged();
                        /// M: Select the new slide.
                        mList.requestFocus();
                        mList.setSelection(position);
                    } else {
                        Toast.makeText(SlideshowEditActivity.this, R.string.cannot_add_slide_anymore, Toast.LENGTH_SHORT).show();
                    }
                    
                    break;
                case MENU_DISCARD_SLIDESHOW:
                    /// M: delete all slides from slideshow.
                    mSlideshowEditor.removeAllSlides();
                    mSlideListAdapter.notifyDataSetChanged();
                    finish();
                    break;
            }
            return true;
        }
    }
    /// @}

    /// M: Code analyze 001, fix bug ALPS00342944, Using idleHandler to
    /// finish activity for avoiding NPE @{
    final class SlideshowEditorIdler implements android.os.MessageQueue.IdleHandler {
        public final boolean queueIdle() {
            finish();      /// M: call finish function.
            return false;  /// M: which means only execute once.
        }
    }
    /// @}
}
