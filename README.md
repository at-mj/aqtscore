# AQT Score - Android Target Scoring App

An Android application for automatically scoring shooting targets using computer vision.

## Features

- **Image Capture**: Take photos of shot targets using the device camera
- **Image Selection**: Import target images from gallery
- **Bullet Hole Detection**: Automatically detects bullet holes using OpenCV
- **Visual Overlay**: Highlights detected holes with .30 caliber circles
- **Automatic Scoring**: Scores targets based on bullseye distance (10-0 point scale)
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
4. **Scoring**: Each hole is scored based on distance from bullseye center
5. **Annotation**: Image is annotated with:
   - Green circles around detected holes
   - Yellow .30 caliber circles (7.62mm diameter)
   - Red crosshairs at hole centers
   - Score labels for each hole
   - Red marker at target center

## Detection Algorithm

The app uses OpenCV's Hough Circle Transform with the following pipeline:

1. Convert to grayscale
2. Apply Gaussian blur (9x9 kernel)
3. Run Hough Circles with tuned parameters:
   - Min radius: 5px
   - Max radius: 40px
   - Edge detection thresholds optimized for holes

## Scoring System

Standard bullseye scoring with 11 zones:
- **10 points**: Center ring
- **9-1 points**: Progressively outer rings
- **0 points**: Outside scoring area

Score is calculated based on radial distance from target center.

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

### Scoring Rings
Modify the `calculateScore()` function to change ring widths and point values.

## Future Enhancements

- [ ] Automatic target type detection (bullseye vs silhouette)
- [ ] Calibration mode for accurate measurements
- [ ] Support for different calibers
- [ ] Machine learning-based hole detection
- [ ] Score history and statistics
- [ ] Export results as PDF/image
- [ ] Multi-target analysis
- [ ] Custom scoring zone configuration

## Troubleshooting

**Issue**: Holes not detected
- Ensure good lighting and focus
- Target should fill most of the frame
- Adjust `HOUGH_CIRCLES_PARAM2` (lower value = more sensitive)

**Issue**: False positives (non-holes detected)
- Increase `HOUGH_CIRCLES_PARAM2`
- Adjust `MIN_HOLE_RADIUS` and `MAX_HOLE_RADIUS`

**Issue**: OpenCV initialization fails
- Check that OpenCV dependency is properly included
- Verify device has sufficient resources

## License

This project is provided as-is for educational and personal use.

## Contributing

Feel free to submit issues and enhancement requests!
