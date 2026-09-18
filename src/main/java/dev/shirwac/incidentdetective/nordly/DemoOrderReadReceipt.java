package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Backend-observed receipt for one read-only demo-order request.")
public record DemoOrderReadReceipt(
        String operation,
        int readOperations,
        int recordsReturned,
        int writeOperations,
        int aiCalls,
        boolean writeToolsAvailable,
        boolean actionExecuted
) {
    public DemoOrderReadReceipt {
        if (readOperations != 1
                || recordsReturned < 1
                || writeOperations != 0
                || aiCalls != 0
                || writeToolsAvailable
                || actionExecuted) {
            throw new IllegalArgumentException(
                    "Demo-order receipts must prove one read and no AI or writes"
            );
        }
    }

    static DemoOrderReadReceipt list(int recordsReturned) {
        return new DemoOrderReadReceipt(
                "list_demo_orders",
                1,
                recordsReturned,
                0,
                0,
                false,
                false
        );
    }

    static DemoOrderReadReceipt lookup() {
        return new DemoOrderReadReceipt(
                "get_demo_order",
                1,
                1,
                0,
                0,
                false,
                false
        );
    }
}
