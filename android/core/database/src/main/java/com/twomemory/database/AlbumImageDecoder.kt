package com.twomemory.database

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface

/** Read-only EXIF correction; original imported photo bytes remain unchanged. */
object AlbumImageDecoder {
    private fun orientation(path: String): Int = runCatching {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    fun displayDimensions(path: String, width: Int, height: Int): Pair<Int, Int> =
        if (orientation(path) in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270)
            height to width else width to height

    fun decode(path: String, target: Int): Bitmap? {
        require(target > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > target) sample *= 2
        val original = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val matrix = Matrix()
        when (orientation(path)) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return original
        }
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also {
            if (it !== original) original.recycle()
        }
    }
}
