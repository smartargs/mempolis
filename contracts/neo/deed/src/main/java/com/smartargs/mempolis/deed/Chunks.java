package com.smartargs.mempolis.deed;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.StorageMap;

final class Chunks {

    private Chunks() {}

    static void append(StorageMap chunks, int held, ByteString data, int unit, int perChunk) {
        int offset = 0;
        while (offset < data.length()) {
            int index = held + offset / unit;
            int chunk = index / perChunk + 1;
            int room = (perChunk - index % perChunk) * unit;
            int take = data.length() - offset < room ? data.length() - offset : room;
            ByteString piece = data.range(offset, take);
            chunks.put(chunk, index % perChunk == 0 ? piece : chunks.get(chunk).concat(piece));
            offset += take;
        }
    }
}
