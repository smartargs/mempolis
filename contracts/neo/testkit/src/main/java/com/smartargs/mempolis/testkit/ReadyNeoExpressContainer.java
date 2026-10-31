package com.smartargs.mempolis.testkit;

import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoBlockCount;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.test.NeoExpressTestContainer;

import java.io.IOException;

/** The test node, which counts as started only once its RPC methods answer, not when its port opens. */
public final class ReadyNeoExpressContainer extends NeoExpressTestContainer {

    private static final long TIMEOUT_MILLIS = 60_000;
    private static final long POLL_MILLIS = 100;

    @Override
    public void start() {
        super.start();
        Neow3j neow3j = Neow3j.build(new HttpService(getNodeUrl()));
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!answers(neow3j)) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("The test node never answered RPC calls.");
            }
            pause();
        }
    }

    private static boolean answers(Neow3j neow3j) {
        try {
            NeoBlockCount response = neow3j.getBlockCount().send();
            return !response.hasError();
        } catch (IOException notListeningYet) {
            return false;
        }
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
