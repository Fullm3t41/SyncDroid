# Synchronization core

The shared synchronization core owns file-level conflict classification, parent-content-hash resolution, content-block hashing, adaptive block sizing, safe relative-path normalization, index reconciliation, acknowledgement bounds, index export ranges, index-record validation, resumable-transfer progress, bounded mesh fan-out routing, and the all-participant overwrite-only exception finalization rule.

Room/SQLite persistence, temporary-file access, atomic application, networking, notifications, and operating-system integrations remain platform adapters.
