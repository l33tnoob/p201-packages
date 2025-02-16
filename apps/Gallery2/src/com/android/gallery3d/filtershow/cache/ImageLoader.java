/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.cache;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.android.gallery3d.app.GalleryActionBar;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.exif.ExifTag;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.FilterEnvironment;
import com.android.gallery3d.filtershow.tools.XmpPresets;
import com.android.gallery3d.util.XmpUtilHelper;
import com.mediatek.gallery3d.data.IMediaRequest;
import com.mediatek.gallery3d.data.RequestManager;
import com.mediatek.gallery3d.drm.DrmHelper;
import com.mediatek.gallery3d.util.MediatekFeature;
import com.mediatek.gallery3d.util.MtkLog;

import com.mediatek.dcfdecoder.DcfDecoder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class ImageLoader {

    private static final String LOGTAG = "ImageLoader";

    public static final String JPEG_MIME_TYPE = "image/jpeg";
    public static final int DEFAULT_COMPRESS_QUALITY = 95;

    public static final int ORI_NORMAL = ExifInterface.Orientation.TOP_LEFT;
    public static final int ORI_ROTATE_90 = ExifInterface.Orientation.RIGHT_TOP;
    public static final int ORI_ROTATE_180 = ExifInterface.Orientation.BOTTOM_LEFT;
    public static final int ORI_ROTATE_270 = ExifInterface.Orientation.RIGHT_BOTTOM;
    public static final int ORI_FLIP_HOR = ExifInterface.Orientation.TOP_RIGHT;
    public static final int ORI_FLIP_VERT = ExifInterface.Orientation.BOTTOM_RIGHT;
    public static final int ORI_TRANSPOSE = ExifInterface.Orientation.LEFT_TOP;
    public static final int ORI_TRANSVERSE = ExifInterface.Orientation.LEFT_BOTTOM;

    private static final int BITMAP_LOAD_BACKOUT_ATTEMPTS = 5;
    private static final float OVERDRAW_ZOOM = 1.2f;
    private ImageLoader() {}

    /**
     * Returns the Mime type for a Url.  Safe to use with Urls that do not
     * come from Gallery's content provider.
     */
    public static String getMimeType(Uri src) {
        String postfix = MimeTypeMap.getFileExtensionFromUrl(src.toString());
        String ret = null;
        if (postfix != null) {
            ret = MimeTypeMap.getSingleton().getMimeTypeFromExtension(postfix);
        }
        return ret;
    }

    public static String getLocalPathFromUri(Context context, Uri uri) {
        /// M: fix cursor leak.
        Cursor cursor = null;
        String path = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{MediaStore.Images.Media.DATA}, null, null, null);
            if (cursor == null) {
                return null;
            }
            int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            path = cursor.getString(index);
        } catch (Exception e) {
            Log.e(LOGTAG, "Exception at getLocalPathFromUri()", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            return path;
        }
    }

    /**
     * Returns the image's orientation flag.  Defaults to ORI_NORMAL if no valid
     * orientation was found.
     */
    public static int getMetadataOrientation(Context context, Uri uri) {
        if (uri == null || context == null) {
            throw new IllegalArgumentException("bad argument to getOrientation");
        }

        // First try to find orientation data in Gallery's ContentProvider.
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[] { MediaStore.Images.ImageColumns.ORIENTATION },
                    null, null, null);
            if (cursor != null && cursor.moveToNext()) {
                int ori = cursor.getInt(0);
                switch (ori) {
                    case 90:
                        return ORI_ROTATE_90;
                    case 270:
                        return ORI_ROTATE_270;
                    case 180:
                        return ORI_ROTATE_180;
                    default:
                        return ORI_NORMAL;
                }
            }
        } catch (SQLiteException e) {
            // Do nothing
        } catch (IllegalArgumentException e) {
            // Do nothing
        } catch (IllegalStateException e) {
            // Do nothing
        } finally {
            Utils.closeSilently(cursor);
        }
        ExifInterface exif = new ExifInterface();
        InputStream is = null;
        // Fall back to checking EXIF tags in file or input stream.
        try {
            if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                String mimeType = getMimeType(uri);
                if (!JPEG_MIME_TYPE.equals(mimeType)) {
                    return ORI_NORMAL;
                }
                String path = uri.getPath();
                exif.readExif(path);
            } else {
                is = context.getContentResolver().openInputStream(uri);
                exif.readExif(is);
            }
            return parseExif(exif);
        } catch (IOException e) {
            Log.w(LOGTAG, "Failed to read EXIF orientation", e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                Log.w(LOGTAG, "Failed to close InputStream", e);
            }
        }
        return ORI_NORMAL;
    }

    private static int parseExif(ExifInterface exif){
        Integer tagval = exif.getTagIntValue(ExifInterface.TAG_ORIENTATION);
        if (tagval != null) {
            int orientation = tagval;
            switch(orientation) {
                case ORI_NORMAL:
                case ORI_ROTATE_90:
                case ORI_ROTATE_180:
                case ORI_ROTATE_270:
                case ORI_FLIP_HOR:
                case ORI_FLIP_VERT:
                case ORI_TRANSPOSE:
                case ORI_TRANSVERSE:
                    return orientation;
                default:
                    return ORI_NORMAL;
            }
        }
        return ORI_NORMAL;
    }

    /**
     * Returns the rotation of image at the given URI as one of 0, 90, 180,
     * 270.  Defaults to 0.
     */
    public static int getMetadataRotation(Context context, Uri uri) {
        int orientation = getMetadataOrientation(context, uri);
        switch(orientation) {
            case ORI_ROTATE_90:
                return 90;
            case ORI_ROTATE_180:
                return 180;
            case ORI_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    /**
     * Takes an orientation and a bitmap, and returns the bitmap transformed
     * to that orientation.
     */
    public static Bitmap orientBitmap(Bitmap bitmap, int ori) {
        Matrix matrix = new Matrix();
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (ori == ORI_ROTATE_90 ||
                ori == ORI_ROTATE_270 ||
                ori == ORI_TRANSPOSE ||
                ori == ORI_TRANSVERSE) {
            int tmp = w;
            w = h;
            h = tmp;
        }
        switch (ori) {
            case ORI_ROTATE_90:
                matrix.setRotate(90, w / 2f, h / 2f);
                break;
            case ORI_ROTATE_180:
                matrix.setRotate(180, w / 2f, h / 2f);
                break;
            case ORI_ROTATE_270:
                matrix.setRotate(270, w / 2f, h / 2f);
                break;
            case ORI_FLIP_HOR:
                matrix.preScale(-1, 1);
                break;
            case ORI_FLIP_VERT:
                matrix.preScale(1, -1);
                break;
            case ORI_TRANSPOSE:
                matrix.setRotate(90, w / 2f, h / 2f);
                matrix.preScale(1, -1);
                break;
            case ORI_TRANSVERSE:
                matrix.setRotate(270, w / 2f, h / 2f);
                matrix.preScale(1, -1);
                break;
            case ORI_NORMAL:
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
    }

    /**
     * Returns the bitmap for the rectangular region given by "bounds"
     * if it is a subset of the bitmap stored at uri.  Otherwise returns
     * null.
     */
    public static Bitmap loadRegionBitmap(Context context, BitmapCache cache,
                                          Uri uri, BitmapFactory.Options options,
                                          Rect bounds) {
        InputStream is = null;
        int w = 0;
        int h = 0;
        if (options.inSampleSize != 0) {
            return null;
        }
        try {
            is = context.getContentResolver().openInputStream(uri);
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
            Rect r = new Rect(0, 0, decoder.getWidth(), decoder.getHeight());
            w = decoder.getWidth();
            h = decoder.getHeight();
            Rect imageBounds = new Rect(bounds);
            // return null if bounds are not entirely within the bitmap
            if (!r.contains(imageBounds)) {
                imageBounds.intersect(r);
                bounds.left = imageBounds.left;
                bounds.top = imageBounds.top;
            }
            Bitmap reuse = cache.getBitmap(imageBounds.width(),
                    imageBounds.height(), BitmapCache.REGION);
            options.inBitmap = reuse;
            Bitmap bitmap = decoder.decodeRegion(imageBounds, options);
            if (bitmap != reuse) {
                cache.cache(reuse); // not reused, put back in cache
            }
            return bitmap;
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "FileNotFoundException for " + uri, e);
        } catch (IOException e) {
            Log.e(LOGTAG, "FileNotFoundException for " + uri, e);
        } catch (IllegalArgumentException e) {
            Log.e(LOGTAG, "exc, image decoded " + w + " x " + h + " bounds: "
                    + bounds.left + "," + bounds.top + " - "
                    + bounds.width() + "x" + bounds.height() + " exc: " + e);
        } finally {
            Utils.closeSilently(is);
        }
        return null;
    }

    /**
     * Returns the bounds of the bitmap stored at a given Url.
     */
    public static Rect loadBitmapBounds(Context context, Uri uri) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        loadBitmap(context, uri, o);
        return new Rect(0, 0, o.outWidth, o.outHeight);
    }

    /**
     * Loads a bitmap that has been downsampled using sampleSize from a given url.
     */
    public static Bitmap loadDownsampledBitmap(Context context, Uri uri, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inSampleSize = sampleSize;
        /// M: remove alpha channel of png to avoid overlay display
        return MediatekFeature.replaceBitmapBgColor(loadBitmap(context, uri, options), 0xFF000000, true);
    }


    /**
     * Returns the bitmap from the given uri loaded using the given options.
     * Returns null on failure.
     */
    public static Bitmap loadBitmap(Context context, Uri uri, BitmapFactory.Options o) {
        if (uri == null || context == null) {
            throw new IllegalArgumentException("bad argument to loadBitmap");
        }
        MtkLog.i(LOGTAG, "uri = " + uri);
        InputStream is = null;
        Bitmap result = null;
        ///M : added for Drm image decode. @{
        android.database.Cursor c = null;
        int index_Drm = -1;
        boolean isDrm = false;
        String filePath = null;
        try {
            c = context.getContentResolver().query(uri, new String[] {Images.ImageColumns.IS_DRM, Images.ImageColumns.DATA}, null, null, null);
            if (c != null && c.moveToFirst()) {
                 index_Drm = c.getInt(0);
                 filePath = c.getString(1);
            } 
        } catch (Exception e) {
            // in case any exception happens, we simply do not update the rotation info in item.
            Log.e(LOGTAG, "Exception when trying to fetch orientation info", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (index_Drm == 1) {
            isDrm = true;
        }
        try {
            if (MediatekFeature.isDrmSupported() && null != uri && null != uri.getPath() && isDrm) {
                //check if this file is drm and can get decrypted buffer
                byte[] buffer = DrmHelper.forceDecryptFile(filePath, false);
                result = BitmapFactory.decodeByteArray(buffer, 0, buffer.length, o);
            } else {
        /// @}
            is = context.getContentResolver().openInputStream(uri);
            result = BitmapFactory.decodeStream(is, null, o);
//            return BitmapFactory.decodeStream(is, null, o);
            }
            return result;
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "FileNotFoundException for " + uri, e);
        } finally {
            Utils.closeSilently(is);
        }
        return null;
    }

    /**
     * Loads a bitmap at a given URI that is downsampled so that both sides are
     * smaller than maxSideLength. The Bitmap's original dimensions are stored
     * in the rect originalBounds.
     *
     * @param uri URI of image to open.
     * @param context context whose ContentResolver to use.
     * @param maxSideLength max side length of returned bitmap.
     * @param originalBounds If not null, set to the actual bounds of the stored bitmap.
     * @param useMin use min or max side of the original image
     * @return downsampled bitmap or null if this operation failed.
     */
    public static Bitmap loadConstrainedBitmap(Uri uri, Context context, int maxSideLength,
            Rect originalBounds, boolean useMin) {
        if (maxSideLength <= 0 || uri == null || context == null) {
            throw new IllegalArgumentException("bad argument to getScaledBitmap");
        }
        // Get width and height of stored bitmap
        Rect storedBounds = loadBitmapBounds(context, uri);
        if (originalBounds != null) {
            originalBounds.set(storedBounds);
        }
        int w = storedBounds.width();
        int h = storedBounds.height();

        // If bitmap cannot be decoded, return null
        if (w <= 0 || h <= 0) {
            return null;
        }

        // Find best downsampling size
        int imageSide = 0;
        if (useMin) {
            imageSide = Math.min(w, h);
        } else {
            imageSide = Math.max(w, h);
        }
        int sampleSize = 1;
        while (imageSide > maxSideLength) {
            imageSide >>>= 1;
            sampleSize <<= 1;
        }

        // Make sure sample size is reasonable
        if (sampleSize <= 0 ||
                0 >= (int) (Math.min(w, h) / sampleSize)) {
            return null;
        }
        return loadDownsampledBitmap(context, uri, sampleSize);
    }

    /**
     * Loads a bitmap at a given URI that is downsampled so that both sides are
     * smaller than maxSideLength. The Bitmap's original dimensions are stored
     * in the rect originalBounds.  The output is also transformed to the given
     * orientation.
     *
     * @param uri URI of image to open.
     * @param context context whose ContentResolver to use.
     * @param maxSideLength max side length of returned bitmap.
     * @param orientation  the orientation to transform the bitmap to.
     * @param originalBounds set to the actual bounds of the stored bitmap.
     * @return downsampled bitmap or null if this operation failed.
     */
    public static Bitmap loadOrientedConstrainedBitmap(Uri uri, Context context, int maxSideLength,
            int orientation, Rect originalBounds) {
        Bitmap bmap = loadConstrainedBitmap(uri, context, maxSideLength, originalBounds, false);
        if (bmap != null) {
            bmap = orientBitmap(bmap, orientation);
            if (bmap.getConfig()!= Bitmap.Config.ARGB_8888){
                bmap = bmap.copy( Bitmap.Config.ARGB_8888,true);
            }
        }
        return bmap;
    }

    public static Bitmap getScaleOneImageForPreset(Context context,
                                                   BitmapCache cache,
                                                   Uri uri, Rect bounds,
                                                   Rect destination) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        if (destination != null) {
            int thresholdWidth = (int) (destination.width() * OVERDRAW_ZOOM);
            if (bounds.width() > thresholdWidth) {
                int sampleSize = 1;
                int w = bounds.width();
                while (w > thresholdWidth) {
                    sampleSize *= 2;
                    w /= sampleSize;
                }
                options.inSampleSize = sampleSize;
            }
        }
        return loadRegionBitmap(context, cache, uri, options, bounds);
    }

    /**
     * Loads a bitmap that is downsampled by at least the input sample size. In
     * low-memory situations, the bitmap may be downsampled further.
     */
    public static Bitmap loadBitmapWithBackouts(Context context, Uri sourceUri, int sampleSize) {
        boolean noBitmap = true;
        int num_tries = 0;
        if (sampleSize <= 0) {
            sampleSize = 1;
        }
        Bitmap bmap = null;
        while (noBitmap) {
            try {
                // Try to decode, downsample if low-memory.
                bmap = loadDownsampledBitmap(context, sourceUri, sampleSize);
                noBitmap = false;
            } catch (java.lang.OutOfMemoryError e) {
                // Try with more downsampling before failing for good.
                if (++num_tries >= BITMAP_LOAD_BACKOUT_ATTEMPTS) {
                    throw e;
                }
                bmap = null;
                System.gc();
                sampleSize *= 2;
            }
        }
        return bmap;
    }

    /**
     * Loads an oriented bitmap that is downsampled by at least the input sample
     * size. In low-memory situations, the bitmap may be downsampled further.
     */
    public static Bitmap loadOrientedBitmapWithBackouts(Context context, Uri sourceUri,
            int sampleSize) {
        Bitmap bitmap = loadBitmapWithBackouts(context, sourceUri, sampleSize);
        if (bitmap == null) {
            return null;
        }
        int orientation = getMetadataOrientation(context, sourceUri);
        bitmap = orientBitmap(bitmap, orientation);
        return bitmap;
    }

    /**
     * Loads bitmap from a resource that may be downsampled in low-memory situations.
     */
    public static Bitmap decodeResourceWithBackouts(Resources res, BitmapFactory.Options options,
            int id) {
        boolean noBitmap = true;
        int num_tries = 0;
        if (options.inSampleSize < 1) {
            options.inSampleSize = 1;
        }
        // Stopgap fix for low-memory devices.
        Bitmap bmap = null;
        while (noBitmap) {
            try {
                // Try to decode, downsample if low-memory.
                bmap = BitmapFactory.decodeResource(
                        res, id, options);
                noBitmap = false;
            } catch (java.lang.OutOfMemoryError e) {
                // Retry before failing for good.
                if (++num_tries >= BITMAP_LOAD_BACKOUT_ATTEMPTS) {
                    throw e;
                }
                bmap = null;
                System.gc();
                options.inSampleSize *= 2;
            }
        }
        return bmap;
    }

    public static XMPMeta getXmpObject(Context context) {
        /// M: close input stream when operation done
        InputStream is = null;
        XMPMeta result = null;
        try {
             is = context.getContentResolver().openInputStream(
                    MasterImage.getImage().getUri());
            result = XmpUtilHelper.extractXMPMeta(is);
        } catch (FileNotFoundException e) {

        }finally {
            Utils.closeSilently(is);
            return result;
        }
    }

    /**
     * Determine if this is a light cycle 360 image
     *
     * @return true if it is a light Cycle image that is full 360
     */
    public static boolean queryLightCycle360(Context context) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(MasterImage.getImage().getUri());
            XMPMeta meta = XmpUtilHelper.extractXMPMeta(is);
            if (meta == null) {
                return false;
            }
            String namespace = "http://ns.google.com/photos/1.0/panorama/";
            String cropWidthName = "GPano:CroppedAreaImageWidthPixels";
            String fullWidthName = "GPano:FullPanoWidthPixels";

            if (!meta.doesPropertyExist(namespace, cropWidthName)) {
                return false;
            }
            if (!meta.doesPropertyExist(namespace, fullWidthName)) {
                return false;
            }

            Integer cropValue = meta.getPropertyInteger(namespace, cropWidthName);
            Integer fullValue = meta.getPropertyInteger(namespace, fullWidthName);

            // Definition of a 360:
            // GFullPanoWidthPixels == CroppedAreaImageWidthPixels
            if (cropValue != null && fullValue != null) {
                return cropValue.equals(fullValue);
            }

            return false;
        } catch (FileNotFoundException e) {
            return false;
        } catch (XMPException e) {
            return false;
        } finally {
            Utils.closeSilently(is);
        }
    }

    public static List<ExifTag> getExif(Context context, Uri uri) {
        String path = getLocalPathFromUri(context, uri);
        if (path != null) {
            Uri localUri = Uri.parse(path);
            String mimeType = getMimeType(localUri);
            if (!JPEG_MIME_TYPE.equals(mimeType)) {
                return null;
            }
            try {
                ExifInterface exif = new ExifInterface();
                exif.readExif(path);
                List<ExifTag> taglist = exif.getAllTags();
                return taglist;
            } catch (IOException e) {
                Log.w(LOGTAG, "Failed to read EXIF tags", e);
            }
        }
        return null;
    }

}
