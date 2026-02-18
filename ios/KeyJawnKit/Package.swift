// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "KeyJawnKit",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "KeyJawnKit", targets: ["KeyJawnKit"]),
    ],
    targets: [
        .target(
            name: "KeyJawnKit",
            path: "Sources/KeyJawnKit",
            swiftSettings: [
                .enableExperimentalFeature("StrictConcurrency"),
            ]
        ),
    ]
)
