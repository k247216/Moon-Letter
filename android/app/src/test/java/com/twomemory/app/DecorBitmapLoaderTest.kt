package com.twomemory.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DecorBitmapLoaderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun avatarAndCoverDecodeAreBoundedWithoutRewritingOriginalBytes() = runBlocking {
        val file=picture(2560,1920,Color.RED)
        val before=digest(file)
        for(edge in listOf(192,1280)) {
            val image=requireNotNull(DecorBitmapLoader.load(compose.activity,Uri.fromFile(file).toString(),edge,scope()))
            assertTrue("Requested maximum edge must bound decoded pixels",maxOf(image.width,image.height)<=edge)
            assertTrue(image.width>0 && image.height>0)
            assertEquals(before,digest(file))
        }
    }

    @Test fun jpegExifRotationAndMirroringKeepUprightPixelsAndOriginalBytes() = runBlocking {
        for(orientation in listOf(6,2)) {
            val file=orientedJpeg(orientation)
            val before=digest(file)
            val image=requireNotNull(DecorBitmapLoader.load(compose.activity,Uri.fromFile(file).toString(),192,scope()))
            val bitmap=image.asAndroidBitmap()
            assertTrue(maxOf(image.width,image.height)<=192)
            if(orientation==6) {
                assertTrue(image.height>image.width)
                assertRed(bitmap.getPixel(image.width/2,image.height/4))
                assertBlue(bitmap.getPixel(image.width/2,image.height*3/4))
            } else {
                assertTrue(image.width>image.height)
                assertBlue(bitmap.getPixel(image.width/4,image.height/2))
                assertRed(bitmap.getPixel(image.width*3/4,image.height/2))
            }
            assertEquals(before,digest(file))
        }
    }

    @Test fun contentProviderReadsRunOffMainAndReopenedDescriptorsAreClosed() {
        val provider=registerProvider(picture(640,480,Color.RED))
        val uri="content://${provider.authority}/image"
        val context=compose.activity
        val job=compose.runOnIdle { CoroutineScope(Dispatchers.Main.immediate).async {
            DecorBitmapLoader.load(context,uri,192,scope())
        } }
        compose.waitUntil(10_000) {org.robolectric.shadows.ShadowLooper.idleMainLooper(); job.isCompleted}
        val image=runBlocking {requireNotNull(job.await())}
        assertTrue(maxOf(image.width,image.height)<=192)
        assertTrue("Bounds and pixels must use separate reopened streams",provider.onMain.size>=2)
        assertTrue("Content-provider IO must not block the main looper",provider.onMain.none {it})
        assertTrue(provider.descriptors.isNotEmpty())
        provider.descriptors.forEach { descriptor ->
            var closed=false
            try {descriptor.fd} catch(_: IllegalStateException) {closed=true}
            assertTrue("Every opened descriptor must be closed",closed)
        }
    }

    @Test fun invalidAndMissingUriReturnNullAndAsyncFailureBecomesReady(): Unit = runBlocking {
        val invalid=File(compose.activity.filesDir,"decor-invalid-${UUID.randomUUID()}").apply {writeText("not an image")}
        val missing=File(compose.activity.filesDir,"decor-missing-${UUID.randomUUID()}")
        assertNull(DecorBitmapLoader.load(compose.activity,Uri.fromFile(invalid).toString(),192,scope()))
        assertNull(DecorBitmapLoader.load(compose.activity,Uri.fromFile(missing).toString(),1280,scope()))
        show(Uri.fromFile(missing).toString(),scope())
        compose.waitUntil(10_000) {compose.onAllNodesWithText("ready:true:missing").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("ready:true:missing").assertIsDisplayed()
    }

    @Test fun nullUriIsImmediatelyReadyWithoutAnyBitmap() {
        show(null,scope())
        compose.onNodeWithText("ready:true:missing").assertIsDisplayed()
    }

    @Test fun cancelledDecodePropagatesCancellationAfterNoncooperativeProviderReturns() {
        val provider=registerProvider(picture(640,480,Color.RED),holdFirst=true)
        val context=compose.activity
        val job=CoroutineScope(Dispatchers.IO).async {
            DecorBitmapLoader.load(context,"content://${provider.authority}/image",192,scope())
        }
        try {
            compose.waitUntil(10_000) {provider.entered.count==0L}
            job.cancel()
            provider.release.countDown()
            var cancelled=false
            runBlocking {try {job.await()} catch(_: CancellationException) {cancelled=true}}
            assertTrue("Cancellation must not be converted into a missing image",cancelled)
        } finally {provider.release.countDown(); job.cancel()}
    }

    @Test fun switchedUriDoesNotPublishAStaleDelayedBitmap() {
        val provider=registerProvider(picture(640,480,Color.RED),holdFirst=true)
        val newFile=picture(640,480,Color.BLUE)
        val uri=mutableStateOf("content://${provider.authority}/image")
        val decorScope=scope()
        compose.activity.runOnUiThread {compose.activity.setContent {TwoMemoryTheme {
            val state=rememberDecorBitmap(uri.value,192,decorScope)
            Text(label(state))
        } } }
        try {
            compose.waitUntil(10_000) {provider.entered.count==0L}
            compose.onNodeWithText("ready:false:missing").assertExists()
            compose.runOnIdle {uri.value=Uri.fromFile(newFile).toString()}
            compose.waitUntil(10_000) {compose.onAllNodesWithText("ready:true:blue").fetchSemanticsNodes().isNotEmpty()}
            provider.release.countDown()
            compose.waitUntil(10_000) {provider.returned.get()}
            compose.waitForIdle()
            compose.onNodeWithText("ready:true:blue").assertIsDisplayed()
            compose.onNodeWithText("ready:true:red").assertDoesNotExist()
        } finally {provider.release.countDown()}
    }

    @Test fun fileSignatureRevalidationDiscardsChangedAndMissingCachedPixels() = runBlocking {
        val file=picture(640,480,Color.RED)
        val uri=Uri.fromFile(file).toString()
        val decorScope=scope()
        assertRed(requireNotNull(DecorBitmapLoader.load(compose.activity,uri,192,decorScope)).asAndroidBitmap().getPixel(0,0))
        val version=file.lastModified()
        val replacement=picture(320,200,Color.BLUE)
        replacement.copyTo(file,overwrite=true)
        assertTrue(file.setLastModified(version+5000))
        val changed=requireNotNull(DecorBitmapLoader.load(compose.activity,uri,192,decorScope))
        assertBlue(changed.asAndroidBitmap().getPixel(0,0))
        assertTrue(file.delete())
        assertNull(DecorBitmapLoader.load(compose.activity,uri,192,decorScope))
    }

    @Test fun contentPixelsAreScopedAndDifferentTargetsDoNotReuseWrongResolution() = runBlocking {
        val provider=registerProvider(picture(640,480,Color.RED))
        val uri="content://${provider.authority}/image"
        val firstScope=scope()
        val small=requireNotNull(DecorBitmapLoader.load(compose.activity,uri,192,firstScope))
        assertTrue(maxOf(small.width,small.height)<=192)
        assertRed(small.asAndroidBitmap().getPixel(0,0))
        provider.file=picture(640,480,Color.BLUE)
        val otherScope=requireNotNull(DecorBitmapLoader.load(compose.activity,uri,192,scope()))
        assertBlue(otherScope.asAndroidBitmap().getPixel(0,0))
        val cover=requireNotNull(DecorBitmapLoader.load(compose.activity,uri,1280,firstScope))
        assertEquals(640,cover.width)
        assertBlue(cover.asAndroidBitmap().getPixel(0,0))
    }

    @Test fun actualPipeProviderDecodesBoundedPixelsWithoutChangingSource(): Unit = runBlocking {
        val file=picture(640,480,Color.RED)
        val before=digest(file)
        val provider=registerProvider(file,pipe=true)
        val uri="content://${provider.authority}/image"
        // Prove this is a readable real pipe, rather than treating fixture setup errors as RED.
        compose.activity.contentResolver.openInputStream(Uri.parse(uri))!!.use {assertEquals(137,it.read())}
        val image=requireNotNull(DecorBitmapLoader.load(compose.activity,uri,192,scope()))
        assertTrue(maxOf(image.width,image.height)<=192)
        assertRed(image.asAndroidBitmap().getPixel(0,0))
        assertEquals(before,digest(file))
    }

    @Test fun pipeProviderWithoutStableVersionNeverReusesPriorPixels(): Unit = runBlocking {
        val provider=registerProvider(picture(640,480,Color.RED),pipe=true)
        val uri="content://${provider.authority}/image"
        val decorScope=scope()
        compose.activity.contentResolver.openInputStream(Uri.parse(uri))!!.use {assertEquals(137,it.read())}
        assertRed(requireNotNull(DecorBitmapLoader.load(compose.activity,uri,192,decorScope)).asAndroidBitmap().getPixel(0,0))
        provider.file=picture(640,480,Color.BLUE)
        assertBlue(requireNotNull(DecorBitmapLoader.load(compose.activity,uri,192,decorScope)).asAndroidBitmap().getPixel(0,0))
    }

    private fun show(uri: String?,decorScope: String) {
        compose.activity.runOnUiThread {compose.activity.setContent {TwoMemoryTheme {
            Text(label(rememberDecorBitmap(uri,192,decorScope)))
        } } }
    }

    private fun label(state: DecorBitmapState): String {
        val pixel=state.bitmap?.asAndroidBitmap()?.getPixel(0,0)
        val color=when {pixel==null -> "missing"; Color.red(pixel)>Color.blue(pixel) -> "red"; else -> "blue"}
        return "ready:${state.ready}:$color"
    }

    private fun scope()="decor-test-${UUID.randomUUID()}"
    private fun picture(width: Int,height: Int,color: Int): File {
        val file=File(compose.activity.filesDir,"decor-${UUID.randomUUID()}.png")
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {bitmap.eraseColor(color); file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}
        finally {bitmap.recycle()}
        return file
    }

    private fun orientedJpeg(orientation: Int): File {
        val bitmap=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888)
        for(y in 0 until 200) for(x in 0 until 400) bitmap.setPixel(x,y,if(x<200) Color.RED else Color.BLUE)
        val output=ByteArrayOutputStream()
        try {check(bitmap.compress(Bitmap.CompressFormat.JPEG,100,output))} finally {bitmap.recycle()}
        val jpeg=output.toByteArray()
        val exif=byteArrayOf(-1,-31,0,34,69,120,105,102,0,0,73,73,42,0,8,0,0,0,
            1,0,18,1,3,0,1,0,0,0,orientation.toByte(),0,0,0,0,0,0,0)
        return File(compose.activity.filesDir,"decor-exif-${UUID.randomUUID()}.jpg").apply {
            outputStream().use {it.write(jpeg,0,2); it.write(exif); it.write(jpeg,2,jpeg.size-2)}
        }
    }

    private fun digest(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input -> val buffer=ByteArray(8192); while(true) {
            val count=input.read(buffer); if(count<0) break; digest.update(buffer,0,count)
        } }
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    private fun assertRed(pixel: Int)=assertTrue(Color.red(pixel)>200 && Color.blue(pixel)<50)
    private fun assertBlue(pixel: Int)=assertTrue(Color.blue(pixel)>200 && Color.red(pixel)<50)
    private fun registerProvider(file: File,holdFirst: Boolean=false,pipe: Boolean=false): DecorProvider {
        val provider=DecorProvider(file,holdFirst,"decor-${UUID.randomUUID()}",pipe)
        provider.attachInfo(compose.activity, android.content.pm.ProviderInfo().apply { authority = provider.authority })
        ShadowContentResolver.registerProviderInternal(provider.authority,provider)
        return provider
    }

    private class DecorProvider(@Volatile var file: File, val holdFirst: Boolean, val authority: String,
        val pipe: Boolean=false): ContentProvider() {
        val onMain=CopyOnWriteArrayList<Boolean>()
        val descriptors=CopyOnWriteArrayList<ParcelFileDescriptor>()
        val entered=CountDownLatch(1)
        val release=CountDownLatch(1)
        val returned=AtomicBoolean(false)
        private val held=AtomicBoolean(false)
        override fun openFile(uri: Uri,mode: String): ParcelFileDescriptor {
            onMain+=Looper.myLooper()==Looper.getMainLooper()
            if(holdFirst && held.compareAndSet(false,true)) {
                entered.countDown()
                check(release.await(10,TimeUnit.SECONDS)) {"Test provider latch timed out"}
                returned.set(true)
            }
            if(pipe) {
                val ends=ParcelFileDescriptor.createPipe()
                val source=file
                // These solid PNG fixtures fit within a pipe buffer. Complete the
                // write before returning so the Windows shadow cannot snapshot EOF.
                ParcelFileDescriptor.AutoCloseOutputStream(ends[1]).use {output ->
                    source.inputStream().use {it.copyTo(output)}
                }
                return ends[0].also {descriptors+=it}
            }
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).also {descriptors+=it}
        }
        override fun onCreate()=true
        override fun getType(uri: Uri)="image/png"
        override fun query(uri: Uri,projection: Array<out String>?,selection: String?,selectionArgs: Array<out String>?,sortOrder: String?): Cursor?=null
        override fun insert(uri: Uri,values: ContentValues?): Uri?=null
        override fun delete(uri: Uri,selection: String?,selectionArgs: Array<out String>?)=0
        override fun update(uri: Uri,values: ContentValues?,selection: String?,selectionArgs: Array<out String>?)=0
    }
}
