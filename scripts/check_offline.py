#!/usr/bin/env python3
"""Fail if server plumbing or raw logging returns to the compiled offline source sets."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
for source in ("commonMain", "iosMain"):
    for file in (root / "core/src" / source).rglob("*.kt"):
        text = file.read_text()
        for token in ("io.ktor", "HttpUploader", "PositionQueue", "NetworkMonitor", "serverUrl", "deviceId", "NSURLSession", "println("):
            assert token not in text, f"Forbidden runtime plumbing {token}: {file}"
        assert "${position.latitude}" not in text and "${position.longitude}" not in text, file
assert "sqldelight" not in (root / "core/build.gradle.kts").read_text()
assert "TraccarClientAutoInit" not in (root / "Package.swift").read_text()
print("Offline source audit passed")
