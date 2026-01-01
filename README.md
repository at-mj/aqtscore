# AQT Score - Android Target Scoring App

An Android application for automatically scoring Project Appleseed AQT (Army Qualification Test) targets using computer vision.

## Features

- **Image Capture**: Take photos of shot targets using the device camera
- **Image Selection**: Import target images from gallery
- **Bullet Hole Detection**: Automatically detects bullet holes using OpenCV
- **Visual Overlay**: Highlights detected holes with .30 caliber circles
- **AQT Scoring**: Scores targets based on Project Appleseed AQT 25m specifications (5-4-3 point zones)
- **Scoring Zone Overlay**: Displays the three scoring rings on the annotated image
- **Real-time Display**: Shows annotated image with hole markers and scores

## Technology Stack

- **Language**: Kotlin
- **Computer Vision**: OpenCV 4.8.0
- **Architecture**: Activity-based with coroutines for background processing
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## How It Works

1. **Image Acquisition**: User captures or selects a target image
2. **Preprocessing**: Image is converted to grayscale and noise-reduced
3. **Circle Detection**: Hough Circle Transform identifies circular bullet holes
4. **AQT Scoring**: Each hole is scored based on Project Appleseed AQT specifications
5. **Annotation**: Image is annotated with:
   - AQT scoring zone overlays (5pt, 4pt, 3pt rings)
   - Green circles around detected holes
   - Yellow .30 caliber circles (7.62mm diameter)
   - Red crosshairs at hole centers
   - Score labels for each hole with background
   - Red marker at target center

## Detection Algorithm

The app uses OpenCV's Hough Circle Transform with the following pipeline:

1. Convert to grayscale
2. Apply Gaussian blur (9x9 kernel)
3. Run Hough Circles with tuned parameters:
   - Min radius: 5px
   - Max radius: 40px
   - Edge detection thresholds optimized for holes

## Scoring System - Project Appleseed AQT (25m)

The app uses the official Project Appleseed AQT target specifications for 25 meters:

### Target Dimensions
- **Center black circle**: 1" diameter (25.4mm)
- **Inner white ring**: 1"-2" diameter (25.4mm-50.8mm)
- **Outer black ring**: 2"-4" diameter (50.8mm-101.6mm)

### Scoring Zones
- **5 points**: Center black circle (1" diameter)
- **4 points**: Inner white ring (between 1"-2" diameter)
- **3 points**: Outer black ring (between 2"-4" diameter)
- **0 points**: Outside all scoring rings

### Scoring Rules
- **Edge Breaking**: A shot counts for the higher value if any part of the bullet hole touches or breaks the scoring line (following standard competitive shooting rules)
- The algorithm accounts for .30 caliber bullet diameter (7.62mm) when determining which zone the edge of the hole reaches
- Each of the 4 AQT targets on a standard sheet has a maximum score of 25 points (5 shots × 5 points max)
- Total AQT score: 100 points maximum (4 targets × 25 points)

## Building the App

### Prerequisites
- Android Studio Hedgehog or later
- JDK 8 or later
- Android SDK with API level 34

### Steps
1. Clone the repository
2. Open project in Android Studio
3. Sync Gradle files
4. Build and run on device or emulator

```bash
./gradlew assembleDebug
```

## Permissions Required

- **CAMERA**: For capturing target photos
- **READ_MEDIA_IMAGES** (Android 13+) / **READ_EXTERNAL_STORAGE** (Android 12 and below): For selecting images from gallery

## Configuration & Tuning

### Detection Parameters
Adjust these constants in `BulletHoleDetector.kt` to tune detection:

```kotlin
MIN_HOLE_RADIUS = 5          // Minimum hole size in pixels
MAX_HOLE_RADIUS = 40         // Maximum hole size in pixels
HOUGH_CIRCLES_PARAM2 = 30.0  // Lower = more detections, more false positives
```

### Caliber Circle Size
The .30 caliber circle radius is set in:
```kotlin
DEFAULT_CALIBER_RADIUS_PX = 15f  // Adjust based on image resolution
```

### AQT Target Zone Ratios
The scoring zones are calculated as ratios of the detected target diameter:
```kotlin
AQT_CENTER_RATIO = 0.125    // 1" center / 8" total diameter
AQT_INNER_RATIO = 0.25      // 2" inner ring / 8" total diameter
AQT_OUTER_RATIO = 0.50      // 4" outer ring / 8" total diameter
```

Modify these in `BulletHoleDetector.kt:56-58` if using a different target configuration.

## About Project Appleseed AQT

The Army Qualification Test (AQT) is used by [Project Appleseed](https://appleseedinfo.org/) to teach and assess rifle marksmanship skills. The AQT simulates the historic military qualification course and is shot at scaled distances:
- **Stage 1**: 4 targets, standing position, 2 minutes
- **Stage 2**: 4 targets, sitting/kneeling position, 55 seconds
- **Stage 3**: 4 targets, prone position, 65 seconds
- **Stage 4**: 4 targets, prone position, 5 minutes

Each target consists of 3 concentric scoring rings. A passing score (Rifleman patch) requires 210+ points out of 250 total.

## Future Enhancements

- [ ] Automatic detection of individual AQT targets on full sheet
- [ ] Multi-target batch scoring (all 4 targets at once)
- [ ] Calibration mode for accurate measurements
- [ ] Support for different calibers (.223, .308, etc.)
- [ ] Machine learning-based hole detection
- [ ] Score history and statistics tracking
- [ ] Export results as PDF/image with breakdown
- [ ] Support for different AQT distances (100m, 200m, 300m, 400m)
- [ ] Custom scoring zone configuration
- [ ] Stage-by-stage scoring breakdown

## Troubleshooting

**Issue**: Holes not detected
- Ensure good lighting and focus when photographing target
- Target should fill most of the frame (80-90%)
- Photograph target square-on to avoid perspective distortion
- Adjust `HOUGH_CIRCLES_PARAM2` (lower value = more sensitive, default: 30.0)

**Issue**: False positives (non-holes detected)
- Increase `HOUGH_CIRCLES_PARAM2` to be more strict
- Adjust `MIN_HOLE_RADIUS` and `MAX_HOLE_RADIUS` range
- Ensure clean target background (minimize creases, marks, shadows)

**Issue**: Scoring seems inaccurate
- Verify target is centered in the frame
- Check that the full target (all scoring rings) is visible
- Target size estimation assumes ~90% frame fill - adjust if needed
- For best accuracy, photograph individual targets rather than full sheet

**Issue**: OpenCV initialization fails
- Check that OpenCV dependency is properly included in build.gradle
- Verify device has sufficient resources
- Try clearing app cache and rebuilding

## License

This project is provided as-is for educational and personal use.

## Contributing

Feel free to submit issues and enhancement requests!
