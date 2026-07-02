package io.github.alexshamrai.startup;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessStateTest {

    @Test
    void awaitReady_alreadyReady_returnsTrueImmediately() throws InterruptedException {
        ReadinessState state = new ReadinessState();
        state.markReady();

        assertThat(state.awaitReady(0, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void awaitReady_neverReady_returnsFalseAfterTimeout() throws InterruptedException {
        ReadinessState state = new ReadinessState();

        assertThat(state.awaitReady(0, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    void awaitReady_becomesReadyDuringWait_returnsTrue() throws InterruptedException {
        ReadinessState state = new ReadinessState();
        Thread readyLater = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            state.markReady();
        });
        readyLater.start();

        assertThat(state.awaitReady(2, TimeUnit.SECONDS)).isTrue();
        readyLater.join();
    }
}
