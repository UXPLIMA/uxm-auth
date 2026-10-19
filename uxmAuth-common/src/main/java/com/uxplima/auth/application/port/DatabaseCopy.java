package com.uxplima.auth.application.port;

import java.util.Map;

/** The plugin's tables, where they live now and at the database they move to. */
public interface DatabaseCopy extends AutoCloseable {

    /** The rows each table holds where the accounts live now. */
    Map<String, Long> sourceRows();

    /** The rows each table holds at the target, brought up to date before it is counted. */
    Map<String, Long> targetRows();

    /** Copies every row of every table, and answers how many it wrote to each. */
    Map<String, Long> copy();

    @Override
    void close();
}
