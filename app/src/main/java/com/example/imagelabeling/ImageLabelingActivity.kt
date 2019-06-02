package com.example.imagelabeling

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.Facing
import com.otaliastudios.cameraview.SessionType
import kotlinx.android.synthetic.main.content_image_labeling.*

class ImageLabelingActivity : AppCompatActivity() {

    private val imageView by lazy { findViewById<ImageView>(R.id.image_labeling_image_view)!! }

    private var cameraFacing: Facing = Facing.BACK
    private val bottomSheetButton by lazy { findViewById<FrameLayout>(R.id.bottom_sheet_button)!! }
    private val bottomSheetRecyclerView by lazy { findViewById<RecyclerView>(R.id.bottom_sheet_recycler_view)!! }
    private val bottomSheetBehavior by lazy { BottomSheetBehavior.from(findViewById(R.id.bottom_sheet)!!) }

    private val imageLabelingModels = ArrayList<ImageLabelingModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_labeling)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        cameraView.sessionType = SessionType.PICTURE
        cameraView.facing = cameraFacing
        cameraView.setLifecycleOwner(this)
        cameraView.addCameraListener(object: CameraListener() {
            override fun onPictureTaken(jpeg: ByteArray?) {
                analyzeImage(BitmapFactory.decodeByteArray(jpeg, 0, jpeg?.size ?: 0))
            }
        })

        bottomSheetButton.setOnClickListener {
            cameraView.capturePicture()
        }

        bottomSheetRecyclerView.layoutManager = LinearLayoutManager(this)
        bottomSheetRecyclerView.adapter = ImageLabelingAdapter(this, imageLabelingModels)
    }

    private fun analyzeImage(image: Bitmap?) {
        if (image == null) {
            Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
            return
        }

        imageView.setImageBitmap(null)
        imageLabelingModels.clear()
        bottomSheetRecyclerView.adapter?.notifyDataSetChanged()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        showProgress()

        val firebaseVisionImage = FirebaseVisionImage.fromBitmap(image)
        val options = FirebaseVisionOnDeviceImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7F)
            .build()
        //TODO tut bag
        val labelDetector = FirebaseVision.getInstance().getOnDeviceImageLabeler(options)
        labelDetector.processImage(firebaseVisionImage)
            .addOnSuccessListener {
                val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)

                labelImage(it, mutableImage)

                imageView.setImageBitmap(mutableImage)
                hideProgress()
                bottomSheetRecyclerView.adapter?.notifyDataSetChanged()
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            .addOnFailureListener {
                Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
                hideProgress()
            }
    }

    private fun labelImage(labels: List<FirebaseVisionImageLabel>?, image: Bitmap?) {
        if (labels == null || image == null) {
            Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
            return
        }

        for (label in labels) {

            imageLabelingModels.add(ImageLabelingModel(label.text, label.confidence))
        }
    }

    private fun showProgress() {
        findViewById<View>(R.id.bottom_sheet_button_image).visibility = View.GONE
        findViewById<View>(R.id.bottom_sheet_button_progress).visibility = View.VISIBLE
    }

    private fun hideProgress() {
        findViewById<View>(R.id.bottom_sheet_button_image).visibility = View.VISIBLE
        findViewById<View>(R.id.bottom_sheet_button_progress).visibility = View.GONE
    }

}