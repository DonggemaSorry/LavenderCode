package com.lavendercode.core.provider;

import java.util.Iterator;

public interface StreamEventIterator extends Iterator<StreamEvent>, AutoCloseable {
    @Override
    void close();
}
