package com.tahamalas.custom_image_picker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
import android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
import android.provider.Settings
import android.util.Log
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.util.*


/**
 * CustomImagePickerPlugin
 */
class CustomImagePickerPlugin(internal var activity: Activity, internal var methodChannel: MethodChannel, registrar: Registrar) : MethodCallHandler {

    private var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    private var result: Result? = null

    init {
        this.methodChannel.setMethodCallHandler(this)
        /*this.activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            
            override fun onActivityPaused(p0: Activity) {
            }

            override fun onActivityResumed(p0: Activity) {
                getPermissionResult(result!!, p0, {})
            }

            override fun onActivityStarted(p0: Activity) {
            }

            override fun onActivityDestroyed(p0: Activity) {
            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
            }

            override fun onActivityStopped(p0: Activity) {
            }

            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
            }
        }*/
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        this.result = result
        when {
            call.method == "startListening" -> getPermissionResult(result, activity, call.arguments)
            call.method == "cancelListening" -> cancelListening(call.arguments, result)
            else -> result.notImplemented()
        }
    }

    fun getPermissionResult(result: Result, activity: Activity, arguments: Any?) {
        Dexter.withActivity(activity)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(object : PermissionListener {
                    override fun onPermissionGranted(response: PermissionGrantedResponse) {
                        val argsMap = arguments as Map<*, *>

                        when (argsMap["id"] as Int) {
                            CallbacksEnum.GET_IMAGES.ordinal -> startListening(argsMap, result, "getAllImages")
                            CallbacksEnum.GET_GALLERY.ordinal -> startListening(argsMap, result, "getAlbumList")
                            CallbacksEnum.GET_IMAGES_OF_GALLERY.ordinal -> startListening(argsMap, result, "getPhotosOfAlbum")
                        }

                        //                        when (methodName) {
//                            "getAllImages" -> {
//                                result.success(getAllImageList(activity))
//                            }
//                            "getAlbumList" -> {
//                                result.success(getAlbumList(MEDIA_TYPE_IMAGE, activity.contentResolver))
//                            }
//                            "getPhotosOfAlbum" -> {
//                                result.success(getPhotosOfAlbum(activity, arguments as String))
//                            }
//                        }

//                        when (methodName) {
//                            "getAllImages" -> {
//                                result.success(getAllImageList(activity))
//                            }
//                            "getAlbumList" -> {
//                                result.success(getAlbumList(MEDIA_TYPE_IMAGE, activity.contentResolver))
//                            }
//                            "getPhotosOfAlbum" -> {
//                                result.success(getPhotosOfAlbum(activity, arguments as String))
//                            }
//                        }
                    }

                    override fun onPermissionDenied(response: PermissionDeniedResponse) {
                        val builder = AlertDialog.Builder(activity)
                        builder.setMessage("This permission is needed for use this features of the app so please, allow it!")
                        builder.setTitle("We need this permission")
                        builder.setCancelable(false)
                        builder.setPositiveButton("OK") { dialog, id ->
                            dialog.cancel()
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts("package", activity.packageName, null)
                            intent.data = uri
                            activity.startActivity(intent)
                        }
                        builder.setNegativeButton("Cancel") { dialog, id -> dialog.cancel() }
                        val alert = builder.create()
                        alert.show()
                    }

                    override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest, token: PermissionToken) {
                        val builder = AlertDialog.Builder(activity)
                        builder.setMessage("This permission is needed for use this features of the app so please, allow it!")
                        builder.setTitle("We need this permission")
                        builder.setCancelable(false)
                        builder.setPositiveButton("OK") { dialog, id ->
                            dialog.cancel()
                            token.continuePermissionRequest()
                        }
                        builder.setNegativeButton("Cancel") { dialog, id ->
                            dialog.cancel()
                            token.cancelPermissionRequest()
                        }
                        val alert = builder.create()
                        alert.show()
                    }
                }).check()
    }

    private val callbackById: MutableMap<Int, Runnable> = mutableMapOf()

    fun startListening(args: Any, result: Result, methodName: String) {
        // Get callback id
        println("the args are $args")
        val argsFromFlutter = args as Map<*, *>
        val currentListenerId = argsFromFlutter["id"] as Int
        val runnable = Runnable {
            if (callbackById.containsKey(currentListenerId)) {
                val argsMap: MutableMap<String, Any> = mutableMapOf()
                argsMap["id"] = currentListenerId

                when (methodName) {
                    "getAllImages" -> {
                        argsMap["args"] = getAllImageList(activity)
                    }
                    "getAlbumList" -> {
                        argsMap["args"] = getAlbumList(MEDIA_TYPE_IMAGE, activity.contentResolver)
                    }
                    "getPhotosOfAlbum" -> {
                        val callArgs = argsFromFlutter["args"] as Map<*, *>
                        argsMap["args"] = getPhotosOfAlbum(activity, callArgs["albumID"] as String, callArgs["page"] as Int)
                    }
                }
                // Send some value to callback
                activity.runOnUiThread {
                    methodChannel.invokeMethod("callListener", argsMap)
                }
            }
        }
        val thread = Thread(runnable)
        callbackById[currentListenerId] = runnable
        thread.start()
        // Return immediately
        result.success(null)
    }

    private fun cancelListening(args: Any, result: Result) {
        // Get callback id
        val currentListenerId = args as Int
        // Remove callback
        callbackById.remove(currentListenerId)
        // Do additional stuff if required to cancel the listener
        result.success(null)
    }

    private fun getPhotosOfAlbum(activity: Activity, albumID: String, pageNumber: Int): String {

        val phonePhotos = mutableListOf<PhonePhoto>()

        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID)

        val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val cur = activity.contentResolver.query(images,
                projection, "${MediaStore.Images.ImageColumns.BUCKET_ID} == ?", arrayOf(
                albumID
        ),  MediaStore.Images.Media.DATE_MODIFIED + " DESC " + " LIMIT ${(pageNumber - 1) * 50}, 50 "
        )

        if (cur != null && cur!!.count > 0) {
            Log.i("DeviceImageManager", " query count=" + cur!!.count)

            if (cur!!.moveToFirst()) {
                var bucketName: String
                var data: String
                var imageId: String
                val bucketNameColumn = cur!!.getColumnIndex(
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                val imageUriColumn = cur!!.getColumnIndex(
                        MediaStore.Images.Media.DATA)

                val imageIdColumn = cur!!.getColumnIndex(
                        MediaStore.Images.Media._ID)

                do {
                    bucketName = cur!!.getString(bucketNameColumn)
                    data = cur!!.getString(imageUriColumn)
                    imageId = cur!!.getString(imageIdColumn)
                    phonePhotos.add(PhonePhoto(imageId, bucketName, data))

                } while (cur!!.moveToNext())
            }

            cur!!.close()
            var string = "[ "
            for (phonePhoto in phonePhotos) {
                string += phonePhoto.toJson()
                if (phonePhotos.indexOf(phonePhoto) != phonePhotos.size - 1)
                    string += ", "
            }
            string += "]"
            return string
        } else {
            return "[]"
        }

    }


    /*
    *
    *   mediaType could be one of
    *
    *   public static final int MEDIA_TYPE_IMAGE = 1;
    *
    *   public static final int MEDIA_TYPE_VIDEO = 3;
    *
    *   from android.provider.MediaStore class
    *
    */
    private fun getAlbumList(mediaType: Int, contentResolver: ContentResolver): String {

        val phoneAlbums = mutableListOf<PhoneAlbum>()

        var contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (mediaType == MEDIA_TYPE_VIDEO) {
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Images.ImageColumns.BUCKET_ID, MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, MediaStore.Images.ImageColumns.DATE_TAKEN, MediaStore.Images.ImageColumns.DATA)
        val bucketGroupBy = "1) GROUP BY ${MediaStore.Images.ImageColumns.BUCKET_ID}, (${MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME}"
        val bucketOrderBy = MediaStore.Images.Media.DATE_MODIFIED + " DESC"

        val cursor = contentResolver.query(contentUri, projection, bucketGroupBy, null, bucketOrderBy)


        if (cursor != null) {
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) // Thumb image path

                val selection = MediaStore.Images.Media.BUCKET_ID + "='" + bucketId + "'"

                val countCursor = contentResolver.query(contentUri, arrayOf("count(" + MediaStore.Images.ImageColumns._ID + ")"), selection, null, bucketOrderBy)

                var count = 0
                if (countCursor != null) {
                    countCursor.moveToFirst()
                    count = countCursor.getInt(0)
                    countCursor.close()
                }

                Log.d("AlbumScanner", "bucketId : $bucketId | name : $name | count : $count | path : $path")

                phoneAlbums.add(PhoneAlbum(bucketId, name, path, count))
            }
            cursor.close()
            var string = "[ "
            for (phoneAlbum in phoneAlbums) {
                string += phoneAlbum.toJson()
                if (phoneAlbums.indexOf(phoneAlbum) != phoneAlbums.size - 1)
                    string += ", "
            }
            string += "]"
            return string
        } else {
            return "[]"
        }
    }

