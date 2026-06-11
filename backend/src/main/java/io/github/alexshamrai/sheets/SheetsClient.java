package io.github.alexshamrai.sheets;

import java.util.List;

/**
 * Thin abstraction over Google Sheets I/O.
 * Defined as an interface so sync services can be unit-tested with a mock.
 */
public interface SheetsClient {

    /**
     * Reads all rows from the given sheet tab (no header logic — caller decides).
     *
     * @param sheetName the tab name (e.g. "Artists")
     * @return all rows as a list of cell values; never null (empty list when tab is empty)
     */
    List<List<Object>> read(String sheetName);

    /**
     * Clears the given sheet tab and writes {@code rows} starting at A1.
     * Large datasets are written in chunks of 10,000 rows to stay within
     * the per-request size limit of the Sheets API.
     *
     * @param sheetName the tab name
     * @param rows      rows to write (header row should be the first element if desired)
     */
    void overwrite(String sheetName, List<List<Object>> rows);
}
