// Release-test helper only. It is not copied into the distributed app.
import CoreGraphics
import Foundation

guard CommandLine.arguments.count == 2, let pid = Int(CommandLine.arguments[1]) else {
    exit(2)
}
let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements],
                                        kCGNullWindowID) as? [[String: Any]] ?? []
let ownVisibleWindows = windows.filter { window in
    guard (window[kCGWindowOwnerPID as String] as? Int) == pid,
          (window[kCGWindowLayer as String] as? Int) == 0,
          let bounds = window[kCGWindowBounds as String] as? [String: Any],
          let width = bounds["Width"] as? NSNumber,
          let height = bounds["Height"] as? NSNumber else { return false }
    return width.doubleValue >= 100 && height.doubleValue >= 100
}
// Never print names or metadata from other applications.
print(ownVisibleWindows.count)
exit(ownVisibleWindows.isEmpty ? 1 : 0)
