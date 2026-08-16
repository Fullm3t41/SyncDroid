# Mesh protocol

Platform-neutral version vectors, canonical signed payload construction, public identity helpers, signed overwrite-only exception models, and bounded mesh-bundle, pairing, peer-proof, session, index, and file-transfer wire codecs live here. Both applications keep private-key storage and signing, pairing key agreement, persistence, TLS sockets, networking, and filesystem operations while exchanging the exact shared bytes.

Compatibility is locked by `protocol/fixtures/shared-core-v1.properties`, `protocol/fixtures/wire-codecs-v1.properties`, and adapter tests in both applications.
