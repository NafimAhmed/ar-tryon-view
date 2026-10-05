# ar_tryon_view example

This example intentionally demonstrates only the package's two supported features:

1. Transparent PNG overlay on the native front-camera preview.
2. Android native ARCore placement of a user-selected `.glb` model.

Run:

```bash
flutter pub get
flutter run
```

The example keeps only `assets/glasses_01.png` as a bundled demo asset. GLB models are selected at runtime from the Android document picker.