//    fun getPhoneAlbums(activity: Activity): String {
//        val phoneAlbums = mutableListOf<PhoneAlbum>()
//        val albumsNames = mutableListOf<String>()
//
//        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID)
//
//        val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//
//        val cur = activity.contentResolver.query(images,
//                projection, null, null, MediaStore.Images.Media.DATE_MODIFIED + " DESC"
//        )
//
//        if (cur != null && cur!!.count > 0) {
//            Log.i("DeviceImageManager", " query count=" + cur!!.getCount())
//
//            if (cur!!.moveToFirst()) {
//                var bucketName: String
//                var data: String
//                var imageId: String
//                val bucketNameColumn = cur!!.getColumnIndex(
//                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
//
//                val imageUriColumn = cur!!.getColumnIndex(
//                        MediaStore.Images.Media.DATA)
//
//                val imageIdColumn = cur!!.getColumnIndex(
//                        MediaStore.Images.Media._ID)
//
//                do {
//                    bucketName = cur!!.getString(bucketNameColumn)
//                    data = cur!!.getString(imageUriColumn)
//                    imageId = cur!!.getString(imageIdColumn)
//
////                    val phonePhoto = PhonePhoto(Integer.valueOf(imageId), bucketName, data)
//
//                    if (albumsNames.contains(bucketName)) {
//                        for (album in phoneAlbums) {
//                            if (album.name == bucketName) {
////                                album.increasePhotosCount()
////                                album.albumPhotos.add(phonePhoto)
//                                Log.i("DeviceImageManager", "A photo was added to album => $bucketName")
//                                break
//                            }
//                        }
//                    } else {
//                        val album = PhoneAlbum(imageId, bucketName, data, 0)
//                        Log.i("DeviceImageManager", "A new album was created => $bucketName")
////                        album.albumPhotos.add(phonePhoto)
//                        Log.i("DeviceImageManager", "A photo was added to album => $bucketName")
////                        album.increasePhotosCount()
//                        phoneAlbums.add(album)
//                        albumsNames.add(bucketName)
//                    }
//
//                } while (cur!!.moveToNext())
//            }
//
//            cur!!.close()
//            var string = "[ "
//            for (phoneAlbum in phoneAlbums) {
//                string += phoneAlbum.toJson()
//                if (phoneAlbums.indexOf(phoneAlbum) != phoneAlbums.size - 1)
//                    string += ", "
//            }
//            string += "]"
//            return string
//        } else {
//            return "[]"
//        }
//    }

    private fun getAllImageList(activity: Activity): ArrayList<String> {
        val allImageList = ArrayList<String>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.ImageColumns.DATA, MediaStore.Images.ImageColumns.DISPLAY_NAME, MediaStore.Images.ImageColumns.DATE_ADDED, MediaStore.Images.ImageColumns.TITLE)
        val c = activity.contentResolver.query(uri, projection, null, null, null)
        if (c != null) {
            while (c.moveToNext()) {
                //  ImageModel imageModel = new ImageModel();
                Log.e("", "getAllImageList: " + c.getString(0))
                Log.e("", "getAllImageList: " + c.getString(1))
                Log.e("", "getAllImageList: " + c.getString(2))
                allImageList.add(c.getString(0))
            }
            c.close()
        }
        return allImageList
    }


    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "custom_image_picker")
            channel.setMethodCallHandler(CustomImagePickerPlugin(registrar.activity(), channel, registrar))
        }
    }

}
