// Copyright (C) 2026 HoDoKu contributors. GPL-3.0-or-later.
// Collection helper only; never installed in the app or attached to an existing user process.
import Foundation
import CoreGraphics
import Darwin
import os.log

guard CommandLine.arguments.count == 4,
      let target = Int32(CommandLine.arguments[1]), target > 1 else { exit(2) }
let output = URL(fileURLWithPath: CommandLine.arguments[2])
let ready = URL(fileURLWithPath: CommandLine.arguments[3])
FileManager.default.createFile(atPath: output.path, contents: nil)
let file = try FileHandle(forWritingTo: output)
let started = DispatchTime.now().uptimeNanoseconds
let logger = OSLog(subsystem: "net.sourceforge.hodoku", category: "SaveLifecycle")
func record(_ phase: String, _ detail: [String: Any] = [:]) {
    var row = detail
    row["schema"] = 1
    row["source"] = "external_observer"
    row["pid"] = String(target)
    row["phase"] = phase
    row["observer_ms"] = Double(DispatchTime.now().uptimeNanoseconds - started) / 1e6
    row["wall_epoch_ms"] = Date().timeIntervalSince1970 * 1000
    if let data = try? JSONSerialization.data(withJSONObject: row, options: [.sortedKeys]) {
        file.write(data)
        file.write(Data([10]))
    }
}
func windows() -> Int? {
    guard let all = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements],
                                       kCGNullWindowID) as? [[String: Any]] else { return nil }
    return all.filter { item in
        guard (item[kCGWindowOwnerPID as String] as? Int32) == target,
              (item[kCGWindowLayer as String] as? Int) == 0,
              let bounds = item[kCGWindowBounds as String] as? [String: Any],
              let width = bounds["Width"] as? NSNumber,
              let height = bounds["Height"] as? NSNumber else { return false }
        return width.doubleValue >= 100 && height.doubleValue >= 100
    }.count
}
let queue = kqueue()
guard queue >= 0 else { record("observer.failed", ["errno": errno]); exit(3) }
var registration = kevent(ident: UInt(target), filter: Int16(EVFILT_PROC),
                          flags: UInt16(EV_ADD | EV_ENABLE | EV_ONESHOT),
                          fflags: UInt32(NOTE_EXIT), data: 0, udata: nil)
guard kevent(queue, &registration, 1, nil, 0, nil) == 0 else {
    record("observer.failed", ["errno": errno]); exit(3)
}
record("observer.ready", ["sample_interval_ms": 100])
try Data("ready\n".utf8).write(to: ready, options: .atomic)
var previous = -1
var samples = 0
var sampleMilliseconds = 0.0
var maximumSampleMilliseconds = 0.0
var lastSampleWall = 0.0
while true {
    let sampleStart = DispatchTime.now().uptimeNanoseconds
    let count = windows()
    let duration = Double(DispatchTime.now().uptimeNanoseconds - sampleStart) / 1e6
    samples += 1
    sampleMilliseconds += duration
    maximumSampleMilliseconds = max(maximumSampleMilliseconds, duration)
    lastSampleWall = Date().timeIntervalSince1970 * 1000
    if let count = count, count != previous {
        record("window.visible_count", ["count": count, "sample_duration_ms": duration])
        previous = count
    }
    if count == nil { record("window.unavailable") }
    var event = kevent()
    var timeout = timespec(tv_sec: 0, tv_nsec: 100_000_000)
    let received = kevent(queue, nil, 0, &event, 1, &timeout)
    if received < 0 {
        if errno == EINTR { continue }
        record("observer.failed", ["errno": errno]); exit(3)
    }
    if received > 0 {
        // Timestamp before any additional window sampling or JSON formatting.
        record("process.exit", ["event_fflags": event.fflags, "samples": samples,
                                "window_sampling_ms": sampleMilliseconds,
                                "max_window_sample_ms": maximumSampleMilliseconds,
                                "last_window_sample_wall_epoch_ms": lastSampleWall,
                                "last_window_count": count as Any? ?? NSNull()])
        os_log("Observed measured process exit; pid=%d", log: logger, type: .info, target)
        break
    }
}
file.synchronizeFile()
file.closeFile()
close(queue)
