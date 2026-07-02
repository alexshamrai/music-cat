package io.github.alexshamrai.sheets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes all Sheets sync operations — pushes (clear-then-write of the tabs) and
 * pulls/boot restores (read of the tabs + DB rebuild). Without mutual exclusion a pull
 * could read a tab mid-overwrite (cleared but not yet rewritten), commit a gutted DB,
 * and a subsequent push would then destroy the spreadsheet.
 */
@Component
@ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
public class SheetsSyncLock {

    private final ReentrantLock lock = new ReentrantLock();

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
