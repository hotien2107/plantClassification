package com.example.plantclassification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var textView: TextView
    private lateinit var captureButton: Button
    private lateinit var galleryButton: Button
    private lateinit var tflite: Interpreter
    private val labels = listOf("Cây 1", "Cây 2", "Cây 3") // Thay thế bằng danh sách nhãn của bạn
    private val imageSize = 224

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Thay thế bằng layout của bạn

        imageView = findViewById(R.id.imageView)
        textView = findViewById(R.id.textView)
        captureButton = findViewById(R.id.captureButton)
        galleryButton = findViewById(R.id.galleryButton)

        // Khởi tạo TensorFlow Lite Interpreter
        tflite = Interpreter(loadModelFile(this, "leaf_recognition_model.tflite"))

        // Yêu cầu quyền truy cập camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_PERMISSION_CAMERA
            )
        }

        // Xử lý sự kiện click nút chụp ảnh
        captureButton.setOnClickListener {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE)
        }

        // Xử lý sự kiện click nút chọn ảnh từ thư viện
        galleryButton.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, REQUEST_IMAGE_PICK)
        }
    }

    // Hàm nạp model từ assets
    private fun loadModelFile(activity: AppCompatActivity, filename: String): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Hàm xử lý ảnh và chạy model
    private fun classifyImage(bitmap: Bitmap) {
        val resizedImage = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedImage)
        val output = Array(1) { FloatArray(labels.size) }
        tflite.run(byteBuffer, output)

        // Tìm nhãn có xác suất cao nhất
        val predictedIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        val predictedLabel = if (predictedIndex != -1) labels[predictedIndex] else "Không xác định"

        textView.text = "Kết quả: $predictedLabel"
    }

    // Hàm chuyển đổi Bitmap sang ByteBuffer
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(imageSize * imageSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until imageSize) {
            for (j in 0 until imageSize) {
                val `val` = intValues[pixel++]
                byteBuffer.putFloat(((`val` shr 16 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((`val` shr 8 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((`val` and 0xFF) - 127.5f) / 127.5f)
            }
        }
        return byteBuffer
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    imageView.setImageBitmap(imageBitmap)
                    classifyImage(imageBitmap)
                }
                REQUEST_IMAGE_PICK -> {
                    val imageUri = data?.data
                    val imageBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri!!))
                    imageView.setImageBitmap(imageBitmap)
                    classifyImage(imageBitmap)
                }
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSION_CAMERA = 100
        private const val REQUEST_IMAGE_CAPTURE = 101
        private const val REQUEST_IMAGE_PICK = 102
    }
}