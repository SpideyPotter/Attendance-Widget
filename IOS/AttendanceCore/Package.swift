// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AttendanceCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v13),
    ],
    products: [
        .library(name: "AttendanceCore", targets: ["AttendanceCore"]),
    ],
    targets: [
        .target(name: "AttendanceCore"),
        .testTarget(
            name: "AttendanceCoreTests",
            dependencies: ["AttendanceCore"],
            resources: [.process("Fixtures")]
        ),
    ]
)
