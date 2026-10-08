// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "TraccarClientSDK", platforms: [.iOS(.v15)],
    products: [.library(name: "TraccarClientSDK", targets: ["TraccarClientSDK"])],
    targets: [.binaryTarget(name: "TraccarClientSDK", url: "https://github.com/adriaandotcom/traccar-client-sdk/releases/download/v1.1.1-places.1/TraccarClientSDK-1.1.1-places.1.xcframework.zip", checksum: "276ab28997956eef0be78350d31108345e1494c350335710f138401243cb1a97")])
