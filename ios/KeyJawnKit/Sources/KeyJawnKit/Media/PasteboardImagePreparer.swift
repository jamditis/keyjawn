import CoreGraphics
import Foundation
import ImageIO
import UIKit
import UniformTypeIdentifiers

/// Prepares a pasteboard image for SCP upload without ever materializing the
/// full-resolution bitmap.
///
/// A keyboard extension runs under a tight jetsam ceiling (roughly 48-70 MB), so
/// decoding a large screenshot to a bitmap (a 3648x5472 photo is about 80 MB
/// decoded, plus the JPEG output buffer) can get the extension killed
/// mid-interaction. ImageIO's thumbnail path decodes straight to a bounded size,
/// so the full bitmap is never allocated.
///
/// Every method here touches only Foundation, ImageIO, and CoreGraphics, all of
/// which are thread-safe, so it is meant to be called off the main actor.
public enum PasteboardImagePreparer {

    /// Decoded-pixel budget for an upload, in total pixels (width times height).
    ///
    /// The decoded bitmap costs 4 bytes per pixel, so a 6 MP budget caps the
    /// decode at about 24 MB, which leaves headroom under the extension's jetsam
    /// ceiling. Bounding total *area* rather than the longest edge is what keeps
    /// tall scrolling screenshots readable: a 1290x6000 grab downsamples to about
    /// 1135x5280 (still legible), where a fixed 2048px longest-edge cap would
    /// have collapsed it to 440x2048.
    public static let defaultMaxPixelCount = 6_000_000

    /// Downsamples encoded image bytes so the decoded bitmap stays within
    /// `maxPixelCount` total pixels, then re-encodes them as JPEG, without ever
    /// decoding the full-resolution bitmap.
    ///
    /// - Parameters:
    ///   - data: raw encoded image bytes (PNG/JPEG/HEIC/...), as read from the
    ///     pasteboard with ``UIKit/UIPasteboard/firstImageData``.
    ///   - maxPixelCount: the decoded-pixel budget (width times height). A
    ///     smaller image is never upscaled.
    ///   - compressionQuality: JPEG quality for the re-encode (0...1).
    /// - Returns: JPEG-encoded `Data`, or `nil` if the bytes are not a decodable
    ///   image.
    public static func downsampledJPEGData(
        from data: Data,
        maxPixelCount: Int = defaultMaxPixelCount,
        compressionQuality: CGFloat = 0.85
    ) -> Data? {
        // The autoreleasepool frees ImageIO's intermediate buffers promptly
        // rather than at the next runloop turn, which matters under jetsam.
        autoreleasepool {
            let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
            guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions) else {
                return nil
            }

            let thumbnailOptions = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                // Bake the EXIF orientation into the pixels so the upload is
                // upright without carrying an orientation tag.
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: maxPixelSize(for: source, maxPixelCount: maxPixelCount),
                kCGImageSourceShouldCacheImmediately: true,
            ] as CFDictionary
            guard let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbnailOptions) else {
                return nil
            }

            let output = NSMutableData()
            guard let destination = CGImageDestinationCreateWithData(
                output as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil
            ) else {
                return nil
            }
            let destinationOptions = [
                kCGImageDestinationLossyCompressionQuality: compressionQuality
            ] as CFDictionary
            CGImageDestinationAddImage(destination, thumbnail, destinationOptions)
            guard CGImageDestinationFinalize(destination) else { return nil }
            return output as Data
        }
    }

    /// The longest-edge bound to hand ImageIO so the decoded bitmap stays within
    /// `maxPixelCount` total pixels, without upscaling a smaller image.
    ///
    /// Reads the stored pixel dimensions from the image metadata, so it does not
    /// decode. `kCGImageSourceThumbnailMaxPixelSize` only bounds the longest
    /// edge, so this converts the area budget into the matching edge value for
    /// the source's actual aspect ratio.
    private static func maxPixelSize(for source: CGImageSource, maxPixelCount: Int) -> Int {
        guard
            let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
            let width = properties[kCGImagePropertyPixelWidth] as? Int,
            let height = properties[kCGImagePropertyPixelHeight] as? Int,
            width > 0, height > 0
        else {
            // Dimensions unknown (very rare for real pasteboard images): fall
            // back to a longest-edge cap derived from the same budget, so
            // maxPixelCount stays the single source of truth for the decode bound.
            return max(1, Int(Double(maxPixelCount).squareRoot().rounded()))
        }

        let longestEdge = max(width, height)
        let totalPixels = width * height
        guard totalPixels > maxPixelCount else { return longestEdge }

        let scale = (Double(maxPixelCount) / Double(totalPixels)).squareRoot()
        return max(1, Int((Double(longestEdge) * scale).rounded()))
    }
}

public extension UIPasteboard {

    /// The raw encoded bytes of the first image on the pasteboard, without
    /// decoding it to a bitmap.
    ///
    /// Reading `UIPasteboard.image` eagerly decodes the full bitmap on the
    /// calling thread; this reads only the stored encoded representation, which
    /// is cheap and safe to grab on the main actor before handing the bytes to a
    /// background decode. Returns `nil` if the pasteboard has no image.
    var firstImageData: Data? {
        guard hasImages else { return nil }

        // Prefer the common encodings screenshots and photos actually use.
        let preferred: [UTType] = [.png, .jpeg, .heic, .heif, .tiff, .gif, .bmp]
        for type in preferred {
            if let data = data(forPasteboardType: type.identifier) {
                return data
            }
        }

        // Fall back to any present representation that conforms to public.image.
        for identifier in types where UTType(identifier)?.conforms(to: .image) == true {
            if let data = data(forPasteboardType: identifier) {
                return data
            }
        }
        return nil
    }
}
