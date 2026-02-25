package `in`.cartunez.flow.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.DialogFragment
import java.io.File

class FullScreenImageDialog : DialogFragment() {

    companion object {
        fun newInstance(path: String) = FullScreenImageDialog().apply {
            arguments = Bundle().also { it.putString("path", path) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val path = arguments?.getString("path")
        val iv = ZoomableImageView(requireContext())
        iv.setBackgroundColor(android.graphics.Color.BLACK)
        iv.setOnClickListener { dismiss() }
        if (!path.isNullOrEmpty() && File(path).exists()) {
            iv.fitImage(path)
        }
        return iv
    }
}

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val mMatrix = Matrix()
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                mMatrix.postScale(d.scaleFactor, d.scaleFactor, d.focusX, d.focusY)
                imageMatrix = mMatrix
                return true
            }
        })

    private var lastX = 0f
    private var lastY = 0f

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    mMatrix.postTranslate(event.x - lastX, event.y - lastY)
                    imageMatrix = mMatrix
                }
                lastX = event.x; lastY = event.y
            }
        }
        return true
    }

    fun fitImage(path: String) {
        val bm = BitmapFactory.decodeFile(path) ?: return
        setImageBitmap(bm)
        post {
            if (width == 0 || height == 0) return@post
            val scale = minOf(width.toFloat() / bm.width, height.toFloat() / bm.height)
            mMatrix.reset()
            mMatrix.postScale(scale, scale)
            mMatrix.postTranslate(
                (width - bm.width * scale) / 2f,
                (height - bm.height * scale) / 2f
            )
            imageMatrix = mMatrix
        }
    }
}
