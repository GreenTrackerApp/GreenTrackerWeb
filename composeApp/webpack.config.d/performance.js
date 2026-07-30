// Custom Webpack configuration to handle Kotlin/Wasm + Compose asset sizes
// Skiko/Skia based Wasm applications naturally exceed the default 244KiB limit.
config.performance = {
    hints: false,
    maxEntrypointSize: 15 * 1024 * 1024,
    maxAssetSize: 15 * 1024 * 1024
};
