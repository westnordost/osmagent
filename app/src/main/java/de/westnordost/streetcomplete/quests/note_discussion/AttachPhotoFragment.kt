package de.westnordost.streetcomplete.quests.note_discussion

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.ApplicationConstants.*
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osmnotes.deleteImages
import de.westnordost.streetcomplete.ktx.toast
import de.westnordost.streetcomplete.util.AdapterDataChangedWatcher
import de.westnordost.streetcomplete.util.decodeScaledBitmapAndNormalize
import kotlinx.android.synthetic.main.fragment_attach_photo.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class AttachPhotoFragment : Fragment() {

    val imagePaths: List<String> get() = noteImageAdapter.list
    private var photosListView : RecyclerView? = null
    private var hintView : TextView? = null

    private var currentImagePath: String? = null

    private lateinit var noteImageAdapter: NoteImageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_attach_photo, container, false)

        if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            view.visibility = View.GONE
        }
        photosListView = view.findViewById(R.id.gridView)
        hintView = view.findViewById(R.id.photosAreUsefulExplanation)
        return view
    }

    private fun updateHintVisibility(){
        val isImagePathsEmpty = imagePaths.isEmpty()
        photosListView?.isGone = isImagePathsEmpty
        hintView?.isGone = !isImagePathsEmpty
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        takePhotoButton.setOnClickListener { takePhoto() }

        val paths: ArrayList<String>
        if (savedInstanceState != null) {
            paths = savedInstanceState.getStringArrayList(PHOTO_PATHS)!!
            currentImagePath = savedInstanceState.getString(CURRENT_PHOTO_PATH)
        } else {
            paths = ArrayList()
            currentImagePath = null
        }

        noteImageAdapter = NoteImageAdapter(paths, requireContext())
        gridView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        gridView.adapter = noteImageAdapter
        noteImageAdapter.registerAdapterDataObserver(AdapterDataChangedWatcher { updateHintVisibility() })
        updateHintVisibility()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(PHOTO_PATHS, ArrayList(imagePaths))
        outState.putString(CURRENT_PHOTO_PATH, currentImagePath)
    }

    private fun takePhoto() {
        val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        activity?.packageManager?.let { packageManager ->
            if (takePhotoIntent.resolveActivity(packageManager) != null) {
                try {
                    val photoFile = createImageFile()
                    val photoUri = if (Build.VERSION.SDK_INT > 21) {
                        //Use FileProvider for getting the content:// URI, see: https://developer.android.com/training/camera/photobasics.html#TaskPath
                        FileProvider.getUriForFile(requireContext(),getString(R.string.fileprovider_authority),photoFile)
                    } else {
                        photoFile.toUri()
                    }
                    currentImagePath = photoFile.path
                    takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startActivityForResult(takePhotoIntent, REQUEST_TAKE_PHOTO)
                } catch (e: IOException) {
                    Log.e(TAG, "Unable to create file for photo", e)
                    context?.toast(R.string.quest_leave_new_note_create_image_error)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Unable to create file for photo", e)
                    context?.toast(R.string.quest_leave_new_note_create_image_error)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_TAKE_PHOTO) {
            if (resultCode == RESULT_OK) {
                try {
                    val path = currentImagePath!!
                    val bitmap = decodeScaledBitmapAndNormalize(path, ATTACH_PHOTO_MAXWIDTH, ATTACH_PHOTO_MAXHEIGHT) ?: throw IOException()
                    val out = FileOutputStream(path)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, ATTACH_PHOTO_QUALITY, out)

                    noteImageAdapter.list.add(path)
                    noteImageAdapter.notifyItemInserted(imagePaths.size - 1)
                } catch (e: IOException) {
                    Log.e(TAG, "Unable to rescale the photo", e)
                    context?.toast(R.string.quest_leave_new_note_create_image_error)
                    removeCurrentImage()
                }

            } else {
                removeCurrentImage()
            }
            currentImagePath = null
        }
    }

    private fun removeCurrentImage() {
        currentImagePath?.let {
            val photoFile = File(it)
            if (photoFile.exists()) {
                photoFile.delete()
            }
        }
    }

    private fun createImageFile(): File {
        val directory = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFileName = "photo_" + System.currentTimeMillis() + ".jpg"
        val file = File(directory, imageFileName)
        if(!file.createNewFile()) throw IOException("Photo file with exactly the same name already exists")
        return file
    }

    fun deleteImages() {
        deleteImages(imagePaths)
    }

    companion object {

        private const val TAG = "AttachPhotoFragment"
        private const val REQUEST_TAKE_PHOTO = 1

        private const val PHOTO_PATHS = "photo_paths"
        private const val CURRENT_PHOTO_PATH = "current_photo_path"
    }
}
