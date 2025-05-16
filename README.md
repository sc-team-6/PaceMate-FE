# PaceMate - Smartphone Usage Monitor

PaceMate is an Android application that helps users monitor and manage their smartphone usage patterns. It provides real-time insights about screen time, scroll behavior, and app usage to promote healthier digital habits.

## Features

- **Usage Dashboard**: Clear visualization of smartphone usage metrics including screen time, scroll rate, unlock count, and app switches
- **Usage Alert**: Receive notifications when your usage exceeds personalized thresholds
- **Usage Ranking**: Compare your smartphone usage with other users
- **Adaptive Recommendations**: Personalized suggestions to improve usage habits based on your patterns

## Technical Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **UI Components**: 
  - Jetpack Compose for modern UI elements
  - Traditional XML layouts for certain screens
- **Machine Learning**:
  - On-device inference with ONNX Runtime
  - Custom models for predicting usage patterns and addiction probability
- **Data Storage**:
  - DataStore Preferences for user settings
  - Room Database for usage history (planned)
- **Networking**:
  - Retrofit for API communication
  - OkHttp for HTTP client

## Implementation Details

### Usage Data Collection

The application collects the following usage data:
- Screen time duration
- Scroll pixel distance and rate
- Screen unlock frequency
- App switching patterns
- App usage categorization

This data is collected through Android's UsageStatsManager and custom Accessibility Service to track scrolling behavior.

### Personalized Thresholds

During onboarding, users provide information about:
- Typical screen time habits
- Usage reduction goals
- Sleep schedule

These inputs help create personalized usage thresholds that adapt to individual lifestyles.

### Machine Learning Integration

The app uses on-device machine learning to:
- Predict addiction probability
- Analyze usage patterns
- Categorize app usage behavior
- Provide targeted recommendations

Models are deployed using ONNX Runtime for efficient on-device inference.

## Privacy Considerations

- All usage data is processed and stored on-device
- No personal data is uploaded to servers without explicit consent
- API communications are limited to anonymous usage comparisons
- Permission requests are clearly explained and minimized

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/gdg/scrollmanager/
│   │   │   ├── adapters/         # RecyclerView and ViewPager adapters
│   │   │   ├── api/              # API service interfaces 
│   │   │   ├── fragments/        # UI fragments for various screens
│   │   │   ├── gemma/            # ML model integration
│   │   │   ├── models/           # Data models and entities
│   │   │   ├── ml/               # Machine learning predictors
│   │   │   ├── services/         # Background services
│   │   │   ├── utils/            # Utility classes
│   │   │   ├── views/            # Custom view components
│   │   │   └── activities        # Main entry points
│   │   ├── res/                  # UI resources 
│   │   └── assets/               # ML models and data
│   └── androidTest/              # Instrumented tests
└── build.gradle                  # Build configuration
```

## Setup Instructions

1. Clone the repository:
   ```
   git clone https://github.com/username/PaceMate.git
   ```

2. Open the project in Android Studio

3. Build and run the application on a device or emulator

4. Required permissions:
   - Usage Stats permission (for tracking app usage)
   - Accessibility Service permission (for tracking scrolling)

## Future Enhancements

- Detailed usage analytics and trend visualization
- Social features for community-based motivation
- Integration with digital wellbeing APIs
- Support for wearable devices to track usage across platforms
- Enhanced machine learning models for more accurate predictions

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
