package com.marshallai.trading.execution;

public enum ExitReason {

    STOP,

    SELL,

    STALE,

    /**
     * Governed evaluation-period boundary liquidation.
     *
     * A surviving position must be closed using the final bar
     * close of the period as the execution reference.
     */
    PERIOD_END
}