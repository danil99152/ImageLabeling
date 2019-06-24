package com.example.imagelabeling

import android.graphics.*
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.Facing
import com.otaliastudios.cameraview.SessionType
import kotlinx.android.synthetic.main.content_image_labeling.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.FirebaseApp
import java.util.*
import kotlin.collections.ArrayList


class ImageLabelingActivity : AppCompatActivity() {

    private val imageView by lazy { findViewById<ImageView>(R.id.image_labeling_image_view)!! }

    private val barcodeornot by lazy {findViewById<Switch>(R.id.barcodeornot)}

    private var cameraFacing: Facing = Facing.BACK
    private val bottomSheetButton by lazy { findViewById<FrameLayout>(R.id.bottom_sheet_button)!! }
    private val bottomSheetRecyclerView by lazy { findViewById<RecyclerView>(R.id.bottom_sheet_recycler_view)!! }
    private val bottomSheetBehavior by lazy { BottomSheetBehavior.from(findViewById(R.id.bottom_sheet)!!) }

    private val barcodeScanningModels = ArrayList<BarcodeScanningModel>()
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
        if (barcodeornot != null) {
            analyzeBarcode(image)
        } else {

            imageView.setImageBitmap(null)
            imageLabelingModels.clear()
            bottomSheetRecyclerView.adapter?.notifyDataSetChanged()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            showProgress()

            val firebaseVisionImage = FirebaseVisionImage.fromBitmap(image)
            val options = FirebaseVisionOnDeviceImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.7F)
                .build()

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

    private fun analyzeBarcode(image: Bitmap?) {
        if (image == null) {
            Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
            return
        }

        imageView.setImageBitmap(null)
        barcodeScanningModels.clear()
        bottomSheetRecyclerView.adapter?.notifyDataSetChanged()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        showProgress()

        val firebaseVisionImage = FirebaseVisionImage.fromBitmap(image)
        val barcodeDetector = FirebaseVision.getInstance().visionBarcodeDetector
        barcodeDetector.detectInImage(firebaseVisionImage)
            .addOnSuccessListener {
                val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)

                scanBarcode(it, mutableImage)

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

    //получаем точку входа для базы данных
    private val mFirebaseDatabase = FirebaseDatabase.getInstance()
    //получаем ссылку для работы с базой данных
    private val mDatabaseReference = mFirebaseDatabase.getReference()
    private fun scanBarcode(barcodes: List<FirebaseVisionBarcode>?, image: Bitmap?) {
        if (barcodes == null || image == null) {
            Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
            return
        }

        val canvas = Canvas(image)
        val rectPaint = Paint()
        rectPaint.color = Color.RED
        rectPaint.style = Paint.Style.STROKE
        rectPaint.strokeWidth = 4F
        val textPaint = Paint()
        textPaint.color = Color.RED
        textPaint.textSize = 60F
        textPaint.typeface = Typeface.DEFAULT_BOLD

        //инициализируем наше приложение для Firebase согласно параметрам в google-services.json
        // (google-services.json - файл, с настройками для firebase, кот. мы получили во время регистрации)
        FirebaseApp.initializeApp(this)
        for ((index, barcode) in barcodes.withIndex()) {

            canvas.drawRect(barcode.boundingBox, rectPaint)
            canvas.drawText(index.toString(), barcode.cornerPoints!![2].x.toFloat(), barcode.cornerPoints!![2].y.toFloat(), textPaint)
            barcodeScanningModels.add(BarcodeScanningModel(index, barcode.rawValue))
            val name = mDatabaseReference.child("Barcode")
                .child(barcode.rawValue.toString())
                .child("name").toString()
            Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
        }
    }

    private val input_name = findViewById<EditText>(R.id.name)
    private val input_id = findViewById<EditText>(R.id.id)

    private fun createBarcode() {
    //создаем элемент класса DB
    val db  = DB(input_name.getText().toString(), //берем данные имени и значения баркода из полей ввода
        input_id.getText().toString())
    //сохраняем данные в базе данных Firebase по пути Barcode
    mDatabaseReference.child("Barcode").setValue(db)
    //очищаем поля ввода
    clearEditText()
    }
    private fun clearEditText() {
    input_id.setText("")
    input_name.setText("")
    }

    private fun deleteBarcode(selectedBarcode : DB) {
        mDatabaseReference.child("Barcode")
            .child(selectedBarcode.name)
            .child(selectedBarcode.id)
            .removeValue()
        clearEditText()
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