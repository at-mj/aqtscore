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

        // Project Appleseed AQT Target specifications (25m scaled target)
        // The AQT target has four circles with the following diameters (in MOA):
        // - Center bullseye: 4 MOA (approx 1.05" or 26.67mm at 25m)
        // - Inner ring: extends to 8 MOA (approx 2.09" or 53.34mm at 25m)
        // - Outer scoring ring: 16 MOA diameter (approx 4.19" or 106.68mm at 25m)
        // Actual printed AQT 25m target dimensions:
        // - Black center circle: 1" diameter (25.4mm)
        // - Inner white ring edge: 2" diameter (50.8mm)
        // - Outer black ring edge: 4" diameter (101.6mm)

        // These will be calculated as ratios of the detected target size
        private const val AQT_CENTER_RATIO = 0.125    // 1" / 8" target diameter
        private const val AQT_INNER_RATIO = 0.25      // 2" / 8" target diameter
        private const val AQT_OUTER_RATIO = 0.50      // 4" / 8" target diameter
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
     * Calculates score based on Project Appleseed AQT target specifications
     * AQT Scoring (25m target):
     * - 5 points: Center black circle (1" diameter)
     * - 4 points: Inner white ring (1"-2" diameter)
     * - 3 points: Outer black ring (2"-4" diameter)
     * - 0 points: Outside scoring rings
     *
     * Note: Shots that "break" or touch a scoring line count for the higher value
     */
    private fun calculateScore(
        hole: BulletHole,
        targetCenter: Point,
        imageWidth: Int,
        imageHeight: Int
    ): Int {
        val dx = hole.x - targetCenter.x
        val dy = hole.y - targetCenter.y
        val distanceFromCenter = sqrt(dx.pow(2) + dy.pow(2))

        // Estimate target diameter based on image size
        // Assuming the target fills most of the frame
        val estimatedTargetDiameter = minOf(imageWidth, imageHeight) * 0.9

        // Calculate actual zone radii based on AQT ratios
        val centerRadius = estimatedTargetDiameter * AQT_CENTER_RATIO
        val innerRadius = estimatedTargetDiameter * AQT_INNER_RATIO
        val outerRadius = estimatedTargetDiameter * AQT_OUTER_RATIO

        // Account for bullet diameter when scoring (edge of bullet breaks the line)
        // .30 caliber = 7.62mm diameter
        val bulletRadius = hole.radius.toDouble()

        // Calculate edge-to-center distance (closest edge of bullet to center)
        val edgeDistance = distanceFromCenter - bulletRadius

        // Score based on which zone the bullet edge reaches
        val score = when {
            edgeDistance <= centerRadius -> 5      // Center black (5 points)
            edgeDistance <= innerRadius -> 4       // Inner white ring (4 points)
            edgeDistance <= outerRadius -> 3       // Outer black ring (3 points)
            else -> 0                               // Miss (0 points)
        }

        Log.d(TAG, "Hole at (${hole.x}, ${hole.y}), distance: $distanceFromCenter, " +
                "edge distance: $edgeDistance, centerR: $centerRadius, " +
                "innerR: $innerRadius, outerR: $outerRadius, score: $score")
        return score
    }

    /**
     * Draws annotations on the target image including AQT scoring zones
     */
    private fun drawAnnotations(
        mat: Mat,
        bulletHoles: List<BulletHole>,
        targetCenter: Point,
        scores: List<Int>
    ) {
        // Calculate AQT zone radii for visualization
        val estimatedTargetDiameter = minOf(mat.width(), mat.height()) * 0.9
        val centerRadius = estimatedTargetDiameter * AQT_CENTER_RATIO
        val innerRadius = estimatedTargetDiameter * AQT_INNER_RATIO
        val outerRadius = estimatedTargetDiameter * AQT_OUTER_RATIO

        // Draw AQT scoring zones (semi-transparent overlays)
        // Outer ring (3 points) - cyan
        Imgproc.circle(mat, targetCenter, outerRadius.toInt(), Scalar(255.0, 255.0, 0.0), 2)

        // Inner ring (4 points) - blue
        Imgproc.circle(mat, targetCenter, innerRadius.toInt(), Scalar(255.0, 128.0, 0.0), 2)

        // Center circle (5 points) - red
        Imgproc.circle(mat, targetCenter, centerRadius.toInt(), Scalar(255.0, 0.0, 0.0), 2)

        // Draw target center marker
        Imgproc.circle(mat, targetCenter, 5, Scalar(255.0, 0.0, 0.0), -1)
        Imgproc.circle(mat, targetCenter, 10, Scalar(255.0, 0.0, 0.0), 2)

        // Add zone labels
        Imgproc.putText(
            mat,
            "5pts",
            Point(targetCenter.x + centerRadius + 5, targetCenter.y),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.5,
            Scalar(255.0, 255.0, 255.0),
            2
        )
        Imgproc.putText(
            mat,
            "4pts",
            Point(targetCenter.x + innerRadius + 5, targetCenter.y),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.5,
            Scalar(255.0, 255.0, 255.0),
            2
        )
        Imgproc.putText(
            mat,
            "3pts",
            Point(targetCenter.x + outerRadius + 5, targetCenter.y),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.5,
            Scalar(255.0, 255.0, 255.0),
            2
        )

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

            // Draw score label with background for visibility
            if (index < scores.size) {
                val text = "${scores[index]}pt"
                val textPos = Point(hole.x + 20.0, hole.y - 20.0)

                // Draw text background (black rectangle)
                val textSize = Imgproc.getTextSize(text, Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, 2, intArrayOf(0))
                Imgproc.rectangle(
                    mat,
                    Point(textPos.x - 2, textPos.y - textSize.height - 2),
                    Point(textPos.x + textSize.width + 2, textPos.y + 4),
                    Scalar(0.0, 0.0, 0.0),
                    -1
                )

                // Draw score text (white)
                Imgproc.putText(
                    mat,
                    text,
                    textPos,
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.7,
                    Scalar(255.0, 255.0, 255.0),
                    2
                )
            }
        }
    }
}
