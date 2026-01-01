package com.aqtscore

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.sqrt

data class BulletHole(
    val x: Float,
    val y: Float,
    val radius: Float
)

data class TargetAnalysisResult(
    val annotatedBitmap: Bitmap,
    val bulletHoles: List<BulletHole>,
    val scores: List<Int>,
    val totalScore: Int,
    val targetCenter: Point?
)

class BulletHoleDetector {

    companion object {
        private const val TAG = "BulletHoleDetector"

        // Caliber size in pixels (will need calibration based on image resolution)
        // .30 caliber = 7.62mm = 0.30 inches
        // This is a default that should be adjusted based on actual target distance/resolution
        private const val DEFAULT_CALIBER_RADIUS_PX = 15f

        // Detection parameters
        private const val MIN_HOLE_RADIUS = 5
        private const val MAX_HOLE_RADIUS = 40
        private const val CANNY_THRESHOLD1 = 50.0
        private const val CANNY_THRESHOLD2 = 150.0
        private const val HOUGH_CIRCLES_DP = 1.2
        private const val HOUGH_CIRCLES_MIN_DIST = 20.0
        private const val HOUGH_CIRCLES_PARAM1 = 100.0
        private const val HOUGH_CIRCLES_PARAM2 = 30.0
    }

    /**
     * Detects bullet holes in a target image and returns analysis results
     */
    fun analyzeTarget(originalBitmap: Bitmap): TargetAnalysisResult {
        val startTime = System.currentTimeMillis()

        // Convert bitmap to Mat
        val originalMat = Mat()
        Utils.bitmapToMat(originalBitmap, originalMat)

        // Convert to grayscale
        val grayMat = Mat()
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGB2GRAY)

        // Apply Gaussian blur to reduce noise
        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(9.0, 9.0), 2.0, 2.0)

        // Detect circles using Hough Circle Transform
        val circles = Mat()
        Imgproc.HoughCircles(
            blurredMat,
            circles,
            Imgproc.HOUGH_GRADIENT,
            HOUGH_CIRCLES_DP,
            HOUGH_CIRCLES_MIN_DIST,
            HOUGH_CIRCLES_PARAM1,
            HOUGH_CIRCLES_PARAM2,
            MIN_HOLE_RADIUS,
            MAX_HOLE_RADIUS
        )

        Log.d(TAG, "Detected ${circles.cols()} potential bullet holes")

        // Extract bullet holes
        val bulletHoles = mutableListOf<BulletHole>()
        for (i in 0 until circles.cols()) {
            val circle = circles.get(0, i)
            bulletHoles.add(
                BulletHole(
                    x = circle[0].toFloat(),
                    y = circle[1].toFloat(),
                    radius = circle[2].toFloat()
                )
            )
        }

        // Find target center (assume it's the center of the image or detect bullseye)
        val targetCenter = findTargetCenter(originalMat, bulletHoles)

        // Calculate scores for each hole
        val scores = bulletHoles.map { hole ->
            calculateScore(hole, targetCenter, originalMat.width(), originalMat.height())
        }

        // Create annotated image
        val annotatedMat = originalMat.clone()
        drawAnnotations(annotatedMat, bulletHoles, targetCenter, scores)

        // Convert back to bitmap
        val annotatedBitmap = Bitmap.createBitmap(
            annotatedMat.cols(),
            annotatedMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(annotatedMat, annotatedBitmap)

        // Cleanup
        originalMat.release()
        grayMat.release()
        blurredMat.release()
        circles.release()
        annotatedMat.release()

        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Analysis completed in ${totalTime}ms")

        return TargetAnalysisResult(
            annotatedBitmap = annotatedBitmap,
            bulletHoles = bulletHoles,
            scores = scores,
            totalScore = scores.sum(),
            targetCenter = targetCenter
        )
    }

    /**
     * Attempts to find the target center
     * Currently uses image center, but could be enhanced to detect bullseye
     */
    private fun findTargetCenter(mat: Mat, bulletHoles: List<BulletHole>): Point {
        // Simple approach: use image center
        // TODO: Could enhance this to detect actual bullseye circle
        return Point(mat.width() / 2.0, mat.height() / 2.0)
    }

    /**
     * Calculates score based on distance from target center
     * Standard bullseye scoring: 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
     */
    private fun calculateScore(
        hole: BulletHole,
        targetCenter: Point,
        imageWidth: Int,
        imageHeight: Int
    ): Int {
        val dx = hole.x - targetCenter.x
        val dy = hole.y - targetCenter.y
        val distance = sqrt(dx.pow(2) + dy.pow(2))

        // Estimate target radius based on image size
        val targetRadius = minOf(imageWidth, imageHeight) / 2.0

        // Divide target into 11 zones (10 to 0)
        val ringWidth = targetRadius / 11.0

        val score = when {
            distance <= ringWidth -> 10
            distance <= ringWidth * 2 -> 9
            distance <= ringWidth * 3 -> 8
            distance <= ringWidth * 4 -> 7
            distance <= ringWidth * 5 -> 6
            distance <= ringWidth * 6 -> 5
            distance <= ringWidth * 7 -> 4
            distance <= ringWidth * 8 -> 3
            distance <= ringWidth * 9 -> 2
            distance <= ringWidth * 10 -> 1
            else -> 0
        }

        Log.d(TAG, "Hole at (${hole.x}, ${hole.y}), distance: $distance, score: $score")
        return score
    }

    /**
     * Draws annotations on the target image
     */
    private fun drawAnnotations(
        mat: Mat,
        bulletHoles: List<BulletHole>,
        targetCenter: Point,
        scores: List<Int>
    ) {
        // Draw target center
        Imgproc.circle(mat, targetCenter, 5, Scalar(255.0, 0.0, 0.0), -1)
        Imgproc.circle(mat, targetCenter, 10, Scalar(255.0, 0.0, 0.0), 2)

        // Draw each bullet hole with .30 caliber circle
        bulletHoles.forEachIndexed { index, hole ->
            val center = Point(hole.x.toDouble(), hole.y.toDouble())

            // Draw detected hole (green)
            Imgproc.circle(mat, center, hole.radius.toInt(), Scalar(0.0, 255.0, 0.0), 2)

            // Draw .30 caliber circle (yellow)
            Imgproc.circle(
                mat,
                center,
                DEFAULT_CALIBER_RADIUS_PX.toInt(),
                Scalar(255.0, 255.0, 0.0),
                2
            )

            // Draw crosshair at center
            val crosshairSize = 8
            Imgproc.line(
                mat,
                Point(hole.x - crosshairSize, hole.y.toDouble()),
                Point(hole.x + crosshairSize, hole.y.toDouble()),
                Scalar(255.0, 0.0, 0.0),
                2
            )
            Imgproc.line(
                mat,
                Point(hole.x.toDouble(), hole.y - crosshairSize),
                Point(hole.x.toDouble(), hole.y + crosshairSize),
                Scalar(255.0, 0.0, 0.0),
                2
            )

            // Draw score label
            if (index < scores.size) {
                Imgproc.putText(
                    mat,
                    scores[index].toString(),
                    Point(hole.x + 20.0, hole.y - 20.0),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.7,
                    Scalar(255.0, 255.0, 255.0),
                    2
                )
            }
        }
    }
}
